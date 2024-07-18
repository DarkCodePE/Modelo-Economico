package ms.hispam.budget.dto;

import lombok.Builder;
import lombok.Getter;
import ms.hispam.budget.dto.countries.ConventArgDTO;

import javax.persistence.Column;
@Getter
@Builder
public class ParameterDefaultDTO {
    private Integer id;
    private Integer bu;
    private Integer vParameter;
    private Double value;
    private ConventArgDTO conventArg;
}
