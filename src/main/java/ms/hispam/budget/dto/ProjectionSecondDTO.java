package ms.hispam.budget.dto;

import lombok.*;
import ms.hispam.budget.dto.projections.RealesProjection;

import java.util.List;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProjectionSecondDTO {

    private ResumeSpecDTO resumeComponent;
    private ResumeSpecDTO resumeAccount;
    private List<MonthProjection> monthProjections;
    private List<MonthProjection> yearProjections;
    private List<ResumenComponentDTO> realesMonth;
    private List<ResumenComponentDTO> realesYear;
    private List<MonthProjection> resumeRealesYear;
    private List<MonthProjection> resumeRealesMonth;
    private ViewPosition viewPosition;


}
