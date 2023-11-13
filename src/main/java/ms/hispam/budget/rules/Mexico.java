package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.DateCache;
import ms.hispam.budget.cache.SimpleCache;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j(topic = "Mexico")
public class Mexico {
    private final DaysVacationOfTimeRepository daysVacationOfTimeRepository;
    static final String TYPEMONTH="yyyyMM";
    private DateCache dateCache = new DateCache(3);
    public Mexico(DaysVacationOfTimeRepository daysVacationOfTimeRepository) {
        this.daysVacationOfTimeRepository = daysVacationOfTimeRepository;
    }

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
        AtomicReference<Double> incrementSalaryPercent = new AtomicReference<>((double) 0);
        parameters.stream()
                .filter(p -> p.getParameter().getId() == 32)
                .findFirst()
                .ifPresent(param -> {
                    incrementSalaryPercent.set(param.getValue());
                });
        //List min salaries
        List<ParametersDTO> salaryList = parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getName(), "Salario Mínimo Mexico"))
                .collect(Collectors.toList());
        double percent = incrementSalaryPercent.get()/100;
        double paramValueSalary;
        boolean currentSalaryParamStatus;
        for (int i = 0; i < paymentComponentDTO.getProjections().size(); i++) {
            double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
            String currentMonthProjection = paymentComponentDTO.getProjections().get(i).getMonth();
            ParamFilterDTO currentSalaryParamByMonth = getValueSalaryByPeriod(salaryList,currentMonthProjection);
            currentSalaryParamStatus = currentSalaryParamByMonth.getStatus();
            log.info("currentSalaryParamStatus: {}",currentSalaryParamStatus);
            if (Boolean.TRUE.equals(currentSalaryParamStatus)) {
                paramValueSalary = currentSalaryParamByMonth.getValue();
                DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                        .appendPattern(TYPEMONTH)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter();
                LocalDate dateProjection = LocalDate.parse(currentMonthProjection, dateFormat);
                //save in cache current salary min
                this.dateCache.put(dateProjection,paramValueSalary);
                if (amount < paramValueSalary && paramValueSalary != 0.0) {
                    // R11*(1+SI(R11<S$2;S$3;0)));
                    double incrementSalary = amount * percent;
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount + incrementSalary));
                } else {
                    paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                }
            } else {
                paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
            }
        }
        component.add(paymentComponentDTO);
    }
    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters){
        component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .parallel()
                .forEach(paymentComponentDTO -> {
                    //boolean isApplyPercent=false;
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
                    //List min salaries
                    String[] periodRevisionSalary = periodRetractive.get();
                    double salaryEnd = this.dateCache.getUltimoValorEnRango(periodRevisionSalary[0], periodRevisionSalary[1]);
                    double differPercent=0.0;
                    if(Boolean.TRUE.equals(isRetroactive.get())){
                        int idxStart;
                        idxStart=  Shared.getIndex(paymentComponentDTO.getProjections().stream()
                                .map(MonthProjection::getMonth).collect(Collectors.toList()),periodRevisionSalary[0]);
                        AtomicReference<Double> salaryFirst= new AtomicReference<>(0.0);
                        salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                        //isApplyPercent = salaryFirst.get() < salaryEnd;
                        differPercent=(salaryFirst.get()/(salaryEnd))-1;
                    }
                    double percent = percetange.get()/100;
                    if(differPercent > 0 && differPercent <= percent){
                        differPercent = percent - differPercent;
                    }else {
                        differPercent = percent;
                    }
                    int idxRevSal = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                            .map(MonthProjection::getMonth).collect(Collectors.toList()),periodSalaryMin.get());
                    if (idxRevSal != -1){
                        for (int i = idxRevSal; i < paymentComponentDTO.getProjections().size(); i++) {
                            double v;
                            double amount = i==0?paymentComponentDTO.getProjections().get(i).getAmount().doubleValue(): paymentComponentDTO.getProjections().get(i-1).getAmount().doubleValue();
                            //R11*(1+SI(P11<Q$2;0;S$4))
                            if(paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(periodSalaryMin.get())){
                                v = amount* (1+(differPercent));
                            }else {
                                v = amount;
                            }
                           /* paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(isApplyPercent ? amount * (1 + percent) : amount));*/
                            paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(v));
                            //differPercent = 0.0;
                        }
                    }
                });

    }
    public ParamFilterDTO getValueSalaryByPeriod(List<ParametersDTO> params, String periodProjection){
        //ordenar lista
        sortParameters(params);
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>("");
        Boolean status = params.stream()
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
        //TODO ELEMINAR EL ELEMENTO SELECCIONADO DE LISTA
        return ParamFilterDTO.builder()
                .status(status)
                .value(value.get())
                .period(periodSalaryMin.get())
                .build();
    }
    private void sortParameters(List<ParametersDTO> params) {
        params.sort((o1, o2) -> {
            DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                    .appendPattern(TYPEMONTH)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter();
            LocalDate dateParam = LocalDate.parse(o1.getPeriod(),dateFormat);
            LocalDate dateProjection = LocalDate.parse(o2.getPeriod(), dateFormat);
            return dateParam.compareTo(dateProjection);
        });
    }
    public static ParamFilterDTO getParamByPeriod(List<ParametersDTO> params, String periodProjection){
        log.info("periodProjection: {}",periodProjection);
        log.info("params: {}",params);
        return params.stream()
                .parallel()
                .filter(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    return dateParam.equals(dateProjection);
                }).map(parametersDTO -> ParamFilterDTO.builder()
                        .value(parametersDTO.getValue())
                        .period(parametersDTO.getPeriod())
                        .status(true)
                        .build())
                .findFirst()
                .orElse(ParamFilterDTO
                        .builder()
                        .status(false)
                        .build());
    }
    public void provAguinaldo(List<PaymentComponentDTO> component, String period, Integer range) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // create payment component prov aguinaldo
        if (paymentComponentDTO != null){
            PaymentComponentDTO paymentComponentProvAguin = new PaymentComponentDTO();
            paymentComponentProvAguin.setPaymentComponent("PROV_AGUINALDO");
            paymentComponentProvAguin.setAmount(BigDecimal.valueOf((paymentComponentDTO.getAmount().doubleValue() / 30) * 0.25));
            paymentComponentProvAguin.setProjections(paymentComponentDTO.getProjections());
            // prov aguinaldo
            for (int i = 0; i < paymentComponentProvAguin.getProjections().size(); i++) {
                double amountProj = paymentComponentProvAguin.getProjections().get(i).getAmount().doubleValue();
                paymentComponentProvAguin.getProjections().get(i).setAmount(BigDecimal.valueOf((amountProj/30) * 1.25));
            }
            component.add(paymentComponentProvAguin);
        }
    }
    public void provVacaciones(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, Date dateContract, Date dateBirth) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // get date contract
        LocalDate dateContractLocal = Shared.convertToLocalDateViaInstant(dateContract);
        // get date birth
        LocalDate dateBirthLocal = Shared.convertToLocalDateViaInstant(dateBirth);
        // get date actual
        LocalDate dateActual = LocalDate.now();
        int diffYears = dateActual.getYear() - dateContractLocal.getYear();
        //get list vacation days
        int daysVacation = this.getDaysVacation(diffYears);
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

    private int getDaysVacation(int diffYears) {
        return daysVacationOfTimeRepository.findAll()
                .stream()
                .mapToInt(daysVacationOfTime -> {
                    String[] timeOfService = daysVacationOfTime.getRange().split("a");
                    int init = Integer.parseInt(timeOfService[0]);
                    int end = Integer.parseInt(timeOfService[1]);
                    if (end == 0){
                        return diffYears >= init ? daysVacationOfTime.getVacationDays() : 0;
                    }else {
                        return diffYears >= init && diffYears <= end ? daysVacationOfTime.getVacationDays() : 0;
                    }
                }).sum();
    }
}
