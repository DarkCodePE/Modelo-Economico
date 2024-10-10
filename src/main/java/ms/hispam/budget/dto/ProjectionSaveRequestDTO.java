package ms.hispam.budget.dto;

import lombok.Data;

@Data
public class ProjectionSaveRequestDTO {
    private ParametersByProjection projection;
    private String sessionId;
    private String reportName;
    private ProjectionSecondDTO projectionResult;
}
