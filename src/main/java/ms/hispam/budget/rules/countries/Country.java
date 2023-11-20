package ms.hispam.budget.rules.countries;

import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;

import java.util.List;

public interface Country {
    void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
    void revisionSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
    void vacationEnjoyment(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
    void baseSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range);
}
