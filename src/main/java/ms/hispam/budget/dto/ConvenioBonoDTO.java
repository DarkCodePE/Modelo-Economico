package ms.hispam.budget.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ConvenioBonoDTO {
    private int id;
    private String convenioNivel;
    private Double bonoPercentage;
}
