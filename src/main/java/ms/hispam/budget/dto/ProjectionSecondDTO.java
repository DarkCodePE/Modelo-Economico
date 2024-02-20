package ms.hispam.budget.dto;

import lombok.*;

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
}
