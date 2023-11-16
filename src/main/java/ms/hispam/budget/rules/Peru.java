package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.util.Shared;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Component
@Slf4j(topic = "RULES_PERU")
public class Peru {
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        log.info("salary");
        AtomicReference<Double> baseSalary = new AtomicReference<>((double) 0);
        AtomicReference<Double> baseSalaryIntegral = new AtomicReference<>((double) 0);
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC960400"))
                .findFirst()
                .ifPresent(p -> {
                    baseSalary.set(p.getAmount().doubleValue());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC960401"))
                .findFirst()
                .ifPresent(p -> {
                    baseSalaryIntegral.set(p.getAmount().doubleValue());
                });
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("SALARY");
        paymentComponentDTO.setAmount(BigDecimal.valueOf(Stream.of(
                baseSalary.get(),baseSalaryIntegral.get()
        ).max(Double::compareTo).orElse(0.0)));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
            double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
            if (classEmployee.equals("emp")){

            }
        }
    }
}
