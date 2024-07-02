package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.repository.mysql.EmployeeClassificationRepository;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.service.ProjectionService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
@Component
@Slf4j(topic = "XlsReportService")
public class XlsReportService {

    private final ReportJobRepository reportJobRepository;

    private ProjectionService service;
    private final  ExternalService externalService;
    private static final ReentrantLock lock = new ReentrantLock();
    // Crear un ReentrantLock
    //private static final ReentrantLock lock = new ReentrantLock();
    private static final String[] headers = {"po","idssff"};
    private static final String[] headersInput = {"po","idssff","Nombre de la posición", "Tipo de Empleado", "Fecha de Nacimiento", "Fecha de Contratación", "Convenio", "Nivel"};

    private final EmailService emailService;
    private static final String[] headerParameter={"Tipo de Parametro","Periodo","Valor","Comparativo","Periodos comparativos","Rango"};
    private static final Set<String> sheetNames = ConcurrentHashMap.newKeySet();
    private final XlsSheetCreationService xlsSheetCreationService;

    // Repositorios adicionales
    private final EmployeeClassificationRepository employeeClassificationRepository;
    private static final Map<String, EmployeeClassification> classificationMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, Lock> sheetLocks;
    @Autowired
    public XlsReportService(ReportJobRepository reportJobRepository, ProjectionService service,
                            ExternalService externalService, EmailService emailService,
                            XlsSheetCreationService xlsSheetCreationService,
                            EmployeeClassificationRepository employeeClassificationRepository, ExecutorService executorService, ConcurrentHashMap<String, Lock> sheetLocks) {
        this.reportJobRepository = reportJobRepository;
        this.service = service;
        this.externalService = externalService;
        this.emailService = emailService;
        this.xlsSheetCreationService = xlsSheetCreationService;
        this.employeeClassificationRepository = employeeClassificationRepository;
        this.executorService = executorService;
        this.sheetLocks = sheetLocks;
    }

    @PostConstruct
    public void init() {
        List<EmployeeClassification> classifications = employeeClassificationRepository.findAll();
        for (EmployeeClassification classification : classifications) {
            classificationMap.put(classification.getCategory(), classification);
        }
    }

    @Autowired
    public void setService(@Lazy ProjectionService service) {
        this.service = service;
    }
    public byte[] generateExcelProjection(ParametersByProjection projection, ProjectionSecondDTO data, DataBaseMainReponse dataBase, List<ComponentProjection> components, Integer idBu) {
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        // vista Parametros
        generateParameter(workbook, projection.getParameters());
        // vista Input
        generateInput(workbook, dataBase, projection);
        //Vista anual
        generateMoreView("Vista Anual", workbook, data, idBu);
        generateMoreViewMonth("Vista Mensual", workbook, data);

        // Usar un conjunto para rastrear nombres únicos
        Set<String> sheetNames = new HashSet<>();
        Map<ComponentProjection, String> uniqueComponentNames = new ConcurrentHashMap<>();
        Map<String, String> uniqueHeaderNames = new ConcurrentHashMap<>();

      /*  log.info("List components: {}", components);
        log.info("List components: {}", components.size());
        log.info("uniqueComponentNames: {}", uniqueComponentNames);
        log.info("uniqueHeaderNames: {}", uniqueHeaderNames);*/

        // Primera pasada para recoger todos los nombres de las hojas y garantizar su unicidad
        components.stream()
                .filter(c -> (c.getIscomponent() && c.getShow()) || (!c.getIscomponent() && c.getShow()) || !c.getIsBase())
               /* .filter(d -> Objects.equals(d.getComponent(), "OVER_TIME")
                        || Objects.equals(d.getComponent(), "INCREASE_AFP")
                        || Objects.equals(d.getComponent(), "INCREASE_AFP_1023")
                        || Objects.equals(d.getComponent(), "MOBILITY_AND_REFRESHMENT")
                        || Objects.equals(d.getComponent(), "STORE_DAY")
                )*/
                .forEach(c -> {
                    String uniqueName = xlsSheetCreationService.generateUniqueSheetName(workbook, c.getName());
                    uniqueComponentNames.put(c, uniqueName); // Actualizar el nombre del componente con el nombre único
                });

        //log.info("uniqueComponentNames: {}", uniqueComponentNames);

        projection.getBaseExtern()
                .getHeaders()
                .stream()
                .filter(r -> Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(r)))
                .forEach(c -> {
                    String uniqueName = xlsSheetCreationService.generateUniqueSheetName(workbook, c);
                    uniqueHeaderNames.put(c, uniqueName); // Actualizar el nombre del header con el nombre único
                });

