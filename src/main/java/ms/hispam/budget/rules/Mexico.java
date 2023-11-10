package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j(topic = "Mexico")
public class Mexico {
    static final String TYPEMONTH="yyyyMM";
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
       //SI(ESNUMERO(HALLAR("CP";$H4));
        // HEADCOUNT: OBTIENS LAS PROYECCION Y LA PO, AQUI AGREGAMOS LOS PARAMETROS CUSTOM
        AtomicReference<Double> baseSalary = new AtomicReference<>((double) 0);
        AtomicReference<Double> baseSalaryIntegral = new AtomicReference<>((double) 0);
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC320001"))
                .findFirst()
                .ifPresent(p -> {
                    baseSalary.set(p.getAmount().doubleValue());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC320002"))
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
        //List min salaries
        List<ParametersDTO> salaryList = parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getName(), "Salario Mínimo Mexico"))
                .collect(Collectors.toList());

        AtomicReference<Double> salaryMin = new AtomicReference<>((double) 0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>((String) "");
        parameters.stream()
                .filter(p -> p.getParameter().getId() == 2)
                .findFirst()
                .ifPresent(param -> {
                    salaryMin.set(param.getValue());
                    periodSalaryMin.set(param.getPeriod());
                });
        int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
        if (idxEmp != -1){
            for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                //validate month march substring 03
                if (paymentComponentDTO.getProjections().get(i).getMonth().substring(4).equalsIgnoreCase("03")){
                    // TODO
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                }else{
                    if(paymentComponentDTO
                            .getProjections()
                            .get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                        if (amount < salaryMin.get()){
                            // amount 20%
                            double incrementSalary = amount * 0.2;
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount + incrementSalary ));
                        }else {
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        }
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
    }
    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters){
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .parallel()
                .forEach(paymentComponentDTO -> {
                    double differPercent=0.0;
                    AtomicReference<Double> salaryMin = new AtomicReference<>((double) 0);
                    AtomicReference<String> periodSalaryMin = new AtomicReference<>((String) "");
                    AtomicReference<Boolean> isRetroactive = new AtomicReference<>((Boolean) false);
                    AtomicReference<String[]> periodRetractive = new AtomicReference<>((String[]) null);
                    AtomicReference<Double> percetange = new AtomicReference<>((double) 0);
                    parameters.stream()
                            .filter(p -> p.getParameter().getId() == 1)
                            .findFirst()
                            .ifPresent(param -> {
                                salaryMin.set(param.getValue());
                                periodSalaryMin.set(param.getPeriod());
                                isRetroactive.set(param.getIsRetroactive());
                                periodRetractive.set(param.getPeriodRetroactive().split("-"));
                                percetange.set(param.getValue());
                            });
                    if(Boolean.TRUE.equals(isRetroactive.get())){
                        int idxStart;
                        int idxEnd;
                        String[] periodRevisionSalary = periodRetractive.get();
                        idxStart=  Shared.getIndex(paymentComponentDTO.getProjections().stream()
                                .map(MonthProjection::getMonth).collect(Collectors.toList()),periodRevisionSalary[0]);
                        idxEnd=  Shared.getIndex(paymentComponentDTO.getProjections().stream()
                                .map(MonthProjection::getMonth).collect(Collectors.toList()),periodRevisionSalary.length==1? periodRevisionSalary[0]:periodRevisionSalary[1]);
                        AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
                        AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
                        salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                        salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                        differPercent=(salaryEnd.get())/(salaryFirst.get())-1;
                    }
                });

    }
    public static ParamFilterDTO getValueSalaryByPeriod(List<ParametersDTO> params, String periodProjection){
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>("");
        Boolean status = params.stream()
                .parallel()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    value.set(p.getValue());
                    periodSalaryMin.set(p.getPeriod());
                    return dateParam.equals(dateProjection);
                });
        return ParamFilterDTO.builder()
                .status(status)
                .value(value.get())
                .build();
    }
    public void provAguinaldo(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // create payment component prov aguinaldo
        double amount = 0.0;
        if (paymentComponentDTO != null) amount = (paymentComponentDTO.getAmount().doubleValue() / 30) * 1.25;
        PaymentComponentDTO paymentComponentProvAguin = new PaymentComponentDTO();
        paymentComponentProvAguin.setPaymentComponent("PROV_AGUINALDO");
        paymentComponentProvAguin.setAmount(BigDecimal.valueOf(amount));
        paymentComponentProvAguin.setProjections(Shared.generateMonthProjection(period,range,paymentComponentProvAguin.getAmount()));
        // prov aguinaldo
        int idxEmp = Shared.getIndex(paymentComponentProvAguin.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()),period);
        if (idxEmp != -1){
            for (int i = idxEmp; i < paymentComponentProvAguin.getProjections().size(); i++) {
                double amountProj = i==0?paymentComponentProvAguin.getProjections().get(i).getAmount().doubleValue(): paymentComponentProvAguin.getProjections().get(i-1).getAmount().doubleValue();
                paymentComponentProvAguin.getProjections().get(i).setAmount(BigDecimal.valueOf(amountProj));
            }
        }
        component.add(paymentComponentDTO);
    }
public void provVacaciones(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // get date contract
        AtomicReference<String> dateContract = new AtomicReference<>("");

        // create payment component prov aguinaldo
        double amount = 0.0;
        if (paymentComponentDTO != null) amount = (paymentComponentDTO.getAmount().doubleValue() / 30) * 0.25;
        PaymentComponentDTO paymentComponentProvAguin = new PaymentComponentDTO();
        paymentComponentProvAguin.setPaymentComponent("PROV_VACACIONES");
        paymentComponentProvAguin.setAmount(BigDecimal.valueOf(amount));
        paymentComponentProvAguin.setProjections(Shared.generateMonthProjection(period,range,paymentComponentProvAguin.getAmount()));
        // prov aguinaldo
        int idxEmp = Shared.getIndex(paymentComponentProvAguin.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()),period);
        if (idxEmp != -1){
            for (int i = idxEmp; i < paymentComponentProvAguin.getProjections().size(); i++) {
                double amountProj = i==0?paymentComponentProvAguin.getProjections().get(i).getAmount().doubleValue(): paymentComponentProvAguin.getProjections().get(i-1).getAmount().doubleValue();
                paymentComponentProvAguin.getProjections().get(i).setAmount(BigDecimal.valueOf(amountProj));
            }
        }
        component.add(paymentComponentDTO);
    }
}
