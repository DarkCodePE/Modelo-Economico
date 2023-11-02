package ms.hispam.budget.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ParamSalaryDTO {
    private Double salary;
    private String period;
    private Boolean isRetroactive;
    private String periodRetroactive;
}
