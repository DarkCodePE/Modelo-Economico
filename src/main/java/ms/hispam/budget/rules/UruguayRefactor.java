package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
import ms.hispam.budget.util.Shared;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
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
        //log.debug("salaryBaseComponent: {}", salaryBaseComponent);
        //log.debug("salaryBaseComponent: {}", salaryBaseComponent);
        //log.debug("daysComponent: {}", daysComponent);
        //TODO: Validate if daysComponent is null
        //return (salaryBaseComponent.getAmount().doubleValue() / daysComponent.getAmount().doubleValue()) * 30;
        return salaryBaseComponent.getAmount().doubleValue();
    }

    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> aumentoConsejoList, String classEmployee, String period, Integer range, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("0010");
        double salaryBase = salaryBaseComponent == null ? 0 : salaryBaseComponent.getAmount().doubleValue();
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        double proportionBase =  proporcionMensualMap.get(nextPeriod) == null ? 0 : proporcionMensualMap.get(nextPeriod).getValue();
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("SALARY");
        salaryComponent.setAmount(BigDecimal.valueOf(salaryBase * (1 + proportionBase)));
        salaryComponent.setProjections(Shared.generateMonthProjection(period, range, salaryComponent.getAmount()));
        double ultimaProporcion = 0;
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            String month = projection.getMonth();
            ParametersDTO proporcionMensual = proporcionMensualMap.get(month);
            double proporcion;
            if (proporcionMensual != null) {
                proporcion = proporcionMensual.getValue();
                ultimaProporcion = proporcion;
            } else {
                proporcion = ultimaProporcion;
            }
            double salary = projection.getAmount().doubleValue() * (1 + proporcion);
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

    //Guardia Especial SI($K5>0,BJ16*15%,0)
    //paymentComponent: GUARDIA_ESPECIAL_BASE_UR -> $K5
    //PaymentComponent: SALARY -> BJ16
    public void specialGuard(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardBaseUrComponent = componentMap.get("GUARDIA_ESPECIAL_BASE_UR");
        double guardBaseUr = guardBaseUrComponent == null ? 0 : guardBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getAmount().doubleValue();
        double specialGuardBase = salaryTeorico > 0 ? salaryTeorico * 0.15 : 0;
        PaymentComponentDTO specialGuardComponent = new PaymentComponentDTO();
        specialGuardComponent.setPaymentComponent("SPECIAL_GUARD");
        specialGuardComponent.setAmount(BigDecimal.valueOf(specialGuardBase));
        List<MonthProjection> projections = new ArrayList<>();
        if (guardBaseUrComponent != null && guardBaseUrComponent.getProjections() != null && salaryBaseComponent.getProjections() != null) {
            for (MonthProjection projection : salaryBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = projection.getAmount().doubleValue();
                double specialGuard = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.15 : 0;
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

    //Guardia Jurídica  SI($L6>0,BK17*5%,0)
    //paymentComponent: GUARDIA_JURIDICA_BASE_UR -> $L6
    //PaymentComponent: SALARY -> BK17
    public void legalGuard(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO guardBaseUrComponent = componentMap.get("GUARDIA_JURIDICA_BASE_UR");
        double guardBaseUr = guardBaseUrComponent == null ? 0 : guardBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryTeorico = salaryBaseComponent.getAmount().doubleValue();
        double legalGuardBase = salaryTeorico > 0 ? salaryTeorico * 0.05 : 0;
        PaymentComponentDTO legalGuardComponent = new PaymentComponentDTO();
        legalGuardComponent.setPaymentComponent("LEGAL_GUARD");
        legalGuardComponent.setAmount(BigDecimal.valueOf(legalGuardBase));
        List<MonthProjection> projections = new ArrayList<>();
        if (guardBaseUrComponent != null && guardBaseUrComponent.getProjections() != null && salaryBaseComponent.getProjections() != null) {
            for (MonthProjection projection : salaryBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double salaryPerMonth = projection.getAmount().doubleValue();
                double legalGuard = projection.getAmount().doubleValue() > 0 ? salaryPerMonth * 0.05 : 0;
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
        double salaryTeorico = salaryBaseComponent.getAmount().doubleValue();
        quarterlyBonusComponent.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.15) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
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
        double salaryTeorico = salaryBaseComponent.getAmount().doubleValue();
        quarterlyBonus8Component.setAmount(salaryTeorico > 0 ? BigDecimal.valueOf(bonusBaseUr * 0.08) : BigDecimal.valueOf(0));
        List<MonthProjection> projections = new ArrayList<>();
        if (bonusBaseUrComponent != null && bonusBaseUrComponent.getProjections() != null) {
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
        double salaryTeorico = salaryBaseComponent.getAmount().doubleValue();
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
        //BONO_ANUAL is percentage
        double annualBonus = annualBonusBaseUrComponent == null ? 0.0 : ((annualBonusBaseUrComponent.getAmount().doubleValue() / 100) * salaryBase) / 12;
        PaymentComponentDTO annualBonusComponent = new PaymentComponentDTO();
        annualBonusComponent.setPaymentComponent("ANNUAL_BONUS");
        annualBonusComponent.setAmount(BigDecimal.valueOf(annualBonus));
        List<MonthProjection> projections = new ArrayList<>();
        if (annualBonusBaseUrComponent !=null && salaryBaseComponent != null && salaryBaseComponent.getProjections() != null) {
            for (MonthProjection projection : salaryBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double annualBonusPerMonth = ((annualBonusBaseUrComponent.getAmount().doubleValue() / 100) * salaryBaseComponent.getAmount().doubleValue()) / 12;
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
        double salesBonusComponentAmount = salesBonusBaseUrComponent == null ? 0 : salesBonusBaseUrComponent.getAmount().doubleValue();
        double salesBonus = annualBonus == 0 ? 0 : salesBonusComponentAmount * (1 + proporcionMensualBaseValue);
        PaymentComponentDTO salesBonusComponent = new PaymentComponentDTO();
        salesBonusComponent.setPaymentComponent("SALES_BONUS");
        salesBonusComponent.setAmount(BigDecimal.valueOf(salesBonus));
        List<MonthProjection> projections = new ArrayList<>();
        if (salesBonusBaseUrComponent != null && salesBonusBaseUrComponent.getProjections() != null && annualBonus == 0) {
            double lastSalesBonus = 0;
            for (MonthProjection projection : salesBonusBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO proporcionMensualMonth = proporcionMensualMap.get(month);
                double proporcionMensualValueMonth;
                if (proporcionMensualMonth != null) {
                    proporcionMensualValueMonth = proporcionMensualMonth.getValue();
                    lastSalesBonus = proporcionMensualValueMonth;
                } else {
                    proporcionMensualValueMonth = lastSalesBonus;
                }
                double salesBonusPerMonth = projection.getAmount().doubleValue() * (1 + proporcionMensualValueMonth);
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
        double salesCommissions;
        if (salesCommissionsBaseUrComponent != null) {
            salesCommissions = salesCommissionsBaseUrComponent.getAmount().doubleValue();
        } else {
            salesCommissions = 0;
        }
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporcionMensualBase = proporcionMensualMap.get(nextPeriod);
        double propocioMensualBaseValue = proporcionMensualBase == null ? 0 : proporcionMensualBase.getValue() / 100;
        PaymentComponentDTO salesCommissionsComponent = new PaymentComponentDTO();
        salesCommissionsComponent.setPaymentComponent("SALES_COMMISSIONS");
        salesCommissionsComponent.setAmount(BigDecimal.valueOf(salesCommissions * (1 + propocioMensualBaseValue)));
        salesCommissionsComponent.setProjections(Shared.generateMonthProjection(period, range, salesCommissionsComponent.getAmount()));
        log.debug("salesCommissionsComponent: {}", salesCommissionsComponent);
        List<MonthProjection> projections = new ArrayList<>();
        if (salesCommissionsBaseUrComponent != null && salesCommissionsBaseUrComponent.getProjections() != null) {
            double lastSalesCommissions = 0;

            for (MonthProjection projection : salesCommissionsBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO proporcionMensual = proporcionMensualMap.get(month);
                double proporcionMensualValue;
                if (proporcionMensual != null) {
                    proporcionMensualValue = proporcionMensual.getValue() / 100;
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
            List<MonthProjection> limitedProjections = collectionCommissionsBaseUrComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            for (MonthProjection projection : limitedProjections) {
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
        foodTicketComponent.setProjections(Shared.generateMonthProjection(period, range, foodTicketComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (foodTicketBaseUrComponent != null && foodTicketBaseUrComponent.getProjections() != null) {
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
                double foodTicketPerMonth = projection.getAmount().doubleValue() * (1 + aumentoTicketValueMonth);
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
            List<MonthProjection> limitedProjections = suatBaseUrComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            double lastSuat = 0;
            for (MonthProjection projection : limitedProjections) {
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
    //SUAT (grav. dinero) -> SI($U5>0;$U5*(1+SI(ESNUMERO(HALLAR("CEO";$D5));0;SI(O(ESNUMERO(HALLAR("Director";$D5));ESNUMERO(HALLAR("Gerente";$D5)));BJ$4;BJ$3)));0)
    //externalComponent: SUAT_GRAV_DINERO -> $U5
    //param: %Aumento por Consejo de Salario -> BJ$4
    //param: %Rev x Inflación -> BJ$3
    public void suatGravDinero(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoConsejoList, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO suatMoneyBaseUrComponent = componentMap.get("SUAT_GRAV_DINERO_BASE");
        double suatMoneyBaseUr = suatMoneyBaseUrComponent == null ? 0 : suatMoneyBaseUrComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporcionMensual = proporcionMensualMap.get(nextPeriod);
        double proporcionMensualBaseValue = proporcionMensual == null ? 0 : proporcionMensual.getValue();
        double suatMoney = suatMoneyBaseUr > 0 ? suatMoneyBaseUr * (1 + proporcionMensualBaseValue) : 0;
        PaymentComponentDTO suatMoneyComponent = new PaymentComponentDTO();
        suatMoneyComponent.setPaymentComponent("SUAT_GRAV_DINERO");
        suatMoneyComponent.setAmount(BigDecimal.valueOf(suatMoney));
        suatMoneyComponent.setProjections(Shared.generateMonthProjection(period, range, suatMoneyComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        double lastSuatMoney = 0;
        if (suatMoneyBaseUrComponent != null && suatMoneyBaseUrComponent.getProjections() != null) {
            for (MonthProjection projection : suatMoneyBaseUrComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO proporcionMensualMonth = proporcionMensualMap.get(month);
                double proporcionMensualValueMonth;
                if (proporcionMensualMonth != null) {
                    proporcionMensualValueMonth = proporcionMensualMonth.getValue();
                    lastSuatMoney = proporcionMensualValueMonth;
                } else {
                    proporcionMensualValueMonth = lastSuatMoney;
                }
                double suatMoneyPerMonth = projection.getAmount().doubleValue() * (1 + proporcionMensualValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(suatMoneyPerMonth));
                projections.add(monthProjection);
            }
            suatMoneyComponent.setProjections(projections);
        } else {
            suatMoneyComponent.setAmount(BigDecimal.valueOf(0));
            suatMoneyComponent.setProjections(Shared.generateMonthProjection(period, range, suatMoneyComponent.getAmount()));
        }
        component.add(suatMoneyComponent);
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
            List<MonthProjection> limitedProjections = bcBsBaseUrComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            double lastBcBs = 0;

            for (MonthProjection projection : limitedProjections) {
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
                MonthProjection monthProjection = new MonthProjection(); // Create once
                monthProjection.setMonth(month); // Reuse
                monthProjection.setAmount(BigDecimal.valueOf(bcBsPerMonth)); // Reuse
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

    //Metlife (grav. Dinero) -> SI($X5>0,$X5*(1+SI(ESNUMERO(HALLAR("CEO",$D5)),0,SI(O(ESNUMERO(HALLAR("Director",$D5)),ESNUMERO(HALLAR("Gerente",$D5))),BJ$4,BJ$3))),0)
    //externalComponent: METLIFE_GRAV_DINERO -> $X5
    //param: %Aumento por Consejo de Salario -> BJ$3
    //param: %Rev x Inflación -> BJ$4
    public void metlifeGravDinero(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoConsejoList, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO metlifeGravDineroBaseUrComponent = componentMap.get("METLIFE_GRAV_DINERO_BASE");
        double metlifeGravDineroBaseUr = metlifeGravDineroBaseUrComponent == null ? 0 : metlifeGravDineroBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double salaryBase = salaryBaseComponent == null ? 0 : salaryBaseComponent.getAmount().doubleValue();
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporcionMensualBase = proporcionMensualMap.get(nextPeriod);
        double proporcionMensualBaseValue = proporcionMensualBase == null ? 0 : proporcionMensualBase.getValue();
        double metlifeGravDinero = metlifeGravDineroBaseUr > 0 ? metlifeGravDineroBaseUr * (1 + proporcionMensualBaseValue) : 0;
        PaymentComponentDTO metlifeGravDineroComponent = new PaymentComponentDTO();
        metlifeGravDineroComponent.setPaymentComponent("METLIFE_GRAV_DINERO");
        metlifeGravDineroComponent.setAmount(BigDecimal.valueOf(metlifeGravDinero));
        List<MonthProjection> projections = new ArrayList<>();
        if (metlifeGravDineroBaseUrComponent != null && metlifeGravDineroBaseUrComponent.getProjections() != null) {
            double lastMetlifeGravDinero = 0;
            for (MonthProjection projection : metlifeGravDineroBaseUrComponent.getProjections()) {
                ParametersDTO proporcionMensual = proporcionMensualMap.get(projection.getMonth());
                double proporcionMensualValue;
                if (proporcionMensual != null) {
                    proporcionMensualValue = proporcionMensual.getValue();
                    lastMetlifeGravDinero = proporcionMensualValue;
                } else {
                    proporcionMensualValue = lastMetlifeGravDinero;
                }
                String month = projection.getMonth();
                double metlifeGravDineroPerMonth = projection.getAmount().doubleValue() * (1 + proporcionMensualValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(metlifeGravDineroPerMonth));
                projections.add(monthProjection);
            }
            metlifeGravDineroComponent.setProjections(projections);
        } else {
            metlifeGravDineroComponent.setAmount(BigDecimal.valueOf(0));
            metlifeGravDineroComponent.setProjections(Shared.generateMonthProjection(period, range, metlifeGravDineroComponent.getAmount()));
        }
        component.add(metlifeGravDineroComponent);
    }



    //Viático auto ->$Z5*(1+SI(ESNUMERO(HALLAR("CEO";$D5));0;SI(O(ESNUMERO(HALLAR("Director";$D5));ESNUMERO(HALLAR("Gerente";$D5)));AI$4;AI$3)))
    //externalComponent: VIATICO_AUTO -> $Z5
    //param: %Rev x Inflación -> AI$4
    //param: %Aumento por Consejo de Salario -> AI$3
    public void carAllowance(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoConsejoList, List<ParametersDTO> inflacionList) {
        List<ParametersDTO> proporcionMensualList = getParametersByPosition(classEmployee, inflacionList, aumentoConsejoList);
        Map<String, ParametersDTO> proporcionMensualMap = new HashMap<>();
        Map<String, Double> proporcionMensualCache = new HashMap<>();
        createCache(proporcionMensualList, proporcionMensualMap, proporcionMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO carAllowanceBaseUrComponent = componentMap.get("VIATICO_AUTO");
        double carAllowanceBaseUr = carAllowanceBaseUrComponent == null ? 0 : carAllowanceBaseUrComponent.getAmount().doubleValue();
        PaymentComponentDTO carAllowanceComponent = new PaymentComponentDTO();
        carAllowanceComponent.setPaymentComponent("CAR_ALLOWANCE");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporcionMensual = proporcionMensualMap.get(nextPeriod);
        double proporcionMensualBaseValue = proporcionMensual == null ? 0 : proporcionMensual.getValue();
        double carAllowance = carAllowanceBaseUr * (1 + proporcionMensualBaseValue);
        carAllowanceComponent.setAmount(BigDecimal.valueOf(carAllowance));
        List<MonthProjection> projections = new ArrayList<>();
        if (carAllowanceBaseUrComponent != null && carAllowanceBaseUrComponent.getProjections() != null) {
            List<MonthProjection> limitedProjections = carAllowanceBaseUrComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            double lastCarAllowance = 0;

            for (MonthProjection projection : limitedProjections) {
                ParametersDTO proporcionMensualMonth = proporcionMensualMap.get(projection.getMonth());
                double proporcionMensualValueMonth;
                if (proporcionMensualMonth != null) {
                    proporcionMensualValueMonth = proporcionMensualMonth.getValue();
                    lastCarAllowance = proporcionMensualValueMonth;
                } else {
                    proporcionMensualValueMonth = lastCarAllowance;
                }
                String month = projection.getMonth();
                double carAllowancePerMonth = carAllowanceBaseUrComponent.getAmount().doubleValue() * (1 + proporcionMensualValueMonth);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(carAllowancePerMonth));
                projections.add(monthProjection);
            }
            carAllowanceComponent.setProjections(projections);
        } else {
            carAllowanceComponent.setAmount(BigDecimal.valueOf(0));
            carAllowanceComponent.setProjections(Shared.generateMonthProjection(period, range, carAllowanceComponent.getAmount()));
        }
        component.add(carAllowanceComponent);
    }
    //Aguinaldo -> (AI17+AI24+AI31+AI38+AI45+AI52+AI59+AI66+AI73+AI80+AI87+AI94+AI101+AI143/2)/12
    //paymentComponent: SALARY -> AI17
    public void aguinaldo(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        List<String> aguinaldoComponents = Arrays.asList("SALARY", "HHEE", "GUARD", "GUARD_ESPECIAL", "GUARD_JURIDICAL","QUARTERLY_BONUS", "QUARTERLY_BONUS_8", "MONTHLY_BONUS", "MONTHLY_BONUS_15", "ANNUAL_BONUS", "SALES_BONUS", "SALES_COMMISSIONS", "COLLECTION_COMMISSIONS", "CAR_ALLOWANCE");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> aguinaldoComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        BigDecimal totalAmount = aguinaldoComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentComponentDTO salaryBaseComponent = componentMap.get("SALARY");
        double aguinaldo = totalAmount.doubleValue() / 2 / 12;
        PaymentComponentDTO aguinaldoComponent = new PaymentComponentDTO();
        aguinaldoComponent.setPaymentComponent("AGUINALDO");
        aguinaldoComponent.setAmount(BigDecimal.valueOf(aguinaldo));
        List<MonthProjection> projections = new ArrayList<>();
        if (salaryBaseComponent != null && salaryBaseComponent.getProjections() != null) {
            for (MonthProjection projection : salaryBaseComponent.getProjections()) {
                double totalAmountPerMonth = aguinaldoComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(c -> c.getProjections().stream())
                        .filter(p -> p.getMonth().equals(projection.getMonth()))
                        .mapToDouble(p -> p.getAmount().doubleValue())
                        .sum();
                String month = projection.getMonth();
                double aguinaldoPerMonth = totalAmountPerMonth / 2 / 12;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(aguinaldoPerMonth));
                projections.add(monthProjection);
            }
            aguinaldoComponent.setProjections(projections);
        } else {
            aguinaldoComponent.setAmount(BigDecimal.valueOf(0));
            aguinaldoComponent.setProjections(Shared.generateMonthProjection(period, range, aguinaldoComponent.getAmount()));
        }
        //log.debug("aguinaldoComponent: {}", aguinaldoComponent);
        component.add(aguinaldoComponent);
    }

    //BSE -> ((BJ16+BJ23+BJ30+BJ37+BJ44+BJ51+BJ58+BJ65+BJ72+BJ79+BJ86+BJ93+BJ100+BJ156+BJ114+BJ142+BJ149+($AC5+$AD5+$AE5+$AF5+$AG5+$AH5+$AJ5+$AM5+$AN5+$AO5+$AP5+$AQ5+$AR5+$AS5+$AT5+$AU5+$AV5+$AW5+$AX5+$AY5+$AZ5)+BJ107+BJ121+BJ135+BJ128+($AI5+$AK5+$AL5))*BJ$9
    //BJ16 -> SALARY
    //BJ23 -> HHEE
    //BJ30 -> GUARD
    //BJ37 -> GUARD_ESPECIAL
    //BJ44 -> GUARD_JURIDICAL
    //BJ51 -> QUARTERLY_BONUS
    //BJ58 -> QUARTERLY_BONUS_8
    //BJ65 -> MONTHLY_BONUS
    //BJ72 -> MONTHLY_BONUS_15
    //BJ79 -> ANNUAL_BONUS
    //BJ86 -> SALES_BONUS
    //BJ93 -> SALES_COMMISSIONS
    //BJ100 -> COLLECTION_COMMISSIONS
    //BJ156 -> CAR_ALLOWANCE
    //BJ114 -> FOOD_TICKET
    //BJ142 -> SUAT
    //BJ149 -> SUAT_GRAV_ESPECIE
    //BJ107 -> BC_BS
    //BJ121 -> METLIFE
    //BJ135 -> METLIFE_GRAV_DINERO
    //BJ128 -> AGUINALDO
    //$AC5 -> INCIDENCIA_LICENCIA_BASE
    //$AD5 -> LICENCIA_MATERNA_BASE
    //$AE5 -> HORARIO_CIUDADANO_BASE
    //$AF5 -> SUPLENCIA_BASE
    //$AG5 -> FALTAS_BASE
    //$AH5 -> MOVILIDAD_BASE
    //$AJ5 -> AUTO_EMPRESA_BASE
    //$AM5 -> PRESTACION_VIVIENDA_BASE
    //$AN5 -> ALQUILER_VEHICULO_BASE
    //$AO5 -> SEGURO_VIDA_BASE
    //$AP5 -> FERIADO_LABORABLE_BASE
    //$AQ5 -> FERIADO_NO_LABORABLE_BASE
    //$AR5 -> AD_HORA_NOCTURNA_BASE
    //$AS5 -> AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AT5 -> EMERGENCIA_DIURNA_BASE
    //$AU5 -> EMERGENCIA_NOCTURNA_BASE
    //$AV5 -> EMERGENCIA_FERIADO_NO_LABORADO_BASE
    //$AW5 -> EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AX5 -> OBJETIVO_LIDERES_BASE
    //$AY5 -> PREMIO_COORDINADORES_BASE
    //$AZ5 -> PREMIO_MENSUAL_DISTRIBUIDO_BASE
    //BJ$9 -> %Aumento BSE
    //$AI5 -> PREMIO_CTA_PYMES_BASE
    //$AK5 -> ADELANTO_CUENTA_CS_BASE
    //$AL5 -> HIRING_BONUS_BASE
    public void bse(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> aumentoBseList) {
        Map<String, ParametersDTO> aumentoBseMap = new HashMap<>();
        Map<String, Double> aumentoBseCache = new HashMap<>();
        createCache(aumentoBseList, aumentoBseMap, aumentoBseCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> bseComponents = Arrays.asList("SALARY", "HHEE", "GUARD", "GUARD_ESPECIAL", "GUARD_JURIDICAL", "QUARTERLY_BONUS", "QUARTERLY_BONUS_8", "MONTHLY_BONUS", "MONTHLY_BONUS_15", "ANNUAL_BONUS", "SALES_BONUS", "SALES_COMMISSIONS", "COLLECTION_COMMISSIONS", "CAR_ALLOWANCE", "FOOD_TICKET", "SUAT", "SUAT_GRAV_ESPECIE", "BC_BS", "METLIFE", "METLIFE_GRAV_DINERO", "AGUINALDO", "INCIDENCIA_LICENCIA_BASE", "LICENCIA_MATERNA_BASE", "HORARIO_CIUDADANO_BASE", "SUPLENCIA_BASE", "FALTAS_BASE", "MOVILIDAD_BASE", "AUTO_EMPRESA_BASE", "PRESTACION_VIVIENDA_BASE", "ALQUILER_VEHICULO_BASE", "SEGURO_VIDA_BASE", "FERIADO_LABORABLE_BASE", "FERIADO_NO_LABORABLE_BASE", "AD_HORA_NOCTURNA_BASE", "AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_DIURNA_BASE", "EMERGENCIA_NOCTURNA_BASE", "EMERGENCIA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "OBJETIVO_LIDERES_BASE", "PREMIO_COORDINADORES_BASE", "PREMIO_MENSUAL_DISTRIBUIDO_BASE", "PREMIO_CTA_PYMES_BASE", "ADELANTO_CUENTA_CS_BASE", "HIRING_BONUS_BASE");
        BigDecimal totalAmount = bseComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        //NEXT DAY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO aumentoBse = aumentoBseMap.get(nextPeriod);
        double aumentoBseValue = aumentoBse == null ? 0 : aumentoBse.getValue();
        double bse = totalAmount.doubleValue() * aumentoBseValue;
        PaymentComponentDTO bseComponent = new PaymentComponentDTO();
        bseComponent.setPaymentComponent("BSE");
        bseComponent.setAmount(BigDecimal.valueOf(bse));
        bseComponent.setProjections(Shared.generateMonthProjection(period, range, bseComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        double lastAunmentoBse = 0;
        if(bseComponent.getProjections() != null) {
            for (MonthProjection projection : bseComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO aumentoBsePerMonth;
                if (aumentoBseMap.get(month) != null) {
                    aumentoBsePerMonth = aumentoBseMap.get(month);
                    lastAunmentoBse = aumentoBsePerMonth.getValue();
                } else {
                    aumentoBsePerMonth = new ParametersDTO();
                    aumentoBsePerMonth.setValue(lastAunmentoBse);
                }
                double aumentoBsePerMonthValue = aumentoBsePerMonth == null ? 0 : aumentoBsePerMonth.getValue();
                double bsePerMonth = totalAmount.doubleValue() * aumentoBsePerMonthValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(bsePerMonth));
                projections.add(monthProjection);
            }
            bseComponent.setProjections(projections);
        } else {
            bseComponent.setAmount(BigDecimal.valueOf(0));
            bseComponent.setProjections(Shared.generateMonthProjection(period, range, bseComponent.getAmount()));
        }
        component.add(bseComponent);
    }
    //Salario Vacacional ->(BJ16+BJ23+BJ30+BJ37+BJ44+BJ51+BJ58+BJ65+BJ72+BJ79+BJ86+BJ93+BJ100+BJ114+BJ142+BJ149+BJ156+($AC5+$AD5+$AE5+$AF5+$AG5+$AH5+$AJ5+$AM5+$AN5+$AO5+$AP5+$AQ5+$AR5+$AS5+$AT5+$AU5+$AV5+$AW5+$AX5+$AY5+$AZ5)+BJ107+BJ121+BJ128+BJ135+BJ163+($AI5+$AK5+$AL5))/30*(BJ$10+TRUNCAR(BJ170/5;0))/12
    //BJ16 -> SALARY
    //BJ23 -> HHEE
    //BJ30 -> GUARD
    //BJ37 -> GUARD_ESPECIAL
    //BJ44 -> GUARD_JURIDICAL
    //BJ51 -> QUARTERLY_BONUS
    //BJ58 -> QUARTERLY_BONUS_8
    //BJ65 -> MONTHLY_BONUS
    //BJ72 -> MONTHLY_BONUS_15
    //BJ79 -> ANNUAL_BONUS
    //BJ86 -> SALES_BONUS
    //BJ93 -> SALES_COMMISSIONS
    //BJ100 -> COLLECTION_COMMISSIONS
    //BJ114 -> FOOD_TICKET
    //BJ142 -> SUAT
    //BJ149 -> SUAT_GRAV_ESPECIE
    //BJ156 -> CAR_ALLOWANCE
    //BJ107 -> BC_BS
    //BJ121 -> METLIFE
    //BJ128 -> AGUINALDO
    //BJ135 -> METLIFE_GRAV_DINERO
    //BJ163 -> BSE
    //$AC5 -> INCIDENCIA_LICENCIA_BASE
    //$AD5 -> LICENCIA_MATERNA_BASE
    //$AE5 -> HORARIO_CIUDADANO_BASE
    //$AF5 -> SUPLENCIA_BASE
    //$AG5 -> FALTAS_BASE
    //$AH5 -> MOVILIDAD_BASE
    //$AJ5 -> AUTO_EMPRESA_BASE
    //$AM5 -> PRESTACION_VIVIENDA_BASE
    //$AN5 -> ALQUILER_VEHICULO_BASE
    //$AO5 -> SEGURO_VIDA_BASE
    //$AP5 -> FERIADO_LABORABLE_BASE
    //$AQ5 -> FERIADO_NO_LABORABLE_BASE
    //$AR5 -> AD_HORA_NOCTURNA_BASE
    //$AS5 -> AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AT5 -> EMERGENCIA_DIURNA_BASE
    //$AU5 -> EMERGENCIA_NOCTURNA_BASE
    //$AV5 -> EMERGENCIA_FERIADO_NO_LABORADO_BASE
    //$AW5 -> EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AX5 -> OBJETIVO_LIDERES_BASE
    //$AY5 -> PREMIO_COORDINADORES_BASE
    //$AZ5 -> PREMIO_MENSUAL_DISTRIBUIDO_BASE
    //BJ$10 -> %Dias licencia anual
    //BJ170 -> Numero de años trabajados (antiguedad)
    public void vacationSalary(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> diasLicenciaAnualList, LocalDate dateContract) {
        Map<String, ParametersDTO> diasLicenciaAnualMap = new HashMap<>();
        Map<String, Double> diasLicenciaAnualCache = new HashMap<>();
        createCache(diasLicenciaAnualList, diasLicenciaAnualMap, diasLicenciaAnualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> salarioVacacionalComponents = Arrays.asList("SALARY", "HHEE", "GUARD", "GUARD_ESPECIAL", "GUARD_JURIDICAL", "QUARTERLY_BONUS", "QUARTERLY_BONUS_8", "MONTHLY_BONUS", "MONTHLY_BONUS_15", "ANNUAL_BONUS", "SALES_BONUS", "SALES_COMMISSIONS", "COLLECTION_COMMISSIONS", "CAR_ALLOWANCE", "FOOD_TICKET", "SUAT", "SUAT_GRAV_ESPECIE", "BC_BS", "METLIFE", "METLIFE_GRAV_DINERO", "AGUINALDO", "INCIDENCIA_LICENCIA_BASE", "LICENCIA_MATERNA_BASE", "HORARIO_CIUDADANO_BASE", "SUPLENCIA_BASE", "FALTAS_BASE", "MOVILIDAD_BASE", "AUTO_EMPRESA_BASE", "PRESTACION_VIVIENDA_BASE", "ALQUILER_VEHICULO_BASE", "SEGURO_VIDA_BASE", "FERIADO_LABORABLE_BASE", "FERIADO_NO_LABORABLE_BASE", "AD_HORA_NOCTURNA_BASE", "AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_DIURNA_BASE", "EMERGENCIA_NOCTURNA_BASE", "EMERGENCIA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "OBJETIVO_LIDERES_BASE", "PREMIO_COORDINADORES_BASE", "PREMIO_MENSUAL_DISTRIBUIDO_BASE", "PREMIO_CTA_PYMES_BASE", "ADELANTO_CUENTA_CS_BASE", "HIRING_BONUS_BASE");
        BigDecimal totalAmount = salarioVacacionalComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentComponentDTO salaryVacacionalComponent = new PaymentComponentDTO();
        salaryVacacionalComponent.setPaymentComponent("VACATION_SALARY");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO diasLicenciaAnual = diasLicenciaAnualMap.get(nextPeriod);
        double diasLicenciaAnualValue = diasLicenciaAnual == null ? 0 : diasLicenciaAnual.getValue();
        //calcular antiguedad por la fecha de contratacion
        LocalDate dateActual = LocalDate.now();
        long seniority = 0;
        if (dateContract != null) {
            seniority = Math.max(ChronoUnit.YEARS.between(dateContract, dateActual), 0);
        }
        //La fecha de contratación se utiliza para calcular la antigüedad de la persona en la posición, y cada 5 años de antigüedad cumplidos a Diciembre del año anterior, se incrementa 1 día de licencia adicional a los ingresados en el parámetro {Días de licencia (anual)}.
        //(BK$10+TRUNCAR(BK173/5;0) -> diasLicenciaAnualValue + Math.floor((double) seniority / 5)
        //cada 5 años de antiguedad se incrementa 1 dia de licencia adicional
        //log.debug("seniority: {}", seniority);
        double salarioVacacional = totalAmount.doubleValue() / 30 * (diasLicenciaAnualValue + Math.floor((double) seniority / 5)) / 12;
        salaryVacacionalComponent.setAmount(BigDecimal.valueOf(salarioVacacional));
        salaryVacacionalComponent.setProjections(Shared.generateMonthProjection(period, range, salaryVacacionalComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (salaryVacacionalComponent.getProjections() != null) {
            for (MonthProjection projection : salaryVacacionalComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO diasLicenciaAnualPerMonth = diasLicenciaAnualMap.get(month);
                double diasLicenciaAnualPerMonthValue = diasLicenciaAnualPerMonth == null ? 0 : diasLicenciaAnualPerMonth.getValue();
                double salarioVacacionalPerMonth = totalAmount.doubleValue() / 30 * (diasLicenciaAnualPerMonthValue + Math.floor((double) seniority / 5)) / 12;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(salarioVacacionalPerMonth));
                projections.add(monthProjection);
            }
            salaryVacacionalComponent.setProjections(projections);
        } else {
            salaryVacacionalComponent.setAmount(BigDecimal.valueOf(0));
            salaryVacacionalComponent.setProjections(Shared.generateMonthProjection(period, range, salaryVacacionalComponent.getAmount()));
        }
        component.add(salaryVacacionalComponent);
    }
    //CONCEPTO AUXILIAR: NO SE AGREGA LA COMPONENTE DE PAGO
    //Montepío Patronal (sobre haberes en dinero)
    //[Auxiliar en Excel para calcular Montepío Patronal (sobre haberes en dinero)]
    // Montepio -> =-$Z5/2
    //nomina component: MONTE_PIO_PERSONAL_BASE -> $Z5
    public void montepioPatronal(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO montepioPersonalBaseComponent = componentMap.get("MONTE_PIO_PERSONAL_BASE");
        double montepioPersonalBase = montepioPersonalBaseComponent == null ? 0 : montepioPersonalBaseComponent.getAmount().doubleValue();
        double montepio = -montepioPersonalBase / 2;
        PaymentComponentDTO montepioComponent = new PaymentComponentDTO();
        montepioComponent.setPaymentComponent("MONTE_PIO");
        montepioComponent.setAmount(BigDecimal.valueOf(montepio));
        List<MonthProjection> projections = new ArrayList<>();
        if (montepioPersonalBaseComponent != null && montepioPersonalBaseComponent.getProjections() != null) {
            List<MonthProjection> limitedProjections = montepioPersonalBaseComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            for (MonthProjection projection : limitedProjections) {
                String month = projection.getMonth();
                double montepioPerMonth = -montepioPersonalBaseComponent.getAmount().doubleValue() / 2;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(montepioPerMonth));
                projections.add(monthProjection);
            }
            montepioComponent.setProjections(projections);
        } else {
            montepioComponent.setAmount(BigDecimal.valueOf(0));
            montepioComponent.setProjections(Shared.generateMonthProjection(period, range, montepioComponent.getAmount()));
        }
        component.add(montepioComponent);
    }
    // Montepío Patronal (sobre haberes en especie)
    //[Auxiliar en Excel para calcular]"Montepío Patronal (sobre haberes en especie)
    // Montepio -> =(BJ107+BJ121+BJ135+BJ128+BJ163)*BJ$11
    //BJ107 -> TICKET_ALIMENTACION
    //BJ121 -> SUAT_GRAV_ESPECIE
    //BJ135 -> BC_BS
    //BJ128 -> METLIFE
    //BJ163 -> BSE
    //BJ$11 -> %Montepío
    public void montepioPatronalEspecie(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> montepioList) {
        Map<String, ParametersDTO> montepioMap = new HashMap<>();
        Map<String, Double> montepioCache = new HashMap<>();
        createCache(montepioList, montepioMap, montepioCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> montepioComponents = Arrays.asList("TICKET_ALIMENTACION", "SUAT_GRAV_ESPECIE", "BC_BS", "METLIFE", "BSE");
        BigDecimal totalAmount = montepioComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentComponentDTO montepioComponent = new PaymentComponentDTO();
        montepioComponent.setPaymentComponent("MONTE_PIO_ESPECIE");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO montepio = montepioMap.get(nextPeriod);
        double montepioValue = montepio == null ? 0 : montepio.getValue();
        double montepioBase = totalAmount.doubleValue() * montepioValue;
        montepioComponent.setAmount(BigDecimal.valueOf(montepioBase));
        List<MonthProjection> projections = new ArrayList<>();
        if (montepioComponents.stream().allMatch(componentMap::containsKey)) {
            List<MonthProjection> limitedProjections = componentMap.get("TICKET_ALIMENTACION").getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            for (MonthProjection projection : limitedProjections) {
                double totalAmountPerMonth = montepioComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(c -> c.getProjections().stream())
                        .filter(p -> p.getMonth().equals(projection.getMonth()))
                        .mapToDouble(p -> p.getAmount().doubleValue())
                        .sum();
                String month = projection.getMonth();
                double montepioPerMonth = totalAmountPerMonth * montepioValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(montepioPerMonth));
                projections.add(monthProjection);
            }
            montepioComponent.setProjections(projections);
        } else {
            montepioComponent.setAmount(BigDecimal.valueOf(0));
            montepioComponent.setProjections(Shared.generateMonthProjection(period, range, montepioComponent.getAmount()));
        }
        component.add(montepioComponent);
    }
    //"FRL Patronal
    //[Auxiliar en Excel para calcular]"FRL Patronal
    // FRL -> == -1*$AA6
    //$AA6 -> FRL_PERSONAL_BASE
    public void frlPatronal(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO frlPersonalBaseComponent = componentMap.get("FRL_PERSONAL_BASE");
        double frlPersonalBase = frlPersonalBaseComponent == null ? 0 : frlPersonalBaseComponent.getAmount().doubleValue();
        double frl = -1 * frlPersonalBase;
        PaymentComponentDTO frlComponent = new PaymentComponentDTO();
        frlComponent.setPaymentComponent("FRL");
        frlComponent.setAmount(BigDecimal.valueOf(frl));
        frlComponent.setProjections(Shared.generateMonthProjection(period, range, frlComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (frlPersonalBaseComponent != null && frlPersonalBaseComponent.getProjections() != null) {
            for (MonthProjection projection : frlPersonalBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double frlPerMonth = -1 * frlPersonalBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(frlPerMonth));
                projections.add(monthProjection);
            }
            frlComponent.setProjections(projections);
        } else {
            frlComponent.setAmount(BigDecimal.valueOf(0));
            frlComponent.setProjections(Shared.generateMonthProjection(period, range, frlComponent.getAmount()));
        }
        component.add(frlComponent);
    }
    //"Fonasa Patronal
    //[Auxiliar en Excel para calcular]"Fonasa Patronal
    // Fonasa -> = (BJ18+BJ25+BJ32+BJ39+BJ46+BJ53+BJ60+BJ67+BJ74+BJ81+BJ88+BJ95+BJ102+BJ116+BJ144+BJ158+($AC7+$AD7+$AE7+$AF7+$AG7+$AH7+$AJ7+$AM7+$AN7+$AO7+$AP7+$AQ7+$AR7+$AS7+$AT7+$AU7+$AV7+$AW7+$AX7+$AY7+$AZ7))*BJ$12
    //BJ18 -> SALARY
    //BJ25 -> HHEE
    //BJ32 -> GUARD
    //BJ39 -> GUARD_ESPECIAL
    //BJ46 -> GUARD_JURIDICAL
    //BJ53 -> QUARTERLY_BONUS
    //BJ60 -> QUARTERLY_BONUS_8
    //BJ67 -> MONTHLY_BONUS
    //BJ74 -> MONTHLY_BONUS_15
    //BJ81 -> ANNUAL_BONUS
    //BJ88 -> SALES_BONUS
    //BJ95 -> SALES_COMMISSIONS
    //BJ102 -> COLLECTION_COMMISSIONS
    //BJ116 -> FOOD_TICKET
    //BJ144 -> SUAT
    //BJ158 -> METLIFE_GRAV_DINERO
    //BJ116 -> CAR_ALLOWANCE
    //$AC7 -> INCIDENCIA_LICENCIA_BASE
    //$AD7 -> LICENCIA_MATERNA_BASE
    //$AE7 -> HORARIO_CIUDADANO_BASE
    //$AF7 -> SUPLENCIA_BASE
    //$AG7 -> FALTAS_BASE
    //$AH7 -> MOVILIDAD_BASE
    //AJ7 -> PRESTACION_VIVIENDA_BASE
    //$AM7 -> FERIADO_LABORABLE_BASE
    //$AN7 -> FERIADO_NO_LABORABLE_BASE
    //$AO7 -> AD_HORA_NOCTURNA_BASE
    //$AP7 -> AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AQ7 -> EMERGENCIA_DIURNA_BASE
    //$AR7 -> EMERGENCIA_NOCTURNA_BASE
    //$AS7 -> EMERGENCIA_FERIADO_NO_LABORADO_BASE
    //$AT7 -> EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AU7 -> OBJETIVO_LIDERES_BASE
    //$AV7 -> PREMIO_COORDINADORES_BASE
    //$AW7 -> PREMIO_MENSUAL_DISTRIBUIDO_BASE
    //$AX7 -> PREMIO_CTA_PYMES_BASE
    //$AY7 -> ADELANTO_CUENTA_CS_BASE
    //$AZ7 -> HIRING_BONUS_BASE
    //BJ$12 -> %Fonasa
    public void fonasaPatronal(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> fonasaList) {
        Map<String, ParametersDTO> fonasaMap = new HashMap<>();
        Map<String, Double> fonasaCache = new HashMap<>();
        createCache(fonasaList, fonasaMap, fonasaCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> fonasaComponents = Arrays.asList("SALARY", "HHEE", "GUARD", "GUARD_ESPECIAL", "GUARD_JURIDICAL", "QUARTERLY_BONUS", "QUARTERLY_BONUS_8", "MONTHLY_BONUS", "MONTHLY_BONUS_15", "ANNUAL_BONUS", "SALES_BONUS", "SALES_COMMISSIONS", "COLLECTION_COMMISSIONS", "FOOD_TICKET", "SUAT", "METLIFE_GRAV_DINERO", "CAR_ALLOWANCE", "INCIDENCIA_LICENCIA_BASE", "LICENCIA_MATERNA_BASE", "HORARIO_CIUDADANO_BASE", "SUPLENCIA_BASE", "FALTAS_BASE", "MOVILIDAD_BASE", "PRESTACION_VIVIENDA_BASE", "FERIADO_LABORABLE_BASE", "FERIADO_NO_LABORABLE_BASE", "AD_HORA_NOCTURNA_BASE", "AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_DIURNA_BASE", "EMERGENCIA_NOCTURNA_BASE", "EMERGENCIA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "OBJETIVO_LIDERES_BASE", "PREMIO_COORDINADORES_BASE", "PREMIO_MENSUAL_DISTRIBUIDO_BASE", "PREMIO_CTA_PYMES_BASE", "ADELANTO_CUENTA_CS_BASE", "HIRING_BONUS_BASE");
        BigDecimal totalAmount = fonasaComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentComponentDTO fonasaComponent = new PaymentComponentDTO();
        fonasaComponent.setPaymentComponent("FONASA");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO fonasa = fonasaMap.get(nextPeriod);
        double fonasaValue = fonasa == null ? 0 : fonasa.getValue();
        double fonasaPatronal = totalAmount.doubleValue() * fonasaValue;
        fonasaComponent.setAmount(BigDecimal.valueOf(fonasaPatronal));
        fonasaComponent.setProjections(Shared.generateMonthProjection(period, range, fonasaComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (fonasaComponent.getProjections() != null) {
            List<MonthProjection> limitedProjections = fonasaComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            for (MonthProjection projection : limitedProjections) {
                String month = projection.getMonth();
                double fonasaPerMonth = totalAmount.doubleValue() * fonasaValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(fonasaPerMonth));
                projections.add(monthProjection);
            }
            fonasaComponent.setProjections(projections);
        } else {
            fonasaComponent.setAmount(BigDecimal.valueOf(0));
            fonasaComponent.setProjections(Shared.generateMonthProjection(period, range, fonasaComponent.getAmount()));
        }
        component.add(fonasaComponent);
    }
    //FGCL Patronal
    //[Auxiliar en Excel para calcular]
    // FGCL -> = =(BJ16+BJ23+BJ30+BJ37+BJ44+BJ51+BJ58+BJ65+BJ72+BJ79+BJ86+BJ93+BJ100+BJ114+BJ142+BJ149+BJ156+($AC5+$AD5+$AE5+$AF5+$AG5+$AH5+$AJ5+$AM5+$AN5+$AO5+$AP5+$AQ5+$AR5+$AS5+$AT5+$AU5+$AV5+$AW5+$AX5+$AY5+$AZ5))*BJ$13
    //BJ16 -> SALARY
    //BJ23 -> HHEE
    //BJ30 -> GUARD
    //BJ37 -> GUARD_ESPECIAL
    //BJ44 -> GUARD_JURIDICAL
    //BJ51 -> QUARTERLY_BONUS
    //BJ58 -> QUARTERLY_BONUS_8
    //BJ65 -> MONTHLY_BONUS
    //BJ72 -> MONTHLY_BONUS_15
    //BJ79 -> ANNUAL_BONUS
    //BJ86 -> SALES_BONUS
    //BJ93 -> SALES_COMMISSIONS
    //BJ100 -> COLLECTION_COMMISSIONS
    //BJ114 -> SUAT
    //BJ142 -> METLIFE_GRAV_DINERO
    //BJ149 -> AGUINALDO
    //BJ156 -> CAR_ALLOWANCE
    //$AC5 -> INCIDENCIA_LICENCIA_BASE
    //$AD5 -> LICENCIA_MATERNA_BASE
    //$AE5 -> HORARIO_CIUDADANO_BASE
    //$AF5 -> SUPLENCIA_BASE
    //$AG5 -> FALTAS_BASE
    //$AH5 -> MOVILIDAD_BASE
    //$AJ5 -> PRESTACION_VIVIENDA_BASE
    //$AM5 -> FERIADO_LABORABLE_BASE
    //$AN5 -> FERIADO_NO_LABORABLE_BASE
    //$AO5 -> AD_HORA_NOCTURNA_BASE
    //$AP5 -> AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AQ5 -> EMERGENCIA_DIURNA_BASE
    //$AR5 -> EMERGENCIA_NOCTURNA_BASE
    //$AS5 -> EMERGENCIA_FERIADO_NO_LABORADO_BASE
    //$AT5 -> EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE
    //$AU5 -> OBJETIVO_LIDERES_BASE
    //$AV5 -> PREMIO_COORDINADORES_BASE
    //$AW5 -> PREMIO_MENSUAL_DISTRIBUIDO_BASE
    //$AX5 -> PREMIO_CTA_PYMES_BASE
    //$AY5 -> ADELANTO_CUENTA_CS_BASE
    //$AZ5 -> HIRING_BONUS_BASE
    //BJ$13 -> %FGCL
    public void fgclPatronal(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> fgclList) {
        Map<String, ParametersDTO> fgclMap = new HashMap<>();
        Map<String, Double> fgclCache = new HashMap<>();
        createCache(fgclList, fgclMap, fgclCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> fgclComponents = Arrays.asList("SALARY", "HHEE", "GUARD", "GUARD_ESPECIAL", "GUARD_JURIDICAL", "QUARTERLY_BONUS", "QUARTERLY_BONUS_8", "MONTHLY_BONUS", "MONTHLY_BONUS_15", "ANNUAL_BONUS", "SALES_BONUS", "SALES_COMMISSIONS", "COLLECTION_COMMISSIONS", "SUAT", "METLIFE_GRAV_DINERO", "AGUINALDO", "CAR_ALLOWANCE", "INCIDENCIA_LICENCIA_BASE", "LICENCIA_MATERNA_BASE", "HORARIO_CIUDADANO_BASE", "SUPLENCIA_BASE", "FALTAS_BASE", "MOVILIDAD_BASE", "PRESTACION_VIVIENDA_BASE", "FERIADO_LABORABLE_BASE", "FERIADO_NO_LABORABLE_BASE", "AD_HORA_NOCTURNA_BASE", "AD_HORA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_DIURNA_BASE", "EMERGENCIA_NOCTURNA_BASE", "EMERGENCIA_FERIADO_NO_LABORADO_BASE", "EMERGENCIA_NOCTURNA_FERIADO_NO_LABORADO_BASE", "OBJETIVO_LIDERES_BASE", "PREMIO_COORDINADORES_BASE", "PREMIO_MENSUAL_DISTRIBUIDO_BASE", "PREMIO_CTA_PYMES_BASE", "ADELANTO_CUENTA_CS_BASE", "HIRING_BONUS_BASE");
        BigDecimal totalAmount = fgclComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        PaymentComponentDTO fgclComponent = new PaymentComponentDTO();
        fgclComponent.setPaymentComponent("FGCL");
        //next day
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO fgcl = fgclMap.get(nextPeriod);
        double fgclValue = fgcl == null ? 0 : fgcl.getValue();
        double fgclPatronal = totalAmount.doubleValue() * fgclValue;
        fgclComponent.setAmount(BigDecimal.valueOf(fgclPatronal));
        fgclComponent.setProjections(Shared.generateMonthProjection(period, range, fgclComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (fgclComponent.getProjections() != null) {
            List<MonthProjection> limitedProjections = fgclComponent.getProjections().stream()
                    .limit(range)
                    .collect(Collectors.toList());
            for (MonthProjection projection : limitedProjections) {
                String month = projection.getMonth();
                double fgclPerMonth = totalAmount.doubleValue() * fgclValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(fgclPerMonth));
                projections.add(monthProjection);
            }
            fgclComponent.setProjections(projections);
        } else {
            fgclComponent.setAmount(BigDecimal.valueOf(0));
            fgclComponent.setProjections(Shared.generateMonthProjection(period, range, fgclComponent.getAmount()));
        }
        component.add(fgclComponent);
    }

    //Aportes Patronales - >BJ186+BJ193+BJ200+BJ207+BJ214
    //BJ186 -> MONTE_PIO
    //BJ193 -> MONTE_PIO_ESPECIE
    //BJ200 -> FRL
    //BJ207 -> FONASA
    //BJ214 -> FGCL
    public void aportesPatronales(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        List<String> aportesComponents = Arrays.asList("MONTE_PIO", "MONTE_PIO_ESPECIE", "FRL", "FONASA", "FGCL");
        BigDecimal totalAmount = aportesComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentComponentDTO aportesComponent = new PaymentComponentDTO();
        aportesComponent.setPaymentComponent("EMPLOYER_CONTRIBUTIONS");
        aportesComponent.setAmount(totalAmount);
        aportesComponent.setProjections(Shared.generateMonthProjection(period, range, aportesComponent.getAmount()));
        component.add(aportesComponent);
    }
    //Uniforme -> $BB6
    //nomina component: UNIFORME_BASE -> $BB6
    public void uniforme(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO uniformeBaseComponent = componentMap.get("UNIFORME_BASE");
        double uniformeBase = uniformeBaseComponent == null ? 0 : uniformeBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO uniformeComponent = new PaymentComponentDTO();
        uniformeComponent.setPaymentComponent("UNIFORME");
        uniformeComponent.setAmount(BigDecimal.valueOf(uniformeBase));
        uniformeComponent.setProjections(Shared.generateMonthProjection(period, range, uniformeComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (uniformeBaseComponent != null && uniformeBaseComponent.getProjections() != null) {
            for (MonthProjection projection : uniformeBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double uniformePerMonth = uniformeBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(uniformePerMonth));
                projections.add(monthProjection);
            }
            uniformeComponent.setProjections(projections);
        } else {
            uniformeComponent.setAmount(BigDecimal.valueOf(0));
            uniformeComponent.setProjections(Shared.generateMonthProjection(period, range, uniformeComponent.getAmount()));
        }
        component.add(uniformeComponent);
    }
    //TFSP -> $BC6
    //nomina component: TFSP_BASE -> $BC6
    public void tfsp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO tfspBaseComponent = componentMap.get("TFSP_BASE");
        double tfspBase = tfspBaseComponent == null ? 0 : tfspBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO tfspComponent = new PaymentComponentDTO();
        tfspComponent.setPaymentComponent("TFSP");
        tfspComponent.setAmount(BigDecimal.valueOf(tfspBase));
        tfspComponent.setProjections(Shared.generateMonthProjection(period, range, tfspComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (tfspBaseComponent != null && tfspBaseComponent.getProjections() != null) {
            for (MonthProjection projection : tfspBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double tfspPerMonth = tfspBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(tfspPerMonth));
                projections.add(monthProjection);
            }
            tfspComponent.setProjections(projections);
        } else {
            tfspComponent.setAmount(BigDecimal.valueOf(0));
            tfspComponent.setProjections(Shared.generateMonthProjection(period, range, tfspComponent.getAmount()));
        }
        component.add(tfspComponent);
    }
    //PSP -> $BD6
    //nomina component: PSP_BASE -> $BD6
    public void psp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pspBaseComponent = componentMap.get("PSP_BASE");
        double pspBase = pspBaseComponent == null ? 0 : pspBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO pspComponent = new PaymentComponentDTO();
        pspComponent.setPaymentComponent("PSP");
        pspComponent.setAmount(BigDecimal.valueOf(pspBase));
        pspComponent.setProjections(Shared.generateMonthProjection(period, range, pspComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (pspBaseComponent != null && pspBaseComponent.getProjections() != null) {
            for (MonthProjection projection : pspBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double pspPerMonth = pspBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(pspPerMonth));
                projections.add(monthProjection);
            }
            pspComponent.setProjections(projections);
        } else {
            pspComponent.setAmount(BigDecimal.valueOf(0));
            pspComponent.setProjections(Shared.generateMonthProjection(period, range, pspComponent.getAmount()));
        }
        component.add(pspComponent);
    }
    //GESP -> $BE6
    //nomina component: GESP_BASE -> $BE6
    public void gesp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO gespBaseComponent = componentMap.get("GESP_BASE");
        double gespBase = gespBaseComponent == null ? 0 : gespBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO gespComponent = new PaymentComponentDTO();
        gespComponent.setPaymentComponent("GESP");
        gespComponent.setAmount(BigDecimal.valueOf(gespBase));
        gespComponent.setProjections(Shared.generateMonthProjection(period, range, gespComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (gespBaseComponent != null && gespBaseComponent.getProjections() != null) {
            for (MonthProjection projection : gespBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double gespPerMonth = gespBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gespPerMonth));
                projections.add(monthProjection);
            }
            gespComponent.setProjections(projections);
        } else {
            gespComponent.setAmount(BigDecimal.valueOf(0));
            gespComponent.setProjections(Shared.generateMonthProjection(period, range, gespComponent.getAmount()));
        }
        component.add(gespComponent);
    }
    //IPD -> $BF6
    //nomina component: IPD_BASE -> $BF6
    public void ipd(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO ipdBaseComponent = componentMap.get("IPD_BASE");
        double ipdBase = ipdBaseComponent == null ? 0 : ipdBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO ipdComponent = new PaymentComponentDTO();
        ipdComponent.setPaymentComponent("IPD");
        ipdComponent.setAmount(BigDecimal.valueOf(ipdBase));
        ipdComponent.setProjections(Shared.generateMonthProjection(period, range, ipdComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (ipdBaseComponent != null && ipdBaseComponent.getProjections() != null) {
            for (MonthProjection projection : ipdBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double ipdPerMonth = ipdBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(ipdPerMonth));
                projections.add(monthProjection);
            }
            ipdComponent.setProjections(projections);
        } else {
            ipdComponent.setAmount(BigDecimal.valueOf(0));
            ipdComponent.setProjections(Shared.generateMonthProjection(period, range, ipdComponent.getAmount()));
        }
        component.add(ipdComponent);
    }
    //Fiesta -> $BG6
    //nomina component: FIESTA_BASE -> $BG6
    public void fiesta(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO fiestaBaseComponent = componentMap.get("FIESTA_BASE");
        double fiestaBase = fiestaBaseComponent == null ? 0 : fiestaBaseComponent.getAmount().doubleValue();
        PaymentComponentDTO fiestaComponent = new PaymentComponentDTO();
        fiestaComponent.setPaymentComponent("FIESTA");
        fiestaComponent.setAmount(BigDecimal.valueOf(fiestaBase));
        fiestaComponent.setProjections(Shared.generateMonthProjection(period, range, fiestaComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (fiestaBaseComponent != null && fiestaBaseComponent.getProjections() != null) {
            for (MonthProjection projection : fiestaBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double fiestaPerMonth = fiestaBaseComponent.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(fiestaPerMonth));
                projections.add(monthProjection);
            }
            fiestaComponent.setProjections(projections);
        } else {
            fiestaComponent.setAmount(BigDecimal.valueOf(0));
            fiestaComponent.setProjections(Shared.generateMonthProjection(period, range, fiestaComponent.getAmount()));
        }
        component.add(fiestaComponent);
    }
}
