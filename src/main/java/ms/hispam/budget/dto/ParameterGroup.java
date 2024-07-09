package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ParameterProjection;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterGroup {
    private String convenio;
    private List<ParameterProjection> parameters;
}