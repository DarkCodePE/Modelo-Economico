package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.entity.mysql.ConventArg;
import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.util.Shared;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "ARGENTINA")
public class Argentina {
    static final String COUNTRY = "ARGENTINA";
    static final String TYPEMONTH = "yyyyMM";
    // Método auxiliar para crear el caché de parámetros
    private void createCache(List<ParametersDTO> parameterList, Map<String, ParametersDTO> parameterMap, Map<String, Double> cache, BiConsumer<ParametersDTO, ParametersDTO> updateMaps) {
        List<ParametersDTO> sortedParameterList = new ArrayList<>(parameterList);
        sortedParameterList.sort(Comparator.comparing(ParametersDTO::getPeriod));
        for (ParametersDTO parameter : parameterList) {
            ParametersDTO mapParameter = parameterMap.get(parameter.getPeriod());
            if (mapParameter == null) {
                mapParameter = findLatestSalaryForPeriod(sortedParameterList, parameter.getPeriod());
                if (mapParameter != null) {
                    parameterMap.put(parameter.getPeriod(), mapParameter);
                }
            }
            if (mapParameter != null) {
                updateMaps.accept(parameter, mapParameter);
                cache.put(parameter.getPeriod(), mapParameter.getValue());
            }
        }
    }
    // Método auxiliar para encontrar el salario más reciente para un período dado
    private ParametersDTO findLatestSalaryForPeriod(List<ParametersDTO> salaryList, String period) {
        for (int i = salaryList.size() - 1; i >= 0; i--) {
            if (salaryList.get(i).getPeriod().compareTo(period) <= 0) {
                return salaryList.get(i);
            }
        }
        return null;
    }
    // Método auxiliar para crear un mapa de componentes de pago
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
    }
    //Sueldo Básico
    //=M4*(1+
    //SI($H4="FC MVL";'Parámetros'!N$2;
    //SI($H4="FC TASA";'Parámetros'!N$3;
    //SI($H4="FC VALORA";'Parámetros'!N$4;
    //SI($H4="CEPETEL TASA"; 'Parámetros'!N$5;
    //SI($H4="FATEL MVL";'Parámetros'!N$6;
    //SI($H4="FATEL TASA";  'Parámetros'!N$7;
    //SI($H4="FOEESITRA MVL"; 'Parámetros'!N$8;
    //SI($H4="FOEESITRA TASA"; 'Parámetros'!N$9;
    //SI($H4="FOETRA MVL"; 'Parámetros'!N$10;
    //SI($H4="FOETRA TASA"; 'Parámetros'!N$11;
    //SI($H4="FOPSTTA MVL"; 'Parámetros'!N$12;
    //SI($H4="FOPSTTA TASA"; 'Parámetros'!N$13;
    //SI($H4="UPJET MVL"; 'Parámetros'!N$14;
    //SI($H4="UPJET TASA";  'Parámetros'!N$15;0)))))))))))))))*(1+SI(Y(MES(N$2)=MES($K4);AÑO(N$2)=AÑO($K4));$L4;0))
    //Adicionales Rem (Salario Conf)
    public void basicSalary(List<PaymentComponentDTO> components, List<ParametersDTO> parameters, String period, Integer range, Map<String, ConventArg> conventArgMap, String convenio) {
        List<ParametersDTO> relevantParameters = filterParametersByConvenio(parameters, convenio);
        Map<String, ParametersDTO> parameterMap = createCacheMap(relevantParameters);
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO basicSalaryBase = componentMap.get("BASIC_SALARY_BASE");
        if (basicSalaryBase != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            double basicSalaryValue = getCachedValue(parameterMap, nextPeriod);
            //Sueldo Básico
            PaymentComponentDTO basicSalary = new PaymentComponentDTO();
            basicSalary.setPaymentComponent("BASIC_SALARY");
            double salary = basicSalaryBase.getAmount().doubleValue() * (1 + basicSalaryValue);
            double promoAdjustment = calculatePromoAdjustment(period, componentMap);
            double totalSalary = salary * promoAdjustment;
            basicSalary.setAmount(BigDecimal.valueOf(totalSalary));
            basicSalary.setProjections(Shared.generateMonthProjection(period, range, basicSalary.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (basicSalaryBase.getProjections() != null) {
                for (MonthProjection projection : basicSalaryBase.getProjections()) {
                    ParametersDTO parameter = parameterMap.get(projection.getMonth());
                    double parameterValue = parameter != null ? parameter.getValue() : 0;
                    MonthProjection newProjection = new MonthProjection();
                    newProjection.setMonth(projection.getMonth());
                    String month = projection.getMonth();
                    double newAmount = projection.getAmount().doubleValue() * (1 + parameterValue);
                    double newPromoAdjustment = calculatePromoAdjustment(month, componentMap);
                    double newTotalAmount = newAmount * newPromoAdjustment;
                    newProjection.setAmount(BigDecimal.valueOf(newTotalAmount));
                    projections.add(newProjection);
                }
                basicSalary.setProjections(projections);
            } else {
                basicSalary.setAmount(BigDecimal.ZERO);
                basicSalary.setProjections(Shared.generateMonthProjection(period, range, basicSalary.getAmount()));
            }
            components.add(basicSalary);
        } else {
            PaymentComponentDTO basicSalary = new PaymentComponentDTO();
            basicSalary.setPaymentComponent("BASIC_SALARY");
            basicSalary.setAmount(BigDecimal.ZERO);
            basicSalary.setProjections(Shared.generateMonthProjection(period, range, basicSalary.getAmount()));
            components.add(basicSalary);
        }
    }
    // Método auxiliar para calcular el ajuste de promoción
    private double calculatePromoAdjustment(String month, Map<String, PaymentComponentDTO> componentMap) {
        PaymentComponentDTO promoMonthComponent = componentMap.get("mes_promo");
        PaymentComponentDTO promoComponent = componentMap.get("promo");
        if (promoMonthComponent != null && promoComponent != null && promoMonthComponent.getAmountString() != null) {
            PaymentComponentDTO promoComponentProject = new PaymentComponentDTO();
            promoComponentProject.setPaymentComponent("promotion");
            promoComponentProject.setAmount(promoComponent.getAmount());
            promoComponentProject.setAmountString(promoMonthComponent.getAmountString());
            DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                    .appendPattern(TYPEMONTH)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter();
            LocalDate promoDate;
            try {
                promoDate = LocalDate.parse(promoComponentProject.getAmountString(), dateFormat);
            } catch (DateTimeParseException e) {
                int excelDate = Integer.parseInt(promoComponentProject.getAmountString());
                promoDate = LocalDate.of(1900, 1, 1).plusDays(excelDate - 2);
            }
            promoDate = promoDate.withDayOfMonth(1);
            LocalDate date = LocalDate.parse(month, dateFormat);
            if (!promoComponentProject.getAmountString().isEmpty() && !promoDate.isAfter(date) && promoDate.getMonthValue() == date.getMonthValue()) {
                return promoComponentProject.getAmount().doubleValue();
            }
        }
        return 0;
    }
    //=K3*(1+
    //SI($C3="FC MVL";'Parámetros'!N$2;
    //SI($C3="FC TASA";'Parámetros'!N$3;
    //SI($C3="FC VALORA";'Parámetros'!N$4;
    //SI($C3="CEPETEL TASA"; 'Parámetros'!N$16;
    //SI($C3="FATEL MVL";'Parámetros'!N$17;
    //SI($C3="FATEL TASA";  'Parámetros'!N$18;
    //SI($C3="FOEESITRA MVL"; 'Parámetros'!N$19;
    //SI($C3="FOEESITRA TASA"; 'Parámetros'!N$20;
    //SI($C3="FOETRA MVL"; 'Parámetros'!N$21;
    //SI($C3="FOETRA TASA"; 'Parámetros'!N$22;
    //SI($C3="FOPSTTA MVL"; 'Parámetros'!N$23;
    //SI($C3="FOPSTTA TASA"; 'Parámetros'!N$24;
    //SI($C3="UPJET MVL"; 'Parámetros'!N$25;
    //SI($C3="UPJET TASA";  'Parámetros'!N$26;0)))))))))))))))
    public void additionalRemuneration(List<PaymentComponentDTO> components, List<ParametersDTO> parameters, String period, Integer range, Map<String, ConventArg> conventArgMap, String convenio) {
        List<ParametersDTO> relevantParameters = filterParametersByConvenio(parameters, convenio);
        Map<String, ParametersDTO> parameterMap = createCacheMap(relevantParameters);
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO additionalRemunerationBase = componentMap.get("ADDITIONAL_REMUNERATION_BASE");
        if (additionalRemunerationBase != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            double additionalRemunerationValue = getCachedValue(parameterMap, nextPeriod);
            //Adicionales Rem (Salario Conf)
            PaymentComponentDTO additionalRemuneration = new PaymentComponentDTO();
            additionalRemuneration.setPaymentComponent("ADDITIONAL_REMUNERATION");
            //K3*(1+additionalRemunerationValue)
            additionalRemuneration.setAmount(BigDecimal.valueOf(additionalRemunerationBase.getAmount().doubleValue() * (1 + additionalRemunerationValue)));
            additionalRemuneration.setProjections(Shared.generateMonthProjection(period, range, additionalRemuneration.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (additionalRemunerationBase.getProjections() != null) {
                for (MonthProjection projection : additionalRemunerationBase.getProjections()) {
                    ParametersDTO parameter = parameterMap.get(projection.getMonth());
                    MonthProjection newProjection = new MonthProjection();
                    newProjection.setMonth(projection.getMonth());
                    newProjection.setAmount(BigDecimal.valueOf(projection.getAmount().doubleValue() * (1 + (parameter != null ? parameter.getValue() : 0))));
                    projections.add(newProjection);
                }
                additionalRemuneration.setProjections(projections);
            }else {
                additionalRemuneration.setAmount(BigDecimal.ZERO);
                additionalRemuneration.setProjections(Shared.generateMonthProjection(period, range, additionalRemuneration.getAmount()));
            }
            components.add(additionalRemuneration);
        }else {
            PaymentComponentDTO additionalRemuneration = new PaymentComponentDTO();
            additionalRemuneration.setPaymentComponent("ADDITIONAL_REMUNERATION");
            additionalRemuneration.setAmount(BigDecimal.ZERO);
            additionalRemuneration.setProjections(Shared.generateMonthProjection(period, range, additionalRemuneration.getAmount()));
            components.add(additionalRemuneration);
        }
    }
    //Jornada Discontinua
    //=D3
    public void discontinuousDay(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO discontinuousDayBase = componentMap.get("DISCONTINUOUS_DAY_BASE");
        if (discontinuousDayBase != null) {
            PaymentComponentDTO discontinuousDayComponent = new PaymentComponentDTO();
            discontinuousDayComponent.setPaymentComponent("DISCONTINUOUS_DAY");
            discontinuousDayComponent.setAmount(discontinuousDayBase.getAmount());
            discontinuousDayComponent.setProjections(Shared.generateMonthProjection(period, range, discontinuousDayComponent.getAmount()));
            components.add(discontinuousDayComponent);
        }
    }
    //Tarifa Telefónica
    //=D3
    public void telephoneRate(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO telephoneRateBase = componentMap.get("TELEPHONE_RATE_BASE");
        if (telephoneRateBase != null) {
            PaymentComponentDTO telephoneRateComponent = new PaymentComponentDTO();
            telephoneRateComponent.setPaymentComponent("TELEPHONE_RATE");
            telephoneRateComponent.setAmount(telephoneRateBase.getAmount());
            telephoneRateComponent.setProjections(Shared.generateMonthProjection(period, range, telephoneRateComponent.getAmount()));
            components.add(telephoneRateComponent);
        }
    }
    //Viáticos NR
    //=I3*(1+
    //SI($C3="FC MVL";'Parámetros'!N$2;
    //SI($C3="FC TASA";'Parámetros'!N$3;
    //SI($C3="FC VALORA";'Parámetros'!N$4;
    //SI($C3="CEPETEL TASA"; 'Parámetros'!N$27;
    //SI($C3="FATEL MVL";'Parámetros'!N$28;
    //SI($C3="FATEL TASA";  'Parámetros'!N$29;
    //SI($C3="FOEESITRA MVL"; 'Parámetros'!N$30;
    //SI($C3="FOEESITRA TASA"; 'Parámetros'!N$31;
    //SI($C3="FOETRA MVL"; 'Parámetros'!N$32;
    //SI($C3="FOETRA TASA"; 'Parámetros'!N$33;
    //SI($C3="FOPSTTA MVL"; 'Parámetros'!N$34;
    //SI($C3="FOPSTTA TASA"; 'Parámetros'!N$35;
    //SI($C3="UPJET MVL"; 'Parámetros'!N$36;
    //SI($C3="UPJET TASA";  'Parámetros'!N$37;0))))))))))))))
    public void nonResidentAllowance(List<PaymentComponentDTO> components, List<ParametersDTO> parameters, String period, Integer range, Map<String, ConventArg> conventArgMap, String convenio) {
        List<ParametersDTO> relevantParameters = filterParametersByConvenio(parameters, convenio);
        Map<String, ParametersDTO> parameterMap = createCacheMap(relevantParameters);
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO nonResidentAllowanceBase = componentMap.get("NON_RESIDENT_ALLOWANCE_BASE");
        if (nonResidentAllowanceBase != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            double nonResidentAllowanceValue = getCachedValue(parameterMap, nextPeriod);
            //Viáticos NR
            PaymentComponentDTO nonResidentAllowance = new PaymentComponentDTO();
            nonResidentAllowance.setPaymentComponent("NON_RESIDENT_ALLOWANCE");
            //I3*(1+nonResidentAllowanceValue)
            nonResidentAllowance.setAmount(BigDecimal.valueOf(nonResidentAllowanceBase.getAmount().doubleValue() * (1 + nonResidentAllowanceValue)));
            nonResidentAllowance.setProjections(Shared.generateMonthProjection(period, range, nonResidentAllowance.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (nonResidentAllowanceBase.getProjections() != null) {
                for (MonthProjection projection : nonResidentAllowanceBase.getProjections()) {
                    ParametersDTO parameter = parameterMap.get(projection.getMonth());
                    MonthProjection newProjection = new MonthProjection();
                    newProjection.setMonth(projection.getMonth());
                    newProjection.setAmount(BigDecimal.valueOf(projection.getAmount().doubleValue() * (1 + (parameter != null ? parameter.getValue() : 0))));
                    projections.add(newProjection);
                }
                nonResidentAllowance.setProjections(projections);
            } else {
                nonResidentAllowance.setAmount(BigDecimal.ZERO);
                nonResidentAllowance.setProjections(Shared.generateMonthProjection(period, range, nonResidentAllowance.getAmount()));
            }
            components.add(nonResidentAllowance);
        } else {
            PaymentComponentDTO nonResidentAllowance = new PaymentComponentDTO();
            nonResidentAllowance.setPaymentComponent("NON_RESIDENT_ALLOWANCE");
            nonResidentAllowance.setAmount(BigDecimal.ZERO);
            nonResidentAllowance.setProjections(Shared.generateMonthProjection(period, range, nonResidentAllowance.getAmount()));
            components.add(nonResidentAllowance);
        }
    }
    //SNR
    //=D3
    public void snr(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO snrBase = componentMap.get("SNR_BASE");
        if (snrBase != null) {
            PaymentComponentDTO snrComponent = new PaymentComponentDTO();
            snrComponent.setPaymentComponent("SNR");
            snrComponent.setAmount(snrBase.getAmount());
            snrComponent.setProjections(Shared.generateMonthProjection(period, range, snrComponent.getAmount()));
            components.add(snrComponent);
        }
    }
    //SUMA SALARIO CONFORMADO
    //=+'Sueldo Básico'!N3+'Adicionales Rem (Salario Conf)'!L3+'Jornada Discontinua'!E3+'Tarifa Telefónica'!E3+'Viáticos NR'!J3+SNR!E3
    public void sumConformedSalary(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO basicSalary = componentMap.get("BASIC_SALARY");
        PaymentComponentDTO additionalRemuneration = componentMap.get("ADDITIONAL_REMUNERATION");
        PaymentComponentDTO discontinuousDay = componentMap.get("DISCONTINUOUS_DAY");
        PaymentComponentDTO telephoneRate = componentMap.get("TELEPHONE_RATE");
        PaymentComponentDTO nonResidentAllowance = componentMap.get("NON_RESIDENT_ALLOWANCE");
        PaymentComponentDTO snr = componentMap.get("SNR");
        PaymentComponentDTO sumConformedSalary = new PaymentComponentDTO();
        sumConformedSalary.setPaymentComponent("SUM_CONFORMED_SALARY");
        sumConformedSalary.setAmount(BigDecimal.ZERO);
        sumConformedSalary.setProjections(Shared.generateMonthProjection(period, range, sumConformedSalary.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        for(MonthProjection projection: sumConformedSalary.getProjections()) {
            MonthProjection newProjection = new MonthProjection();
            newProjection.setMonth(projection.getMonth());
            double amount = 0;
            if (basicSalary != null) {
                amount += getAmountForMonth(basicSalary.getProjections(), projection.getMonth());
            }
            if (additionalRemuneration != null) {
                amount += getAmountForMonth(additionalRemuneration.getProjections(), projection.getMonth());
            }
            if (discontinuousDay != null) {
                amount += getAmountForMonth(discontinuousDay.getProjections(), projection.getMonth());
            }
            if (telephoneRate != null) {
                amount += getAmountForMonth(telephoneRate.getProjections(), projection.getMonth());
            }
            if (nonResidentAllowance != null) {
                amount += getAmountForMonth(nonResidentAllowance.getProjections(), projection.getMonth());
            }
            if (snr != null) {
                amount += getAmountForMonth(snr.getProjections(), projection.getMonth());
            }
            newProjection.setAmount(BigDecimal.valueOf(amount));
            projections.add(newProjection);
        }
        sumConformedSalary.setProjections(projections);
        components.add(sumConformedSalary);
    }
    private double getAmountForMonth(List<MonthProjection> projections, String month) {
        return projections.stream()
                .filter(p -> p.getMonth().equals(month))
                .findFirst()
                .map(p -> p.getAmount().doubleValue())
                .orElse(0.0);
    }
    //%Incremento Salario Conformado
    //=IF('SUMA SAL CONF'!C3<>0,
    //('SUMA SAL CONF'!D3-'SUMA SAL CONF'!C3)/'SUMA SAL CONF'!C3,0)
    public void percentageIncreaseConformedSalary(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO sumConformedSalary = componentMap.get("SUM_CONFORMED_SALARY");
        PaymentComponentDTO percentageIncreaseConformedSalary = new PaymentComponentDTO();
        percentageIncreaseConformedSalary.setPaymentComponent("PERCENTAGE_INCREASE_CONFORMED_SALARY");
        if (sumConformedSalary != null) {
            List<MonthProjection> projections = sumConformedSalary.getProjections();
            List<MonthProjection> percentageProjections = new ArrayList<>();
            percentageIncreaseConformedSalary.setAmount(BigDecimal.valueOf(projections.get(0).getAmount().doubleValue() - projections.get(1).getAmount().doubleValue()));
            for (int i = 1; i < projections.size(); i++) { // Comienza desde el segundo mes
                MonthProjection currentProjection = projections.get(i);
                MonthProjection previousProjection = projections.get(i - 1);
                double percentageIncrease = 0;
                if (previousProjection.getAmount().doubleValue() != 0) {
                    percentageIncrease = (currentProjection.getAmount().doubleValue() - previousProjection.getAmount().doubleValue()) / previousProjection.getAmount().doubleValue();
                }
                currentProjection.setAmount(BigDecimal.valueOf(percentageIncrease));
                percentageProjections.add(currentProjection);
            }
            percentageIncreaseConformedSalary.setProjections(percentageProjections);
        } else {
            percentageIncreaseConformedSalary.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        components.add(percentageIncreaseConformedSalary);
    }
    //Adicionales
    //=D3
    public void additional(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO additionalBase = componentMap.get("ADDITIONAL_BASE");
        if (additionalBase != null) {
            PaymentComponentDTO additionalComponent = new PaymentComponentDTO();
            additionalComponent.setPaymentComponent("ADDITIONAL");
            additionalComponent.setAmount(additionalBase.getAmount());
            additionalComponent.setProjections(Shared.generateMonthProjection(period, range, additionalComponent.getAmount()));
            components.add(additionalComponent);
        }
    }



    public List<ParametersDTO> filterParametersByConvenio(List<ParametersDTO> parameters, String convenio) {
        //log.info("parameters {}", parameters);
        //log.info("convenio {}", convenio);
        return parameters.stream()
                .filter(p -> p.getConventArg().getConvenio().equals(convenio))
                .collect(Collectors.toList());
    }

    private Map<String, ParametersDTO> createCacheMap(List<ParametersDTO> parameterList) {
        Map<String, ParametersDTO> parameterMap = new HashMap<>();
        Map<String, Double> cache = new HashMap<>();
        createCache(parameterList, parameterMap, cache, (parameter, mapParameter) -> {
        });
        return parameterMap;
    }

    private double getCachedValue(Map<String, ParametersDTO> cacheMap, String period) {
        ParametersDTO parameter = cacheMap.get(period);
        return parameter != null ? parameter.getValue() : 0;
    }
}
