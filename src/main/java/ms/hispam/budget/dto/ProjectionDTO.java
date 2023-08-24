package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionDTO {

    private String idssff;
    private String po;
    private String poName;
    private List<PaymentComponentDTO> components;


}
