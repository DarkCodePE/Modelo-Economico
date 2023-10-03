package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DisabledPoDTO {
    private String po;
    private String idssff;
    private String from;
    private String to;
}
