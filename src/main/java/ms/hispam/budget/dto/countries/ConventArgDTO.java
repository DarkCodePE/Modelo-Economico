package ms.hispam.budget.dto.countries;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConventArgDTO {
    private Integer id;
    private String convenio;
    private String areaPersonal;
}
