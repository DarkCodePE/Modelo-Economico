package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataBaseResponse {

    private String po;
    private String idssff;
    private String poName;
    private List<ComponentAmount> components;
}
