package ms.hispam.budget.rules.operations;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;

import java.util.List;

public interface Mediator {
    void executeOperation(Operation operation, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
}
