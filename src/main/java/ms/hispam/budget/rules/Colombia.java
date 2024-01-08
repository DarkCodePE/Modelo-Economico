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
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@Slf4j(topic = "Colombia")
public class Colombia {
    private static final Integer SALARY_MIN = 1160000;
    private static final Integer SALARY_MIN_PRA = 1424115;
    private static final Integer SALARY_MIN_INC_PLA_DIR = (SALARY_MIN*112)/100;
    private static final Integer SALARY_MIN_PRA_INC_PLA_DIR = (SALARY_MIN_PRA*112)/100;
    static final String TYPEMONTH="yyyyMM";

    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        // HEADCOUNT: OBTIENS LAS PROYECCION Y LA PO, AQUI AGREGAMOS LOS PARAMETROS CUSTOM
        AtomicReference<Double> baseSalary = new AtomicReference<>((double) 0);
        AtomicReference<Double> baseSalaryIntegral = new AtomicReference<>((double) 0);
        boolean flagExistSalaryBase = false;
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938001"))
                .findFirst()
                .ifPresent(p -> {
                   baseSalary.set(p.getAmount().doubleValue());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938005"))
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
        //SALARIO MIN
        AtomicReference<Double> salaryMin = new AtomicReference<>((double) 0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>((String) "");
        AtomicReference<Double> salaryIntegral = new AtomicReference<>((double) 0);
        AtomicReference<String> periodSalaryMinIntegral = new AtomicReference<>((String) "");
        switch (classEmployee){
            case "PRA":
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 26)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryMin.set(param.getValue());
                            periodSalaryMin.set(param.getPeriod());
                        });
                //SI(R13<=S$5;S$5;R13)
                //verificamos en que periodo(meses) este parámetro afectara a al monto de la proyección
                int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> months= paymentComponentDTO.getProjections();
                if (idx != -1){
                    for (int i = idx; i < months.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : months.get(i-1).getAmount().doubleValue();
                        //FORMULA
                        //SI(R13<=S$5;S$5;R13);
                       /* SI($F4<>"";
                        SI(R13<=S$3;S$3;R13);SI(R13<S$4;S$4;R13));*/
                        if (amount <= salaryMin.get()){
                            months.get(i).setAmount(BigDecimal.valueOf(salaryMin.get()));
                        }else {
                            months.get(i).setAmount(BigDecimal.valueOf(amount));
                        }

                    }
                }
                break;
            case "JP":
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 27)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryMin.set(param.getValue());
                            periodSalaryMin.set(param.getPeriod());
                        });
                //FIND MONTH TO APPLY PARAMETER T
                //mes
                int idxT = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(d->d.getMonth()).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> monthsT= paymentComponentDTO.getProjections();
                if (idxT != -1){
                    for (int i = idxT; i < monthsT.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : monthsT.get(i-1).getAmount().doubleValue();
                        //FORMULA
                        //SI(R13 <= S$6;S$6;R13);
                        if (amount <= salaryMin.get()){
                            monthsT.get(i).setAmount(BigDecimal.valueOf( salaryMin.get()));
                        }else {
                            monthsT.get(i).setAmount(BigDecimal.valueOf(amount));
                        }

                    }
                }
                break;
            case "APR":
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 43)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryMin.set(param.getValue());
                            periodSalaryMin.set(param.getPeriod());
                        });
                //mes
                int idxApr = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(d->d.getMonth()).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> monthsApr= paymentComponentDTO.getProjections();
                if (idxApr != -1){
                    for (int i = idxApr; i < monthsApr.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : monthsApr.get(i-1).getAmount().doubleValue();
                        //FORMULA
                        //SI(R13 <= S$3/2;S$3/2;MAX(R13;S$3));
                        if (amount <= salaryMin.get()/2 ){
                            monthsApr.get(i).setAmount(BigDecimal.valueOf(salaryMin.get()/2));
                        }else {
                            monthsApr.get(i).setAmount(BigDecimal.valueOf(Stream.of(
                                    amount,salaryMin.get()
                            ).max(Double::compareTo).orElse(0.0)));
                        }
                    }
                }
                break;
            default:
                //revision param
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 2)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryMin.set(param.getValue());
                            periodSalaryMin.set(param.getPeriod());
                        });
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 42)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryIntegral.set(param.getValue());
                            periodSalaryMinIntegral.set(param.getPeriod());
                        });

                double salaryMinInternal = baseSalary.get() == 0.0?salaryIntegral.get(): salaryMin.get();
                String periodSalaryMinInternal = baseSalary.get() == 0.0?periodSalaryMinIntegral.get(): periodSalaryMin.get();
                int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMinInternal);
                if (idxEmp != -1){
                    for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                        double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                        paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        //ignore month marcth
                        if(paymentComponentDTO
                                .getProjections()
                                .get(i).getMonth().equalsIgnoreCase(periodSalaryMinInternal)){
                            //SI($F4<>"";SI(R13<=S$3;S$3;R13);SI(R13<S$4;S$4;R13));
                            if (amount <= salaryMinInternal){
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(salaryMin.get()));
                            }else {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                            }
                        }
                    }
                }
        }
        component.add(paymentComponentDTO);
    }
    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters,String period, Integer range){
         component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                 .parallel()
                 .forEach(paymentComponentDTO -> {
                     AtomicReference<Double> salaryMinParam = new AtomicReference<>((double) 0);
                     AtomicReference<String> periodSalaryMinParam = new AtomicReference<>((String) "");
                     parameters.stream()
                             .filter(p -> p.getParameter().getId() == 2)
                             .findFirst()
                             .ifPresent(param -> {
                                 salaryMinParam.set(param.getValue());
                                 periodSalaryMinParam.set(param.getPeriod());
                             });
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
                         //SI(T13<=U$3;U$3;T13);SI(T13<U$4;U$4;T13));
                         //T13*(1+SI(S13/R13-1>0;SI(S13/R13-1<=U$8;U$8-(S13/R13-1);0);U$8)));
                         differPercent=(salaryEnd.get())/(salaryFirst.get())-1;
                     }
                     double percent = percetange.get()/100;
                     int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                             .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                     if (idx != -1){
                         for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                             double revisionSalaryAmount=0;
                             double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                             double minSalaryAmount = salaryMinParam.get();
                             paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                             if(paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                                 // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                                 if(differPercent > 0){
                                     // 9%
                                     if(differPercent <= percent){
                                         differPercent = percent - differPercent;
                                     }else {
                                         differPercent = 0;
                                     }
                                 }else {
                                     differPercent = percent;
                                 }
                                 revisionSalaryAmount = amount* (1+(differPercent));
                                 if (minSalaryAmount > revisionSalaryAmount) {
                                     paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(minSalaryAmount));
                                 } else {
                                     paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                                 }
                             }
                         }
                     }
                });
    }

    public void commission(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission) {
        // BUSCAMOS LOS PAYMENT COMPONENTS NECESARIOS PARA EL CALCUL  DE COMISIONES
        AtomicReference<BigDecimal> commision1 = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> commision2 = new AtomicReference<>(BigDecimal.ZERO);
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938003"))
                .findFirst()
                .ifPresent(p -> {
                    commision1.set(p.getAmount());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("PC938012"))
                .findFirst()
                .ifPresent(p -> {
                    commision2.set(p.getAmount());
                });
        List<ParametersDTO> commissionList = parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getName(), "Comisiones (anual)"))
                .collect(Collectors.toList());
        // Crear un componente de pago para las comisiones(sumatoria de los dos componentes)
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("COMMISSION");
        //PC938003 / PC938012
        //mes base -> el primero del mes
        AtomicReference<BigDecimal> maxCommission = new AtomicReference<>(BigDecimal.ZERO);
        maxCommission.set(getMayor(commision1.get(),commision2.get()));
        BigDecimal totalBase = BigDecimal.valueOf(commissionList.isEmpty()?0:commissionList.get(0).getValue());
        BigDecimal sumCommissionValue = sumCommission.doubleValue()==0?BigDecimal.ONE:sumCommission;
        paymentComponentDTO.setAmount(BigDecimal.valueOf(totalBase.doubleValue()/12*
                maxCommission.get().doubleValue()/sumCommissionValue.doubleValue()));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));

        // buscamos el primer valor del parametro de comisiones
        AtomicReference<Double> paramCommissionInitValue = new AtomicReference<>((double) 0);
        AtomicReference<String> paramCommissionInitPeriod = new AtomicReference<>((String) "");
        parameters.stream()
                .filter(p -> p.getParameter().getId() == 28)
                .findFirst()
                .ifPresent(param -> {
                    paramCommissionInitValue.set
                            (param.getValue());
                });
        AtomicReference<Double> paramValue = new AtomicReference<>(0.0);
        component.stream()
                .parallel()
                .forEach(c -> {
                    double defaultSum = 1.0;
                    for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                        try {
                            ParamFilterDTO res = isRefreshCommisionValue(commissionList,paymentComponentDTO.getProjections().get(i).getMonth());
                            if (Boolean.TRUE.equals(res.getStatus())) paramValue.set(res.getValue());
                            double sum = sumCommission.doubleValue()==0?defaultSum:sumCommission.doubleValue();
                            double vc = maxCommission.get().doubleValue()/sum;
                            double vd = paramValue.get()/12;
                            double v = vc * vd;
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(v));
                        }catch (Exception e){
                            //log.error("error -> {}", e.getMessage());
                        }
                    }
                });
        component.add(paymentComponentDTO);
    }

    public static ParamFilterDTO isRefreshCommisionValue(List<ParametersDTO> params, String periodProjection){
        AtomicReference<Double> value = new AtomicReference<>(0.0);
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
                    return dateParam.equals(dateProjection);
                });
    return ParamFilterDTO.builder()
            .status(status)
            .value(value.get())
            .build();
    }
    public void prodMonthPrime(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        // BUSCAMOS LOS PAYMENT COMPONENTS NECESARIOS PARA EL CALCUL  DE COMISIONES
        AtomicReference<BigDecimal> salary = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> commission = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<BigDecimal> hhee = new AtomicReference<>(BigDecimal.ZERO);
        //TRAER COMPONENTES DE PAGO PARA CALCULAR EL SALARIO BASE PC938001 - PC938005
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .ifPresent(p -> {
                    salary.set(p.getAmount());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("COMMISSION"))
                .findFirst()
                .ifPresent(p -> {
                    commission.set(p.getAmount());
                });
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("HHEE"))
                .findFirst()
                .ifPresent(p -> {
                    hhee.set(p.getAmount());
                });
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("PRIMA MENSUAL");
        //SUMA DE LOS COMPONENTES
        //paymentComponentDTO.setAmount(salary.get().add(commission.get()).add(hhee.get()));
        paymentComponentDTO.setAmount(commission.get().add(hhee.get()));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        if (!Objects.equals(classEmployee, "PRA") || !Objects.equals(classEmployee, "APR")){
            component.stream()
                    .parallel()
                    .forEach(c -> {
                        if (!c.getPaymentComponent().isEmpty()){
                            for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf((Math.round(paymentComponentDTO.getAmount().doubleValue() * 100d) / 100d)));
                            }
                        }else {
                            for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.ZERO);
                            }
                        }
                    });
        }else {
            component.stream()
                    .parallel()
                    .forEach(c -> {
                        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.ZERO);
                        }
                    });
        }
        component.add(paymentComponentDTO);
    }

    private BigDecimal getMayor(BigDecimal b1 , BigDecimal b2){
         return b1.doubleValue()>b2.doubleValue()?b1:b2;
    }
}
