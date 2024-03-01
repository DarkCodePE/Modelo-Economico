package ms.hispam.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemporalComponentsDTO {
    private List<PaymentComponentDTO> temporalComponents;
}
