package ms.hispam.budget.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthProjection implements Cloneable{
    private String month;
    private BigDecimal amount;
    @Override
    public MonthProjection clone() {
        try {
            return (MonthProjection) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error al clonar MonthProjection", e);
        }
    }
}
