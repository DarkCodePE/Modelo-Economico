package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentComponentDTO {
    private String paymentComponent;
    private Integer type;
    private Double amount;
    private List<MonthProjection> projections;

    public Double getAmount() {
        if(amount==null){
            return 0.0;
        }
        return amount;
    }
}
