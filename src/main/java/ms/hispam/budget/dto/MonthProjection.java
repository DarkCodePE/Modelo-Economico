package ms.hispam.budget.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthProjection {
    private String month;
    private BigDecimal amount;
}
