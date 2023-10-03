package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.ComponentProjection;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page<T> {
    private Integer pageNumber;
    private Integer totalPages;
    private Integer totalResults;
    private List<T> items;
    private List<ComponentAmount> resume;
    private List<ComponentProjection> components;
    private List<DisabledPoDTO> disabledPo;
    private List<ParametersDTO> parameters;

    public Page(Integer pageNumber, Integer totalPages, Integer totalResults, List<T> items, List<ComponentAmount> resume) {
        this.pageNumber = pageNumber;
        this.totalPages = totalPages;
        this.totalResults = totalResults;
        this.items = items;
        this.resume = resume;
    }
}
