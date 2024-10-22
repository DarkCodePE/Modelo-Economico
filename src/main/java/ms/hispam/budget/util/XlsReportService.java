package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.ProjectionCache;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.repository.mysql.EmployeeClassificationRepository;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.service.ProjectionService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.LinkOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    private Set<String> conceptSheets = ConcurrentHashMap.newKeySet();
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
    private final ConcurrentHashMap<String, Lock> sheetLocks;
    private final SseReportService sseReportService;
    private final ProjectionUtils projectionUtils;
    private final ProjectionCache projectionCache;
    private final AsyncTaskExecutor asyncTaskExecutor;
    @Autowired
    public XlsReportService(ReportJobRepository reportJobRepository, ProjectionService service,
                            ExternalService externalService, EmailService emailService,
                            XlsSheetCreationService xlsSheetCreationService,
                            EmployeeClassificationRepository employeeClassificationRepository,
                            ConcurrentHashMap<String, Lock> sheetLocks,
                            SseReportService sseReportService, ProjectionUtils projectionUtils, ProjectionCache projectionCache,
                            @Qualifier("reportTaskExecutor") AsyncTaskExecutor asyncTaskExecutor) {
        this.reportJobRepository = reportJobRepository;
        this.service = service;
        this.externalService = externalService;
        this.emailService = emailService;
        this.xlsSheetCreationService = xlsSheetCreationService;
        this.employeeClassificationRepository = employeeClassificationRepository;
        this.sheetLocks = sheetLocks;
        this.sseReportService = sseReportService;
        this.projectionUtils = projectionUtils;
        this.projectionCache = projectionCache;
        this.asyncTaskExecutor = asyncTaskExecutor;
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
    public byte[] generateExcelProjection(ParametersByProjection projection, ProjectionSecondDTO data, DataBaseMainReponse dataBase, List<ComponentProjection> components, Integer idBu, String user, ReportJob reportJob, String sessionId) {
        //log.info("Número de hilos activos en el ejecutor: {}", ((ThreadPoolTaskExecutor) asyncTaskExecutor).getActiveCount());
        //log.info("Tamaño de la cola del ejecutor: {}", ((ThreadPoolTaskExecutor) asyncTaskExecutor).getThreadPoolExecutor().getQueue().size());
        sseReportService.sendUpdate(sessionId, "procesando", "preparando el archivo Excel", 5);
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        // vista Parametros
        sseReportService.sendUpdate(sessionId, "procesando", "Generando hojas de parámetros e input", 10);
        generateParameter(workbook, projection.getParameters());
        // vista Input
        generateInput(workbook, dataBase, projection);
        //Vista anual
        sseReportService.sendUpdate(sessionId, "procesando", "Generando vistas anuales y mensuales", 15);
        generateMoreView("Vista Anual", workbook, data, idBu);
        generateMoreViewMonth("Vista Mensual", workbook, data);
        sseReportService.sendUpdate(sessionId, "procesando", "Creando hojas de componentes", 25);
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
        //sseReportService.sendUpdatesseReportService.sendUpdate(sessionId, "procesando", "Creando hojas de componentes");
        //log.info("uniqueComponentNames: {}", uniqueComponentNames);
        sseReportService.sendUpdate(sessionId, "procesando", "Creando hojas de base externa", 30);
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
        AtomicInteger currentProgress = new AtomicInteger(35);
        int totalSheets = components.size() + projection.getBaseExtern().getHeaders().size();
        int progressPerSheet = 55 / totalSheets;
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
                                        processAndWriteDataInChunks(sheet, data.getViewPosition().getPositions(), 700, idBu, c.getComponent(), sessionId);
                                        sseReportService.sendDetailUpdate(sessionId, String.format("Procesado componente: %s", c.getName()));
                                    }
                                }, asyncTaskExecutor).thenRun(() -> {
                                    sseReportService.sendUpdate(sessionId, "generando", "Generando conceptos de pago", currentProgress.addAndGet(progressPerSheet));
                                });
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
                                        processAndWriteDataInChunks(sheet, data.getViewPosition().getPositions(), 500, idBu, c, sessionId);
                                        sseReportService.sendUpdate(sessionId, "generando",
                                                String.format("Procesando base externa: %s", c),
                                                currentProgress.addAndGet(progressPerSheet));
                                    }
                                }, asyncTaskExecutor);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        })
                        .toArray(CompletableFuture[]::new)
        );

        CompletableFuture.allOf(sheetCreationTasks, baseExternTasks).join();
        sseReportService.sendUpdate(sessionId, "finalizando", "Guardando el archivo Excel", 95);
        // Crear el índice de conceptos después de que todas las hojas se hayan creado
        //createConceptIndex(workbook, conceptSheets);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            sseReportService.sendUpdate(sessionId, "completado", "Reporte generado exitosamente", 100);
            return outputStream.toByteArray();
        } catch (IOException e) {
            sseReportService.sendUpdate(sessionId, "fallido", "Error al generar el reporte: " + e.getMessage(), 100);
            log.error("Error al generar el reporte: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private boolean hasDataForSheet(String component, List<ProjectionDTO> projections) {
        return projections.stream()
                .anyMatch(projectionDTO -> projectionDTO.getComponents().stream()
                        .anyMatch(u -> u.getPaymentComponent().equalsIgnoreCase(component)));
    }

    private static byte[] generatePlanner(ParametersByProjection parametersByProjection, List<ProjectionDTO> vdata, List<AccountProjection> accountProjections, Bu bu, ReportJob reportJob, String user) {
        try {
            SXSSFWorkbook workbook = new SXSSFWorkbook();

            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, Function.identity(), (existingValue, newValue) -> newValue));
            accountProjections = null; // Liberar memoria
            Map<GroupKey, GroupData> groupedData = new HashMap<>();
            double sum = 0.0;
            for (ProjectionDTO data : vdata) {
                //TODO AGREGAR EL POS DE BASE EXTERNA
                for (PaymentComponentDTO component : data.getComponents()) {
                    // filter by component name AF
                    for (MonthProjection projection : component.getProjections()) {
                        //log.info("Position desde report -> : {}", data.getPo());
                        // Obtener los datos de AF
                        Optional<Map<String, Object>> baseExternEntry = parametersByProjection.getBaseExtern().getData().stream()
                                .filter(r -> r.get("po").equals(data.getPo()))
                                .findFirst();
                        //log.debug("Base Externa Entry: {}", baseExternEntry);
                        String areaFuncional = baseExternEntry.map(r -> r.get("AF").toString()).orElse("");
                        //log.debug("Area Funcional: {}", areaFuncional);
                        // Include the month and position in the GroupKey
                        GroupKey key = new GroupKey(
                                mapaComponentesValidos.get(component.getPaymentComponent()).getAccount(),
                                areaFuncional,
                                data.getCCostos(),
                                component.getPaymentComponent(),
                                projection.getMonth(),
                                data.getPo(),  // Añadir la posición aquí
                                data.getIdssff()  // Añadir ID_SSFF aquí
                        );
                        GroupData groupData = groupedData.getOrDefault(key, new GroupData(new ArrayList<>(), new HashMap<>(), 0.0));
                        groupData.meses.add(projection.getMonth());
                        groupData.montoPorMes.put(projection.getMonth(), projection.getAmount().doubleValue());
                        groupData.sum += projection.getAmount().doubleValue();
                        sum += projection.getAmount().doubleValue();
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
            String[] headers = {
                    "Periodo ejecución/proyección", "Nombre Proyección", "ID_PO", "ID_SSFF",
                    "Actividad Funcional (B. Externa OCUP + VAC)", "CeCo (B.Case VAC)", "Concepto",
                    "Cuenta SAP", "Mes", "Monto", "Fecha Ejecución proyección", "Usuario ejecutador",
                    "Fecha de traspaso", "Usuario de traspaso", "Q de registros", "Importe Total", "País"
            };

            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (Map.Entry<GroupKey, GroupData> entry : groupedData.entrySet()) {
                GroupKey key = entry.getKey();
                GroupData groupData = entry.getValue();
                //double totalAmount = groupData.sum;
              /*  if (key.getPo().equals("BC123")) {
                    log.debug("GroupKey: {}", key);
                    log.debug("GroupData: {}", groupData);
                }*/

                for (String mes : groupData.meses) {
                    if (rowNum > 1048575) {
                        sheetNum++;
                        sheet = workbook.createSheet("PLANNER" + sheetNum);
                        rowNum = 0;
                        headerRow = sheet.createRow(rowNum++);
                        for (int i = 0; i < headers.length; i++) {
                            headerRow.createCell(i).setCellValue(headers[i]);
                            headerRow.getCell(i).setCellStyle(headerStyle);
                        }
                    }
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(parametersByProjection.getPeriod()); // Periodo ejecución/proyección
                    row.createCell(1).setCellValue(""); // Nombre Proyección (ejemplo)
                    row.createCell(2).setCellValue(key.getPo()); // ID_PO
                    row.createCell(3).setCellValue(key.getIdSsff()); // ID_SSFF
                    row.createCell(4).setCellValue(key.getActividadFuncional()); // Actividad Funcional
                    row.createCell(5).setCellValue(key.getCeCo()); // CeCo
                    row.createCell(6).setCellValue(key.getConcepto()); // Concepto
                    row.createCell(7).setCellValue(key.getCuentaSap()); // Cuenta SAP
                    row.createCell(8).setCellValue(mes); // Mes
                    row.createCell(9).setCellValue(groupData.montoPorMes.get(mes)); // Monto por Mes
                    //Fecha Ejecución proyección -> localDateTime
                    String fechaEjecucionProyeccion = LocalDate.now().toString();
                    row.createCell(10).setCellValue(fechaEjecucionProyeccion);
                    //Usuario ejecutador -> user
                    row.createCell(11).setCellValue(user);
                    //Fecha de traspaso -> localDateTime
                    row.createCell(12).setCellValue(reportJob.getCreationDate().toString());
                    //Usuario de traspaso -> user
                    row.createCell(13).setCellValue(user);
                    //Q de registros -> cantidad de registros
                    row.createCell(14).setCellValue(groupedData.size());
                    //Importe Total -> suma de los montos
                    row.createCell(15).setCellValue(sum);
                    //País -> user
                    row.createCell(16).setCellValue(bu.getBu());
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error al generar el planner", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el planner", e);
        }
    }

    private static byte[] generateCdg(ParametersByProjection parameters ,List<ProjectionDTO> vdata, Bu bu,List<AccountProjection> accountProjections, ReportJob reportJob, String user) {
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
                        Optional<Map<String, Object>> baseExternEntry = parameters.getBaseExtern().getData().stream()
                                .filter(r -> r.get("po").equals(data.getPo()))
                                .findFirst();
                        Map<String, Object> baseExtern = parameters.getBaseExtern().getData().stream()
                                .filter(r -> r.get("po").equals("BC123")).findFirst().orElse(null);
                        if (baseExtern != null) {
                            log.debug("Base Externa Entry: {}", baseExtern);
                        }
                        //log.debug("Base Externa Entry: {}", baseExternEntry);
                        String areaFuncional = baseExternEntry.map(r -> r.get("AF").toString()).orElse("");
                        log.debug("Area Funcional: {}", areaFuncional);
                        // Include the month and position in the GroupKey
                        GroupKey key = new GroupKey(
                                mapaComponentesValidos.get(component.getPaymentComponent()).getAccount(),
                                areaFuncional,
                                data.getCCostos(),
                                component.getPaymentComponent(),
                                projection.getMonth()
                        );

                        GroupData groupData = groupedData.getOrDefault(key, new GroupData(new ArrayList<>(), new HashMap<>(), 0.0));
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
            headerRow.createCell(0).setCellValue("Nombre Proyección");
            headerRow.createCell(1).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
            headerRow.createCell(2).setCellValue("CeCo (B.Case VAC)");
            headerRow.createCell(3).setCellValue("Concepto");
            headerRow.createCell(4).setCellValue("Cuenta SAP");
            headerRow.createCell(5).setCellValue("Mes");
            headerRow.createCell(6).setCellValue("Monto");
            for (int i = 0; i <= 6; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            for (Map.Entry<GroupKey, GroupData> entry : groupedData.entrySet()) {
                GroupKey key = entry.getKey();
                GroupData groupData = entry.getValue();

                for (String mes : groupData.meses) {
                    if (rowNum > 1048575) {
                        sheetNum++;
                        sheet = workbook.createSheet("CDG" + sheetNum);
                        rowNum = 0;
                        headerRow = sheet.createRow(rowNum++);
                        headerRow.createCell(0).setCellValue("Nombre Proyección");
                        headerRow.createCell(1).setCellValue("Actividad Funcional (B. Externa OCUP + VAC)");
                        headerRow.createCell(2).setCellValue("CeCo (B.Case VAC)");
                        headerRow.createCell(3).setCellValue("Concepto");
                        headerRow.createCell(4).setCellValue("Cuenta SAP");
                        headerRow.createCell(5).setCellValue("Mes");
                        headerRow.createCell(6).setCellValue("Monto");
                        for (int i = 0; i <= 6; i++) {
                            headerRow.getCell(i).setCellStyle(headerStyle);
                        }
                    }
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue("PPTO24"); // Nombre Proyección (ejemplo)
                    row.createCell(1).setCellValue(key.getActividadFuncional()); // Actividad Funcional
                    row.createCell(2).setCellValue(key.getCeCo()); // CeCo
                    row.createCell(3).setCellValue(key.getConcepto()); // Concepto
                    row.createCell(4).setCellValue(key.getCuentaSap()); // Cuenta SAP
                    row.createCell(5).setCellValue(mes); // Mes
                    row.createCell(6).setCellValue(groupData.sum); // Monto
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error al generar el reporte: " + e.getMessage());
            throw new RuntimeException(e);
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
                    .categoryLocal(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("categoryLocal")).orElse("").toString())
                    .estadoVacante(dataBase2.getBc().getData().stream().filter(r->r.get("po").equals(position)).findFirst().map(r->r.get("estadoVacante")).orElse("").toString())
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
                log.debug("Componente: {}", dataBase.getComponents().get(j).getComponent());
                if(dataBase.getComponents().get(j).getComponent().equalsIgnoreCase("Salario 14")){
                    log.debug("Se encontro el salario 14");
                }
                pdataCell = data.createCell(j+8);
                Cell finalPdataCell = pdataCell;
                int finalJ = j;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().trim().equalsIgnoreCase(dataBase.getComponents().get(finalJ).getComponent())).findFirst()
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

    private static void generateMoreView(String namePage, Workbook workbook, ProjectionSecondDTO projection, Integer idBu) {
        Sheet psheet = workbook.createSheet(namePage);
        Row pheader = psheet.createRow(0);

        // Agregar "Cuenta" y "Concepto" como las primeras dos columnas
        pheader.createCell(0).setCellValue("Cuenta");
        pheader.createCell(1).setCellValue("Concepto");

        int hstart = 2;  // Empezar desde la tercera columna para los años

        // Remover "Cuenta" y "Concepto" si ya están en yearProjections
        if (!projection.getYearProjections().isEmpty()) {
            if ("Cuenta".equals(projection.getYearProjections().get(0).getMonth())) {
                projection.getYearProjections().remove(0);
            }
            if (!projection.getYearProjections().isEmpty() && "Concepto".equals(projection.getYearProjections().get(0).getMonth())) {
                projection.getYearProjections().remove(0);
            }
        }

        for (MonthProjection yearProjection : projection.getYearProjections()) {
            Cell pheaderCell = pheader.createCell(hstart);
            pheaderCell.setCellValue(yearProjection.getMonth());
            hstart++;
        }

        int pstart = 1;

        for (ResumenComponentDTO resumeAccount : projection.getResumeComponent().getResumeComponentYear()) {
            Row row = psheet.createRow(pstart);
            row.createCell(0).setCellValue(resumeAccount.getAccount());
            row.createCell(1).setCellValue(resumeAccount.getComponent());

            int column = 2;
            for (MonthProjection year : resumeAccount.getProjections()) {
                Cell cell = row.createCell(column);
                cell.setCellValue(year.getAmount().doubleValue());
                column++;
            }
            pstart++;
        }
    }

    private static void generateMoreViewMonth(String namePage, Workbook workbook, ProjectionSecondDTO projection) {
        Sheet psheet = workbook.createSheet(namePage);
        Row pheader = psheet.createRow(0);

        // Agregar "Cuenta" y "Concepto" como las primeras dos columnas
        pheader.createCell(0).setCellValue("Cuenta");
        pheader.createCell(1).setCellValue("Concepto");

        int hstart = 2;  // Empezar desde la tercera columna para los meses

        // Remover "Cuenta" y "Concepto" si ya están en monthProjections
        if (!projection.getMonthProjections().isEmpty()) {
            if ("Cuenta".equals(projection.getMonthProjections().get(0).getMonth())) {
                projection.getMonthProjections().remove(0);
            }
            if (!projection.getMonthProjections().isEmpty() && "Concepto".equals(projection.getMonthProjections().get(0).getMonth())) {
                projection.getMonthProjections().remove(0);
            }
        }

        for (MonthProjection monthProjection : projection.getMonthProjections()) {
            Cell pheaderCell = pheader.createCell(hstart);
            try {
                pheaderCell.setCellValue(Shared.nameMonth(monthProjection.getMonth()));
            } catch (DateTimeParseException e) {
                // Si no se puede parsear como fecha, usar el valor original
                pheaderCell.setCellValue(monthProjection.getMonth());
            }
            hstart++;
        }

        int pstart = 1;

        for (ResumenComponentDTO resumeAccount : projection.getResumeComponent().getResumeComponentMonth()) {
            Row row = psheet.createRow(pstart);
            row.createCell(0).setCellValue(resumeAccount.getAccount());
            row.createCell(1).setCellValue(resumeAccount.getComponent());

            int column = 2;
            for (MonthProjection month : resumeAccount.getProjections()) {
                Cell cell = row.createCell(column);
                cell.setCellValue(month.getAmount().doubleValue());
                column++;
            }
            pstart++;
        }
    }

    // Modifica este método para que sea asíncrono
    public CompletableFuture<byte[]> generateExcelProjectionAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, Integer idBu, String userContact, ReportJob job, String sessionId, String reportName) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = ProjectionUtils.generateHash(projection);
            ProjectionSecondDTO data;
            // Verifica si la proyección ya está en la caché
            if (projectionCache.containsKey(cacheKey)) {
                log.info("Usando proyección de la caché para reporte con clave: {}", cacheKey);
                data = projectionCache.get(cacheKey);
            } else {
                // Si no está, genera la proyección y almacénala
                data = service.getNewProjection(projection,sessionId,reportName);
            }
            //projection.setViewPo(true);
            return generateExcelProjection(projection, data, dataBase, components, idBu, userContact, job, sessionId);
        }, asyncTaskExecutor);
    }

    public CompletableFuture<byte[]> generatePlannerAsync(ParametersByProjection projection, List<ProjectionDTO> vdata, List<AccountProjection> accountProjections, Bu bu, ReportJob job, String userContact) {
        return CompletableFuture.supplyAsync(() -> {
            //log.info("vdata: {}", vdata);
           return generatePlanner(projection, vdata,accountProjections, bu, job,userContact);
        });
    }
    //generateCdgAsync
    public CompletableFuture<byte[]> generateCdgAsync(ParametersByProjection projection,List<ProjectionDTO> vdata, Bu bu, List<AccountProjection> accountProjections, ReportJob job, String userContact) {
        return CompletableFuture.supplyAsync(() -> {
            return generateCdg(projection, vdata,bu,accountProjections,job,userContact);
        });
    }

    // Método para notificar al usuario, a ser implementado según tu mecanismo de notificación
    private void notifyUser(String notificationDetail, String userContact) {
        // Enviar notificación al usuario (correo electrónico, mensaje de texto, etc.)
        String subject = "Notificación de generación de reporte";
        emailService.sendSimpleMessage(userContact, subject, notificationDetail);
    }

    @Async
    public void generateAndCompleteReportAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, String userContact, ReportJob job, String user, Integer idBu, String sessionId, String reportName) {

        //sseReportService.sendUpdate(sessionId, "procesando", "procesando la información");

        generateExcelProjectionAsync(projection, components, dataBase, idBu, userContact, job, sessionId, reportName)
                .thenAccept(reportData -> {
                    //sseReportService.sendUpdate(sessionId, "generando", "Generando el archivo Excel");
                    //sseReportService.sendUpdate(sessionId, "subiendo", "Subiendo el archivo al almacenamiento");
                    job.setStatus("completado");
                    // Guarda el reporte en el almacenamiento externo
                    MultipartFile multipartFile = new ByteArrayMultipartFile(reportData, "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    FileDTO responseUpload =  externalService.uploadExcelReport(1,multipartFile);
                    job.setReportUrl(responseUpload.getPath());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    //sseReportService.sendUpdate(sessionId, "completado", "El reporte está listo para descargar");
                    sseReportService.completeEmitter(sessionId);
                    notifyUser("El reporte de proyección está listo para su descarga, vuelva a la aplicación para descargarlo", userContact);
                })
                .exceptionally(e -> {
                    job.setStatus("fallido");
                    job.setIdSsff(userContact);
                    job.setErrorMessage(String.format("Error al generar el reporte: %s - %s- %s", e.getMessage(), e.getCause(), Arrays.toString(e.getStackTrace())));
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    //sseReportService.sendUpdate(sessionId, "fallido", "Error al generar el reporte: " + e.getMessage());
                    notifyUser("Falló la generación del reporte de proyección para el usuario con el contacto: " + userContact , userContact);
                    log.error("Error al generar el reporte", (Object) e.getStackTrace());
                    log.info("Error al generar el reporte", e);
                    log.info("Error al generar el reporte", e.getCause());
                    return null;
                });
    }
    //generatePlannerAsync
    @Async
    public void generateAndCompleteReportAsyncPlanner(ParametersByProjection projection, List<ProjectionDTO> vdata, Bu bu,List<AccountProjection> accountProjections, ReportJob job, String userContact) {
        generatePlannerAsync(projection, vdata, accountProjections, bu, job, userContact)
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
        generateCdgAsync(projection, vdata, bu, accountProjections, job, userContact)
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

    private void processAndWriteDataInChunks(SXSSFSheet sheet, List<ProjectionDTO> projections, int chunkSize, Integer idBu, String component, String jobId) {
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
                //conceptSheets.add(sheet.getSheetName());
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
        //headerCell = header.createCell(3);
        //headerCell.setCellValue(Shared.nameMonth(period));
        int startHeader = 3;
        for (String m : Shared.generateRangeMonth(period, range)) {
            headerCell = header.createCell(startHeader);
            headerCell.setCellValue(m.trim());
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
                    /*cell = row.createCell(colNum++);
                    cell.setCellValue(componentDTO.get().getAmount().doubleValue());*/
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
