package ms.hispam.budget.rules.operations.salary;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.rules.operations.Mediator;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j(topic = "RevisionSalaryOperationPeru")
public class RevisionSalaryOperationPeru implements Operation {

    @Override
    public void execute(Mediator mediator, List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<RangeBuDTO> temporalParameters, LocalDate birthDate, Map<String, List<Double>> vacationSeasonalityValues) {
        mediator.executeOperation(this, component, parameters, classEmployee, period, range, temporalParameters, birthDate, vacationSeasonalityValues);
    }
}