        // Segunda pasada para crear las hojas físicamente en el workbook
        // Pre-crear todas las hojas
        // Pre-crear todas las hojas
        uniqueComponentNames.values().forEach(sheetName -> {
            if (workbook.getSheet(sheetName) == null) {
                SXSSFSheet sheet = workbook.createSheet(sheetName);
                addHeaders(sheet, projection.getPeriod(), projection.getRange());
            }
        });
        uniqueHeaderNames.values().forEach(sheetName -> {
            if (workbook.getSheet(sheetName) == null) {
                SXSSFSheet sheet = workbook.createSheet(sheetName);
                addHeaders(sheet, projection.getPeriod(), projection.getRange());
            }
        });
        //log.info("uniqueComponentNames: {}", uniqueComponentNames);
        //log.info("uniqueHeaderNames: {}", uniqueHeaderNames);
        CompletableFuture<Void> sheetCreationTasks = CompletableFuture.allOf(
                components.stream()
                        .map(c -> {
                            if (hasDataForSheet(c.getComponent(), data.getViewPosition().getPositions())) {
                                String sheetName = uniqueComponentNames.get(c);
                                //log.info("Sheet name: {}", sheetName);
                                SXSSFSheet sheet = workbook.getSheet(sheetName);
                                return CompletableFuture.runAsync(() -> {
                                    if (sheet != null) {
                                        //log.info("Filling data in sheet: {}", sheetName);
                                        processAndWriteDataInChunks(sheet, data.getViewPosition().getPositions(), 500, idBu, c.getComponent());
                                    }
                                }, executorService);
                            } else {
                                //log.info("No data for sheet: {}", c.getComponent());
                                return CompletableFuture.completedFuture(null);
                            }
                        })
                        .toArray(CompletableFuture[]::new)
        );

