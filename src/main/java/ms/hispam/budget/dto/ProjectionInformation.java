package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ParameterProjectionBD;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionInformation {

    private List<ParameterProjectionBD> parameters;
    private List<DisabledPoDTO> poDisableds;
    private BaseExternResponse baseExtern;
    private BaseExternResponse bc;
    //temporal parameter
    private List<ParameterProjectionBD> parametersTemporal;
}
