package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionBaselineDTO {

    private String position;
    private String idssff;
    private String from;
    private String to;
}
