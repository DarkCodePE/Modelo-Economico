package ms.hispam.budget.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;


@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RangeBuDTO {
    private String name;
    private Integer idBu;
    private List<RangeBuDetailDTO> rangeBuDetails;
}