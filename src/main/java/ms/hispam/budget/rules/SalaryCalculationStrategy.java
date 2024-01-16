package ms.hispam.budget.rules;

import ms.hispam.budget.dto.ParametersDTO;

import java.util.List;

public interface SalaryCalculationStrategy {
    double calculateSalary(List<ParametersDTO> parameters, String period);
}
