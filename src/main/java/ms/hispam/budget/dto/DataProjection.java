package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ComponentCashProjection;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataProjection {

    List<ProjectionDTO> totalData;
    List<ProjectionDTO> groupData;
    List<ComponentCashProjection> components;
}
