package ms.hispam.budget.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ExcelReportDTO {
    private Integer id;
    private String code;
    private String URL;
    private String status;
}
