package ms.hispam.budget.rules.operations;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.ProjectionDTO;

import java.util.List;

public interface Operation {
    void execute(ProjectionDTO headcountData, List<ParametersDTO> parameters, String period, Integer range);
}