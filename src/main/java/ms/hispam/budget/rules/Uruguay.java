package ms.hispam.budget.rules;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.util.Shared;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Double.NaN;

public class Uruguay {
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
    private ParametersDTO findLatestSalaryForPeriod(List<ParametersDTO> salaryList, String period) {
        for (int i = salaryList.size() - 1; i >= 0; i--) {
            if (salaryList.get(i).getPeriod().compareTo(period) <= 0) {
                return salaryList.get(i);
            }
        }
        return null;
    }
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> salaryIncreaseList, String classEmployee, String period, Integer range, List<ParametersDTO> inflationList) {
        Map<String, ParametersDTO> salaryIncreaseMap = new HashMap<>();
        Map<String, Double> salaryIncreaseCache = new HashMap<>();
        createCache(salaryIncreaseList, salaryIncreaseMap, salaryIncreaseCache, (parameter, mapParameter) -> {});
        Map<String, ParametersDTO> inflationMap = new HashMap<>();
        Map<String, Double> inflationCache = new HashMap<>();
        createCache(inflationList, inflationMap, inflationCache, (parameter, mapParameter) -> {});
        //next period
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO salaryIncrease = salaryIncreaseMap.get(nextPeriod);
        double salaryIncreaseValue = salaryIncrease != null ? salaryIncrease.getValue() : 0.0;
        ParametersDTO inflation = inflationMap.get(nextPeriod);
        double inflationValue = inflation != null ? inflation.getValue() : 0.0;
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("010");
        PaymentComponentDTO daysComponent = componentMap.get("020");
        if (salaryBaseComponent != null && daysComponent != null && daysComponent.getAmount().doubleValue() > 0) {
            double salaryBase = (salaryBaseComponent.getAmount().doubleValue() / daysComponent.getAmount().doubleValue()) * 30;
            PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
         /*   Para PO's con título de puesto = 'Director' = 'Gerente',
            Sueldo se ajusta según {Factor inflacional} ingresado para ese mes.*/
            double nominalSalary;
            if (classEmployee.equals("Director") || classEmployee.equals("Gerente")) {
                nominalSalary = salaryBase * (1 + inflationValue);
            } else {
                nominalSalary = salaryBase * (1 + salaryIncreaseValue);
            }
            salaryComponent.setPaymentComponent("SALARY");
            salaryComponent.setAmount(BigDecimal.valueOf(nominalSalary));
            salaryComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(nominalSalary)));
            List<MonthProjection> projections = new ArrayList<>();
            double lastInflation = inflationValue;
            double lastSalaryIncrease = salaryIncreaseValue;
            for(MonthProjection projection : salaryComponent.getProjections()){
               ParametersDTO inflationParameter = inflationMap.get(projection.getMonth());
                double inflationParameterVal;
                if(inflationParameter != null){
                    inflationParameterVal = inflationParameter.getValue();
                    lastInflation = inflationParameterVal;
                }else{
                    inflationParameterVal = lastInflation;
                }
                ParametersDTO salaryIncreaseParameter = salaryIncreaseMap.get(projection.getMonth());
                double salaryIncreaseParameterVal;
                if(salaryIncreaseParameter != null){
                    salaryIncreaseParameterVal = salaryIncreaseParameter.getValue();
                    lastSalaryIncrease = salaryIncreaseParameterVal;
                }else{
                    salaryIncreaseParameterVal = lastSalaryIncrease;
                }
                double salaryNominalProjection;
                if (classEmployee.equals("Director") || classEmployee.equals("Gerente")) {
                    salaryNominalProjection = projection.getAmount().doubleValue() * (1 + inflationParameterVal);
                } else {
                    salaryNominalProjection = projection.getAmount().doubleValue() * (1 + salaryIncreaseParameterVal);
                }
                MonthProjection newProjection = new MonthProjection();
                newProjection.setMonth(projection.getMonth());
                newProjection.setAmount(BigDecimal.valueOf(salaryNominalProjection));
                projections.add(newProjection);
            }
            salaryComponent.setProjections(projections);
            component.add(salaryComponent);
        }else {
            PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
            salaryComponent.setPaymentComponent("SALARY");
            salaryComponent.setAmount(BigDecimal.valueOf(0));
            component.add(salaryComponent);
        }
    }
    public void overtime(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteHHEE) {
        Map<String, ParametersDTO> factorAjusteHHEEMap = new HashMap<>();
        Map<String, Double> factorAjusteHHEECache = new HashMap<>();
        createCache(factorAjusteHHEE, factorAjusteHHEEMap, factorAjusteHHEECache, (parameter, mapParameter) -> {});
        //next period
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO factorAjusteHHEEParameter = factorAjusteHHEEMap.get(nextPeriod);
        double factorAjusteHHEEValue = factorAjusteHHEEParameter != null ? factorAjusteHHEEParameter.getValue() : 0.0;
        //find component hhee_base_ur
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO overtimeComponentBase = componentMap.get("HHEE_BASE_UR");
        if (overtimeComponentBase != null) {
            double overtimeBase = overtimeComponentBase.getAmount().doubleValue();
            PaymentComponentDTO overtimeComponent = new PaymentComponentDTO();
            double overtimeNominal = overtimeBase * factorAjusteHHEEValue;
            overtimeComponent.setPaymentComponent("HHEE");
            overtimeComponent.setAmount(BigDecimal.valueOf(overtimeNominal));
            List<MonthProjection> projections = new ArrayList<>();
            double lastFactorAjusteHHEE = factorAjusteHHEEValue;
            for (MonthProjection projection : overtimeComponentBase.getProjections()) {
                ParametersDTO factorAjusteHHEEParameterProj = factorAjusteHHEEMap.get(projection.getMonth());
                double factorAjusteHHEEParameterVal;
                if (factorAjusteHHEEParameter != null) {
                    factorAjusteHHEEParameterVal = factorAjusteHHEEParameterProj.getValue();
                    lastFactorAjusteHHEE = factorAjusteHHEEParameterVal;
                } else {
                    factorAjusteHHEEParameterVal = lastFactorAjusteHHEE;
                }
                double overtimeNominalProjection = overtimeBase * factorAjusteHHEEParameterVal;
                MonthProjection newProjection = new MonthProjection();
                newProjection.setMonth(projection.getMonth());
                newProjection.setAmount(BigDecimal.valueOf(overtimeNominalProjection));
                projections.add(newProjection);
            }
            overtimeComponent.setProjections(projections);
            component.add(overtimeComponent);
        } else {
            PaymentComponentDTO overtimeComponent = new PaymentComponentDTO();
            overtimeComponent.setPaymentComponent("HHEE");
            overtimeComponent.setAmount(BigDecimal.valueOf(0));
            component.add(overtimeComponent);
        }
    }
    //GUARDIA
    public void guard(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteGuardia) {
        Map<String, ParametersDTO> factorAjusteGuardiaMap = new HashMap<>();
        Map<String, Double> factorAjusteGuardiaCache = new HashMap<>();
        createCache(factorAjusteGuardia, factorAjusteGuardiaMap, factorAjusteGuardiaCache, (parameter, mapParameter) -> {});
        //next period
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO factorAjusteGuardiaParameter = factorAjusteGuardiaMap.get(nextPeriod);
        double factorAjusteGuardiaValue = factorAjusteGuardiaParameter != null ? factorAjusteGuardiaParameter.getValue() : 0.0;
        //find component guardia_base_ur
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardComponentBase = componentMap.get("GUARDIA_BASE_UR");
        if (guardComponentBase != null) {
            double guardBase = guardComponentBase.getAmount().doubleValue();
            PaymentComponentDTO guardComponent = new PaymentComponentDTO();
            double guardNominal = guardBase * factorAjusteGuardiaValue;
            guardComponent.setPaymentComponent("GUARDIA");
            guardComponent.setAmount(BigDecimal.valueOf(guardNominal));
            List<MonthProjection> projections = new ArrayList<>();
            double lastFactorAjusteGuardia = factorAjusteGuardiaValue;
            for (MonthProjection projection : guardComponentBase.getProjections()) {
                ParametersDTO factorAjusteGuardiaParameterProj = factorAjusteGuardiaMap.get(projection.getMonth());
                double factorAjusteGuardiaParameterVal;
                if (factorAjusteGuardiaParameter != null) {
                    factorAjusteGuardiaParameterVal = factorAjusteGuardiaParameterProj.getValue();
                    lastFactorAjusteGuardia = factorAjusteGuardiaParameterVal;
                } else {
                    factorAjusteGuardiaParameterVal = lastFactorAjusteGuardia;
                }
                double guardNominalProjection = guardBase * factorAjusteGuardiaParameterVal;
                MonthProjection newProjection = new MonthProjection();
                newProjection.setMonth(projection.getMonth());
                newProjection.setAmount(BigDecimal.valueOf(guardNominalProjection));
                projections.add(newProjection);
            }
            guardComponent.setProjections(projections);
            component.add(guardComponent);
        } else {
            PaymentComponentDTO guardComponent = new PaymentComponentDTO();
            guardComponent.setPaymentComponent("GUARDIA");
            guardComponent.setAmount(BigDecimal.valueOf(0));
            component.add(guardComponent);
        }
    }
}
