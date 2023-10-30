package ms.hispam.budget.util;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExcelService {

    private static final String[] headers = {"po","idssff"};
    private static final String[] headersInput = {"po","idssff","Nombre de la posición"};


    private static final String[] headerParameter={"Tipo de Parametro","Periodo","Valor","Comparativo","Periodos comparativos","Rango"};

    public static byte[] generateExcelProjection(ParameterDownload projection , List<ComponentProjection> components,DataBaseMainReponse dataBase){
        Workbook workbook = new XSSFWorkbook();
        // vista Parametros
        generateParameter(workbook,projection.getParameters());
        // vista Input
        generateInput(workbook,dataBase);
        //Vista anual
        generateMoreView("Vista Anual",workbook,projection.getViewAnnual());
        generateMoreView("Vista Mensual",workbook,projection.getViewMonthly());
        components.stream().filter(c->(c.getIscomponent() && c.getShow()) || (!c.getIscomponent() && c.getShow())).forEach(c->{
            writeExcelPage(workbook,c.getName(),c.getComponent(),projection.getPeriod(),projection.getRange(),projection.getData());
        });
        projection.getBaseExtern().getHeaders().stream().filter(r-> Arrays.stream(headers).noneMatch(c->c.equalsIgnoreCase(r))).forEach(c->{
            writeExcelPage(workbook,c,c,projection.getPeriod(),projection.getRange(),projection.getData());
        });


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
                       .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount()),
                       ()->finalPdataCell.setCellValue(0));
               starDetail++;
           }
           for (int k = 0; k < dataBase.getNominas().size(); k++) {
               pdataCell = data.createCell(starDetail+3);
               Cell finalPdataCell = pdataCell;
               int finalK= k;
               dataBase.getData().get(i).getComponents().stream().filter(r->r.getComponent().equalsIgnoreCase(dataBase.getNominas().get(finalK).getCodeNomina())).findFirst()
                       .ifPresentOrElse(r-> finalPdataCell.setCellValue(r.getAmount()),
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

    private static void writeExcelPage(Workbook workbook,String name,String component,String period,Integer range,List<ProjectionDTO> projection){
        Sheet sheet = workbook.createSheet(name);
        sheet.setColumnWidth(0, 5000);
        sheet.setColumnWidth(1, 4000);
        Row header = sheet.createRow(0);
        Cell headerCell = header.createCell(0);
        headerCell.setCellValue("POSSFF");
        headerCell = header.createCell(1);
        headerCell.setCellValue("IDSSFF");
        headerCell = header.createCell(2);
        headerCell.setCellValue(Shared.nameMonth(period));
        int  startHeader =3;
        for(String m:Shared.generateRangeMonth(period,range)){
            headerCell = header.createCell(startHeader);
            headerCell.setCellValue(m);
            startHeader++;
        }
        int start =1;
        for (int i = 0; i < projection.size(); i++) {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            Row row = sheet.createRow(start);
            Cell cell = row.createCell(0);
            cell.setCellValue(projection.get(i).getPo());
            cell.setCellStyle(style);
            cell = row.createCell(1);
            cell.setCellValue(projection.get(i).getIdssff());
            cell.setCellStyle(style);
            Optional<PaymentComponentDTO> componentDTO =   projection.get(i).getComponents().stream()
                    .filter(u->u.getPaymentComponent().equalsIgnoreCase(component)).findFirst();
            if (componentDTO.isPresent()){
                cell = row.createCell(2);
                cell.setCellValue( componentDTO.get().getAmount());
                cell.setCellStyle(style);
                int column=3;
                for (int k = 0; k < componentDTO.get().getProjections().size(); k++) {
                    MonthProjection month = componentDTO.get().getProjections().get(k);
                    cell = row.createCell(column);
                    cell.setCellValue(month.getAmount());
                    cell.setCellStyle(style);
                    column++;
                }
                start++;
            }

        }
    }
}
