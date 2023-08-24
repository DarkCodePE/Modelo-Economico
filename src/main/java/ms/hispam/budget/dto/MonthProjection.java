package ms.hispam.budget.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthProjection {
    private String month;
    private Double amount;
}
