package ms.hispam.budget.rules.operations.salary;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.rules.countries.Country;
import ms.hispam.budget.rules.operations.Operation;

import java.util.List;

public class Peru implements Country {
    private final List<Operation> operations;

    public Peru(List<Operation> operations) {
        this.operations = operations;
    }

    @Override
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        for (Operation operation : operations) {
            //operation.execute(component, parameters, classEmployee, period, range);
        }
    }
}
