package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {
    private String code;
    private String name;
}
