package ms.hispam.budget.util;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ComponentProjection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelService {

    private static String[] headerParameter={"Tipo de Parametro","Periodo","Valor","Retroactivo","Periodo Retroactivo","Rango"};

    public static byte[] generateExcelProjection(List<ProjectionDTO> projection, List<ComponentProjection> components, ParametersByProjection intro){
        Workbook workbook = new XSSFWorkbook();
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
        for (int i = 0; i < intro.getParameters().size(); i++) {
            ParametersDTO pam = intro.getParameters().get(i);
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
            pdataCell = data.createCell(6);
            pstart++;
        }
        components.stream().filter(c->(c.getIscomponent() && c.getShow()) || (!c.getIscomponent() && c.getShow())).forEach(c->{
            Sheet sheet = workbook.createSheet(c.getName());
            sheet.setColumnWidth(0, 5000);
            sheet.setColumnWidth(1, 4000);
            Row header = sheet.createRow(0);
            Cell headerCell = header.createCell(0);
            headerCell.setCellValue("POSSFF");
            headerCell = header.createCell(1);
            headerCell.setCellValue("IDSSFF");
            headerCell = header.createCell(2);
            headerCell.setCellValue(Shared.nameMonth(intro.getPeriod()));
           int  startHeader =3;
           for(String m:Shared.generateRangeMonth(intro.getPeriod(),intro.getRange())){
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
                PaymentComponentDTO componentDTO =   projection.get(i).getComponents().stream()
                        .filter(u->u.getPaymentComponent().equalsIgnoreCase(c.getComponent())).findFirst().get() ;
                cell = row.createCell(2);
                cell.setCellValue(componentDTO.getAmount());
                cell.setCellStyle(style);
                    int column=3;
                    for (int k = 0; k < componentDTO.getProjections().size(); k++) {
                        MonthProjection month = componentDTO.getProjections().get(k);
                        cell = row.createCell(column);
                        cell.setCellValue(month.getAmount());
                        cell.setCellStyle(style);
                        column++;
                    }

                start++;
            }




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
}
