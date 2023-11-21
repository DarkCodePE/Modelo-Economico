package ms.hispam.budget.rules.operations.salary;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.rules.operations.Mediator;
import ms.hispam.budget.rules.operations.Operation;

import java.time.LocalDate;
import java.util.List;

public class BaseSalaryOperation implements Operation {

    @Override
    public void execute(Mediator mediator, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<RangeBuDTO> temporalParameters, LocalDate birthDate) {
        mediator.executeOperation(this, component, parameters, classEmployee, period, range, temporalParameters, birthDate);
    }
}
