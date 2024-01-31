package ms.hispam.budget.util;

import ms.hispam.budget.dto.ProjectionDTO;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public class ExcelSheetTask implements Runnable {
    private Workbook workbook;
    private String sheetName;
    private String componentName;
    private String period;
    private Integer range;
    private List<ProjectionDTO> projection;


    public ExcelSheetTask(Workbook workbook, String sheetName, String componentName, String period, Integer range, List<ProjectionDTO> projection) {
        this.workbook = workbook;
        this.sheetName = sheetName;
        this.componentName = componentName;
        this.period = period;
        this.range = range;
        this.projection = projection;
    }

    @Override
    public void run() {
        //ExcelService.writeExcelPage(sheetName, componentName, period, range, projection);
    }
}
