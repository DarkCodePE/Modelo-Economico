package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RangeBuDetailDTO {
    private Integer id;
    private String range;
    private Integer idPivot;
    private Integer value;
}
