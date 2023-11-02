package ms.hispam.budget.dto.projections;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ParamFilterDTO {
    private Integer id;
    private Double value;
    private Boolean status;
}
