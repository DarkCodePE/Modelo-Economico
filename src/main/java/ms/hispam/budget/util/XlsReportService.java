package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.service.ProjectionService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
@Component
@Slf4j(topic = "XlsReportService")
public class XlsReportService {

    private final ReportJobRepository reportJobRepository;

    private  ProjectionService service;
    private final  ExternalService externalService;
    // Crear un ReentrantLock
    private static final ReentrantLock lock = new ReentrantLock();
    private static final String[] headers = {"po","idssff"};
    private static final String[] headersInput = {"po","idssff","Nombre de la posición"};

    private final EmailService emailService;
    private static final String[] headerParameter={"Tipo de Parametro","Periodo","Valor","Comparativo","Periodos comparativos","Rango"};

    public XlsReportService(ReportJobRepository reportJobRepository,  ExternalService externalService, EmailService emailService) {
        this.reportJobRepository = reportJobRepository;
        this.externalService = externalService;
        this.emailService = emailService;
    }
    @Autowired
    public void setService(@Lazy ProjectionService service) {
        this.service = service;
    }
    public static byte[] generateExcelProjection(ParametersByProjection projection ,ProjectionSecondDTO data, DataBaseMainReponse dataBase,List<ComponentProjection> components){

        SXSSFWorkbook workbook = new SXSSFWorkbook();
        // vista Parametros
        generateParameter(workbook,projection.getParameters());
        // vista Input
        generateInput(workbook,dataBase,projection);
        //Vista anual
        generateMoreView("Vista Anual",workbook,data);
        generateMoreViewMonth("Vista Mensual",workbook,data);

        components.stream()
                //.filter(c -> c.getName().equals("Consolidado De Intereses De Cesantias - Temporales"))
                .filter(c->(c.getIscomponent() && c.getShow()) || (!c.getIscomponent() && c.getShow()))
                //.limit(30)
                .forEach(c-> writeExcelPage(workbook,c.getName(),c.getComponent(),projection.getPeriod(),projection.getRange(),
                        data.getViewPosition().getPositions()));

     projection.getBaseExtern()
                .getHeaders()
                .stream()
                .filter(r-> Arrays.stream(headers).noneMatch(c->c.equalsIgnoreCase(r)))
                .forEach(c-> writeExcelPage(workbook,c,c,projection.getPeriod(),projection.getRange(),
                        data.getViewPosition().getPositions()));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Error al generar el reporte", e);
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateExcelType(List<ProjectionDTO> vdata, Integer type, Bu bu, List<AccountProjection> accountProjections) {
        if(type==2){
            return generatePlanner(vdata,accountProjections);
        }else if (type==3){
            return generateCdg(vdata,bu,accountProjections);
        }
        return null;
    }

    private static byte[] generatePlanner(List<ProjectionDTO> vdata, List<AccountProjection> accountProjections){
        try {

            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, componente -> componente));
            // Crea un nuevo libro de Excel
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Proyecciones de Pago");

            // Encabezados
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID_PO");
            headerRow.createCell(1).setCellValue("ID_SSFF");
            headerRow.createCell(2).setCellValue("AF");
            headerRow.createCell(3).setCellValue("Segmento");
            headerRow.createCell(4).setCellValue("CeCo");
            headerRow.createCell(5).setCellValue("Concepto");
            headerRow.createCell(6).setCellValue("Cuenta SAP");
            headerRow.createCell(7).setCellValue("Mes");
            headerRow.createCell(8).setCellValue("Monto");
            // Contador para el número de filas
            int rowNum = 1;

            // Itera sobre la lista de objetos
            for (ProjectionDTO data : vdata) {
                // Obtiene la lista de componentes
                List<PaymentComponentDTO> components = data.getComponents();

                // Recorre la lista de componentes
                for (PaymentComponentDTO component : components) {
                    // Obtiene la lista de proyecciones
                    List<MonthProjection> projections = component.getProjections();

                    // Recorre la lista de proyecciones
                    for (MonthProjection projection : projections) {
                        // Crea una nueva fila en el Excel
                        Row row = sheet.createRow(rowNum++);

                        // Agrega la información a la fila
                        row.createCell(0).setCellValue(data.getPo());
                        row.createCell(1).setCellValue(data.getIdssff());
                        row.createCell(2).setCellValue(data.getAreaFuncional());
                        row.createCell(3).setCellValue(data.getDivision());
                        row.createCell(4).setCellValue(data.getCCostos());
                        row.createCell(5).setCellValue(component.getName());
                        row.createCell(6).setCellValue(mapaComponentesValidos.get(component.getPaymentComponent()).getAccount());
                        row.createCell(7).setCellValue(projection.getMonth());
                        row.createCell(8).setCellValue(projection.getAmount().doubleValue());
                    }
                }
            }

            // Guarda el libro de Excel en un archivo
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw  new ResponseStatusException(HttpStatus.NOT_FOUND);

        }
    }

    private static byte[] generateCdg(List<ProjectionDTO> vdata, Bu bu,List<AccountProjection> accountProjections){
        try {
            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, componente -> componente));
            // Crea un nuevo libro de Excel
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("CDG");


            // Encabezados
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Periodo");
            headerRow.createCell(1).setCellValue("Escenario");
            headerRow.createCell(2).setCellValue("Cuenta");
            headerRow.createCell(3).setCellValue("Ceco");
            headerRow.createCell(4).setCellValue("Actividad");
            headerRow.createCell(5).setCellValue("Concepto");
            headerRow.createCell(6).setCellValue("Moneda");
            headerRow.createCell(7).setCellValue("Importe");
            headerRow.createCell(8).setCellValue("año");
            // Contador para el número de filas
            int rowNum = 1;

            // Itera sobre la lista de objetos
            for (ProjectionDTO data : vdata) {
                // Obtiene la lista de componentes
                List<PaymentComponentDTO> components = data.getComponents();

                // Recorre la lista de componentes
                for (PaymentComponentDTO component : components) {
                    // Obtiene la lista de proyecciones
                    List<MonthProjection> projections = component.getProjections();

                    // Recorre la lista de proyecciones
                    for (MonthProjection projection : projections) {
                        // Crea una nueva fila en el Excel
                        Row row = sheet.createRow(rowNum++);

                        // Agrega la información a la fila
                        row.createCell(0).setCellValue(projection.getMonth());
                        row.createCell(1).setCellValue("PPTO_0");
                        row.createCell(2).setCellValue(mapaComponentesValidos.get(component.getPaymentComponent()).getAccount());
                        row.createCell(3).setCellValue(data.getCCostos());
                        row.createCell(4).setCellValue("");
                        row.createCell(5).setCellValue(component.getName());
                        row.createCell(6).setCellValue(bu.getCurrent());
                        row.createCell(7).setCellValue(projection.getAmount().doubleValue());
                        row.createCell(8).setCellValue(projection.getMonth().substring(0,4));
                    }
                }
            }

            // Guarda el libro de Excel en un archivo
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw  new ResponseStatusException(HttpStatus.NOT_FOUND);

        }
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

        List<String> headers = dataBase2.getBaseExtern().getHeaders().stream().filter(
                t->!t.equals("po") && !t.equals("idssff")).collect(Collectors.toList());
        for (int i = 0; i < headers.size(); i++) {
            pheaderCell.setCellValue(headers.get(i));
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;
        dataBase.getData().addAll(dataBase2.getBc().getData().stream().map(t->{
                        Object obj = t.get("POXXXXXX");
                        List<ComponentAmount> components = dataBase.getComponents().stream().map(k->
                                ComponentAmount.builder()
                                        .component(k.getComponent())
                                        .amount(t.get(k.getName())!=null?new BigDecimal(t.get(k.getName()).toString()): BigDecimal.ZERO)
                                        .build()).collect(Collectors.toList());
                        return  DataBaseResponse.builder()
                                .po(obj.toString())
                                .idssff("NUEVO")
                                .poName("NUEVA POSICION")
                                .components(components)
                                .build();
                    }
                    ).collect(Collectors.toList()));

        dataBase.getData().forEach(r->{
            List<ComponentAmount> bex = headers.stream().map(k->{
                Optional<Map<String, Object>>   d =   dataBase2.getBaseExtern().getData().stream()
                            .filter(h->h.get("po").equals(r.getPo()) ).findFirst();
                        return ComponentAmount.builder()
                                .component(k)
                                .amount(d.map(stringObjectMap -> new BigDecimal(stringObjectMap.get(k).toString())).orElse(BigDecimal.ZERO))
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
            int starDetail=0;
            for (int j = 0; j < dataBase.getComponents().size(); j++) {
                pdataCell = data.createCell(j+3);
                Cell finalPdataCell = pdataCell;
                int finalJ = j;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().equalsIgnoreCase(dataBase.getComponents().get(finalJ).getComponent())).findFirst()
                        .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                                ()->finalPdataCell.setCellValue(0));
                starDetail++;
            }
            for (int k = 0; k < dataBase.getNominas().size(); k++) {
                pdataCell = data.createCell(starDetail+3);
                Cell finalPdataCell = pdataCell;
                int finalK= k;
                dataBase.getData().get(i).getComponents().stream().filter(r-> r.getComponent() != null
                                && r.getComponent().equalsIgnoreCase(dataBase.getNominas().get(finalK).getCodeNomina())).findFirst()
                        .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                                ()->finalPdataCell.setCellValue(0));
                starDetail++;
            }
            for (int k = 0; k < headers.size(); k++) {
                pdataCell = data.createCell(starDetail+3);
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

    private static void generateMoreView(String namePage,Workbook workbook ,ProjectionSecondDTO projection){
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
    private static final ReentrantLock sheetCreationLock = new ReentrantLock();

    private static Sheet  createSheetWithUniqueName(Workbook workbook, String baseName) {
        sheetCreationLock.lock();
        try {
            String uniqueName = baseName;
            int index = 1;
            while (workbook.getSheet(uniqueName) != null) {
                // Si la hoja ya existe, crea un nuevo nombre único
                uniqueName = baseName + "_" + index++;
            }
            return workbook.createSheet(uniqueName);
        } finally {
            sheetCreationLock.unlock();
        }
    }
    private static void writeExcelPage(Workbook workbook,String name,String component,String period,Integer range,List<ProjectionDTO> projection) {
        Sheet sheet = createSheetWithUniqueName(workbook, name);
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
        Row header = sheet.createRow(0);
        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("POSSFF");
        headerCell = header.createCell(1);
        headerCell.setCellValue("IDSSFF");
        headerCell = header.createCell(2);
        headerCell.setCellValue(Shared.nameMonth(period));
        int startHeader = 3;
        for (String m : Shared.generateRangeMonth(period, range)) {
            headerCell = header.createCell(startHeader);
            headerCell.setCellValue(m);
            startHeader++;
        }
        // Crear el estilo de celda una vez
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        int start = 1;
        for (ProjectionDTO projectionDTO : projection) {
            Row row = sheet.createRow(start);
            Cell cell = row.createCell(0);
            cell.setCellValue(projectionDTO.getPo());
            cell.setCellStyle(style); // Reutilizar el estilo
            cell = row.createCell(1);
            cell.setCellValue(projectionDTO.getIdssff());
            cell.setCellStyle(style); // Reutilizar el estilo
            //debug lista de componentes
            List<PaymentComponentDTO> list = projectionDTO
                    .getComponents();
            //log.debug("Componentes: {}", list);
            //log.debug("Componente: {}", component);
            Optional<PaymentComponentDTO> componentDTO = projectionDTO
                    .getComponents()
                    .stream()
                    .filter(u -> u.getPaymentComponent().equalsIgnoreCase(component))
                    .findFirst();
            //log.debug("Componente: {}", componentDTO);
            if (componentDTO.isPresent()) {
                cell = row.createCell(2);
                cell.setCellValue(componentDTO.get().getAmount().doubleValue());
                cell.setCellStyle(style); // Reutilizar el estilo
                int column = 3;
                for (int k = 0; k < componentDTO.get().getProjections().size(); k++) {
                    MonthProjection month = componentDTO.get().getProjections().get(k);
                    cell = row.createCell(column);
                    cell.setCellValue(month.getAmount().doubleValue());
                    cell.setCellStyle(style); // Reutilizar el estilo
                    column++;
                }
                start++;
            }
        }
    }
    // Añade un ExecutorService para la ejecución de tareas asíncronas
    private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // Modifica este método para que sea asíncrono
    public  CompletableFuture<byte[]> generateExcelProjectionAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase) {
        return CompletableFuture.supplyAsync(() -> {
            projection.setViewPo(true);
            ProjectionSecondDTO data = service.getNewProjection(projection);
            // Toda la lógica actual de generación de reportes
            return generateExcelProjection(projection, data, dataBase,components);
        }, executorService);
    }

    // Método para notificar al usuario, a ser implementado según tu mecanismo de notificación
    private void notifyUser(String notificationDetail, String userContact) {
        // Enviar notificación al usuario (correo electrónico, mensaje de texto, etc.)
        String subject = "Notificación de generación de reporte";
        emailService.sendSimpleMessage(userContact, subject, notificationDetail);
    }

    @Async
    public void generateAndCompleteReportAsync(ParametersByProjection projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, String userContact, ReportJob job, String user) {
        generateExcelProjectionAsync(projection, components, dataBase)
                .thenAccept(reportData -> {
                    job.setStatus("completado");
                    // Guarda el reporte en el almacenamiento externo
                    MultipartFile multipartFile = new ByteArrayMultipartFile(reportData, "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    FileDTO responseUpload =  externalService.uploadExcelReport(1,multipartFile);
                    job.setReportUrl(responseUpload.getPath());

                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("El reporte está listo para su descarga, vuelva a la aplicación para descargarlo", userContact);
                })
                .exceptionally(e -> {
                    job.setStatus("fallido");
                    job.setIdSsff(userContact);
                    job.setErrorMessage(e.getMessage());
                    reportJobRepository.save(job);
                    // Notifica al usuario
                    notifyUser("Falló la generación del reporte para el usuario con el contacto: " + userContact , userContact);
                    log.error("Error al generar el reporte", e);
                    return null;
                });
    }
}
