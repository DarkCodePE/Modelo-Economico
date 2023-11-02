package ms.hispam.budget.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentComponentDTO {
    private String paymentComponent;
    private Integer type;
    private BigDecimal amount;
    private List<MonthProjection> projections;

    public BigDecimal getAmount() {
        if(amount==null){
            return BigDecimal.ZERO;
        }
        return amount;
    }
}
