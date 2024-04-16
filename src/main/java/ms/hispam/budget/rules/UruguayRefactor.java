package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
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
@Slf4j(topic = "URUGUAY_REFACTOR")
public class UruguayRefactor {
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
                    replacement.getProjections().addAll(replacement.getProjections());
                    return replacement;
                }));
    }

    public List<ParametersDTO> getParametersByPosition(String position, List<ParametersDTO> inflationList, List<ParametersDTO> salaryIncreaseList) {
        if (position.contains("CEO")) {
            return new ArrayList<>(); // Retorna una lista vacía para CEO
        } else if (position.contains("Director") || position.contains("Gerente")) {
            return inflationList;
        } else {
            return salaryIncreaseList;
        }
    }

    public double calculateBaseSalary(List<PaymentComponentDTO> component) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("0010");
        PaymentComponentDTO daysComponent = componentMap.get("0020");
        PaymentComponentDTO daysComponentProvitional = componentMap.get("DAYS_WORK");
        log.debug("salaryBaseComponent: {}", salaryBaseComponent);
        //log.debug("salaryBaseComponent: {}", salaryBaseComponent);
        //log.debug("daysComponent: {}", daysComponent);
        //TODO: Validate if daysComponent is null
        return (salaryBaseComponent.getAmount().doubleValue() / 30) * 30;
    }

    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> aumentoConsejoList, String classEmployee, String period, Integer range, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        double salaryBase = calculateBaseSalary(component);
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("SALARY");
        salaryComponent.setAmount(BigDecimal.valueOf(salaryBase));
        salaryComponent.setProjections(Shared.generateMonthProjection(period, range, salaryComponent.getAmount()));
        double ultimaProporcion = 0;
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            if (salaryComponent.getProjections().size() > range) {
                // Regenerate the projection
                salaryComponent.setProjections(Shared.generateMonthProjection(period, range, salaryComponent.getAmount()));
            }
            String month = projection.getMonth();
            ParametersDTO proporcionMensual = proporcionMensualMap.get(month);
            double proporcion;
            if (proporcionMensual != null) {
                proporcion = proporcionMensual.getValue();
                ultimaProporcion = proporcion;
            } else {
                proporcion = ultimaProporcion;
            }
            double salary = salaryBase * proporcion;
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(salary));
            projections.add(monthProjection);
        }
        salaryComponent.setProjections(projections);
        component.add(salaryComponent);
    }

    //HHEE
    public void overtime(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteHHEEList) {
        Map<String, ParametersDTO> factorAjusteHHEEMap = new HashMap<>();
        Map<String, Double> factorAjusteHHEECache = new HashMap<>();
        createCache(factorAjusteHHEEList, factorAjusteHHEEMap, factorAjusteHHEECache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO hheeBaseUrComponent = componentMap.get("HHEE_BASE_UR");
        log.debug("hheeBaseUrComponent: {}", hheeBaseUrComponent);
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO factorAjusteHHEE = factorAjusteHHEEMap.get(nextPeriod);
        double hheeBaseUr = hheeBaseUrComponent == null ? 0 : hheeBaseUrComponent.getAmount().doubleValue() * (1 + factorAjusteHHEE.getValue());
        PaymentComponentDTO overtimeComponent = new PaymentComponentDTO();
        overtimeComponent.setPaymentComponent("HHEE");
        overtimeComponent.setAmount(BigDecimal.valueOf(hheeBaseUr));
        List<MonthProjection> projections = new ArrayList<>();
        double lastOvertime = 0;
        if (hheeBaseUrComponent != null && hheeBaseUrComponent.getProjections() != null) {
            if (hheeBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                hheeBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, hheeBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : hheeBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO factorAjusteHHEEMonth = factorAjusteHHEEMap.get(month);
                double factorAjuste;
                if (factorAjusteHHEEMonth != null) {
                    factorAjuste = factorAjusteHHEEMonth.getValue();
                    lastOvertime = factorAjuste;
                } else {
                    factorAjuste = lastOvertime;
                }
                double hhee = hheeBaseUr * (1 + factorAjuste);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(hhee));
                projections.add(monthProjection);
            }
            overtimeComponent.setProjections(projections);
        } else {
            overtimeComponent.setAmount(BigDecimal.valueOf(0));
            overtimeComponent.setProjections(Shared.generateMonthProjection(period, range, overtimeComponent.getAmount()));
        }
        component.add(overtimeComponent);
    }

    //Guardia Activa $I6*(1+(AK18/$AI18-1))*(1+AK$6)
    //param: Factor ajuste Guardias
    public void activeGuard(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteGuardiasList) {
        Map<String, ParametersDTO> factorAjusteGuardiasMap = new HashMap<>();
        Map<String, Double> factorAjusteGuardiasCache = new HashMap<>();
        createCache(factorAjusteGuardiasList, factorAjusteGuardiasMap, factorAjusteGuardiasCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardBaseUrComponent = componentMap.get("GUARD_BASE_UR");
        //Salary
        log.debug("guardBaseUrComponent: {}", guardBaseUrComponent);
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getProjections().stream()
                .filter(projection -> projection.getMonth().equals(nextPeriod))
                .findFirst()
                .map(projection -> projection.getAmount().doubleValue())
                .orElse(0.0);
        double salaryBase = calculateBaseSalary(component);
        ParametersDTO factorAjusteGuardias = factorAjusteGuardiasMap.get(nextPeriod);
        double guardBaseUr = guardBaseUrComponent == null ? 0 : guardBaseUrComponent.getAmount().doubleValue();
        /*log.info("guardBaseUr: {}", guardBaseUr);
        log.info("salaryTeorico: {}", salaryTeorico);
        log.info("salaryBase: {}", salaryBase);
        log.info("factorAjusteGuardias: {}", factorAjusteGuardias.getValue());*/
        double activeGuard = salaryBase > 0 ? guardBaseUr * (1 + (salaryTeorico / salaryBase - 1)) * (1 + factorAjusteGuardias.getValue()) : 0;
        //log.info("activeGuard: {}", activeGuard);
        PaymentComponentDTO activeGuardComponent = new PaymentComponentDTO();
        activeGuardComponent.setPaymentComponent("GUARD");
        activeGuardComponent.setAmount(BigDecimal.valueOf(activeGuard));
        List<MonthProjection> projections = new ArrayList<>();
        double lastGuard = 0;
        if (guardBaseUrComponent != null && guardBaseUrComponent.getProjections() != null) {
            if (guardBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                guardBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, guardBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : guardBaseUrComponent.getProjections()) {
                PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
                double salaryPerMonth = salaryComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                String month = projection.getMonth();
                ParametersDTO factorAjusteGuardiasMonth = factorAjusteGuardiasMap.get(month);
                double factorAjuste;
                if (factorAjusteGuardiasMonth != null) {
                    factorAjuste = factorAjusteGuardiasMonth.getValue();
                    lastGuard = factorAjuste;
                } else {
                    factorAjuste = lastGuard;
                }
                double guard = salaryBase > 0 ? guardBaseUrComponent.getAmount().doubleValue() * (1 + (salaryPerMonth / salaryBase - 1)) * (1 + factorAjuste) : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(guard));
                projections.add(monthProjection);
            }
            activeGuardComponent.setProjections(projections);
        } else {
            activeGuardComponent.setAmount(BigDecimal.valueOf(0));
            activeGuardComponent.setProjections(Shared.generateMonthProjection(period, range, activeGuardComponent.getAmount()));
        }
        component.add(activeGuardComponent);
    }

    //Guardia Especial SI(AK38>0;AL17*15%*(1+AL$6);0)
    //param: Factor ajuste Guardias -> AL$6
    //paymentComponent: GUARD_ESPECIAL -> AL17
    public void specialGuard(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteGuardiasList) {
        Map<String, ParametersDTO> factorAjusteGuardiasMap = new HashMap<>();
        Map<String, Double> factorAjusteGuardiasCache = new HashMap<>();
        createCache(factorAjusteGuardiasList, factorAjusteGuardiasMap, factorAjusteGuardiasCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardBaseUrComponent = componentMap.get("GUARDIA_ESPECIAL_BASE_UR");
        //log.debug("guardBaseUrComponent: {}", guardBaseUrComponent);
        PaymentComponentDTO specialGuardComponent = new PaymentComponentDTO();
        specialGuardComponent.setPaymentComponent("GUARD_ESPECIAL");
        double guardBaseUr = guardBaseUrComponent == null ? 0 : guardBaseUrComponent.getAmount().doubleValue();
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO factorAjusteGuardias = factorAjusteGuardiasMap.get(nextPeriod);
        double factorAjusteGuardiasValue = factorAjusteGuardias == null ? 0 : factorAjusteGuardias.getValue();
        double specialGuardBase = guardBaseUr > 0 ? guardBaseUr * 0.15 * (1 + factorAjusteGuardiasValue) : 0;
        specialGuardComponent.setAmount(BigDecimal.valueOf(specialGuardBase));
        List<MonthProjection> projections = new ArrayList<>();
        double lastSpecialGuard = 0;
        if (guardBaseUrComponent!= null && guardBaseUrComponent.getProjections() != null) {
            if (guardBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                guardBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, guardBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : guardBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO factorAjusteGuardiasMonth = factorAjusteGuardiasMap.get(month);
                double factorAjuste;
                if (factorAjusteGuardiasMonth != null) {
                    factorAjuste = factorAjusteGuardiasMonth.getValue();
                    lastSpecialGuard = factorAjuste;
                } else {
                    factorAjuste = lastSpecialGuard;
                }
                double specialGuard = guardBaseUrComponent.getAmount().doubleValue() > 0 ? guardBaseUrComponent.getAmount().doubleValue() * 0.15 * (1 + factorAjuste) : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(specialGuard));
                projections.add(monthProjection);
            }
            specialGuardComponent.setProjections(projections);
        } else {
            specialGuardComponent.setAmount(BigDecimal.valueOf(0));
            specialGuardComponent.setProjections(Shared.generateMonthProjection(period, range, specialGuardComponent.getAmount()));
        }
        component.add(specialGuardComponent);
    }

    //Guardia Jurídica  SI($K6>0;AL18*5%*(1+AL$6);0)
    //param: Factor ajuste Guardias -> AL$6
    //paymentComponent: GUARDIA_JURIDICA_BASE_UR -> AL18
    public void legalGuard(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteGuardiasList) {
        Map<String, ParametersDTO> factorAjusteGuardiasMap = new HashMap<>();
        Map<String, Double> factorAjusteGuardiasCache = new HashMap<>();
        createCache(factorAjusteGuardiasList, factorAjusteGuardiasMap, factorAjusteGuardiasCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardBaseUrComponent = componentMap.get("GUARDIA_JURIDICA_BASE_UR");
        //log.debug("guardBaseUrComponent: {}", guardBaseUrComponent);
        PaymentComponentDTO legalGuardComponent = new PaymentComponentDTO();
        legalGuardComponent.setPaymentComponent("GUARD_JURIDICAL");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO factorAjusteGuardias = factorAjusteGuardiasMap.get(nextPeriod);
        double factorAjusteGuardiasValue = factorAjusteGuardias == null ? 0 : factorAjusteGuardias.getValue();
        double guardBaseUrComponentValue= guardBaseUrComponent == null ? 0 : guardBaseUrComponent.getAmount().doubleValue();
        double legalGuardBase = guardBaseUrComponentValue > 0 ? guardBaseUrComponentValue * 0.05 * (1 + factorAjusteGuardiasValue) : 0;
        legalGuardComponent.setAmount(BigDecimal.valueOf(legalGuardBase));

        List<MonthProjection> projections = new ArrayList<>();
        double lastLegalGuard = 0;
        if (guardBaseUrComponent != null && guardBaseUrComponent.getProjections() != null) {
            if (guardBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                guardBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, guardBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : guardBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO factorAjusteGuardiasMonth = factorAjusteGuardiasMap.get(month);
                double factorAjuste;
                if (factorAjusteGuardiasMonth != null) {
                    factorAjuste = factorAjusteGuardiasMonth.getValue();
                    lastLegalGuard = factorAjuste;
                } else {
                    factorAjuste = lastLegalGuard;
                }
                double legalGuard = guardBaseUrComponent.getAmount().doubleValue() > 0 ? guardBaseUrComponent.getAmount().doubleValue() * 0.05 * (1 + factorAjuste) : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(legalGuard));
                projections.add(monthProjection);
            }
            legalGuardComponent.setProjections(projections);
        } else {
            legalGuardComponent.setAmount(BigDecimal.valueOf(0));
            legalGuardComponent.setProjections(Shared.generateMonthProjection(period, range, legalGuardComponent.getAmount()));
        }
        component.add(legalGuardComponent);
    }

    // Premio cuatrimestral : SI(AI52>0;AJ17*15%;0)
    //param: Factor ajuste Premio cuatrimestral -> AK$6
    //paymentComponent: PREMIO_CUATRIMESTRAL_BASE_UR -> AK23
    public void quarterlyBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO bonusBaseUrComponent = componentMap.get("PREMIO_CUATRIMESTRAL_BASE_UR");
        double bonusBaseUr = bonusBaseUrComponent == null ? 0 : bonusBaseUrComponent.getAmount().doubleValue();
        //log.debug("bonusBaseUrComponent: {}", bonusBaseUrComponent);
        PaymentComponentDTO quarterlyBonusComponent = new PaymentComponentDTO();
        quarterlyBonusComponent.setPaymentComponent("QUARTERLY_BONUS");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getProjections().stream()
                .filter(projection -> projection.getMonth().equals(nextPeriod))
                .findFirst()
                .map(projection -> projection.getAmount().doubleValue())
                .orElse(0.0);
        quarterlyBonusComponent.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.15) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
            if (bonusBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                bonusBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, bonusBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : bonusBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = salaryBaseComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                double quarterlyBonus = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.15 : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(quarterlyBonus));
                projections.add(monthProjection);
            }
            quarterlyBonusComponent.setProjections(projections);
        } else {
            quarterlyBonusComponent.setAmount(BigDecimal.valueOf(0));
            quarterlyBonusComponent.setProjections(Shared.generateMonthProjection(period, range, quarterlyBonusComponent.getAmount()));
        }
        component.add(quarterlyBonusComponent);
    }
    // Premio Cuatrimestral 8%: SI(AK59>0;AL17*8%;0)
    //param: Factor ajuste Premio cuatrimestral 8% -> AL$6
    //paymentComponent: PREMIO_CUATRIMESTRAL_8_BASE_UR -> AL17
    public void quarterlyBonus8(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO bonusBaseUrComponent = componentMap.get("PREMIO_CUATRIMESTRAL_8_BASE_UR");
        double bonusBaseUr = bonusBaseUrComponent == null ? 0 : bonusBaseUrComponent.getAmount().doubleValue();
        //log.debug("bonusBaseUrComponent: {}", bonusBaseUrComponent);
        PaymentComponentDTO quarterlyBonus8Component = new PaymentComponentDTO();
        quarterlyBonus8Component.setPaymentComponent("QUARTERLY_BONUS_8");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getProjections().stream()
                .filter(projection -> projection.getMonth().equals(nextPeriod))
                .findFirst()
                .map(projection -> projection.getAmount().doubleValue())
                .orElse(0.0);
        quarterlyBonus8Component.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.08) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
            if (bonusBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                bonusBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, bonusBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : bonusBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = salaryBaseComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                double quarterlyBonus8 = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.08 : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(quarterlyBonus8));
                projections.add(monthProjection);
            }
            quarterlyBonus8Component.setProjections(projections);
        } else {
            quarterlyBonus8Component.setAmount(BigDecimal.valueOf(0));
            quarterlyBonus8Component.setProjections(Shared.generateMonthProjection(period, range, quarterlyBonus8Component.getAmount()));
        }
        component.add(quarterlyBonus8Component);
    }
    //Premio mensual 20%: SI($N5>0;AI17*20%;0)
    //paymentComponent: PREMIO_MENSUAL_20_BASE_UR -> AI17
    public void monthlyBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO bonusBaseUrComponent = componentMap.get("PREMIO_MENSUAL_20_BASE_UR");
        double bonusBaseUr = bonusBaseUrComponent == null ? 0 : bonusBaseUrComponent.getAmount().doubleValue();
        //log.debug("bonusBaseUrComponent: {}", bonusBaseUrComponent);
        PaymentComponentDTO monthlyBonusComponent = new PaymentComponentDTO();
        monthlyBonusComponent.setPaymentComponent("MONTHLY_BONUS");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getProjections().stream()
                .filter(projection -> projection.getMonth().equals(nextPeriod))
                .findFirst()
                .map(projection -> projection.getAmount().doubleValue())
                .orElse(0.0);
        monthlyBonusComponent.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.20) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
            if (bonusBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                bonusBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, bonusBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : bonusBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = salaryBaseComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                double monthlyBonus = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.20 : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(monthlyBonus));
                projections.add(monthProjection);
            }
            monthlyBonusComponent.setProjections(projections);
        } else {
            monthlyBonusComponent.setAmount(BigDecimal.valueOf(0));
            monthlyBonusComponent.setProjections(Shared.generateMonthProjection(period, range, monthlyBonusComponent.getAmount()));
        }
        component.add(monthlyBonusComponent);
    }
    //Premio mensual 15%: SI($N5>0;AI17*10%;0)
    //paymentComponent: PREMIO_MENSUAL_15_BASE_UR -> AI17
    public void monthlyBonus15(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO bonusBaseUrComponent = componentMap.get("PREMIO_MENSUAL_15_BASE_UR");
        double bonusBaseUr = bonusBaseUrComponent == null ? 0 : bonusBaseUrComponent.getAmount().doubleValue();
        //log.debug("bonusBaseUrComponent: {}", bonusBaseUrComponent);
        PaymentComponentDTO monthlyBonus15Component = new PaymentComponentDTO();
        monthlyBonus15Component.setPaymentComponent("MONTHLY_BONUS_15");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getProjections().stream()
                .filter(projection -> projection.getMonth().equals(nextPeriod))
                .findFirst()
                .map(projection -> projection.getAmount().doubleValue())
                .orElse(0.0);
        monthlyBonus15Component.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.15) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
            if (bonusBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                bonusBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, bonusBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : bonusBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = salaryBaseComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                double monthlyBonus15 = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.15 : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(monthlyBonus15));
                projections.add(monthProjection);
            }
            monthlyBonus15Component.setProjections(projections);
        } else {
            monthlyBonus15Component.setAmount(BigDecimal.valueOf(0));
            monthlyBonus15Component.setProjections(Shared.generateMonthProjection(period, range, monthlyBonus15Component.getAmount()));
        }
        component.add(monthlyBonus15Component);
    }
    //Bono anual ($P6*AJ18)/12
    //externalComponent: BONO_ANUAL -> $P6
    //paymentComponent: SALARY -> AJ18
    public void annualBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO annualBonusBaseUrComponent = componentMap.get("BONO_ANUAL");
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryBase = salaryBaseComponent == null ? 0.0 : salaryBaseComponent.getAmount().doubleValue();
        double annualBonus = annualBonusBaseUrComponent == null ? 0.0 : (annualBonusBaseUrComponent.getAmount().doubleValue() * salaryBase) / 12;
        PaymentComponentDTO annualBonusComponent = new PaymentComponentDTO();
        annualBonusComponent.setPaymentComponent("ANNUAL_BONUS");
        annualBonusComponent.setAmount(BigDecimal.valueOf(annualBonus));
        List<MonthProjection> projections = new ArrayList<>();
        if (annualBonusBaseUrComponent !=null && salaryBaseComponent != null && salaryBaseComponent.getProjections() != null) {
            if (salaryBaseComponent.getProjections().size() > range) {
                // Regenerate the projection
                salaryBaseComponent.setProjections(Shared.generateMonthProjection(period, range, salaryBaseComponent.getAmount()));
            }
            for (MonthProjection projection : salaryBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double annualBonusPerMonth = (annualBonusBaseUrComponent.getAmount().doubleValue() * salaryBaseComponent.getAmount().doubleValue()) / 12;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(annualBonusPerMonth));
                projections.add(monthProjection);
            }
            annualBonusComponent.setProjections(projections);
        } else {
            annualBonusComponent.setAmount(BigDecimal.valueOf(0));
            annualBonusComponent.setProjections(Shared.generateMonthProjection(period, range, annualBonusComponent.getAmount()));
        }
        component.add(annualBonusComponent);
    }
    //Bono s/ Ventas SI(AI83=0;$Q8*(1+SI(ESNUMERO(HALLAR("CEO";$D8));0;SI(O(ESNUMERO(HALLAR("Director";$D8));ESNUMERO(HALLAR("Gerente";$D8)));AI$4;AI$3)));0)
    //externalComponent: BONO_VENTAS -> $Q8
    //paymentComponent: ANNUAL_BONUS -> AI83
    //param: %Rev x Inflación -> AI$4
    //param: %Aumento por Consejo de Salario -> AI$3
    //Para determinar si es Director o Gerente, tenemos el metodo getParametersByPosition.
    public void salesBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoConsejoList, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporcionMensual = proporcionMensualMap.get(nextPeriod);
        double proporcionMensualBaseValue = proporcionMensual == null ? 0 : proporcionMensual.getValue();
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salesBonusBaseUrComponent = componentMap.get("BONO_VENTAS");
        PaymentComponentDTO annualBonusComponent = componentMap.get("ANNUAL_BONUS");
        double annualBonus = annualBonusComponent.getAmount().doubleValue();
        double salesBonus = annualBonus == 0 ? 0 : salesBonusBaseUrComponent.getAmount().doubleValue() * (1 + proporcionMensualBaseValue);
        PaymentComponentDTO salesBonusComponent = new PaymentComponentDTO();
        salesBonusComponent.setPaymentComponent("SALES_BONUS");
        salesBonusComponent.setAmount(BigDecimal.valueOf(salesBonus));
        List<MonthProjection> projections = new ArrayList<>();
        if (annualBonusComponent.getProjections() != null) {
            if (annualBonusComponent.getProjections().size() > range) {
                // Regenerate the projection
                annualBonusComponent.setProjections(Shared.generateMonthProjection(period, range, annualBonusComponent.getAmount()));
            }
            double lastSalesBonus = 0;
            for (MonthProjection projection : annualBonusComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO proporcionMensualMonth = proporcionMensualMap.get(month);
                double proporcionMensualValueMonth;
                if (proporcionMensualMonth != null) {
                    proporcionMensualValueMonth = proporcionMensualMonth.getValue();
                    lastSalesBonus = proporcionMensualValueMonth;
                } else {
                    proporcionMensualValueMonth = lastSalesBonus;
                }
                double salesBonusPerMonth = annualBonus == 0 ? 0 : salesBonusBaseUrComponent.getAmount().doubleValue() * (1 + proporcionMensualValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(salesBonusPerMonth));
                projections.add(monthProjection);
            }
            salesBonusComponent.setProjections(projections);
        } else {
            salesBonusComponent.setAmount(BigDecimal.valueOf(0));
            salesBonusComponent.setProjections(Shared.generateMonthProjection(period, range, salesBonusComponent.getAmount()));
        }
        component.add(salesBonusComponent);
    }
    //Comisiones s/ Ventas $R5*(1+SI(ESNUMERO(HALLAR("CEO";$D5));0;SI(O(ESNUMERO(HALLAR("Director";$D5));ESNUMERO(HALLAR("Gerente";$D5)));AI$4;AI$3)))
    //externalComponent: COMISIONES_VENTAS -> $R5
    //param: %Rev x Inflación -> AI$4
    //param: %Aumento por Consejo de Salario -> AI$3
    //Para determinar si es Director o Gerente, tenemos el metodo getParametersByPosition.
    public void salesCommissions(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoConsejoList, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salesCommissionsBaseUrComponent = componentMap.get("COMISIONES_VENTAS");
        double salesCommissions = salesCommissionsBaseUrComponent == null ? 0 : salesCommissionsBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO salesCommissionsComponent = new PaymentComponentDTO();
        salesCommissionsComponent.setPaymentComponent("SALES_COMMISSIONS");
        salesCommissionsComponent.setAmount(BigDecimal.valueOf(salesCommissions));
        List<MonthProjection> projections = new ArrayList<>();
        if (salesCommissionsBaseUrComponent != null && salesCommissionsBaseUrComponent.getProjections() != null) {
            if (salesCommissionsBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                salesCommissionsBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, salesCommissionsBaseUrComponent.getAmount()));
            }
            double lastSalesCommissions = 0;
            for (MonthProjection projection : salesCommissionsBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO proporcionMensual = proporcionMensualMap.get(month);
                double proporcionMensualValue;
                if (proporcionMensual != null) {
                    proporcionMensualValue = proporcionMensual.getValue();
                    lastSalesCommissions = proporcionMensualValue;
                } else {
                    proporcionMensualValue = lastSalesCommissions;
                }
                double salesCommissionsPerMonth = salesCommissionsBaseUrComponent.getAmount().doubleValue() * (1 + proporcionMensualValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(salesCommissionsPerMonth));
                projections.add(monthProjection);
            }
            salesCommissionsComponent.setProjections(projections);
        } else {
            salesCommissionsComponent.setAmount(BigDecimal.valueOf(0));
            salesCommissionsComponent.setProjections(Shared.generateMonthProjection(period, range, salesCommissionsComponent.getAmount()));
        }
        component.add(salesCommissionsComponent);
    }
    //Comisiones s/ Cobranzas -> SI($S6>0;AI18*15%;0)
    //externalComponent: COMISIONES_COBRANZAS -> $S6
    //paymentComponent: SALARY -> AI18
    public void collectionCommissions(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO collectionCommissionsBaseUrComponent = componentMap.get("COMISIONES_COBRANZAS");
        double collectionCommissionsBaseUr = collectionCommissionsBaseUrComponent == null ? 0 : collectionCommissionsBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryBase = salaryBaseComponent == null ? 0 : salaryBaseComponent.getAmount().doubleValue();
        double collectionCommissions = collectionCommissionsBaseUr > 0 ? salaryBase * 0.15 : 0;
        PaymentComponentDTO collectionCommissionsComponent = new PaymentComponentDTO();
        collectionCommissionsComponent.setPaymentComponent("COLLECTION_COMMISSIONS");
        collectionCommissionsComponent.setAmount(BigDecimal.valueOf(collectionCommissions));
        List<MonthProjection> projections = new ArrayList<>();
        if (salaryBaseComponent != null && collectionCommissionsBaseUrComponent != null && collectionCommissionsBaseUrComponent.getProjections() != null) {
            if (collectionCommissionsBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                collectionCommissionsBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, collectionCommissionsBaseUrComponent.getAmount()));
            }
            for (MonthProjection projection : collectionCommissionsBaseUrComponent.getProjections()) {
                double salaryPerMonth = salaryBaseComponent.getProjections().stream()
                        .filter(projectionSalary -> projectionSalary.getMonth().equals(projection.getMonth()))
                        .findFirst()
                        .map(projectionSalary -> projectionSalary.getAmount().doubleValue())
                        .orElse(0.0);
                String month = projection.getMonth();
                double collectionCommissionsPerMonth = collectionCommissionsBaseUr > 0 ? salaryPerMonth * 0.15 : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(collectionCommissionsPerMonth));
                projections.add(monthProjection);
            }
            collectionCommissionsComponent.setProjections(projections);
        } else {
            collectionCommissionsComponent.setAmount(BigDecimal.valueOf(0));
            collectionCommissionsComponent.setProjections(Shared.generateMonthProjection(period, range, collectionCommissionsComponent.getAmount()));
        }
        component.add(collectionCommissionsComponent);
    }
    //Ticket Alimentación -> $T6*(1+AI$7)
    //externalComponent: TICKET_ALIMENTACION -> $T6
    //param: %Aumento Ticket Alimentación -> AI$7
    public void foodTicket(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoTicketList) {
        Map<String, ParametersDTO> aumentoTicketMap = new HashMap<>();
        Map<String, Double> aumentoTicketCache = new HashMap<>();
        createCache(aumentoTicketList, aumentoTicketMap, aumentoTicketCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO foodTicketBaseUrComponent = componentMap.get("TICKET_ALIMENTACION");
        double foodTicketBaseUr = foodTicketBaseUrComponent == null ? 0 : foodTicketBaseUrComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO aumentoTicket = aumentoTicketMap.get(nextPeriod);
        double aumentoTicketValue = aumentoTicket == null ? 0 : aumentoTicket.getValue();
        double foodTicket = foodTicketBaseUr * (1 + aumentoTicketValue);
        PaymentComponentDTO foodTicketComponent = new PaymentComponentDTO();
        foodTicketComponent.setPaymentComponent("FOOD_TICKET");
        foodTicketComponent.setAmount(BigDecimal.valueOf(foodTicket));
        List<MonthProjection> projections = new ArrayList<>();
        if (foodTicketBaseUrComponent != null && foodTicketBaseUrComponent.getProjections() != null) {
            if (foodTicketBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                foodTicketBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, foodTicketBaseUrComponent.getAmount()));
            }
            double lastFoodTicket = 0;
            for (MonthProjection projection : foodTicketBaseUrComponent.getProjections()) {
                ParametersDTO aumentoTicketMonth = aumentoTicketMap.get(projection.getMonth());
                double aumentoTicketValueMonth;
                if (aumentoTicketMonth != null) {
                    aumentoTicketValueMonth = aumentoTicketMonth.getValue();
                    lastFoodTicket = aumentoTicketValueMonth;
                } else {
                    aumentoTicketValueMonth = lastFoodTicket;
                }
                String month = projection.getMonth();
                double foodTicketPerMonth = foodTicketBaseUrComponent.getAmount().doubleValue() * (1 + aumentoTicketValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(foodTicketPerMonth));
                projections.add(monthProjection);
            }
            foodTicketComponent.setProjections(projections);
        } else {
            foodTicketComponent.setAmount(BigDecimal.valueOf(0));
            foodTicketComponent.setProjections(Shared.generateMonthProjection(period, range, foodTicketComponent.getAmount()));
        }
        component.add(foodTicketComponent);
    }
    //SUAT -> $U7*(1+AI$8)
    //externalComponent: SUAT -> $U7
    //param: %Aumento SUAT -> AI$8
    public void suat(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoSuatList) {
        Map<String, ParametersDTO> aumentoSuatMap = new HashMap<>();
        Map<String, Double> aumentoSuatCache = new HashMap<>();
        createCache(aumentoSuatList, aumentoSuatMap, aumentoSuatCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO suatBaseUrComponent = componentMap.get("SUAT_BASE");
        double suatBaseUr = suatBaseUrComponent == null ? 0 : suatBaseUrComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO aumentoSuat = aumentoSuatMap.get(nextPeriod);
        double aumentoSuatValue = aumentoSuat == null ? 0 : aumentoSuat.getValue();
        double suat = suatBaseUr * (1 + aumentoSuatValue);
        PaymentComponentDTO suatComponent = new PaymentComponentDTO();
        suatComponent.setPaymentComponent("SUAT");
        suatComponent.setAmount(BigDecimal.valueOf(suat));
        List<MonthProjection> projections = new ArrayList<>();
        if (suatBaseUrComponent != null && suatBaseUrComponent.getProjections() != null) {
            if (suatBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                suatBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, suatBaseUrComponent.getAmount()));
            }
            double lastSuat = 0;
            for (MonthProjection projection : suatBaseUrComponent.getProjections()) {
                ParametersDTO aumentoSuatMonth = aumentoSuatMap.get(projection.getMonth());
                double aumentoSuatValueMonth;
                if (aumentoSuatMonth != null) {
                    aumentoSuatValueMonth = aumentoSuatMonth.getValue();
                    lastSuat = aumentoSuatValueMonth;
                } else {
                    aumentoSuatValueMonth = lastSuat;
                }
                String month = projection.getMonth();
                double suatPerMonth = suatBaseUrComponent.getAmount().doubleValue() * (1 + aumentoSuatValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(suatPerMonth));
                projections.add(monthProjection);
            }
            suatComponent.setProjections(projections);
        } else {
            suatComponent.setAmount(BigDecimal.valueOf(0));
            suatComponent.setProjections(Shared.generateMonthProjection(period, range, suatComponent.getAmount()));
        }
        component.add(suatComponent);
    }
    //BC & BS -> $V6*(1+AI$9)
    //externalComponent: BC_BS -> $V6
    //param: %Aumento BC & BS -> AI$9
    public void bcBs(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoBcBsList) {
        Map<String, ParametersDTO> aumentoBcBsMap = new HashMap<>();
        Map<String, Double> aumentoBcBsCache = new HashMap<>();
        createCache(aumentoBcBsList, aumentoBcBsMap, aumentoBcBsCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO bcBsBaseUrComponent = componentMap.get("BC_BS_BASE");
        double bcBsBaseUr = bcBsBaseUrComponent == null ? 0 : bcBsBaseUrComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO aumentoBcBs = aumentoBcBsMap.get(nextPeriod);
        double aumentoBcBsValue = aumentoBcBs == null ? 0 : aumentoBcBs.getValue();
        double bcBs = bcBsBaseUr * (1 + aumentoBcBsValue);
        PaymentComponentDTO bcBsComponent = new PaymentComponentDTO();
        bcBsComponent.setPaymentComponent("BC_BS");
        bcBsComponent.setAmount(BigDecimal.valueOf(bcBs));
        List<MonthProjection> projections = new ArrayList<>();
        if (bcBsBaseUrComponent != null && bcBsBaseUrComponent.getProjections() != null) {
            if (bcBsBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                bcBsBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, bcBsBaseUrComponent.getAmount()));
            }
            double lastBcBs = 0;
            for (MonthProjection projection : bcBsBaseUrComponent.getProjections()) {
                ParametersDTO aumentoBcBsMonth = aumentoBcBsMap.get(projection.getMonth());
                double aumentoBcBsValueMonth;
                if (aumentoBcBsMonth != null) {
                    aumentoBcBsValueMonth = aumentoBcBsMonth.getValue();
                    lastBcBs = aumentoBcBsValueMonth;
                } else {
                    aumentoBcBsValueMonth = lastBcBs;
                }
                String month = projection.getMonth();
                double bcBsPerMonth = bcBsBaseUrComponent.getAmount().doubleValue() * (1 + aumentoBcBsValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(bcBsPerMonth));
                projections.add(monthProjection);
            }
            bcBsComponent.setProjections(projections);
        } else {
            bcBsComponent.setAmount(BigDecimal.valueOf(0));
            bcBsComponent.setProjections(Shared.generateMonthProjection(period, range, bcBsComponent.getAmount()));
        }
        component.add(bcBsComponent);
    }
    //Metlife -> $W6*(1+AI$10)
    //externalComponent: METLIFE -> $W6
    //param: %Aumento Metlife -> AI$10
    public void metlife(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoMetlifeList) {
        Map<String, ParametersDTO> aumentoMetlifeMap = new HashMap<>();
        Map<String, Double> aumentoMetlifeCache = new HashMap<>();
        createCache(aumentoMetlifeList, aumentoMetlifeMap, aumentoMetlifeCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO metlifeBaseUrComponent = componentMap.get("METLIFE_BASE");
        double metlifeBaseUr = metlifeBaseUrComponent == null ? 0 : metlifeBaseUrComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO aumentoMetlife = aumentoMetlifeMap.get(nextPeriod);
        double aumentoMetlifeValue = aumentoMetlife == null ? 0 : aumentoMetlife.getValue();
        double metlife = metlifeBaseUr * (1 + aumentoMetlifeValue);
        PaymentComponentDTO metlifeComponent = new PaymentComponentDTO();
        metlifeComponent.setPaymentComponent("METLIFE");
        metlifeComponent.setAmount(BigDecimal.valueOf(metlife));
        List<MonthProjection> projections = new ArrayList<>();
        if (metlifeBaseUrComponent != null && metlifeBaseUrComponent.getProjections() != null) {
            if (metlifeBaseUrComponent.getProjections().size() > range) {
                // Regenerate the projection
                metlifeBaseUrComponent.setProjections(Shared.generateMonthProjection(period, range, metlifeBaseUrComponent.getAmount()));
            }
            double lastMetlife = 0;
            for (MonthProjection projection : metlifeBaseUrComponent.getProjections()) {
                ParametersDTO aumentoMetlifeMonth = aumentoMetlifeMap.get(projection.getMonth());
                double aumentoMetlifeValueMonth;
                if (aumentoMetlifeMonth != null) {
                    aumentoMetlifeValueMonth = aumentoMetlifeMonth.getValue();
                    lastMetlife = aumentoMetlifeValueMonth;
                } else {
                    aumentoMetlifeValueMonth = lastMetlife;
                }
                String month = projection.getMonth();
                double metlifePerMonth = metlifeBaseUrComponent.getAmount().doubleValue() * (1 + aumentoMetlifeValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(metlifePerMonth));
                projections.add(monthProjection);
            }
            metlifeComponent.setProjections(projections);
        } else {
            metlifeComponent.setAmount(BigDecimal.valueOf(0));
            metlifeComponent.setProjections(Shared.generateMonthProjection(period, range, metlifeComponent.getAmount()));
        }
        component.add(metlifeComponent);
    }
}