        CompletableFuture<Void> baseExternTasks = CompletableFuture.allOf(
                projection.getBaseExtern()
                        .getHeaders()
                        .stream()
                        .filter(r -> !Arrays.stream(headers).anyMatch(c -> c.equalsIgnoreCase(r)))
                        .map(c -> {
                            if (hasDataForSheet(c, data.getViewPosition().getPositions())) {
                                String sheetName = uniqueHeaderNames.get(c);
                                SXSSFSheet sheet = workbook.getSheet(sheetName);
                                return CompletableFuture.runAsync(() -> {
                                    if (sheet != null) {
                                        //log.info("Filling data in base extern sheet: {}", sheetName);
                                        processAndWriteDataInChunks(sheet, data.getViewPosition().getPositions(), 500, idBu, c);
                                    }
                                }, executorService);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        })
                        .toArray(CompletableFuture[]::new)
        );

        CompletableFuture.allOf(sheetCreationTasks, baseExternTasks).join();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Error al generar el reporte: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private boolean hasDataForSheet(String component, List<ProjectionDTO> projections) {
        return projections.stream()
                .anyMatch(projectionDTO -> projectionDTO.getComponents().stream()
                        .anyMatch(u -> u.getPaymentComponent().equalsIgnoreCase(component)));
    }

    private static byte[] generatePlanner(List<ProjectionDTO> vdata, List<AccountProjection> accountProjections) {
        try {
            SXSSFWorkbook workbook = new SXSSFWorkbook();

            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, Function.identity(), (existingValue, newValue) -> newValue));
            accountProjections = null; // Liberar memoria
            Map<GroupKey, GroupData> groupedData = new HashMap<>();
            for (ProjectionDTO data : vdata) {
                for (PaymentComponentDTO component : data.getComponents()) {
                    // filter by component name AF
                    for (MonthProjection projection : component.getProjections()) {
                        // Include the month and position in the GroupKey
                        GroupKey key = new GroupKey(
                                mapaComponentesValidos.get(component.getPaymentComponent()).getAccount(),
                                data.getAreaFuncional(),
                                data.getCCostos(),
                                component.getPaymentComponent(),
                                projection.getMonth(),
                                data.getPo(),  // Añadir la posición aquí
                                data.getIdssff()  // Añadir ID_SSFF aquí
                        );

                        GroupData groupData = groupedData.getOrDefault(key, new GroupData(new ArrayList<>(), 0.0));
                        groupData.meses.add(projection.getMonth());
                        groupData.sum = projection.getAmount().doubleValue();
                        groupedData.put(key, groupData);
                    }
                    component.setProjections(null); // Liberar memoria
                }
                data.setComponents(null); // Liberar memoria
            }
            vdata = null; // Liberar memoria

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(createBoldFont(workbook));

            int sheetNum = 0;
            Sheet sheet = workbook.createSheet("PLANNER" + sheetNum);
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.createCell(0).setCellValue("Periodo ejecución/proyección");
            headerRow.createCell(1).setCellValue("Nombre Proyección");
            headerRow.createCell(2).setCellValue("ID_PO");
            headerRow.createCell(3).setCellValue("ID_SSFF");
            headerRow.createCell(4).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
            headerRow.createCell(5).setCellValue("CeCo (B.Case VAC)");
            headerRow.createCell(6).setCellValue("Concepto");
            headerRow.createCell(7).setCellValue("Cuenta SAP");
            headerRow.createCell(8).setCellValue("Mes");
            headerRow.createCell(9).setCellValue("Monto");
            for (int i = 0; i <= 9; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (Map.Entry<GroupKey, GroupData> entry : groupedData.entrySet()) {
                GroupKey key = entry.getKey();
                GroupData groupData = entry.getValue();

                for (String mes : groupData.meses) {
                    if (rowNum > 1048575) {
                        sheetNum++;
                        sheet = workbook.createSheet("PLANNER" + sheetNum);
                        rowNum = 0;
                        headerRow = sheet.createRow(rowNum++);
                        headerRow.createCell(0).setCellValue("Periodo ejecución/proyección");
                        headerRow.createCell(1).setCellValue("Nombre Proyección");
                        headerRow.createCell(2).setCellValue("ID_PO");
                        headerRow.createCell(3).setCellValue("ID_SSFF");
                        headerRow.createCell(4).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
                        headerRow.createCell(5).setCellValue("CeCo (B.Case VAC)");
                        headerRow.createCell(6).setCellValue("Concepto");
                        headerRow.createCell(7).setCellValue("Cuenta SAP");
                        headerRow.createCell(8).setCellValue("Mes");
                        headerRow.createCell(9).setCellValue("Monto");
                        for (int i = 0; i <= 9; i++) {
                            headerRow.getCell(i).setCellStyle(headerStyle);
                        }
                    }
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue("mes i -1"); // Periodo ejecución/proyección
                    row.createCell(1).setCellValue("PPTO24"); // Nombre Proyección (ejemplo)
                    row.createCell(2).setCellValue(key.getPo()); // ID_PO
                    row.createCell(3).setCellValue(key.getIdSsff()); // ID_SSFF
                    row.createCell(4).setCellValue(key.getActividadFuncional()); // Actividad Funcional
                    row.createCell(5).setCellValue(key.getCeCo()); // CeCo
                    row.createCell(6).setCellValue(key.getConcepto()); // Concepto
                    row.createCell(7).setCellValue(key.getCuentaSap()); // Cuenta SAP
                    row.createCell(8).setCellValue(mes); // Mes
                    row.createCell(9).setCellValue(groupData.sum); // Monto
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        }
    }

    private static byte[] generateCdg(ParametersByProjection parameters ,List<ProjectionDTO> vdata, Bu bu,List<AccountProjection> accountProjections) {
        try {
            SXSSFWorkbook workbook = new SXSSFWorkbook();

            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, Function.identity(), (existingValue, newValue) -> newValue));
            accountProjections = null; // Liberar memoria
            Map<GroupKey, GroupData> groupedData = new HashMap<>();
            for (ProjectionDTO data : vdata) {
                for (PaymentComponentDTO component : data.getComponents()) {
                    // filter by component name AF
                    for (MonthProjection projection : component.getProjections()) {
                        // Include the month and position in the GroupKey
                        GroupKey key = new GroupKey(
                                mapaComponentesValidos.get(component.getPaymentComponent()).getAccount(),
                                data.getAreaFuncional(),
                                data.getCCostos(),
                                component.getPaymentComponent(),
                                projection.getMonth(),
                                data.getPo(),  // Añadir la posición aquí
                                data.getIdssff()  // Añadir ID_SSFF aquí
                        );

                        GroupData groupData = groupedData.getOrDefault(key, new GroupData(new ArrayList<>(), 0.0));
                        groupData.meses.add(projection.getMonth());
                        groupData.sum = projection.getAmount().doubleValue();
                        groupedData.put(key, groupData);
                    }
                    component.setProjections(null); // Liberar memoria
                }
                data.setComponents(null); // Liberar memoria
            }
            vdata = null; // Liberar memoria

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(createBoldFont(workbook));

            int sheetNum = 0;
            Sheet sheet = workbook.createSheet("PLANNER" + sheetNum);
            int rowNum = 0;
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.createCell(0).setCellValue("Periodo ejecución/proyección");
            headerRow.createCell(1).setCellValue("Nombre Proyección");
            headerRow.createCell(2).setCellValue("ID_PO");
            headerRow.createCell(3).setCellValue("ID_SSFF");
            headerRow.createCell(4).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
            headerRow.createCell(5).setCellValue("CeCo (B.Case VAC)");
            headerRow.createCell(6).setCellValue("Concepto");
            headerRow.createCell(7).setCellValue("Cuenta SAP");
            headerRow.createCell(8).setCellValue("Mes");
            headerRow.createCell(9).setCellValue("Monto");
            for (int i = 0; i <= 9; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (Map.Entry<GroupKey, GroupData> entry : groupedData.entrySet()) {
                GroupKey key = entry.getKey();
                GroupData groupData = entry.getValue();

                for (String mes : groupData.meses) {
                    if (rowNum > 1048575) {
                        sheetNum++;
                        sheet = workbook.createSheet("PLANNER" + sheetNum);
                        rowNum = 0;
                        headerRow = sheet.createRow(rowNum++);
                        headerRow.createCell(0).setCellValue("Periodo ejecución/proyección");
                        headerRow.createCell(1).setCellValue("Nombre Proyección");
                        headerRow.createCell(2).setCellValue("ID_PO");
                        headerRow.createCell(3).setCellValue("ID_SSFF");
                        headerRow.createCell(4).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
                        headerRow.createCell(5).setCellValue("CeCo (B.Case VAC)");
                        headerRow.createCell(6).setCellValue("Concepto");
                        headerRow.createCell(7).setCellValue("Cuenta SAP");
                        headerRow.createCell(8).setCellValue("Mes");
                        headerRow.createCell(9).setCellValue("Monto");
                        for (int i = 0; i <= 9; i++) {
                            headerRow.getCell(i).setCellStyle(headerStyle);
                        }
                    }
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue("mes i -1"); // Periodo ejecución/proyección
                    row.createCell(1).setCellValue("PPTO24"); // Nombre Proyección (ejemplo)
                    row.createCell(2).setCellValue(key.getPo()); // ID_PO
                    row.createCell(3).setCellValue(key.getIdSsff()); // ID_SSFF
                    row.createCell(4).setCellValue(key.getActividadFuncional()); // Actividad Funcional
                    row.createCell(5).setCellValue(key.getCeCo()); // CeCo
                    row.createCell(6).setCellValue(key.getConcepto()); // Concepto
                    row.createCell(7).setCellValue(key.getCuentaSap()); // Cuenta SAP
                    row.createCell(8).setCellValue(mes); // Mes
                    row.createCell(9).setCellValue(groupData.sum); // Monto
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        }
    }

    // Método auxiliar para crear un Font en negrita
    private static Font createBoldFont(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        return font;
    }

    // Método auxiliar para crear celdas con estilo
    private static void createStyledCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void generateParameter(Workbook workbook ,List<ParametersDTO> parameters){
        Sheet psheet = workbook.createSheet("Parametros");
        psheet.setColumnWidth(0, 7000);
        Row pheader = psheet.createRow(0);
        Cell pheaderCell = pheader.createCell(0);
        int hstart=1;
        for (int i = 0; i < headerParameter.length; i++) {
            pheaderCell.setCellValue(headerParameter[i]);
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;
        for (int i = 0; i < parameters.size(); i++) {
            ParametersDTO pam = parameters.get(i);
            Row data = psheet.createRow(pstart);
            Cell pdataCell = data.createCell(0);
            pdataCell.setCellValue(pam.getParameter().getDescription());
            pdataCell = data.createCell(1);
            pdataCell.setCellValue(pam.getPeriod());
            pdataCell = data.createCell(2);
            pdataCell.setCellValue(pam.getValue());
            pdataCell = data.createCell(3);
            pdataCell.setCellValue(pam.getIsRetroactive());
            pdataCell = data.createCell(4);
            pdataCell.setCellValue(pam.getPeriodRetroactive());
            pdataCell = data.createCell(5);
            pdataCell.setCellValue(pam.getRange());
            pstart++;
        }
    }
    private static void generateInput(Workbook workbook ,DataBaseMainReponse dataBase,ParametersByProjection dataBase2){
        Sheet psheet = workbook.createSheet("Input");
        psheet.setColumnWidth(0, 7000);
        Row pheader = psheet.createRow(0);
        Cell pheaderCell = pheader.createCell(0);
        int hstart=1;
        for (int i = 0; i < headersInput.length; i++) {
            pheaderCell.setCellValue(headersInput[i]);
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }

        for (int i = 0; i < dataBase.getComponents().size(); i++) {
            pheaderCell.setCellValue(dataBase.getComponents().get(i).getName());
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        for (int i = 0; i < dataBase.getNominas().size(); i++) {
            pheaderCell.setCellValue(dataBase.getNominas().get(i).getName());
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }

        List<String> headers = dataBase2
                .getBaseExtern()
                .getHeaders()
                .stream()
                .filter(
                    t->!t.equals("po") && !t.equals("idssff")
                ).collect(Collectors.toList());
       /* log.debug("Headers: {}", headers);*/
        for (int i = 0; i < headers.size(); i++) {
            pheaderCell.setCellValue(headers.get(i));
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;

        Map<String, List<ComponentAmount>> positionMap = new HashMap<>();

        for (Map<String, Object> t : dataBase2.getBc().getData()) {

            String position = t.get("po").toString();
            List<ComponentAmount> components = dataBase.getComponents()
                    .stream()
                    .map(k -> ComponentAmount.builder()
                            .component(k.getComponent())
                            .amount(t.get(k.getName()) != null ? new BigDecimal(t.get(k.getName()).toString()) : BigDecimal.ZERO)
                            .build()
                    )
                    .collect(Collectors.toList());
            //log.debug("Position: {}", position);
            components.addAll(dataBase.getNominas().stream().map(k ->
                    ComponentAmount.builder()
                            .component(k.getCodeNomina())
                            .amount(t.get(k.getName()) != null ? new BigDecimal(t.get(k.getName()).toString()) : BigDecimal.ZERO)
                            .build()).collect(Collectors.toList()));

            if (positionMap.containsKey(position)) {
                positionMap.get(position).addAll(components);
            } else {
                positionMap.put(position, components);
            }
            //log.debug("Position: {}", position);
            //log.debug("dataBase2.getBaseExtern(): {}", dataBase2.getBaseExtern().getHeaders());
            //log.debug("test -> {}", dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)));
            DataBaseResponse newResponse = DataBaseResponse.builder()
                    .po(position)
                    .idssff("")
                    .poName(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("name")).orElse("Nueva posición").toString())
                    .typeEmployee(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("typeEmployee")).orElse("").toString())
                    .birthDate(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("FNAC")).orElse("").toString())
                    .hiringDate(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("FCON")).orElse("").toString())
                    .convent(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("CONV")).orElse("").toString())
                    .level(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("NIV")).orElse("").toString())
                    .components(positionMap.get(position))
                    .build();
            //log.debug("newResponse: {}", newResponse);
            int index = -1;
            for (int i = 0; i < dataBase.getData().size(); i++) {
                if (dataBase.getData().get(i).getPo().equals(position)) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                dataBase.getData().remove(index);
                dataBase.getData().add(index, newResponse);
            } else {
                dataBase.getData().add(newResponse);
            }
        }

        dataBase.getData().forEach(r->{
            List<ComponentAmount> bex = headers.stream().map(k->{
                Optional<Map<String, Object>>   d =   dataBase2.getBaseExtern().getData().stream()
                            .filter(h->h.get("po").equals(r.getPo()) ).findFirst();
                        return ComponentAmount.builder()
                                .component(k)
                                .amount(d.map(stringObjectMap -> stringObjectMap.get(k)!=null?new BigDecimal(stringObjectMap.get(k).toString()): BigDecimal.ZERO).orElse(BigDecimal.ZERO))
                                .build();
                    }).collect(Collectors.toList());
            r.getComponents().addAll(bex);
        });

        for (int i = 0; i < dataBase.getData().size(); i++) {
            Row data = psheet.createRow(pstart);
            Cell pdataCell = data.createCell(0);
            pdataCell.setCellValue(dataBase.getData().get(i).getPo());
            pdataCell = data.createCell(1);
            pdataCell.setCellValue(dataBase.getData().get(i).getIdssff());
            pdataCell = data.createCell(2);
            pdataCell.setCellValue(dataBase.getData().get(i).getPoName());
            pdataCell = data.createCell(3);
            pdataCell.setCellValue(dataBase.getData().get(i).getTypeEmployee());
            pdataCell = data.createCell(4);
            pdataCell.setCellValue(dataBase.getData().get(i).getBirthDate());
            pdataCell = data.createCell(5);
            pdataCell.setCellValue(dataBase.getData().get(i).getHiringDate());
            pdataCell = data.createCell(6);
            pdataCell.setCellValue(dataBase.getData().get(i).getConvent());
            pdataCell = data.createCell(7);
            pdataCell.setCellValue(dataBase.getData().get(i).getLevel());
            int starDetail=0;
            for (int j = 0; j < dataBase.getComponents().size(); j++) {
                pdataCell = data.createCell(j+8);
                Cell finalPdataCell = pdataCell;
                int finalJ = j;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().equalsIgnoreCase(dataBase.getComponents().get(finalJ).getComponent())).findFirst()
                        .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                                ()->finalPdataCell.setCellValue(0));
                starDetail++;
            }
            for (int k = 0; k < dataBase.getNominas().size(); k++) {
                pdataCell = data.createCell(starDetail+8);
                Cell finalPdataCell = pdataCell;
                int finalK= k;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().equalsIgnoreCase(dataBase.getNominas().get(finalK).getCodeNomina())).findFirst()
                        .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                                ()->finalPdataCell.setCellValue(0));
                starDetail++;
            }
            for (int k = 0; k < headers.size(); k++) {
                pdataCell = data.createCell(starDetail+8);
                Cell finalPdataCell = pdataCell;
                int finalK= k;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().equalsIgnoreCase(headers.get(finalK))).findFirst()
                        .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                                ()->finalPdataCell.setCellValue(0));
                starDetail++;
            }
            pstart++;
        }
    }

    private static void generateMoreView(String namePage,Workbook workbook ,ProjectionSecondDTO projection, Integer idBu){
        Sheet psheet = workbook.createSheet(namePage);
        Row pheader = psheet.createRow(0);
        Cell pheaderCell = pheader.createCell(0);
        int hstart=1;
       projection.getYearProjections().add(0,MonthProjection.builder().month("Cuenta").build());
        projection.getYearProjections().add(1,MonthProjection.builder().month("Concepto").build());

        for (int i = 0; i < projection.getYearProjections().size(); i++) {
            pheaderCell.setCellValue(projection.getYearProjections().get(i).getMonth());
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;

        for (ResumenComponentDTO resumeAccount : projection.getResumeComponent().getResumeComponentYear()) {
            Row row = psheet.createRow(pstart);
            Cell cell = row.createCell(0);
            cell.setCellValue(resumeAccount.getAccount());
            cell = row.createCell(1);
            cell.setCellValue(resumeAccount.getComponent());
                int column = 2;
                for (int k = 0; k < resumeAccount.getProjections().size(); k++) {
                    MonthProjection month = resumeAccount.getProjections().get(k);
                    cell = row.createCell(column);
                    cell.setCellValue(month.getAmount().doubleValue());
                    column++;
                }
            pstart++;

        }

    }

    private static void generateMoreViewMonth(String namePage,Workbook workbook ,ProjectionSecondDTO projection){
        Sheet psheet = workbook.createSheet(namePage);
        Row pheader = psheet.createRow(0);
        Cell pheaderCell = pheader.createCell(0);
        int hstart=1;
        projection.getMonthProjections().add(0,MonthProjection.builder().month("Cuenta").build());
        projection.getMonthProjections().add(1,MonthProjection.builder().month("Concepto").build());

        for (int i = 0; i < projection.getMonthProjections().size(); i++) {
            pheaderCell.setCellValue(i>1 ?Shared.nameMonth(projection.getMonthProjections().get(i).getMonth()):projection.getMonthProjections().get(i).getMonth());
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;

        for (ResumenComponentDTO resumeAccount : projection.getResumeComponent().getResumeComponentMonth()) {
            Row row = psheet.createRow(pstart);
            Cell cell = row.createCell(0);
            cell.setCellValue(resumeAccount.getAccount());
            cell = row.createCell(1);
            cell.setCellValue(resumeAccount.getComponent());
            int column = 2;
            for (int k = 0; k < resumeAccount.getProjections().size(); k++) {
                MonthProjection month = resumeAccount.getProjections().get(k);
                cell = row.createCell(column);
                cell.setCellValue(month.getAmount().doubleValue());
                column++;
            }
            pstart++;

        }

    }

    private static Sheet createSheetWithUniqueName(Workbook workbook, String baseName) {
        lock.lock();
        try {
            String uniqueName = baseName;
            int index = 1;
            while (workbook.getSheet(uniqueName) != null || sheetNames.contains(uniqueName)) {
                uniqueName = baseName + " (" + index++ + ")";
                if (index > 3) {
                    throw new RuntimeException("No se pudo crear una hoja con un nombre único después de varios intentos.");
                }
            }
            sheetNames.add(uniqueName);
            return workbook.createSheet(uniqueName);
        } finally {
            lock.unlock();
        }
    }
    private final Object sheetLock = new Object();
    private void clearSheet(SXSSFSheet sheet) {
        synchronized (sheetLock) {
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                SXSSFRow row = sheet.getRow(i);
                if (row != null) {
                    for (int j = 0; j < row.getLastCellNum(); j++) {
                        SXSSFCell cell = row.getCell(j);
                        if (cell != null) {
                            cell.setCellValue(""); // Limpiar el contenido de la celda
                        }
                    }
                }
            }
        }
    }

    private void writeExcelPageNewExcel(SXSSFSheet sheet, String component, String period, Integer range, List<ProjectionDTO> projection, Integer idBu) {
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);

        if (sheet.getPhysicalNumberOfRows() > 0) {
            // No se pueden limpiar las filas en SXSSF, crear una nueva hoja temporal
            sheet = createTemporarySheet(sheet.getWorkbook(), sheet.getSheetName());
        }

        Row header = sheet.createRow(0);
        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("POSSFF");
        headerCell = header.createCell(1);
        headerCell.setCellValue("IDSSFF");
        headerCell = header.createCell(2);
        headerCell.setCellValue("TIPO DE EMPLEADO");
        headerCell = header.createCell(3);
        headerCell.setCellValue(Shared.nameMonth(period));
        int startHeader = 4;
        for (String m : Shared.generateRangeMonth(period, range)) {
            headerCell = header.createCell(startHeader);
            headerCell.setCellValue(m);
            startHeader++;
        }

        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setWrapText(true);
        int start = 1;
        for (ProjectionDTO projectionDTO : projection) {
            Row row = sheet.createRow(start);
            Cell cell = row.createCell(0);
            cell.setCellValue(projectionDTO.getPo());
            cell.setCellStyle(style);
            cell = row.createCell(1);
            cell.setCellValue(projectionDTO.getIdssff());
            cell.setCellStyle(style);
            cell = row.createCell(2);
            String type;
            boolean isCp = projectionDTO.getPoName() != null && projectionDTO.getPoName().contains("CP");
            if (idBu == 4) {
                type = isCp ? "CP" : "NO CP";
            } else if (idBu == 5) {
                String localCategory = projectionDTO.getCategoryLocal();
                if (localCategory == null) {
                    type = "No se encontró la categoría";
                } else {
                    Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(localCategory.toUpperCase()));
                    if (optionalEmployeeClassification.isPresent()) {
                        type = optionalEmployeeClassification.get().getCategory();
                    } else {
                        type = String.format("No se encontró la categoría %s", localCategory);
                    }
                }
            } else {
                type = projectionDTO.getClassEmployee();
            }
            cell.setCellValue(type);
            cell.setCellStyle(style);

            Optional<PaymentComponentDTO> componentDTO = projectionDTO.getComponents().stream()
                    .filter(u -> u.getPaymentComponent().equalsIgnoreCase(component))
                    .findFirst();

            if (componentDTO.isPresent()) {
                //log.info("Componente y->: {}", componentDTO.get().getPaymentComponent());
                cell = row.createCell(3);
                cell.setCellValue(componentDTO.get().getAmount().doubleValue());
                cell.setCellStyle(style);
                int column = 4;
                for (MonthProjection month : componentDTO.get().getProjections()) {
                    cell = row.createCell(column);
                    cell.setCellValue(month.getAmount().doubleValue());
                    cell.setCellStyle(style);
                    column++;
                }
                start++;
            }
        }
    }

    private SXSSFSheet createTemporarySheet(SXSSFWorkbook workbook, String originalSheetName) {
        // Crea una nueva hoja temporal con un nombre único
        return workbook.createSheet(originalSheetName + "_temp");
    }

    // Añade un ExecutorService para la ejecución de tareas asíncronas
    /*private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());*/

    // Modifica este método para que sea asíncrono
    public CompletableFuture<byte[]> generateExcelProjectionAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, Integer idBu) {
        return CompletableFuture.supplyAsync(() -> {
            projection.setViewPo(true);
            ProjectionSecondDTO data = service.getNewProjection(projection);
            //log.info("Data: {}", data);
            return generateExcelProjection(projection, data, dataBase, components, idBu);
        }, executorService);
    }

    public static CompletableFuture<byte[]> generatePlannerAsync(List<ProjectionDTO> vdata, List<AccountProjection> accountProjections) {
        return CompletableFuture.supplyAsync(() -> {
            //log.info("vdata: {}", vdata);
           return generatePlanner(vdata,accountProjections);
        });
    }
    //generateCdgAsync
    public static CompletableFuture<byte[]> generateCdgAsync(ParametersByProjection projection,List<ProjectionDTO> vdata, Bu bu, List<AccountProjection> accountProjections) {
        return CompletableFuture.supplyAsync(() -> {
            return generateCdg(projection, vdata,bu,accountProjections);
        });
    }

    // Método para notificar al usuario, a ser implementado según tu mecanismo de notificación
    private void notifyUser(String notificationDetail, String userContact) {
        // Enviar notificación al usuario (correo electrónico, mensaje de texto, etc.)
        String subject = "Notificación de generación de reporte";
        emailService.sendSimpleMessage(userContact, subject, notificationDetail);
    }

    @Async
    public void generateAndCompleteReportAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, String userContact, ReportJob job, String user, Integer idBu) {
        generateExcelProjectionAsync(projection, components, dataBase, idBu)
                .thenAccept(reportData -> {
                    job.setStatus("completado");
                    // Guarda el reporte en el almacenamiento externo
                    MultipartFile multipartFile = new ByteArrayMultipartFile(reportData, "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    FileDTO responseUpload =  externalService.uploadExcelReport(1,multipartFile);
                    job.setReportUrl(responseUpload.getPath());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("El reporte de proyección está listo para su descarga, vuelva a la aplicación para descargarlo", userContact);
                })
                .exceptionally(e -> {
                    job.setStatus("fallido");
                    job.setIdSsff(userContact);
                    job.setErrorMessage(String.format("Error al generar el reporte: %s - %s- %s", e.getMessage(), e.getCause(), Arrays.toString(e.getStackTrace())));
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("Falló la generación del reporte de proyección para el usuario con el contacto: " + userContact , userContact);
                    log.error("Error al generar el reporte", (Object) e.getStackTrace());
                    log.info("Error al generar el reporte", e);
                    log.info("Error al generar el reporte", e.getCause());
                    return null;
                });
    }
    //generatePlannerAsync
    @Async
    public void generateAndCompleteReportAsyncPlanner(List<ProjectionDTO> vdata, List<AccountProjection> accountProjections, ReportJob job, String userContact) {
        generatePlannerAsync(vdata, accountProjections)
                .thenAccept(reportData -> {
                    job.setStatus("completado");
                    // Guarda el reporte en el almacenamiento externo
                    MultipartFile multipartFile = new ByteArrayMultipartFile(reportData, "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    FileDTO responseUpload =  externalService.uploadExcelReport(1,multipartFile);
                    job.setReportUrl(responseUpload.getPath());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("El reporte para planner está listo para su descarga, vuelva a la aplicación para descargarlo", userContact);
                })
                .exceptionally(e -> {
                    job.setStatus("fallido");
                    job.setIdSsff(userContact);
                    job.setErrorMessage(e.getMessage());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("Falló la generación del reporte para planner para el usuario con el contacto: " + userContact , userContact);
                    log.error("Error al generar el reporte", e);
                    return null;
                });
    }
    //generateCdgAsync
    @Async
    public void generateAndCompleteReportAsyncCdg(ParametersByProjection projection, List<ProjectionDTO> vdata, Bu bu, List<AccountProjection> accountProjections, ReportJob job, String userContact) {
        generateCdgAsync(projection, vdata, bu, accountProjections)
                .thenAccept(reportData -> {
                    job.setStatus("completado");
                    // Guarda el reporte en el almacenamiento externo
                    MultipartFile multipartFile = new ByteArrayMultipartFile(reportData, "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    FileDTO responseUpload =  externalService.uploadExcelReport(1,multipartFile);
                    job.setReportUrl(responseUpload.getPath());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("El reporte para CDG está listo para su descarga, vuelva a la aplicación para descargarlo", userContact);
                })
                .exceptionally(e -> {
                    job.setStatus("fallido");
                    job.setIdSsff(userContact);
                    job.setErrorMessage(e.getMessage());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("Falló la generación del reporte para CDG para el usuario con el contacto: " + userContact , userContact);
                    log.error("Error al generar el reporte", e);
                    return null;
                });
    }


    private void processAndWriteDataInChunks(SXSSFSheet sheet, List<ProjectionDTO> projections, int chunkSize, Integer idBu, String component) {
        //String sheetName = sheet.getSheetName();
        //log.info("Sheet name: {}", sheetName);
        //String truncatedSheetName = sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
        Lock lock = sheetLocks.get(sheet.getSheetName());
        //log.info("Sheet name: {}, component: {}", sheet.getSheetName(), component);
        if (lock != null) {
            lock.lock();
            try {
                int start = 0;
                while (start < projections.size()) {
                    int end = Math.min(start + chunkSize, projections.size());
                    List<ProjectionDTO> chunk = projections.subList(start, end);
                    writeChunkToSheet(sheet, chunk, start, idBu, component);
                    start = end;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void addHeaders(SXSSFSheet sheet, String period, int range) {
        Row header = sheet.createRow(0);
        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("POSSFF");
        headerCell = header.createCell(1);
        headerCell.setCellValue("IDSSFF");
        headerCell = header.createCell(2);
        headerCell.setCellValue("TIPO DE EMPLEADO");
        headerCell = header.createCell(3);
        headerCell.setCellValue(Shared.nameMonth(period));
        int startHeader = 4;
        for (String m : Shared.generateRangeMonth(period, range)) {
            headerCell = header.createCell(startHeader);
            headerCell.setCellValue(m);
            startHeader++;
        }
    }


    private void writeChunkToSheet(SXSSFSheet sheet, List<ProjectionDTO> chunk, int startRow, Integer idBu, String component) {
        //log.info("Writing chunk to sheet: {}", sheet.getSheetName());
        int rowNumber = startRow + 1; // Continuar después de la cabecera

        for (ProjectionDTO projectionDTO : chunk) {
            // Verificar si la fila ya ha sido escrita
            if (rowNumber > sheet.getLastRowNum()) {
                //log.info("Creating new row: {}", rowNumber);
                Row row = sheet.createRow(rowNumber++);
                int colNum = 0;
                Cell cell = row.createCell(colNum++);
                cell.setCellValue(projectionDTO.getPo());

                cell = row.createCell(colNum++);
                cell.setCellValue(projectionDTO.getIdssff());

                // Manejo de diferentes tipos de empleados
                cell = row.createCell(colNum++);
                String type = determineEmployeeType(projectionDTO, idBu);
                cell.setCellValue(type);

                Optional<PaymentComponentDTO> componentDTO = projectionDTO.getComponents()
                        .stream()
                        .filter(u -> u.getPaymentComponent().equalsIgnoreCase(component))
                        .findFirst();
                //log.info("Componente original->: {}", component);
                if (componentDTO.isPresent()) {
                    //log.info("Componente name->: {}", componentDTO.get().getPaymentComponent());
                    cell = row.createCell(colNum++);
                    cell.setCellValue(componentDTO.get().getAmount().doubleValue());
                    for (MonthProjection monthProjection : componentDTO.get().getProjections()) {
                        cell = row.createCell(colNum++);
                        cell.setCellValue(monthProjection.getAmount().doubleValue());
                    }
                }
            }
        }
    }
    private String determineEmployeeType(ProjectionDTO projectionDTO, Integer idBu) {
        String type;
        if (idBu == 4) {
            type = projectionDTO.getPoName() != null && projectionDTO.getPoName().contains("CP") ? "CP" : "NO CP";
        } else if (idBu == 5) {
            String localCategory = projectionDTO.getCategoryLocal();
            if (localCategory == null) {
                type =  "No se encontró la categoría";
            } else {
                Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(localCategory.toUpperCase()));
                if (optionalEmployeeClassification.isPresent()) {
                    type = optionalEmployeeClassification.get().getCategory();
                } else {
                    type = String.format("No se encontró la categoría %s", localCategory);
                }
            }
        } else {
            type = projectionDTO.getClassEmployee();
        }
        return type;
    }
}
