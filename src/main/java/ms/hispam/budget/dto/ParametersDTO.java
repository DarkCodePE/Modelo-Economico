package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametersDTO {

    private ParameterDTO parameter;
    private Integer type;
    private String period;
    private Double value;
    private Boolean isRetroactive;
    private String periodRetroactive;
    private String range;
    private Integer order;
}
