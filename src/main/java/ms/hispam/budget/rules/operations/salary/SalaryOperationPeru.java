package ms.hispam.budget.rules.operations.salary;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.ProjectionDTO;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.rules.operations.salary.Peru;

import java.util.List;

public class SalaryOperationPeru implements Operation {
    private final Peru methodsPeru;

    public SalaryOperationPeru(Peru methodsPeru) {
        this.methodsPeru = methodsPeru;
    }

    @Override
    public void execute(ProjectionDTO headcountData, List<ParametersDTO> parameters, String period, Integer range) {
        methodsPeru.salary(headcountData.getComponents(), parameters, headcountData.getClassEmployee(), period, range);
    }
}