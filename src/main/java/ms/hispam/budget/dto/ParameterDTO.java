package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParameterDTO {
    private Integer id;
    private String name;
    private String description;
    private Integer typeValor;

    public ParameterDTO(String name) {
        this.name = name;
    }
}
