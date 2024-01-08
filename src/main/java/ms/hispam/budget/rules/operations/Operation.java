package ms.hispam.budget.rules.operations;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.ProjectionDTO;
import ms.hispam.budget.dto.RangeBuDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface Operation {
    void execute(Mediator mediator, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<RangeBuDTO> temporalParameters, LocalDate birthDate, Map<String, List<Double>> vacationSeasonalityValues);
}