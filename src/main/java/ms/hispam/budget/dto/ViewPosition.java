package ms.hispam.budget.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewPosition {
    private List<ProjectionDTO> positions;
    private Integer count;
}
