package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDTO {
    private Integer id;
    private String name;
    private String description;
    private Integer typeValor;

    public ParameterDTO(String name) {
        this.name = name;
    }
}
