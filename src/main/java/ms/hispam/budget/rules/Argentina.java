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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    //=SUMA(L6:O6)
    //L6 = SALARY_BASE_100
    //M6 = SALARY_BASE_300
    //N6 = SALARY_BASE_320
    //O6 = SALARY_BASE_380
    public void basicSalary(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO salaryBase = componentMap.get("SALARY_BASE");
        double salaryBaseValue = salaryBase != null ? salaryBase.getAmount().doubleValue() : 0;
        PaymentComponentDTO basicSalary = new PaymentComponentDTO();
        basicSalary.setPaymentComponent("BASIC_SALARY");
        basicSalary.setAmount(BigDecimal.valueOf(salaryBaseValue));
        basicSalary.setProjections(Shared.generateMonthProjection(period, range, basicSalary.getAmount()));
        components.add(basicSalary);
    }
    //Adicionales Rem (Salario Conf)
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

    public List<ParametersDTO> filterParametersByConvenio(List<ParametersDTO> parameters, String convenio) {
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
