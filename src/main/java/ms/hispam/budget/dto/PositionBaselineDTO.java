package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionBaselineDTO {

    private String position;
    private String idssff;
    private String from;
    private String to;
}
