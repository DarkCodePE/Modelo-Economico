package ms.hispam.budget.rules.operations;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.ProjectionDTO;

import java.util.List;

public interface Operation {
    void execute(Mediator mediator, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
}