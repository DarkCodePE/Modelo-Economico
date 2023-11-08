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
    public List<PaymentComponentDTO> revisionSalary(List<PaymentComponentDTO> componentDTO, ParametersDTO dto  ){
        double differPercent=0.0;
        if(Boolean.TRUE.equals(dto.getIsRetroactive())){
            int idxStart;
            int idxEnd;
            String[]   period;
            period = dto.getPeriodRetroactive().split("-");
            idxStart=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),period[0]);
            idxEnd=  Shared.getIndex(componentDTO.get(1).getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),period.length==1? period[0]:period[1]);
            AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> comisionEnd= new AtomicReference<>(0.0);
            componentDTO.stream().filter(c->c.getType()==1).findFirst().ifPresent(l->{
                salaryFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                salaryEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
            });
            componentDTO.stream().filter(c->c.getType()==2).findFirst().ifPresent(l->{
                comisionFirst.set(l.getProjections().get(idxStart).getAmount().doubleValue());
                comisionEnd.set(l.getProjections().get(idxEnd).getAmount().doubleValue());
            });
            differPercent=(salaryEnd.get()+comisionEnd.get())/(salaryFirst.get()+comisionFirst.get())-1;
        }
        double percent = dto.getValue()/100;
        for(PaymentComponentDTO o : componentDTO.stream().filter(f->(
                f.getType()==1|| f.getType()==2 || f.getType()==7)).collect(Collectors.toList())){

            int idx = Shared.getIndex(o.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()),dto.getPeriod());
            for (int i = idx; i < o.getProjections().size(); i++) {
                double v=0;
                double amount = i==0?o.getProjections().get(i).getAmount().doubleValue(): o.getProjections().get(i-1).getAmount().doubleValue();
                o.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                if(o.getProjections().get(i).getMonth().equalsIgnoreCase(dto.getPeriod())){
                    if(o.getType()==1 ||o.getType()==7|| o.getType()==2 ){
                        if(o.getType()==7){
                            amount=o.getAmount().doubleValue();
                        }
                        v = amount* (1+(differPercent>=percent?0:percent-differPercent));
                    }
                    o.getProjections().get(i).setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
                }
            }
        }



        return componentDTO;
    }


    public List<PaymentComponentDTO> salMin(List<PaymentComponentDTO> componentDTO, String employeeClass){
        //SI(Y($D4<>"PRA";$D4<>"T";$D4<>"JP";$D4<>"APR");
        if (!Objects.equals(employeeClass, "PRA") || !employeeClass.equals("T") || !employeeClass.equals("JP") || !employeeClass.equals("APR")){
            //TODO: Object
            return componentDTO;
        }
        //SI(MES(S$12)<>3;SI($F4<>""; SI(R13<=S$3;S$3;R13)
        if (componentDTO.stream().anyMatch(p->p.getProjections().stream().anyMatch(m->m.getMonth().equalsIgnoreCase("MAR")))){
            return componentDTO;
        }
        //SI(R13<S$4;S$4;R13))
        //TODO: OBTENER LOS MONTOS
        //componentDTO.
        List<Integer> nums = Arrays.asList(12, 15);
        // max = R13
        Integer max = nums.stream()
                .max(Integer::compareTo).orElse(0);
        if (max != 0 && max < SALARY_MIN_INC_PLA_DIR) {
            log.info("El salario minimo, {}", max);
        }else {
            log.info("El salario minimo, {}", SALARY_MIN_INC_PLA_DIR);
        }
        //R13*(1+SI(Q13/P13-1>0;SI(Q13/P13-1<=S$8;S$8-(Q13/P13-1);0);S$8)))
        //SI($D4="PRA";SI(R13<=S$5;S$5;R13);
        if (Objects.equals(employeeClass, "PRA")){
            if (max <= SALARY_MIN_PRA_INC_PLA_DIR){
                max = SALARY_MIN_PRA_INC_PLA_DIR;
            }else {
                max = max;
            }
        }
        //SI($D4="T";SI(R13<=S$3;S$3;R13)
        if (Objects.equals(employeeClass, "T")){
            if (max <= SALARY_MIN_INC_PLA_DIR){
                log.info("Sal", SALARY_MIN_INC_PLA_DIR);
            }else {
                log.info("Sal", max);
            }
        }
        //SI($D4="JP";SI(R13<=S$6;S$6;R13);
        if (Objects.equals(employeeClass, "JP")){
            if (max <= SALARY_MIN_INC_PLA_DIR){
                log.info("Sal", SALARY_MIN_INC_PLA_DIR);
            }else {
                log.info("Sal", max);
            }
        }
        //SI(R13<=S$3/2;S$3/2;MAX(R13;S$3))
        if (max <= SALARY_MIN_INC_PLA_DIR/2) {
            log.info("Sal", SALARY_MIN_INC_PLA_DIR / 2);
        }else {
            log.info("Sal", max);
        }
        return null;
    }

    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range){
        // HEADCOUNT: OBTIENS LAS PROYECCION Y LA PO, AQUI AGREGAMOS LOS PARAMETROS CUSTOM
        AtomicReference<Double> baseSalary = new AtomicReference<>((double) 0);
        AtomicReference<Double> baseSalaryIntegral = new AtomicReference<>((double) 0);
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
                        .map(d->d.getMonth()).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> months= paymentComponentDTO.getProjections();
                if (idx != -1){
                    for (int i = idx; i < months.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : months.get(i-1).getAmount().doubleValue();
                        double salaryMinInternal = 0.0;
                        double salaryIntegralInternal = 0.0;
                        double maxSalary = 0.0;
                        log.info("{}", classEmployee);
                        //FORMULA
                        //SI(R13<=S$5;S$5;R13);
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
                AtomicReference<Double> salaryT = new AtomicReference<>((double) 0);
                log.info("{}",paymentComponentDTO);
                //mes
                int idxT = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(d->d.getMonth()).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> monthsT= paymentComponentDTO.getProjections();
                if (idxT != -1){
                    for (int i = idxT; i < monthsT.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : monthsT.get(i-1).getAmount().doubleValue();
                        double salaryMinInternal = 0.0;
                        double salaryIntegralInternal = 0.0;
                        double maxSalary = 0.0;
                        log.info("{}", classEmployee);
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
            case "T":
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 25)
                        .findFirst()
                        .ifPresent(param -> {
                            salaryMin.set(param.getValue());
                            periodSalaryMin.set(param.getPeriod());
                        });

                //FIND MONTH TO APPLY PARAMETER T
                AtomicReference<Double> salaryT1 = new AtomicReference<>((double) 0);
                //mes base
                int idxT1 = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(d->d.getMonth()).collect(Collectors.toList()),periodSalaryMin.get());
                List<MonthProjection> monthsT1= paymentComponentDTO.getProjections();
                if (idxT1 != -1){
                    for (int i = idxT1; i < monthsT1.size(); i++) {
                        double amount = (i == 0) ? paymentComponentDTO.getAmount().doubleValue() : monthsT1.get(i-1).getAmount().doubleValue();
                        double salaryMinInternal = 0.0;
                        double salaryIntegralInternal = 0.0;
                        double maxSalary = 0.0;
                        log.info("{}", classEmployee);
                        //FORMULA
                        //SI(R13 <= S$3;S$3;R13);
                        if (amount <= salaryMin.get()){
                            monthsT1.get(i).setAmount(BigDecimal.valueOf( salaryMin.get()));
                        }else {
                            monthsT1.get(i).setAmount(BigDecimal.valueOf(amount));
                        }
                    }
                }
                break;
            case "APR":
                parameters.stream()
                        .filter(p -> p.getParameter().getId() == 25)
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
                        double salaryMinInternal = 0.0;
                        double salaryIntegralInternal = 0.0;
                        double maxSalary = 0.0;
                        log.info("{}", classEmployee);
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
                int idxEmp = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                for (int i = idxEmp; i < paymentComponentDTO.getProjections().size(); i++) {
                    double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                    //ignore month marcth
                    if(paymentComponentDTO
                            .getProjections()
                            .get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                        //SI(R13 <= S$3;S$3;R13);
                        log.info("{}", salaryMin.get());
                        if (amount <= salaryMin.get()){
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(salaryMin.get()));
                        }else {
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                        }
                    }
                }
        }
        component.add(paymentComponentDTO);
    }
    public PaymentComponentDTO execRevisionSalary(PaymentComponentDTO paymentComponentDTO, List<ParametersDTO> parameters){
        double differPercent=0.0;
        AtomicReference<String> periodSalaryMin = new AtomicReference<>((String) "");
        AtomicReference<Boolean> isRetroactive = new AtomicReference<>((Boolean) false);
        AtomicReference<String[]> periodRetractive = new AtomicReference<>((String[]) null);
        AtomicReference<Double> percetange = new AtomicReference<>((double) 0);
        parameters.stream()
                .filter(p -> p.getParameter().getId() == 1)
                .findFirst()
                .ifPresent(param -> {
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
                    .map(monthProjection -> monthProjection.getMonth()).collect(Collectors.toList()),periodRevisionSalary.length==1? periodRevisionSalary[0]:periodRevisionSalary[1]);
            AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
            AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
            salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
            //salaryFirst.set(1160000.0);
            salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
           //salaryEnd.set(120000.0);
            differPercent=(salaryEnd.get())/(salaryFirst.get())-1;
        }
        double percent = percetange.get()/100;
        //double percent = 0.09;
        int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
        for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
            double v=0;
            double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
            if(paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                if(differPercent > 0.00){
                    // 9%
                    if(differPercent <= percent){
                        differPercent = percent - differPercent;
                    }else {
                        differPercent = 0.00;
                    }
                }else {
                    differPercent = percent;
                }
                log.info("differPercent -> {}", differPercent);
                log.info("amount sin ajuste -> {}", amount);
                v = amount* (1+(differPercent));
                log.info("amount con ajuste ->{}", Math.round(v * 100d) / 100d);
                paymentComponentDTO
                        .getProjections()
                        .get(i)
                        .setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
            }
        }
        return paymentComponentDTO;
    }
    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters,String period, Integer range){
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
                                 .map(monthProjection -> monthProjection.getMonth()).collect(Collectors.toList()),periodRevisionSalary.length==1? periodRevisionSalary[0]:periodRevisionSalary[1]);
                         AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
                         AtomicReference<Double> salaryEnd= new AtomicReference<>(0.0);
                         salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                         salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                         differPercent=(salaryEnd.get())/(salaryFirst.get())-1;
                     }
                     double percent = percetange.get()/100;
                     int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                             .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                     for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                         double v=0;
                         double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
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
                             v = amount* (1+(differPercent));
                             log.info("{}", v);
                             paymentComponentDTO
                                     .getProjections()
                                     .get(i)
                                     .setAmount(BigDecimal.valueOf(Math.round(v * 100d) / 100d));
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
        paymentComponentDTO.setAmount(Stream.of(
                commision1.get(),commision2.get()
        ).reduce(BigDecimal.ZERO,BigDecimal::add));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        // buscamos el primer valor del parametro de comisiones
        AtomicReference<Double> paramCommissionInitValue = new AtomicReference<>((double) 0);
        parameters.stream()
                .filter(p -> p.getParameter().getId() == 28)
                .findFirst()
                .ifPresent(param -> paramCommissionInitValue.set
                        (param.getValue()));


        AtomicReference<Double> paramValue = new AtomicReference<>(0.0);
        component.stream()
                .parallel()
                .forEach(c -> {
                    if (c.getPaymentComponent().equalsIgnoreCase("PC938003") || c.getPaymentComponent().equalsIgnoreCase("PC938012")){
                        double defaultSum = 1.0;
                        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                            double amountF = paymentComponentDTO.getProjections().get(i).getAmount().doubleValue();
                            try {
                                ParamFilterDTO res = isRefreshCommisionValue(commissionList,paymentComponentDTO.getProjections().get(i).getMonth());
                                if (Boolean.TRUE.equals(res.getStatus())) paramValue.set(res.getValue());
                                //if (paramValue.get() == 0.0) paramValue.set(paramCommissionInitValue.get());
                                //if (sumCommission.doubleValue() == 0)
                                double sum = sumCommission.doubleValue()==0?defaultSum:sumCommission.doubleValue();
                                log.info("value sum --> {}", sum);
                                double vc = amountF/sum;
                                double vd = paramValue.get()/12;
                                double v = vc * vd;
                                log.info("value mensual --> {}", vc);
                                log.info("value amoutf--> {}", vd);
                                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(v));
                            }catch (Exception e){
                                log.error("error -> {}", e.getMessage());
                            }
                        }
                    }
                });
        component.add(paymentComponentDTO);
    }
    public static double getParamCommissionValue(List<ParametersDTO> params, String periodProjection, String period, Integer range){
        AtomicReference<Double> parameter = new AtomicReference<>((double) 0);
        // necesito saber que parametro debo aplicar a la proyeccion

        // obtiene el valor del parametro segun el periodo de la proyeccion

        //renovar el parametro si el periodo del parametro inferior a la periodo de la proyecion

        //notificador de parametro por rango de periodo
        params.stream()
                .parallel()
                .map(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    double value = 0.0;
                    //LocalDate dateStart = LocalDate.parse(period, dateFormat);
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    if (dateParam.equals(dateProjection)){
                        value = p.getValue();
                    }
                    // es igual o fecha de inicio es mayor a la fecha del parametro
                    return value;
                }).findFirst()
                .ifPresent(parameter::set);
        return parameter.get();
    }
    public static boolean isRefreshParamCommission(List<ParametersDTO> params, String periodProjection){
        return params.stream()
                .parallel()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    return dateParam.equals(dateProjection);
                });
    }
    public static Map<String, Object> isRefreshParamCom(List<ParametersDTO> params, String periodProjection){
        AtomicReference<Integer> idParm = new AtomicReference<>(0);
        HashMap<String, Object> map = new HashMap<>();
        Boolean status = params.stream()
                .parallel()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    idParm.set(p.getParameter().getId());
                    return dateParam.equals(dateProjection);
                });
        map.put("id",idParm.get());
        map.put("status",status);
        return map;
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
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent("PRIMA MENSUAL");
        paymentComponentDTO.setAmount(salary.get().add(commission.get()));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        if (!Objects.equals(classEmployee, "PRA") || !Objects.equals(classEmployee, "APR")){
            component.stream()
                    .parallel()
                    .forEach(c -> {
                        //buscar le payment comoment PC938001
                        if (!c.getPaymentComponent().isEmpty() && !c.getPaymentComponent().equalsIgnoreCase("PC938001")){
                            for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                                int idxF = Shared.getIndex(paymentComponentDTO.getProjections()
                                        .stream()
                                        .map(MonthProjection::getMonth)
                                        .collect(Collectors.toList()),period);
                                List<MonthProjection> monthsT1= paymentComponentDTO.getProjections();
                                if (idxF != -1){
                                    for (int j = idxF; j < monthsT1.size(); j++) {
                                        double amountF = (j == 0) ? paymentComponentDTO.getAmount().doubleValue() : monthsT1.get(j-1).getAmount().doubleValue();
                                        monthsT1.get(j).setAmount(BigDecimal.valueOf(amountF));
                                    }
                                }
                            }
                        }else {
                            for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                                int idxF = Shared.getIndex(paymentComponentDTO.getProjections()
                                        .stream()
                                        .map(MonthProjection::getMonth)
                                        .collect(Collectors.toList()),period);
                                    List<MonthProjection> monthsT1= paymentComponentDTO.getProjections();
                                if (idxF != -1){
                                    for (int j = idxF; j < monthsT1.size(); j++) {
                                        monthsT1.get(j).setAmount(BigDecimal.ZERO);
                                    }
                                }
                            }
                        }
                    });
        }else {
            component.stream()
                    .parallel()
                    .forEach(c -> {
                        //buscar le payment comoment PC938001
                        if (!c.getPaymentComponent().isEmpty() && !c.getPaymentComponent().equalsIgnoreCase("PC938001")) {
                            for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
                                int idxF = Shared.getIndex(paymentComponentDTO.getProjections()
                                        .stream()
                                        .map(MonthProjection::getMonth)
                                        .collect(Collectors.toList()), period);
                                List<MonthProjection> monthsT1 = paymentComponentDTO.getProjections();
                                if (idxF != -1) {
                                    for (int j = idxF; j < monthsT1.size(); j++) {
                                        monthsT1.get(j).setAmount(BigDecimal.ZERO);
                                    }
                                }
                            }
                        }
                    });
        }
        component.add(paymentComponentDTO);
    }

}
