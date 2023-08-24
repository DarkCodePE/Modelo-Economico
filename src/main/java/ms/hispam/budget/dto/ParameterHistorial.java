package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterHistorial {
    private String bu;
    private String name;
    private String period;
    private Integer range;
    private String nominaFrom;
    private String nominaTo;
    private List<ParametersDTO> parameters;
}
