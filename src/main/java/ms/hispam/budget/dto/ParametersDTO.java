package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.entity.mysql.ConventArg;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametersDTO {
    private Integer id;
    private String description;
    private String vparameter;
    private Boolean allPeriod;
    private Boolean inPeriod;
    private Boolean restringed;
    private Boolean isRetroactive;
    private ParameterDTO parameter;
    private Double value;
    private String range;
    private Integer type;
    private String period;
    private String periodRetroactive;
    private String order;
    private ConventArgDTO conventArg;
    private String vname;
    private Boolean vrange;
}
