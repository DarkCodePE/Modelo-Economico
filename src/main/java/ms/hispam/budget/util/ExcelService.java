package ms.hispam.budget.util;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.Bu;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ExcelService {

    private static final String[] headers = {"po","idssff"};
    private static final String[] headersInput = {"po","idssff","Nombre de la posición"};

    private final Executor singleThreadExecutor = Executors.newSingleThreadExecutor();

    public static Future<Workbook> writeExcelPageAsync(String name, String component, String period, Integer range, List<ProjectionDTO> projection) {
        return CompletableFuture.supplyAsync(() -> writeExcelPage(name, component, period, range, projection));
    }

    private static final String[] headerParameter={"Tipo de Parametro","Periodo","Valor","Comparativo","Periodos comparativos","Rango"};

    public static byte[] generateExcelProjection(ParameterDownload projection , List<ComponentProjection> components,DataBaseMainReponse dataBase){
        // Crear un ExecutorService con un número fijo de hilos
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Workbook>> futures = new ArrayList<>();

        // Crear una lista de todos los nombres de las hojas que necesitas generar
        List<String> sheetNames = new ArrayList<>();

        // Agregar los nombres de los componentes que deben mostrarse
        for (ComponentProjection component : components) {
            if ((component.getIscomponent() && component.getShow()) || (!component.getIscomponent() && component.getShow())) {
                sheetNames.add(component.getName());
            }
        }

        // Agregar los encabezados que no están presentes en la matriz de encabezados
        for (String header : projection.getBaseExtern().getHeaders()) {
            if (Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(header))) {
                sheetNames.add(header);
            }
        }

        // Iterar sobre la lista de nombres de hojas y generar cada hoja
        for (String sheetName : sheetNames) {
            Future<Workbook> future = ExcelService.writeExcelPageAsync(sheetName, sheetName, projection.getPeriod(), projection.getRange(), projection.getData());
            futures.add(future);
        }

        // Cerrar el ExecutorService
        executor.shutdown();

        // Esperar a que todas las tareas se completen
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error waiting for Excel generation tasks to complete", e);
        }

        // Recoger todos los Workbook generados
        List<Workbook> workbooks = new ArrayList<>();
        for (Future<Workbook> future : futures) {
            try {
                workbooks.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error getting result from future", e);
            }
        }

        // Combinar todos los Workbook en un solo Workbook principal
        Workbook mainWorkbook = ExcelService.mergeWorkbooks(workbooks);

        // Guardar el Workbook principal en un archivo
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            mainWorkbook.write(outputStream);
            mainWorkbook.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateExcelType(List<ProjectionDTO> vdata,Integer type, Bu bu,List<AccountProjection> accountProjections) {
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
            pdataCell.setCellValue(pam.getParameter().getName());
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
    private static void generateInput(Workbook workbook ,DataBaseMainReponse dataBase){
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
        int pstart =1;
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
               dataBase.getData().get(i).getComponents().stream().filter(r->r.getComponent().equalsIgnoreCase(dataBase.getComponents().get(finalJ).getComponent())).findFirst()
                       .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                       ()->finalPdataCell.setCellValue(0));
               starDetail++;
           }
           for (int k = 0; k < dataBase.getNominas().size(); k++) {
               pdataCell = data.createCell(starDetail+3);
               Cell finalPdataCell = pdataCell;
               int finalK= k;
               dataBase.getData().get(i).getComponents().stream().filter(r->r.getComponent().equalsIgnoreCase(dataBase.getNominas().get(finalK).getCodeNomina())).findFirst()
                       .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount().doubleValue()),
                               ()->finalPdataCell.setCellValue(0));
               starDetail++;
           }
            pstart++;
        }
    }

    private static void generateMoreView(String namePage,Workbook workbook ,ViewDTO parameters){
        Sheet psheet = workbook.createSheet(namePage);
        Row pheader = psheet.createRow(0);
        Cell pheaderCell = pheader.createCell(0);
        int hstart=1;
        for (int i = 0; i < parameters.getHeaders().size(); i++) {
            pheaderCell.setCellValue(parameters.getHeaders().get(i));
            pheaderCell = pheader.createCell(hstart);
            hstart++;
        }
        int pstart =1;
        for (int i = 0; i < parameters.getData().size(); i++) {
            Row data = psheet.createRow(pstart);
            Object[] pam = parameters.getData().get(i);
            Cell pdataCell;
            for (int j = 0; j < pam.length; j++) {
                pdataCell = data.createCell(j);
                if(j>1){
                    pdataCell.setCellValue(Double.parseDouble(pam[j].toString()));
                }else{
                    pdataCell.setCellValue(pam[j].toString());
                }
            }
            pstart++;
        }

    }

    public static Workbook writeExcelPage(String name, String component, String period, Integer range, List<ProjectionDTO> projection) {
        Workbook workbook = new XSSFWorkbook();
        // Crear el estilo de celda una vez
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        // Generar un identificador único para la hoja
        String uniqueId = UUID.randomUUID().toString();
        // Agregar el identificador único al nombre
        String uniqueName = name + "_" + uniqueId;
        // Usar el nombre único al crear la hoja
        Sheet sheet = workbook.createSheet(uniqueName);
        //Sheet sheet = workbook.createSheet(name);
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
        int start = 1;
        for (int i = 0; i < projection.size(); i++) {
            Row row = sheet.createRow(start);
            Cell cell = row.createCell(0);
            cell.setCellValue(projection.get(i).getPo());
            cell.setCellStyle(style); // Aplicar el estilo de celda
            cell = row.createCell(1);
            cell.setCellValue(projection.get(i).getIdssff());
            cell.setCellStyle(style); // Aplicar el estilo de celda
            Optional<PaymentComponentDTO> componentDTO = projection.get(i).getComponents().stream()
                    .filter(u -> u.getPaymentComponent().equalsIgnoreCase(component)).findFirst();
            if (componentDTO.isPresent()) {
                cell = row.createCell(2);
                cell.setCellValue(componentDTO.get().getAmount().doubleValue());
                cell.setCellStyle(style); // Aplicar el estilo de celda
                int column = 3;
                for (int k = 0; k < componentDTO.get().getProjections().size(); k++) {
                    // Resto del código
                }
                start++;
            }
        }
        return workbook;
    }
    public static Workbook mergeWorkbooks(List<Workbook> workbooks) {
        Workbook mainWorkbook = new XSSFWorkbook();
        for (Workbook workbook : workbooks) {
            Sheet sheet = workbook.getSheetAt(0);
            Sheet mainSheet = mainWorkbook.createSheet(sheet.getSheetName());
            // Copia las filas de la hoja al mainSheet...
        }
        return mainWorkbook;
    }
}
