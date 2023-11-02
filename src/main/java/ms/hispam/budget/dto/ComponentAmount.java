package ms.hispam.budget.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentAmount {
    private String component;
    private BigDecimal amount;

}
