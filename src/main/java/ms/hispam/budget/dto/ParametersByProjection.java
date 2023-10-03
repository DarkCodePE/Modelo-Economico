package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametersByProjection {

    private String bu;
    private Integer idBu;
    private String period;
    private Integer range;
    private List<ParametersDTO> parameters;
    private List<PaymentComponentType> paymentComponent;
    private String nominaFrom;
    private String nominaTo;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;


}
