package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametersByProjection {

    private String bu;
    private String period;
    private Integer range;
    private List<ParametersDTO> parameters;
    private List<PaymentComponentType> paymentComponent;
    private Integer page;
    private Integer size;
    private String nominaFrom;
    private String nominaTo;


}
