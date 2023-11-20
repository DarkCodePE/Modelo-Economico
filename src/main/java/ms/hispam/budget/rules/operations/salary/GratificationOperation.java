package ms.hispam.budget.rules.operations.salary;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.rules.operations.Mediator;
import ms.hispam.budget.rules.operations.Operation;

import java.util.List;

public class GratificationOperation implements Operation {
    @Override
    public void execute(Mediator mediator, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        mediator.executeOperation(this, component, parameters, classEmployee, period, range);
    }
}
