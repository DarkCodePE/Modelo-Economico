package ms.hispam.budget.dto;

import lombok.*;

import javax.persistence.Column;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {
    private String code;
    private String name;
    private Integer bu;
    private Boolean isInput;
}
