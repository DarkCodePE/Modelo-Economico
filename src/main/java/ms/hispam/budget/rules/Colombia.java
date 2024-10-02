package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.util.Shared;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
@Slf4j(topic = "Colombia")
public class Colombia {
    private static final Integer SALARY_MIN = 1160000;
    private static final Integer SALARY_MIN_PRA = 1424115;
    private static final Integer SALARY_MIN_INC_PLA_DIR = (SALARY_MIN*112)/100;
    private static final Integer SALARY_MIN_PRA_INC_PLA_DIR = (SALARY_MIN_PRA*112)/100;
    static final String TYPEMONTH="yyyyMM";
    private static final String PC938001 = "PC938001";
    private static final String PC938005 = "PC938005";
    private static final String PC938012 = "PC938012";
    private static final String PC938003 = "PC938003";
    private static final String SALARY = "SALARY";
    private static final String TEMPORAL_SALARY = "TEMPORAL_SALARY";
    private static final String SALARY_PRA = "SALARY_PRA";
    private static final String COMMISSION = "COMMISSION";
    private static final String COMMISSION_TEMP = "COMMISSION_TEMP";
    private Map<String, Object> createSalaryTemporalComponent(double pc938001Component, double pc938005Component, String classEmployee, String period, Integer range, String category, String componentName) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent(componentName);
        String salaryType = "BASE";
        if (classEmployee.equals(category)) {
            if (pc938001Component == 0.0) {
                salaryType = "INTEGRAL";
                salaryComponent.setAmount(BigDecimal.valueOf(pc938005Component));
            } else {
                salaryComponent.setAmount(BigDecimal.valueOf(pc938001Component));
            }
        } else {
            salaryComponent.setAmount(BigDecimal.ZERO);
        }
        salaryComponent.setSalaryType(salaryType);
        salaryComponent.setProjections(Shared.generateMonthProjectionV2(period,range,salaryComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("salaryComponent", salaryComponent);
        result.put("salaryType", salaryType);
        return result;
    }
    private Map<String, Object> createSalaryComponent(PaymentComponentDTO pc938001Component, PaymentComponentDTO pc938005Component, String classEmployee, String period, Integer range, String category, String componentName) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent(componentName);
        String salaryType = "BASE";
        double baseSalary = pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue();
        double baseSalaryIntegral = pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue();
        if (classEmployee.equals(category)) {
            if (baseSalary == 0.0) {
                salaryType = "INTEGRAL";
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalaryIntegral));
            } else {
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalary));
            }
        } else {
            salaryComponent.setAmount(BigDecimal.ZERO);
        }
        salaryComponent.setSalaryType(salaryType);
        salaryComponent.setProjections(Shared.generateMonthProjectionV2(period,range,salaryComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("salaryComponent", salaryComponent);
        result.put("salaryType", salaryType);
        return result;
    }
    private Map<String, Object> createCommissionComponent(PaymentComponentDTO pc938003Component, PaymentComponentDTO pc938012Component, String classEmployee, String period, Integer range, Map<String, Double> cacheCommission, BigDecimal sumCommission) {
        PaymentComponentDTO commissionComponent = new PaymentComponentDTO();
        commissionComponent.setPaymentComponent("COMMISSION");
        double commission1 = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
        double commission2 = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
        double maxCommission = Math.max(commission1, commission2);
        //log.debug("maxCommission -> {}", maxCommission);
        //log.debug("period -> {}", period);
        //log.debug("sumCommission -> {}", sumCommission);
        //log.debug("period -> {}", period);
        //log.debug("cacheCommission -> {}", cacheCommission);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(nextPeriod) == null ? 0.0 : cacheCommission.get(nextPeriod));
        //log.debug("commission -> {}", commission);
        if (!classEmployee.equals("T")) {
            commissionComponent.setAmount(commission.multiply(BigDecimal.valueOf(maxCommission / sumCommission.doubleValue())));
            //commissionComponent.setAmount(commission);
        } else {
            commissionComponent.setAmount(BigDecimal.ZERO);
        }
        commissionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,commissionComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("commissionComponent", commissionComponent);
        return result;
    }
    private Map<String, Object> createTemporalCommissionComponent(PaymentComponentDTO pc938003Component, PaymentComponentDTO pc938012Component, String classEmployee, String period, Integer range, Map<String, Double> cacheCommission, BigDecimal sumCommission) {
        PaymentComponentDTO commissionComponent = new PaymentComponentDTO();
        commissionComponent.setPaymentComponent(COMMISSION_TEMP);
        double commission1 = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
        double commission2 = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
        double maxCommission = Math.max(commission1, commission2);
        ////log.debug("maxCommission -> {}", maxCommission);
        ////log.debug("period -> {}", period);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        period = yearMonth.format(formatter);
        BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(period) == null ? 0.0 : cacheCommission.get(period));
        if (!classEmployee.equals("T") && maxCommission != 0.0) {
            commissionComponent.setAmount(commission.multiply(BigDecimal.valueOf(maxCommission / sumCommission.doubleValue())));
        } else {
            commissionComponent.setAmount(BigDecimal.ZERO);
        }
        commissionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,commissionComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("commissionComponent", commissionComponent);
        return result;
    }
    private Map<String, Object> createSalaryComponent(PaymentComponentDTO pc938001Component, PaymentComponentDTO pc938005Component, String classEmployee, String period, Integer range, List<String> categories, String componentName) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent(componentName);
        String salaryType = "BASE";
        // Calcular el valor de THEORETICAL-SALARY a partir de PC960400 y PC960401
        double baseSalary = pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue();
        double baseSalaryIntegral = pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue();
        if (categories.contains(classEmployee)) {
            if (baseSalary == 0.0) {
                salaryType = "INTEGRAL";
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalaryIntegral));
            } else {
                salaryComponent.setAmount(BigDecimal.valueOf(baseSalary));
            }
        } else {
            salaryComponent.setAmount(BigDecimal.ZERO);
        }
        salaryComponent.setSalaryType(salaryType);
        salaryComponent.setProjections(Shared.generateMonthProjectionV2(period,range,salaryComponent.getAmount()));
        Map<String, Object> result = new HashMap<>();
        result.put("salaryComponent", salaryComponent);
        result.put("salaryType", salaryType);
        return result;
    }
    public String findCategory(String currentCategory) {
        Map<String, String> categoryTitleMap = new HashMap<>();
        categoryTitleMap.put("Joven Profesional", "JP");
        categoryTitleMap.put("Joven Talento", "JP");
        categoryTitleMap.put("Profesional Talento", "JP");
        if ("EMP".equals(currentCategory)) {
            String category = categoryTitleMap.get(currentCategory);
            return Objects.requireNonNullElse(category, "P");
        } else {
            return currentCategory;
        }
    }

    private ParametersDTO getParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .findFirst()
                .orElse(null);
    }
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
   private void createCommissionCache2(List<ParametersDTO> commissionList, Map<String, Double> cache, Integer projectionRange) {
       List<ParametersDTO> sortedCommissionList = new ArrayList<>(commissionList);
       sortedCommissionList.sort(Comparator.comparing(ParametersDTO::getPeriod));

       for (int i = 0; i < sortedCommissionList.size(); i++) {
           ParametersDTO commission = sortedCommissionList.get(i);
           String period = commission.getPeriod();
           String year = period.substring(0, 4);
           String month = period.substring(4, 6);
           double value = commission.getValue();
           // Incrementar el mes antes de entrar al bucle
           int monthInt = Integer.parseInt(month);
           if (monthInt > 12) {
               monthInt = 1;
               year = String.valueOf(Integer.parseInt(year) + 1);
           }
           month = String.format("%02d", monthInt);

           value /= 12;
           int startMonth = Integer.parseInt(month);
           int endMonth = projectionRange;
           // log.debug("endMonth -> {}", endMonth);
           for (int m = startMonth; m <= endMonth; m++) {
               int yearOffset = (m - 1) / 12;
               int monthOffset = (m - 1) % 12 + 1;
               //log.debug("yearOffset -> {}", yearOffset);
               //log.debug("monthOffset -> {}", monthOffset);
               String cachePeriod = String.format("%04d%02d", Integer.parseInt(year) + yearOffset, monthOffset);
               cache.put(cachePeriod, value);
           }
       }
   }

    private void createCommissionCache(List<ParametersDTO> commissionList, String period, int range, Map<String, Double> cache) {
        // Genera todos los meses de la proyección y almacénalos en una lista.
        List<String> allMonths = new ArrayList<>();
        String currentYear = period.substring(0, 4);
        String currentMonth = period.substring(4, 6);
        for (int i = 0; i < range +1; i++) {
            allMonths.add(currentYear + currentMonth);
            int monthInt = Integer.parseInt(currentMonth);
            monthInt++;
            if (monthInt > 12) {
                monthInt = 1;
                int yearInt = Integer.parseInt(currentYear);
                yearInt++;
                currentYear = String.format("%04d", yearInt);
            }
            currentMonth = String.format("%02d", monthInt);
        }

        // Inicializa el mapa de caché con todos los meses de la proyección y un valor inicial de 0.
        for (String month : allMonths) {
            cache.put(month, 0.0);
        }

        // Crea una copia de la lista de comisiones antes de ordenarla.
        List<ParametersDTO> sortedCommissionList = new ArrayList<>(commissionList);
        sortedCommissionList.sort(Comparator.comparing(ParametersDTO::getPeriod));

        // Para cada comisión en la lista de comisiones:
        for (ParametersDTO commission : sortedCommissionList) {
            // Encuentra el índice del mes de la comisión en la lista de meses de la proyección.
            int index = allMonths.indexOf(commission.getPeriod());
            if (index != -1) {
                // Actualiza el valor en el mapa de caché para ese mes y todos los meses siguientes hasta que encuentres un nuevo índice de lista de comisiones que coincida.
                for (int i = index; i < allMonths.size(); i++) {
                    if (i < allMonths.size() - 1 && allMonths.get(i + 1).equals(commission.getPeriod())) {
                        break;
                    }
                    cache.put(allMonths.get(i), commission.getValue() / 12);
                }
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
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> legalSalaryMinList, List<ParametersDTO> revisionSalaryMinList, List<ParametersDTO> revisionSalaryMinEttList, List<ParametersDTO> legalSalaryIntegralMinList) {
        // Crear los mapas como variables locales
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> salaryIntegralMap = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> revisionSalaryMap = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> revisionSalaryMapEtt = new ConcurrentHashMap<>();
        Map<String, Double> cacheRevision = new ConcurrentHashMap<>();
        Map<String, Double> cacheEtt = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryIntegralMin = new ConcurrentHashMap<>();
        createCache(legalSalaryIntegralMinList, salaryIntegralMap, cacheSalaryIntegralMin, (parameter, mapParameter) -> {});
        createCache(revisionSalaryMinList, revisionSalaryMap, cacheRevision, (parameter, mapParameter) -> {});
        createCache(legalSalaryMinList, salaryMap, cacheSalaryMin, (parameter, mapParameter) -> {});
        createCache(revisionSalaryMinEttList, revisionSalaryMapEtt, cacheEtt, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        String category = findCategory(classEmployee);
        //log.debug("period -> {}", period);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, "P", SALARY).get("salaryComponent");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO salaryMinBase = salaryMap.get(nextPeriod);
        ParametersDTO salaryMinIntegralBase = salaryIntegralMap.get(nextPeriod);
        if (category.equals("P")){
            if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
                String salaryType = paymentComponentDTO.getSalaryType();
                double lastSalary = paymentComponentDTO.getAmount().doubleValue();
                double lastLegalBaseSalary = salaryMinBase != null ? salaryMinBase.getValue() : 0.0;
                double lastLegalIntegralSalary = salaryMinIntegralBase != null ? salaryMinIntegralBase.getValue() : 0.0;
                List<MonthProjection> projections = new ArrayList<>();
                if (salaryType.equals("INTEGRAL")) {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        //log.debug("projection.getMonth() -> {}", projection.getMonth());
                        //log.debug("cacheSalaryIntegralMin.get(projection.getMonth()) -> {}", cacheSalaryIntegralMin.get(projection.getMonth()));
                        ParametersDTO salaryIntegralMinMap = salaryIntegralMap.get(projection.getMonth());
                        double salaryIntegralMin;
                        if (salaryIntegralMinMap != null) {
                            salaryIntegralMin = salaryIntegralMinMap.getValue();
                            lastLegalIntegralSalary = salaryIntegralMin;
                        } else {
                            salaryIntegralMin = lastLegalIntegralSalary;
                        }
                        //log.debug("salaryIntegralMin -> {}", salaryIntegralMin);
                        double salaryTemporalProjection;
                        if (projection.getAmount().doubleValue() <= salaryIntegralMin) {
                            salaryTemporalProjection = salaryIntegralMin;
                            lastSalary = salaryTemporalProjection;
                        } else {
                            salaryTemporalProjection = lastSalary;
                        }
                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(projection.getMonth());
                        monthProjection.setAmount(BigDecimal.valueOf(salaryTemporalProjection));
                        //log.debug("monthProjection -> {}", monthProjection);
                        projections.add(monthProjection);
                    }
                } else {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        ParametersDTO salaryMapParameter = salaryMap.get(projection.getMonth());
                        double salaryMin;
                        if (salaryMapParameter != null) {
                            salaryMin = salaryMapParameter.getValue();
                            lastLegalBaseSalary = salaryMin;
                        } else {
                            salaryMin = lastLegalBaseSalary;
                        }
                        //("salaryMin -> {}", salaryMin);lastLegalBaseSalary
                        double salaryTemporalProjection;
                        if (projection.getAmount().doubleValue() <= salaryMin) {
                            salaryTemporalProjection = salaryMin;
                            lastSalary = salaryTemporalProjection;
                        } else {
                            salaryTemporalProjection = lastSalary;
                        }
                       MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(projection.getMonth());
                        monthProjection.setAmount(BigDecimal.valueOf(salaryTemporalProjection));
                        //log.debug("monthProjection -> {}", monthProjection);
                        projections.add(monthProjection);
                    }
                }
                paymentComponentDTO.setProjections(projections);
            }
        }else {
            paymentComponentDTO.setAmount(BigDecimal.ZERO);
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
        }
        component.add(paymentComponentDTO);
        //log.debug("component -> {}","salary");
    }

    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    public void temporalSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> legalSalaryMinList, List<ParametersDTO> revisionSalaryMinList, List<ParametersDTO> revisionSalaryMinEttList, List<ParametersDTO> legalSalaryIntegralMinList,  Map<String, Map<String, Object>> dataMapTemporal, String position){
        // Crear los mapas como variables locales
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
        createCache(legalSalaryMinList, salaryMap, cacheSalaryMin, (parameter, mapParameter) -> {});
        //next period
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO salaryMinBase = salaryMap.get(nextPeriod);
        double salaryMin = salaryMinBase != null ? salaryMinBase.getValue() : 0.0;
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        String category = findCategory(classEmployee);
        if (category.equals("T")){
            if (pc938001Component != null && pc938001Component.getAmount().doubleValue() != 0.0) {
                //double highestAmountSoFar = Math.max(pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue());
                PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryTemporalComponent(pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue(), classEmployee, period, range, category, TEMPORAL_SALARY).get("salaryComponent");
                if (paymentComponentDTO != null){

                    double lastTemporalSalary = paymentComponentDTO.getAmount().doubleValue();
                    double lastLegalBaseSalary = salaryMin;
                    List<MonthProjection> projections = new ArrayList<>();
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        ParametersDTO salaryMinMap = salaryMap.get(projection.getMonth());

                        double salaryMinProjection;
                        if(salaryMinMap != null){
                            salaryMinProjection = salaryMinMap.getValue();
                            lastLegalBaseSalary = salaryMinProjection;
                        }else{
                            salaryMinProjection = lastLegalBaseSalary;
                        }

                        double salaryTemporalProjection;
                        if(projection.getAmount().doubleValue() <= salaryMinProjection){
                            salaryTemporalProjection = salaryMinProjection;
                            lastTemporalSalary = salaryTemporalProjection;
                        }else{
                            salaryTemporalProjection = lastTemporalSalary;
                        }

                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(projection.getMonth());
                        monthProjection.setAmount(BigDecimal.valueOf(salaryTemporalProjection));
                        projections.add(monthProjection);
                    }
                    paymentComponentDTO.setProjections(projections);
                    component.add(paymentComponentDTO);
                }else {
                    PaymentComponentDTO paymentComponentDTO1 = new PaymentComponentDTO();
                    paymentComponentDTO1.setPaymentComponent(TEMPORAL_SALARY);
                    paymentComponentDTO1.setAmount(BigDecimal.ZERO);
                    paymentComponentDTO1.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO1.getAmount()));
                    component.add(paymentComponentDTO1);
                }
            }else {
                PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
                paymentComponentDTO.setPaymentComponent(TEMPORAL_SALARY);
                paymentComponentDTO.setAmount(BigDecimal.ZERO);
                paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
                component.add(paymentComponentDTO);
            }
        } else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent(TEMPORAL_SALARY);
            paymentComponentDTO.setAmount(BigDecimal.ZERO);
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        //log.debug("component -> {}", "temporalSalary");
    }
    public void salaryPra(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> legalSalaryMinList, List<ParametersDTO> salaryPraList){
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> salaryPraMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryPra = new ConcurrentHashMap<>();
        createCache(legalSalaryMinList, salaryMap, cacheSalaryMin, (parameter, mapParameter) -> {});
        createCache(salaryPraList, salaryPraMap, cacheSalaryPra, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        double highestAmountSoFarPra = Math.max(pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue());
        double highestAmountSoFarApr = Math.max(pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue());
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, Arrays.asList("PRA", "APR"), SALARY_PRA).get("salaryComponent");
        if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
            String salaryType = paymentComponentDTO.getSalaryType();
            if (salaryType.equals("BASE")) {
                if (classEmployee.equals("PRA")) {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        Double salaryPraMinMap = cacheSalaryPra.get(projection.getMonth());
                        double salaryPraMin = salaryPraMinMap != null ? salaryPraMinMap : 0.0;
                        if (salaryPraMin != 0.0){
                            if (projection.getAmount().doubleValue() <= salaryPraMin) {
                                highestAmountSoFarPra = Math.max(highestAmountSoFarPra, salaryPraMin);
                                projection.setAmount(BigDecimal.valueOf(salaryPraMin));
                            } else {
                                projection.setAmount(BigDecimal.valueOf(highestAmountSoFarPra));
                            }
                        }else{
                            projection.setAmount(BigDecimal.valueOf(highestAmountSoFarPra));
                        }
                    }
                }
                if (classEmployee.equals("APR")) {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        Double salaryMinMap = cacheSalaryMin.get(projection.getMonth());
                        double salaryMin = salaryMinMap != null ? salaryMinMap : 0.0;
                        if (salaryMin != 0.0){
                            if (projection.getAmount().doubleValue() <= salaryMin/2) {
                                highestAmountSoFarApr = Math.max(highestAmountSoFarApr, salaryMin/2);
                                projection.setAmount(BigDecimal.valueOf(salaryMin/2));
                            } else {
                                highestAmountSoFarApr = Math.max(highestAmountSoFarApr, salaryMin);
                                projection.setAmount(BigDecimal.valueOf(salaryMin));
                            }
                        }else{
                            projection.setAmount(BigDecimal.valueOf(highestAmountSoFarApr));
                        }
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
        // log.debug("component -> {}", "salaryPra");
    }

    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters,String period, Integer range, String classEmployee){
        // Obtén los componentes necesarios para el cálculo
        List<String> salaryComponents = Arrays.asList("SALARY", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> salaryComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

        // Itera sobre cada componente de salario
        for (String salaryComponentName : salaryComponents) {
            PaymentComponentDTO salaryComponent = componentMap.get(salaryComponentName);
            if (salaryComponent != null) {
                // Realiza el cálculo de revisión de salario para el componente actual
                calculateSalaryRevision(salaryComponent, parameters, period, range, salaryComponentName, classEmployee);
            } else {
                // Lanza una excepción si el componente de salario no se encuentra
                throw new RuntimeException("El componente de salario " + salaryComponentName + " no se encuentra en la lista de componentes.");
            }
        }
    }

    public void calculateSalaryRevision(PaymentComponentDTO paymentComponentDTO,List<ParametersDTO> parameters,String period, Integer range, String salaryComponentName, String classEmployee){
        String category = findCategory(classEmployee);
        // Asegúrate de que el período y el rango no son nulos antes de usarlos
        if (period == null || range == null) {
            throw new IllegalArgumentException("El período y el rango no pueden ser nulos.");
        }
        double differPercent=0.0;
        ParametersDTO revisionSalaryMin = getParametersById(parameters, 46);
        ParametersDTO revisionSalaryMinEtt = getParametersById(parameters, 45);

       if (paymentComponentDTO.getProjections() != null) {
           if (revisionSalaryMin != null) {
               if (category.equals("P") && salaryComponentName.equals("SALARY")) {
                   if (Boolean.TRUE.equals(revisionSalaryMin.getIsRetroactive())) {
                       int idxStart;
                       int idxEnd;
                       String[] periodRevisionSalary = revisionSalaryMin.getPeriodRetroactive().split("-");
                       idxStart = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                               .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
                       idxEnd = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                               .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary.length == 1 ? periodRevisionSalary[0] : periodRevisionSalary[1]);
                       AtomicReference<Double> salaryFirst = new AtomicReference<>(0.0);
                       AtomicReference<Double> salaryEnd = new AtomicReference<>(0.0);
                       salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                       salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                       differPercent = (salaryEnd.get()) / (salaryFirst.get()) - 1;
                   }
                   double percent = revisionSalaryMin.getValue() / 100;
                   int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                           .map(MonthProjection::getMonth).collect(Collectors.toList()), revisionSalaryMin.getPeriod());
                   if (idx != -1) {
                       for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                           double revisionSalaryAmount = 0;
                           double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                           paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                           if (paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(revisionSalaryMin.getPeriod())) {
                               // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                               if (differPercent > 0) {
                                   // 9%
                                   if (differPercent <= percent) {
                                       //log.debug("differPercent si es menor -> {}", differPercent);
                                       differPercent = percent - differPercent;
                                   } else {
                                       differPercent = 0;
                                   }
                               } else {
                                   differPercent = percent;
                               }
                               //log.debug("differPercent -> {}", differPercent);
                               revisionSalaryAmount = amount * (1 + (differPercent));
                               //log.debug("revisionSalaryAmount -> {}", revisionSalaryAmount);
                               paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                           }
                       }
                   }
               }
           }
           if (revisionSalaryMinEtt != null) {
               if (category.equals("T") && salaryComponentName.equals("TEMPORAL_SALARY")) {
                   if (Boolean.TRUE.equals(revisionSalaryMinEtt.getIsRetroactive())) {
                       int idxStart;
                       int idxEnd;
                       String[] periodRevisionSalary = revisionSalaryMinEtt.getPeriodRetroactive().split("-");
                       idxStart = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                               .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
                       idxEnd = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                               .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary.length == 1 ? periodRevisionSalary[0] : periodRevisionSalary[1]);
                       AtomicReference<Double> salaryFirst = new AtomicReference<>(0.0);
                       AtomicReference<Double> salaryEnd = new AtomicReference<>(0.0);
                       salaryFirst.set(paymentComponentDTO.getProjections().get(idxStart).getAmount().doubleValue());
                       salaryEnd.set(paymentComponentDTO.getProjections().get(idxEnd).getAmount().doubleValue());
                       differPercent = (salaryEnd.get()) / (salaryFirst.get()) - 1;
                   }
                   double percent = revisionSalaryMinEtt.getValue() / 100;
                   int idx = Shared.getIndex(paymentComponentDTO.getProjections().stream()
                           .map(MonthProjection::getMonth).collect(Collectors.toList()), revisionSalaryMinEtt.getPeriod());
                   if (idx != -1) {
                       for (int i = idx; i < paymentComponentDTO.getProjections().size(); i++) {
                           double revisionSalaryAmount;
                           double amount = i == 0 ? paymentComponentDTO.getProjections().get(i).getAmount().doubleValue() : paymentComponentDTO.getProjections().get(i - 1).getAmount().doubleValue();
                           paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(amount));
                           if (paymentComponentDTO.getProjections().get(i).getMonth().equalsIgnoreCase(revisionSalaryMinEtt.getPeriod())) {
                               // R13 * ( 1 +SI(Q13 / P13 - 1 > 0;SI(Q13 / P13 - 1 <= S$8;S$8 - ( Q13 / P13 - 1 );0);S$8 ) ))
                               if (differPercent > 0) {
                                   // 9%
                                   if (differPercent <= percent) {
                                       differPercent = percent - differPercent;
                                   } else {
                                       differPercent = 0;
                                   }
                               } else {
                                   differPercent = percent;
                               }
                               //log.debug("differPercent -> {}", differPercent);
                               revisionSalaryAmount = amount * (1 + (differPercent));
                               //log.debug("revisionSalaryAmount -> {}", revisionSalaryAmount);
                               paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                           }
                       }
                   }
               }
           }
        }
    }
    public void surcharges(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteHHEE) {
        Map<String, ParametersDTO> surchargesMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSurcharges = new ConcurrentHashMap<>();
        createCache(factorAjusteHHEE, surchargesMap, cacheSurcharges, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO surchargesComponentBase = componentMap.get("SURCHARGES_BASE");
        //log.info("surchargesComponentBase -> {}", surchargesComponentBase);
        PaymentComponentDTO surchargesComponent = new PaymentComponentDTO();
        surchargesComponent.setPaymentComponent("SURCHARGES");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO surcharges = surchargesMap.get(nextPeriod);
        double surchargesValue = surcharges != null ? surcharges.getValue() / 100 : 0.0;
        surchargesComponent.setAmount(BigDecimal.valueOf(surchargesComponent.getAmount().doubleValue() * surchargesValue));
        List<MonthProjection> projections = new ArrayList<>();
        double lastSurchargesParm = surchargesValue;
        double lastSurcharges = surchargesComponent.getAmount().doubleValue();
        if (surchargesComponentBase != null && surchargesComponentBase.getProjections() != null) {
            for (MonthProjection projection : surchargesComponentBase.getProjections()) {
                ParametersDTO surchargesParam = surchargesMap.get(projection.getMonth());
                double surchargesParamValue;
                if (surchargesParam != null) {
                    surchargesParamValue = surchargesParam.getValue() / 100;
                    lastSurchargesParm = surchargesParamValue;
                } else {
                    surchargesParamValue = lastSurchargesParm;
                }

               /* BigDecimal surchargesAmount;
                if (surchargesParam != null) {
                    double surchargesParamValueValid = surchargesParamValue != 0.0 ? surchargesParamValue : 1.0;
                    surchargesAmount = BigDecimal.valueOf((projection.getAmount().doubleValue() / 12) * surchargesParamValueValid);
                    lastSurcharges = surchargesAmount.doubleValue();
                    surchargesAmount = BigDecimal.valueOf(lastSurcharges);
                }*/
                //double surchargesParamValueValid = surchargesParamValue != 0.0 ? surchargesParamValue : 1.0;
                BigDecimal surchargesAmount = BigDecimal.valueOf((projection.getAmount().doubleValue() / 12) * (1 + surchargesParamValue));
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(projection.getMonth());
                monthProjection.setAmount(surchargesAmount);
                projections.add(monthProjection);
            }
            surchargesComponent.setProjections(projections);
        }else {
            surchargesComponent.setAmount(BigDecimal.valueOf(0));
            surchargesComponent.setProjections(Shared.generateMonthProjectionV2(period,range,surchargesComponent.getAmount()));
        }
        component.add(surchargesComponent);
        //log.debug("component -> {}", "surcharges");
    }
    public void overtime(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> factorAjusteHHEE) {
        Map<String, ParametersDTO> overtimeMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheOvertime = new ConcurrentHashMap<>();
        createCache(factorAjusteHHEE, overtimeMap, cacheOvertime, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO overtimeComponentBase = componentMap.get("HHEE_BASE");
        //log.info("overtimeComponentBase -> {}", overtimeComponentBase);
        PaymentComponentDTO overtimeComponent = new PaymentComponentDTO();
        overtimeComponent.setPaymentComponent("HHEE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO overtime = overtimeMap.get(nextPeriod);
        double overtimeValue = overtime != null ? overtime.getValue() : 0.0;
        overtimeComponent.setAmount(BigDecimal.valueOf(overtimeComponent.getAmount().doubleValue() * overtimeValue));
        List<MonthProjection> projections = new ArrayList<>();
        double lastOvertimeParam = overtimeValue;
        double lastOvertime = overtimeComponent.getAmount().doubleValue();
        if (overtimeComponentBase != null && overtimeComponentBase.getProjections() != null) {
            for (MonthProjection projection : overtimeComponentBase.getProjections()) {
                ParametersDTO overtimeParam = overtimeMap.get(projection.getMonth());
                double overtimeParamValue;
                if (overtimeParam != null) {
                    overtimeParamValue = overtimeParam.getValue() / 100;
                    lastOvertimeParam = overtimeParamValue;
                } else {
                    overtimeParamValue = lastOvertimeParam;
                }
                /*BigDecimal overtimeAmount;
                if (overtimeParam != null) {
                    double overtimeParamValueValid = overtimeParamValue != 0.0 ? overtimeParamValue : 1.0;
                    overtimeAmount = BigDecimal.valueOf((projection.getAmount().doubleValue() / 12) * overtimeParamValueValid);
                    lastOvertime = overtimeAmount.doubleValue();
                } else {
                    overtimeAmount = BigDecimal.valueOf(lastOvertime);
                }*/
              /*  BigDecimal overtimeAmount;
                if (overtimeParamValue != 0.0) {
                    overtimeAmount = BigDecimal.valueOf((projection.getAmount().doubleValue() / 12) * overtimeParamValue);
                    lastOvertime = overtimeAmount.doubleValue();
                } else {
                    overtimeAmount = BigDecimal.valueOf(lastOvertime);
                }*/
                //double overtimeParamValueValid = overtimeParamValue != 0.0 ? overtimeParamValue : 1.0;
                BigDecimal overtimeAmount = BigDecimal.valueOf((projection.getAmount().doubleValue() / 12) * (1 + overtimeParamValue));
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(projection.getMonth());
                monthProjection.setAmount(overtimeAmount);
                projections.add(monthProjection);
            }
            overtimeComponent.setProjections(projections);
            component.add(overtimeComponent);
        }else {
            overtimeComponent.setAmount(BigDecimal.valueOf(0));
            overtimeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,overtimeComponent.getAmount()));
            component.add(overtimeComponent);
        }
        //log.debug("component -> {}", "overtime");
    }
    public void commission(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission, List<ParametersDTO> commissionList, List<String> excludedPositionsBC, String position) {
        //commission param
       /* boolean isExcluded = excludedPositionsBC.stream()
                .anyMatch(excludedPosition -> excludedPosition.equals(position));*/
        String category = findCategory(classEmployee);
        if (!category.equals("T")) {
            Map<String, ParametersDTO> commissionMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheCommission = new ConcurrentHashMap<>();
            createCache(commissionList, commissionMap, cacheCommission, (parameter, mapParameter) -> {});
            //log.debug("cacheCommission -> {}", cacheCommission);
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            PaymentComponentDTO pc938003Component = componentMap.get(PC938003);
            PaymentComponentDTO pc938012Component = componentMap.get(PC938012);
            //log.debug("pc938003Component -> {}", pc938003Component);
            //log.debug("pc938012Component -> {}", pc938012Component);
            //String category = findCategory(classEmployee);
            double pc938003Amount = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
            double pc938012Amount = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
            //Sumatoria de las comisiones
            double maxCommission = Math.max(pc938003Amount, pc938012Amount);
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent(COMMISSION);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            double commission = commissionMap.get(nextPeriod) == null ? 0.0 : commissionMap.get(nextPeriod).getValue() / 12;
            double commissionProBase = maxCommission / sumCommission.doubleValue();
            paymentComponentDTO.setAmount(BigDecimal.valueOf(commission * commissionProBase));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            //log.debug("paymentComponentDTO -> {}", paymentComponentDTO);
            List<MonthProjection> projections = new ArrayList<>();
            double lastCommission = commission;
            for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                //SI($I5<>"T";//AJ$10/12*($M5/SUMA($M$4:$M$15));0)
                ParametersDTO commissionParam = commissionMap.get(projection.getMonth());
                double cacheCommissionValue;
                if (commissionParam != null) {
                    cacheCommissionValue = commissionParam.getValue() / 12;
                    lastCommission = cacheCommissionValue;
                } else {
                    cacheCommissionValue = lastCommission;
                }
                //AP$9/12*($M8/SUMA($M$4:$M$15)),0)
                double commissionPro = maxCommission / sumCommission.doubleValue();
                double commissionProj = cacheCommissionValue * commissionPro;
                projection.setMonth(projection.getMonth());
                projection.setAmount(BigDecimal.valueOf(commissionProj));
                projections.add(projection);
            }
            paymentComponentDTO.setProjections(projections);
            component.add(paymentComponentDTO);
        }else {
            PaymentComponentDTO commissionComponent = new PaymentComponentDTO();
            commissionComponent.setPaymentComponent(COMMISSION);
            commissionComponent.setAmount(BigDecimal.valueOf(0));
            commissionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,commissionComponent.getAmount()));
            component.add(commissionComponent);
        }
        //log.debug("component -> {}", "commission");
    }

    public void commission2(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission, List<ParametersDTO> commissionList, List<String> excludedPositionsBC, String position) {
        //commission param
       /* boolean isExcluded = excludedPositionsBC.stream()
                .anyMatch(excludedPosition -> excludedPosition.equals(position));*/
        String category = findCategory(classEmployee);
        if (!category.equals("T") ) {
            //log.debug("sumCommission -> {}", sumCommission);
            Map<String, Double> cacheCommission = new ConcurrentHashMap<>();
            createCommissionCache(commissionList, period, range, cacheCommission);
            //log.debug("cacheCommission -> {}", cacheCommission);
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO pc938003Component = componentMap.get(PC938003);
            PaymentComponentDTO pc938012Component = componentMap.get(PC938012);
            //log.debug("pc938003Component -> {}", pc938003Component);
            //log.debug("pc938012Component -> {}", pc938012Component);
            //String category = findCategory(classEmployee);
            double pc938003Amount = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
            double pc938012Amount = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
            double maxCommission = Math.max(pc938003Amount, pc938012Amount);
            PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO) createCommissionComponent(pc938003Component, pc938012Component, category, period, range, cacheCommission, sumCommission).get("commissionComponent");
            //log.debug("paymentComponentDTO -> {}", paymentComponentDTO);
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                //SI($I5<>"T";//AJ$10/12*($M5/SUMA($M$4:$M$15));0)
                // log.debug("projection.getMonth() -> {}", projection.getMonth());
                BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(projection.getMonth()) == null ? 0.0 : cacheCommission.get(projection.getMonth()));
                projection.setMonth(projection.getMonth());
                if (commission.doubleValue() != 0.0){
                    if (maxCommission != 0.0) {
                        BigDecimal result = commission.multiply(BigDecimal.valueOf(maxCommission / sumCommission.doubleValue()));
                        projection.setAmount(result);
                    } else {
                        projection.setAmount(BigDecimal.valueOf(0));
                    }
                }else{
                    projection.setAmount(BigDecimal.valueOf(0));
                }
                projections.add(projection);
            }
            paymentComponentDTO.setProjections(projections);
            component.add(paymentComponentDTO);
        }else {
            PaymentComponentDTO commissionComponent = new PaymentComponentDTO();
            commissionComponent.setPaymentComponent(COMMISSION);
            commissionComponent.setAmount(BigDecimal.valueOf(0));
            commissionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,commissionComponent.getAmount()));
            component.add(commissionComponent);
        }
        //log.debug("component -> {}", "commission");
    }

    public void prodMonthPrime(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtener los componentes necesarios para el cálculo
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        //log.debug("surcharges -> {}", surcharges);
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Calcular la suma de los componentes
        double totalbase = salary + overtime + surcharges + commission;
        // Calcular la provisión mensual de la prima
        BigDecimal monthlyProvisionBase = BigDecimal.valueOf(totalbase / 12);
        // Crear un nuevo PaymentComponentDTO para la prima mensual
        PaymentComponentDTO monthlyPrimeComponent = new PaymentComponentDTO();
        monthlyPrimeComponent.setPaymentComponent("PRIMA MENSUAL");
        monthlyPrimeComponent.setAmount(monthlyProvisionBase);
        if (salaryComponent != null) {
            if (category.equals("P") &&  salaryComponent.getSalaryType().equals("BASE")) {
                // Calcular la prima mensual para cada proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = Stream.of(salaryComponent, overtimeComponent, surchargesComponent, commissionComponent)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calcular la provisión mensual de la prima
                    BigDecimal monthlyProvision = BigDecimal.valueOf(totalAmount.doubleValue() / 12);

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(monthlyProvision);
                    projections.add(projection);
                }
                monthlyPrimeComponent.setProjections(projections);
            } else {
                monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
                monthlyPrimeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,monthlyPrimeComponent.getAmount()));
            }
        }else {
            monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
            monthlyPrimeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,monthlyPrimeComponent.getAmount()));
        }
        component.add(monthlyPrimeComponent);
       // log.debug("component -> {}", "prodMonthPrime");
    }

    public void consolidatedVacation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> consolidatedVacationList) {
        Map<String, ParametersDTO> consolidatedVacationMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheConsolidatedVacation = new ConcurrentHashMap<>();
        createCache(consolidatedVacationList, consolidatedVacationMap, cacheConsolidatedVacation, (parameter, mapParameter) -> {});
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> vacationComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        //log.info("vacationComponents -> {}", component);
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        //log.info("componentMap -> {}", componentMap);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        if (category.equals("P")){
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            BigDecimal salary = BigDecimal.valueOf(salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue());
            BigDecimal overtime = BigDecimal.valueOf(overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue());
            BigDecimal surcharges = BigDecimal.valueOf(surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue());
            BigDecimal commission = BigDecimal.valueOf(commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue());
            // Crear un nuevo PaymentComponentDTO para el Consolidado de Vacaciones

            PaymentComponentDTO vacationComponent = new PaymentComponentDTO();
            vacationComponent.setPaymentComponent("CONSOLIDADO_VACACIONES");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            //double totalAmountBase = salary + overtime + surcharges + commission;
            ParametersDTO consolidateVacationBase = consolidatedVacationMap.get(nextPeriod);
            double consolidateVacationAmountBase = consolidateVacationBase != null ? consolidateVacationBase.getValue() / 12 : 0.0;
            BigDecimal totalAmountBase = BigDecimal.valueOf((salary.doubleValue() + overtime.doubleValue() + surcharges.doubleValue() + commission.doubleValue())/30);
            BigDecimal result = BigDecimal.valueOf(totalAmountBase.doubleValue() * consolidateVacationAmountBase);
            vacationComponent.setAmount(result);
            // Calcular el Consolidado de Vacaciones para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            //last vacation amount
            double lastVacationAmount = vacationComponent.getAmount().doubleValue();
            for (MonthProjection primeProjection : componentMap.get("SALARY").getProjections()) {
                //CALCULAR EL TOTAL DE LOS COMPONENTES
                BigDecimal totalAmount = vacationComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                //BUSCAR EL VALOR DEL CONSOLIDADO DE VACACIONES
                ParametersDTO consolidateVacation = consolidatedVacationMap.get(primeProjection.getMonth());
                double  consolidateVacationAmount;
                if (consolidateVacation != null){
                    consolidateVacationAmount = consolidateVacation.getValue() / 12;
                    lastVacationAmount = consolidateVacationAmount;
                }else {
                    consolidateVacationAmount = lastVacationAmount;
                }
                // Calcular el costo del Consolidado de Vacaciones
                BigDecimal vacationCost = BigDecimal.valueOf((totalAmount.doubleValue() / 30) * consolidateVacationAmount);
              /*  if (consolidateVacation != null){
                    vacationCost = BigDecimal.valueOf((totalAmount.doubleValue() / 30) * consolidateVacationAmount);
                    lastVacationAmount = vacationCost.doubleValue();
                }else {
                    vacationCost = BigDecimal.valueOf(lastVacationAmount);
                }*/
                //log.debug("vacationCost -> {}", vacationCost);
                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(vacationCost);
                projections.add(projection);
            }
            vacationComponent.setProjections(projections);
            component.add(vacationComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_VACACIONES");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        //log.debug("component -> {}", "consolidatedVacation");
    }
    public void consolidatedSeverance(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        List<String> severanceComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION","SALARY_PRA","TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> severanceComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (category.equals("P") && salaryComponent.getSalaryType().equals("BASE")) {
            // Obtén los componentes necesarios para el cálculo
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Consolidado de Cesantías
            PaymentComponentDTO severanceComponent = new PaymentComponentDTO();
            severanceComponent.setPaymentComponent("CONSOLIDADO_CESANTIAS");
            double salaryBase = salary + overtime + surcharges + commission;
            BigDecimal totalAmountBase = BigDecimal.valueOf(salaryBase / 12);
            severanceComponent.setAmount(totalAmountBase);
            // Calcular el Consolidado de Cesantías para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = severanceComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el costo del Consolidado de Cesantías
                BigDecimal severanceCost = totalAmount.divide(BigDecimal.valueOf(12), BigDecimal.ROUND_HALF_UP);

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(severanceCost);
                projections.add(projection);
            }
            severanceComponent.setProjections(projections);
            component.add(severanceComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_CESANTIAS");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        //log.debug("component -> {}", "consolidatedSeverance");
    }
    public void consolidatedSeveranceInterest(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        Map<String, PaymentComponentDTO> componentMapBase = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMapBase.get(SALARY);
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMapBase.get(SALARY_PRA);
        }
        // Obtén el componente necesario para el cálculo
        PaymentComponentDTO severanceComponent = componentMapBase.get("CONSOLIDADO_CESANTIAS");
        if (!category.equals("T") && salaryComponent.getSalaryType().equals("BASE")) {

            // Crear un nuevo PaymentComponentDTO para el Consolidado de Intereses de Cesantías
            PaymentComponentDTO severanceInterestComponent = new PaymentComponentDTO();
            severanceInterestComponent.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS");
            severanceInterestComponent.setAmount(BigDecimal.valueOf(severanceComponent.getAmount().doubleValue() * 0.12));
            // Calcular el Consolidado de Intereses de Cesantías para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : severanceComponent.getProjections()) {
                BigDecimal severanceAmount = primeProjection.getAmount();

                // Calcular el 12% del Consolidado de Cesantías
                BigDecimal severanceInterest = severanceAmount.multiply(BigDecimal.valueOf(0.12));

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(severanceInterest);
                projections.add(projection);
            }
            severanceInterestComponent.setProjections(projections);
            component.add(severanceInterestComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        //log.debug("component -> {}", "consolidatedSeveranceInterest");
    }

    public void transportSubsidy(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, String classEmployee, String period, Integer range, List<ParametersDTO> subsidyMinList, String code) {
        //log.info("code -> {}", code);
        String category = findCategory(classEmployee);
        //salarymap
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
        Map<String, ParametersDTO>  subsidyMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSubsidy = new ConcurrentHashMap<>();
        createCache(subsidyMinList, subsidyMap, cacheSubsidy,  (parameter, mapParameter) -> {});
        createCache(salaryList, salaryMap, cacheSalary,  (parameter, mapParameter) -> {});
        // Obtén los componentes necesarios para el cálculo
        List<String> subsidyComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> subsidyComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Subsidio de Transporte
        PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
        transportSubsidyComponent.setPaymentComponent("SUBSIDIO_TRANSPORTE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO subsidyMinNext = subsidyMap.get(nextPeriod);
        ParametersDTO salaryMinNext = salaryMap.get(nextPeriod);
        double salaryMinNextValue = salaryMinNext != null ? salaryMinNext.getValue() : 0.0;
        double subsidyMinNextValue = subsidyMinNext != null ? subsidyMinNext.getValue() : 0.0;
        double totalAmountBase = salary + overtime + surcharges + commission;
        transportSubsidyComponent.setAmount(BigDecimal.valueOf(totalAmountBase < 2 * salaryMinNextValue ? subsidyMinNextValue : 0));
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        BigDecimal lastValidSubsidyParam = transportSubsidyComponent.getAmount();
        BigDecimal lastValidSalaryValue = BigDecimal.valueOf(salaryMinNextValue);
        BigDecimal lastValidSubsidyValue = BigDecimal.valueOf(subsidyMinNextValue);
        if (category.equals("P") && salaryType.equals("BASE")) {
            // Calcular el Subsidio de Transporte para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                //(surcharges/12) + (commission/12)
                BigDecimal totalAmount = subsidyComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                //log.info("totalAmount -> {}, month -> {}", totalAmount, primeProjection.getMonth());
                ParametersDTO salaryMinInternalParam = salaryMap.get(primeProjection.getMonth());
                double salaryMinInternalValue;
                if (salaryMinInternalParam != null) {
                    salaryMinInternalValue = salaryMinInternalParam.getValue();
                    lastValidSalaryValue = BigDecimal.valueOf(salaryMinInternalValue);
                } else {
                    salaryMinInternalValue = lastValidSalaryValue.doubleValue();
                }
                //log salary and month
                //log.info("salaryMinInternalValue -> {}, month -> {}", salaryMinInternalValue, primeProjection.getMonth());
                ParametersDTO subsidyMinInternalParam = subsidyMap.get(primeProjection.getMonth());
                double subsidyMinInternalValue;
                if (subsidyMinInternalParam != null) {
                    subsidyMinInternalValue = subsidyMinInternalParam.getValue();
                    lastValidSubsidyParam = BigDecimal.valueOf(subsidyMinInternalValue);
                } else {
                    subsidyMinInternalValue = lastValidSubsidyParam.doubleValue();
                }
                // Calcular el costo del Subsidio de Transporte
                BigDecimal transportSubsidyCost;
                if (totalAmount.doubleValue() < 2 * salaryMinInternalValue) {
                    transportSubsidyCost = BigDecimal.valueOf(subsidyMinInternalValue);
                }else {
                    transportSubsidyCost = BigDecimal.valueOf(0);
                }

                //log total and month
                //log.info("totalAmount -> {}, month -> {}", totalAmount, primeProjection.getMonth());
                //log.info("transportSubsidyCost -> {}, month -> {}", transportSubsidyCost, primeProjection.getMonth());
                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(transportSubsidyCost);
                projections.add(projection);
            }
            transportSubsidyComponent.setProjections(projections);
        }else {
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjectionV2(period,range,transportSubsidyComponent.getAmount()));
        }
        component.add(transportSubsidyComponent);
        log.debug("component -> {}", "transportSubsidy");
    }
    public void contributionBox(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> boxComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> boxComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Aporte a la Caja
        PaymentComponentDTO boxContributionComponent = new PaymentComponentDTO();
        boxContributionComponent.setPaymentComponent("APORTE_CAJA");
        double totalAmountBase = salary + overtime + surcharges + commission;
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        boxContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") ? totalAmountBase * 0.04 : totalAmountBase * 0.70 * 0.04));
        if (category.equals("P")) {
            // Calcular el Aporte a la Caja para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = boxComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el Aporte a la Caja
                BigDecimal boxContribution;
                if (salaryComponent.getSalaryType().equals("BASE")) {
                    boxContribution = totalAmount.multiply(BigDecimal.valueOf(0.04));
                } else {
                    boxContribution = totalAmount.multiply(BigDecimal.valueOf(0.70)).multiply(BigDecimal.valueOf(0.04));
                }

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(boxContribution);
                projections.add(projection);
            }

            boxContributionComponent.setProjections(projections);
        }else {
            boxContributionComponent.setAmount(BigDecimal.valueOf(0));
            boxContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,boxContributionComponent.getAmount()));
        }
        component.add(boxContributionComponent);
        //log.debug("component -> {}", "contributionBox");
    }
    public void companyHealthContribution(List<PaymentComponentDTO> component, String classEmployee,  List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (!category.equals("T")) {
            HashMap<String, ParametersDTO> legalSalaryMinMap = new HashMap<>();
            HashMap<String, Double> cacheLegalSalaryMin = new HashMap<>();
            createCache(parameters, legalSalaryMinMap, cacheLegalSalaryMin, (parameter, mapParameter) -> {
            });
            String salaryCategory = "SALARY";
            if (category.equals("APR") || category.equals("PRA")) {
                salaryCategory = "SALARY_PRA";
            }
            List<String> healthComponents = Arrays.asList(salaryCategory, "HHEE", "SURCHARGES", "COMMISSION");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> healthComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get(salaryCategory);
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Aporte Salud Empresa
            PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
            healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA");
            double totalAmountBase = salary + overtime + surcharges + commission;
            //log.debug("totalAmountBase -> {}", totalAmountBase);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO legalSalaryMin = salaryComponent != null ? legalSalaryMinMap.get(nextPeriod) : null;
            double legalSalaryMinInternalBase = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
            BigDecimal healthContributionBase;
            if (category.equals("APR") || category.equals("PRA")) {
                healthContributionBase = BigDecimal.valueOf(totalAmountBase * 0.04);
            } else if (category.equals("P")) {
                if (salaryType.equals("BASE")) {
                    if (totalAmountBase > 25 * legalSalaryMinInternalBase) {
                        healthContributionBase = BigDecimal.valueOf(25 * legalSalaryMinInternalBase * 0.085);
                    } else if (totalAmountBase > 10 * legalSalaryMinInternalBase) {
                        healthContributionBase = BigDecimal.valueOf(totalAmountBase * 0.085);
                    } else {
                        healthContributionBase = BigDecimal.ZERO;
                    }
                } else { // salaryType is INTEGRAL
                    BigDecimal seventyPercentTotal = BigDecimal.valueOf(totalAmountBase * 0.70);
                    double seventyPercentTotalDouble = seventyPercentTotal.doubleValue();
                    if (seventyPercentTotalDouble > 25 * legalSalaryMinInternalBase) {
                        //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                        healthContributionBase = BigDecimal.valueOf(25 * legalSalaryMinInternalBase * 0.085);
                    } else {
                        //c = seventyPercentTotal.multiply(BigDecimal.valueOf(0.085));
                        healthContributionBase = BigDecimal.valueOf(seventyPercentTotalDouble * 0.085);
                    }
                }
            } else {
                healthContributionBase = BigDecimal.ZERO;
            }
            healthContributionComponent.setAmount(healthContributionBase);
            // Calcular el Aporte Salud Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                double lastValidSalaryValue = legalSalaryMinInternalBase;
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = healthComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    ParametersDTO legalSalaryMinInternalParam = legalSalaryMinMap.get(primeProjection.getMonth());
                    double legalSalaryMinInternalValue;
                    if (legalSalaryMinInternalParam != null) {
                        legalSalaryMinInternalValue = legalSalaryMinInternalParam.getValue();
                        lastValidSalaryValue = legalSalaryMinInternalValue;
                    } else {
                        legalSalaryMinInternalValue = lastValidSalaryValue;
                    }
                    // Calcular el Aporte Salud Empresa
                    BigDecimal healthContribution;
                    if (category.equals("APR") || category.equals("PRA")) {
                        healthContribution = totalAmount.multiply(BigDecimal.valueOf(0.04));
                    } else if (category.equals("P")) {
                        if (salaryComponent.getSalaryType().equals("BASE")) {
                            if (totalAmount.doubleValue() > 25 * legalSalaryMinInternalValue) {
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternalValue * 0.085);
                            } else if (totalAmount.doubleValue() > 10 * legalSalaryMinInternalValue) {
                                healthContribution = BigDecimal.valueOf(totalAmount.doubleValue() * 0.085);
                            } else {
                                healthContribution = BigDecimal.ZERO;
                            }
                        } else { // salaryType is INTEGRAL
                            BigDecimal seventyPercentTotal = totalAmount.multiply(BigDecimal.valueOf(0.70));
                            double seventyPercentTotalDouble = seventyPercentTotal.doubleValue();
                            if (seventyPercentTotalDouble > 25 * legalSalaryMinInternalValue) {
                                //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternalValue * 0.085);
                            } else {
                                //healthContribution = seventyPercentTotal.multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(seventyPercentTotalDouble * 0.085);
                            }
                        }
                    } else {
                        healthContribution = BigDecimal.ZERO;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(healthContribution);
                    projections.add(projection);
                }
                healthContributionComponent.setProjections(projections);
            } else {
                healthContributionComponent.setAmount(BigDecimal.valueOf(0));
                healthContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, healthContributionComponent.getAmount()));
            }
            component.add(healthContributionComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("APORTE_SALUD_EMPRESA");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        log.debug("component -> {}", "companyHealthContribution");
    }
    public void companyRiskContribution(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> salaryParameters = new ConcurrentHashMap<>();
        createCache(salaryList, salaryMap, salaryParameters, (parameter, mapParameter) -> {});
        // Obtén los componentes necesarios para el cálculo
        List<String> riskComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        // Crear un nuevo PaymentComponentDTO para el Aporte Riesgo Empresa
        PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
        riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA");
        double totalAmountBase = salary + overtime + surcharges + commission;
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO salaryMinNext = salaryMap.get(nextPeriod);
        double salaryMinNextValue = salaryMinNext != null ? salaryMinNext.getValue() : 0.0;
        double riskContributionBase;
        if (salaryType.equals("BASE")) {
            if (totalAmountBase > 25 * salaryMinNextValue) {
                riskContributionBase = 25 * salaryMinNextValue * 0.00881;
            } else {
                riskContributionBase = totalAmountBase * 0.00881;
            }
        } else { // salaryType is INTEGRAL
            double adjustedTotalAmount = totalAmountBase * 0.7;
            if (adjustedTotalAmount > 25 * salaryMinNextValue) {
                riskContributionBase = 25 * salaryMinNextValue * 0.00881;
            } else {
                riskContributionBase = adjustedTotalAmount * 0.00881;
            }
        }
        riskContributionComponent.setAmount(BigDecimal.valueOf(riskContributionBase));
        if (category.equals("P")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            BigDecimal lastSalaryMinValue = BigDecimal.valueOf(salaryMinNextValue);
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    ParametersDTO salaryMinInternalParam = salaryMap.get(riskProjection.getMonth());
                    double salaryMinInternalValue;
                    if (salaryMinInternalParam != null) {
                        salaryMinInternalValue = salaryMinInternalParam.getValue();
                        lastSalaryMinValue = BigDecimal.valueOf(salaryMinInternalValue);
                    } else {
                        salaryMinInternalValue = lastSalaryMinValue.doubleValue();
                    }
                    //log.info("salaryMinInternalValue -> {}, month -> {}", salaryMinInternalValue, riskProjection.getMonth());
                    //log total and month
                    //log.info("totalAmount -> {}, month -> {}", totalAmount, riskProjection.getMonth());
                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (salaryComponent.getSalaryType().equals("BASE")) {
                        if (totalAmount > 25 * salaryMinInternalValue) {
                            riskContribution = 25 * salaryMinInternalValue * 0.00881;
                        } else {
                            riskContribution = totalAmount * 0.00881;
                        }
                    } else { // salaryType is INTEGRAL
                        double adjustedTotalAmount = totalAmount * 0.7;
                        if (adjustedTotalAmount > 25 * salaryMinInternalValue) {
                            riskContribution = 25 * salaryMinInternalValue * 0.00881;
                        } else {
                            riskContribution = adjustedTotalAmount * 0.00881;
                        }
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(riskProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(riskContribution));
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
        log.debug("component -> {}", "companyRiskContribution");
    }
    public void companyRiskContributionTrainee2(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("APR") || category.equals("PRA")) {
            HashMap<String, ParametersDTO> legalSalaryMinMap = new HashMap<>();
            HashMap<String, Double> cacheLegalSalaryMin = new HashMap<>();
            createCache(parameters, legalSalaryMinMap, cacheLegalSalaryMin, (parameter, mapParameter) -> {
            });
            // Obtén los componentes necesarios para el cálculo
            List<String> riskComponents = Arrays.asList("SALARY_PRA", "HHEE", "SURCHARGES", "COMMISSION");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY_PRA");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalAmountBase = salary + overtime + surcharges + commission;
            // Crear un nuevo PaymentComponentDTO para el Aporte Riesgo Empresa
            PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
            riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_BECARIOS");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO legalSalaryMin = salaryComponent != null ? legalSalaryMinMap.get(nextPeriod) : null;
            double legalSalaryMinInternalBase = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            riskContributionComponent.setAmount(totalAmountBase > 25 * legalSalaryMinInternalBase ? BigDecimal.valueOf(25 * legalSalaryMinInternalBase * 0.00881) : BigDecimal.valueOf(totalAmountBase * 0.00881));
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            double lastValidSalaryValue = legalSalaryMinInternalBase;
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    ParametersDTO legalSalaryMinInternalParam = legalSalaryMinMap.get(riskProjection.getMonth());
                    double legalSalaryMinInternalValue;
                    //calcular el salario minimo por mes
                    if (legalSalaryMinInternalParam != null) {
                        legalSalaryMinInternalValue = legalSalaryMinInternalParam.getValue();
                        lastValidSalaryValue = legalSalaryMinInternalValue;
                    } else {
                        legalSalaryMinInternalValue = lastValidSalaryValue;
                    }

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternalValue) {
                        riskContribution = 25 * legalSalaryMinInternalValue * 0.00881;
                    } else {
                        riskContribution = totalAmount * 0.00881;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(riskProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(riskContribution));
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            } else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, riskContributionComponent.getAmount()));
            }
            component.add(riskContributionComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("APORTE_RIESGO_EMPRESA_BECARIOS");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        log.debug("component -> {}", "companyRiskContributionTrainee");
    }
    public void icbfContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> icbfComponents = Arrays.asList(SALARY, "HHEE", "SURCHARGES", "COMMISSION", SALARY_PRA);
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> icbfComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Icbf
        PaymentComponentDTO icbfContributionComponent = new PaymentComponentDTO();
        icbfContributionComponent.setPaymentComponent("APORTE_ICBF");
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        double icbfContributionBase;
        if (salaryType.equals("BASE") && totalAmountBase > 10 * legalSalaryMinInternal) {
            icbfContributionBase = totalAmountBase * 0.03;
        } else if (!salaryType.equals("BASE")) {
            icbfContributionBase = totalAmountBase * 0.70 * 0.03;
        } else {
            icbfContributionBase = 0;
        }
        icbfContributionComponent.setAmount(BigDecimal.valueOf(icbfContributionBase));
        if (!category.equals("T")) {
            // Calcular el Aporte Icbf para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = icbfComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Icbf
                    double icbfContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 10 * legalSalaryMinInternal) {
                        icbfContribution = totalAmount * 0.03;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        icbfContribution = totalAmount * 0.70 * 0.03;
                    } else {
                        icbfContribution = 0;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(icbfContribution));
                    projections.add(projection);
                }
                icbfContributionComponent.setProjections(projections);
            }else {
                icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
                icbfContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,icbfContributionComponent.getAmount()));
            }
        }else {
            icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
            icbfContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,icbfContributionComponent.getAmount()));
        }
        component.add(icbfContributionComponent);
        log.debug("component -> {}", "icbfContribution");
    }
    public void senaContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> senaComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> senaComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        if (category.equals("APR") || category.equals("PRA")){
            salaryComponent = componentMap.get(SALARY_PRA);
        }
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
        // Crear un nuevo PaymentComponentDTO para el Aporte Sena
        PaymentComponentDTO senaContributionComponent = new PaymentComponentDTO();
        senaContributionComponent.setPaymentComponent("APORTE_SENA");
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        senaContributionComponent.setAmount(BigDecimal.valueOf(salaryType.equals("BASE") && totalAmountBase > 10 * legalSalaryMinInternal ? totalAmountBase * 0.02 : totalAmountBase * 0.70 * 0.02));
        if (!category.equals("T")) {
            // Calcular el Aporte Sena para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = senaComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Sena
                    double senaContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 10 * legalSalaryMinInternal) {
                        senaContribution = totalAmount * 0.02;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        senaContribution = totalAmount * 0.70 * 0.02;
                    } else {
                        senaContribution = 0;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(senaContribution));
                    projections.add(projection);
                }
                senaContributionComponent.setProjections(projections);
            }else {
                senaContributionComponent.setAmount(BigDecimal.valueOf(0));
                senaContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,senaContributionComponent.getAmount()));
            }
        }else {
            senaContributionComponent.setAmount(BigDecimal.valueOf(0));
            senaContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,senaContributionComponent.getAmount()));
        }
        component.add(senaContributionComponent);
        log.debug("component -> {}", "senaContribution");
    }
    public void companyPensionContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
        createCache(parameters, salaryMap, cacheSalary, (parameter, mapParameter) -> {});
        // Obtén los componentes necesarios para el cálculo
        List<String> pensionComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> pensionComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double totalAmountBase = salary + overtime + surcharges + commission;
// Crear un nuevo PaymentComponentDTO para el Aporte Pensión Empresa
        PaymentComponentDTO pensionContributionComponent = new PaymentComponentDTO();
        pensionContributionComponent.setPaymentComponent("APORTE_PENSION_EMPRESA");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO salaryMinNext = salaryMap.get(nextPeriod);
        double salaryMinNextValue = salaryMinNext != null ? salaryMinNext.getValue() : 0.0;
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        double pensionContributionBase;
        if (salaryType.equals("BASE") && totalAmountBase > 25 * salaryMinNextValue) {
            pensionContributionBase = 25 * salaryMinNextValue * 0.12;
        } else if (salaryType.equals("INTEGRAL") &&  0.70 * totalAmountBase > 25 * salaryMinNextValue) {
            pensionContributionBase = 25 * salaryMinNextValue * 0.12;
        } else if (salaryType.equals("BASE")) {
            pensionContributionBase = totalAmountBase * 0.12;
        } else {
            pensionContributionBase = 0.70 * totalAmountBase * 0.12;
        }
        //log.info("pensionContributionBase -> {}", pensionContributionBase);
        pensionContributionComponent.setAmount(BigDecimal.valueOf(pensionContributionBase));
        if (category.equals("P")) {
            // Calcular el Aporte Pensión Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            BigDecimal lastValidSalaryValue = BigDecimal.valueOf(salaryMinNextValue);
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    String salaryTypeProjection = salaryComponent.getSalaryType();
                    double totalAmount = pensionComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    ParametersDTO salaryMinInternalParam = salaryMap.get(primeProjection.getMonth());
                    double salaryMinInternalValue;
                    if (salaryMinInternalParam != null) {
                        salaryMinInternalValue = salaryMinInternalParam.getValue();
                        lastValidSalaryValue = BigDecimal.valueOf(salaryMinInternalValue);
                    } else {
                        salaryMinInternalValue = lastValidSalaryValue.doubleValue();
                    }
                    //log.info("salaryMinInternalValue -> {}, month -> {}", salaryMinInternalValue, primeProjection.getMonth());
                    //log salry type and month
                    //log.info("salaryTypeProjection -> {}, month -> {}", salaryTypeProjection, primeProjection.getMonth());
                    // Calcular el Aporte Pensión Empresa
                   /* double pensionContribution;
                    if (salaryTypeProjection.equals("BASE") && totalAmount > 25 * salaryMinInternalValue) {
                        pensionContribution = 25 * salaryMinInternalValue * 0.12;
                    } else if (salaryTypeProjection.equals("INTEGRAL") && 0.70 * totalAmount > 25 * salaryMinInternalValue) {
                        pensionContribution = 25 * salaryMinInternalValue * 0.12;
                    } else if (salaryTypeProjection.equals("BASE")) {
                        pensionContribution = totalAmount * 0.12;
                    } else {
                        pensionContribution = 0.70 * totalAmount * 0.12;
                    }*/
                    double pensionContributionBasic;
                    if (salaryTypeProjection.equals("BASE") && totalAmount > 25 * salaryMinInternalValue) {
                        pensionContributionBasic = 25 * salaryMinInternalValue * 0.12;
                    } else {
                        pensionContributionBasic = totalAmount * 0.12;
                    }
                    double pensionContributionIntegral;
                    if (salaryTypeProjection.equals("INTEGRAL") && 0.70 * totalAmount > 25 * salaryMinInternalValue) {
                        pensionContributionIntegral = 25 * salaryMinInternalValue * 0.12;
                    } else {
                        pensionContributionIntegral = 0.70 * totalAmount * 0.12;
                    }
                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(salaryTypeProjection.equals("BASE") ? pensionContributionBasic : pensionContributionIntegral));
                    projections.add(projection);
                }
                pensionContributionComponent.setProjections(projections);
            }else {
                pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
                pensionContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,pensionContributionComponent.getAmount()));
            }
        }else {
            pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
            pensionContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,pensionContributionComponent.getAmount()));
        }
        component.add(pensionContributionComponent);
        log.debug("component -> {}", "companyPensionContribution");
    }

    public void sodexo(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryList, String period, Integer range, List<ParametersDTO> sodexoList, String position, List<RangeBuDetailDTO> excludedPositions) {
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> salaryParameters = new ConcurrentHashMap<>();
        createCache(salaryList, salaryMap, salaryParameters, (parameter, mapParameter) -> {});
        Map<String, ParametersDTO> sodexoMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSodexo = new ConcurrentHashMap<>();
        createCache(sodexoList, sodexoMap, cacheSodexo,  (parameter, mapParameter) -> {});
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> sodexoComponents = Arrays.asList("SALARY", "COMMISSION", "HHEE", "SURCHARGES");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> sodexoComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double totalAmountBase = salary + commission + overtime + surcharges;
        // Use the period to get the ParametersDTO from the sodexoMap
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        //log.info("sodexoMap -> {}", period);
        //ParametersDTO sodexoBase = sodexoMap.get(nextPeriod);
        //double sodexoValueBase = sodexoBase != null ? sodexoBase.getValue() : 0.0;
        //log.info("sodexoValueBase -> {}", sodexoValueBase);
        ParametersDTO sodexoParam= sodexoMap.get(nextPeriod);
        double sodexoValueBase = sodexoParam != null ? sodexoParam.getValue() : 0.0;
        double sodexoValueExclusions =  findExcludedPositions(position, sodexoValueBase, excludedPositions);
        ParametersDTO salaryMinInternal = salaryMap.get(nextPeriod);
        double legalSalaryMinInternal = salaryMinInternal != null ? salaryMinInternal.getValue() : 0.0;
        // Crear un nuevo PaymentComponentDTO para Sodexo
        PaymentComponentDTO sodexoComponent = new PaymentComponentDTO();
        sodexoComponent.setPaymentComponent("SODEXO");
        if (category.equals("P")) {
            double sodexoContributionBase;
            if (totalAmountBase < 2 * legalSalaryMinInternal) {
                sodexoContributionBase = sodexoValueExclusions;
            } else {
                sodexoContributionBase = 0;
            }
            sodexoComponent.setAmount(BigDecimal.valueOf(sodexoContributionBase));
            // Calcular el valor de Sodexo para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            BigDecimal lastSalaryMinValue = BigDecimal.valueOf(legalSalaryMinInternal);
            BigDecimal lastSodexoValue = BigDecimal.valueOf(sodexoValueExclusions);
            BigDecimal lastSodexoParam = BigDecimal.valueOf(sodexoValueBase);
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    ParametersDTO sodexo = sodexoMap.get(primeProjection.getMonth());
                    double sodexoParamValue;
                    if (sodexo != null) {
                        sodexoParamValue = sodexo.getValue();
                        lastSodexoParam = BigDecimal.valueOf(sodexoParamValue);
                    } else {
                        sodexoParamValue = lastSodexoParam.doubleValue();
                    }
                    double sodexoValueExclusionsProjection =  findExcludedPositions(position, sodexoParamValue, excludedPositions);
                    //log.info("position -> {}, sodexoValueExclusionsProjection -> {}, moth -> {}", position, sodexoValueExclusionsProjection, primeProjection.getMonth());
                    double totalAmount = sodexoComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    ParametersDTO salaryMinInternalParam = salaryMap.get(primeProjection.getMonth());
                    double salaryMinInternalValue;
                    if (salaryMinInternalParam != null) {
                        salaryMinInternalValue = salaryMinInternalParam.getValue();
                        lastSalaryMinValue = BigDecimal.valueOf(salaryMinInternalValue);
                    } else {
                        salaryMinInternalValue = lastSalaryMinValue.doubleValue();
                    }

                    double sodexoContribution;
                    if (totalAmount < 2 * salaryMinInternalValue) {
                        sodexoContribution = sodexoValueExclusionsProjection;
                        //lastSodexoValue = BigDecimal.valueOf(sodexoContribution);
                    } else {
                        sodexoContribution = 0.0;
                    }
                    //log salry type and month
                    //log.info("totalAmount -> {}, month -> {}", totalAmount, primeProjection.getMonth());
                    //log.info("sodexoContribution -> {}, month -> {}", sodexoContribution, primeProjection.getMonth());
                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(sodexoContribution));
                    projections.add(projection);
                }
                sodexoComponent.setProjections(projections);
            }else {
                sodexoComponent.setAmount(BigDecimal.valueOf(0));
                sodexoComponent.setProjections(Shared.generateMonthProjectionV2(period,range,sodexoComponent.getAmount()));
            }
        }else {
            sodexoComponent.setAmount(BigDecimal.valueOf(0));
            sodexoComponent.setProjections(Shared.generateMonthProjectionV2(period,range,sodexoComponent.getAmount()));
        }
        component.add(sodexoComponent);
        log.debug("component -> {}", "sodexo");
    }

    public ParametersDTO calculateSodexoCercano(Map<String, ParametersDTO> sodexoMap, String periodo) {
        // Si el mapa está vacío, devolvemos null
        if (sodexoMap.isEmpty()) {
            return null;
        }


        int periodoActual = Integer.parseInt(periodo); // Convertimos el periodo dado a entero para facilitar la comparación
        String periodoCercano = null;

        // Iteramos sobre las claves del mapa
        for (String clave : sodexoMap.keySet()) {
            int periodoClave = Integer.parseInt(clave); // Convertimos la clave a entero para comparar

            // Si el periodo de la clave del mapa es menor o igual al periodo dado y es mayor que el periodo más cercano hacia atrás
            if (periodoClave <= periodoActual && (periodoCercano == null || periodoClave > Integer.parseInt(periodoCercano))) {
                periodoCercano = clave; // Actualizamos el periodo más cercano hacia atrás
            }
        }
        if(periodoCercano== null){return null;}

        // Devolvemos el objeto correspondiente a la clave más cercana hacia atrás
        return sodexoMap.get(periodoCercano);
    }

    public void sena(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;

        // Obtén los componentes necesarios para el cálculo
        List<String> newComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> newComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");

        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();

        double totalAmountBase = salary + overtime + surcharges + commission;

        // Crear un nuevo PaymentComponentDTO para el nuevo componente
        PaymentComponentDTO newComponent = new PaymentComponentDTO();
        newComponent.setPaymentComponent("SENA");
        newComponent.setAmount(category.equals("P") ? BigDecimal.valueOf(totalAmountBase * 0.02) : BigDecimal.valueOf(0));
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        if (category.equals("P")) {
            if (salaryType.equals("BASE")){
                if(totalAmountBase < 10 * legalSalaryMinInternal){
                    newComponent.setAmount(BigDecimal.valueOf(0));
                }else {
                    newComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.02));
                }
            }else {
                if(totalAmountBase < 10 * legalSalaryMinInternal){
                    newComponent.setAmount(BigDecimal.valueOf(0));
                }else {
                    newComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.70 * 0.02));
                }
            }
        }else {
            newComponent.setAmount(BigDecimal.valueOf(0));
        }
        List<MonthProjection> projections = new ArrayList<>();
        if (salaryComponent != null && salaryComponent.getProjections() != null) {
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                double totalAmount = newComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .mapToDouble(projection -> projection.getAmount().doubleValue())
                        .sum();

                // Calcular el nuevo componente
                double newComponentValue;
                if (category.equals("P")) {
                    if (salaryType.equals("BASE")){
                        if(totalAmount < 10 * legalSalaryMinInternal){
                            newComponentValue = 0;
                        }else {
                            newComponentValue = totalAmount * 0.02;
                        }
                    }else {
                        if(totalAmount < 10 * legalSalaryMinInternal){
                            newComponentValue = 0;
                        }else {
                            newComponentValue = totalAmount * 0.70 * 0.02;
                        }
                    }
                }else {
                    newComponentValue = 0;
                }

                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(BigDecimal.valueOf(newComponentValue));
                projections.add(projection);
            }
        }else {
            newComponent.setAmount(BigDecimal.valueOf(0));
            newComponent.setProjections(Shared.generateMonthProjectionV2(period,range,newComponent.getAmount()));
        }
        newComponent.setProjections(projections);
        component.add(newComponent);
        log.debug("component -> {}", "sena");
    }
    public void uniqueBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO pc938014Component = componentMap.get("PC938014");
        PaymentComponentDTO pc938002Component = componentMap.get("PC938002");
        // Obtener el valor del bono objetivo de los componentes de pago PC938014 y PC938002
        double pc938014Amount = pc938014Component != null ? pc938014Component.getAmount().doubleValue() : 0;
        double pc938002Amount = pc938002Component != null ? pc938002Component.getAmount().doubleValue() : 0;
      /*  double bonusTarget = (pc938014Component != null ? pc938014Component.getAmount().doubleValue() : 0) +
                (pc938002Component != null ? pc938002Component.getAmount().doubleValue() : 0);*/
        double bonusTarget = Math.max(pc938014Amount, pc938002Amount);
        String category = findCategory(classEmployee);
        PaymentComponentDTO uniqueBonusComponent = new PaymentComponentDTO();
        uniqueBonusComponent.setPaymentComponent("UNIQUE_BONUS");
        if (category.equals("P") && salaryComponent != null && salaryComponent.getAmount().doubleValue() > 0) {
            double baseSalary = salaryComponent.getAmount().doubleValue();
            String salaryType = salaryComponent.getSalaryType();
            double uniqueBonusBase = calculateUniqueBonus(salaryType, baseSalary, bonusTarget);
            uniqueBonusComponent.setAmount(BigDecimal.valueOf(uniqueBonusBase));
            if (uniqueBonusBase > 0) {
                List<MonthProjection> projections = salaryComponent.getProjections();
                List<MonthProjection> newProjections = new ArrayList<>();
                for (MonthProjection monthProjection : projections) {
                    double salary = monthProjection.getAmount().doubleValue();
                    double uniqueBonus = calculateUniqueBonus(salaryType, salary, bonusTarget);
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(monthProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(uniqueBonus));
                    newProjections.add(projection);
                }
                uniqueBonusComponent.setProjections(newProjections);
            }else {
                uniqueBonusComponent.setAmount(BigDecimal.valueOf(0));
                uniqueBonusComponent.setProjections(Shared.generateMonthProjectionV2(period,range,uniqueBonusComponent.getAmount()));
            }
        }else {
            uniqueBonusComponent.setAmount(BigDecimal.valueOf(0));
            uniqueBonusComponent.setProjections(Shared.generateMonthProjectionV2(period,range,uniqueBonusComponent.getAmount()));
        }
        component.add(uniqueBonusComponent);
        log.debug("component -> {}", "uniqueBonus");
    }

    private double calculateUniqueBonus(String salaryType, double baseSalary, double bonusTarget) {
        Map<String, Double> bonusMultipliers = new HashMap<>();
        bonusMultipliers.put("BASE", 14.12);
        bonusMultipliers.put("INTEGRAL", 12.0);
        double multiplier = bonusMultipliers.getOrDefault(salaryType, 0.0);
        log.debug("salaryType: " + salaryType + ", baseSalary: " + baseSalary + ", bonusTarget: " + bonusTarget + ", multiplier: " + multiplier);
        if (bonusTarget == 0) {
            bonusTarget = 0.00;
        }
        double bonusTargetPercent = bonusTarget / 100;
        return (baseSalary * multiplier * bonusTargetPercent) / 12;
    }
    public void AuxilioDeTransporteAprendizSena(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryPralist, String period, Integer range, List<ParametersDTO> subsidyTransportList) {
        String category = findCategory(classEmployee);
        if (category.equals("PRA") || category.equals("APR")) {
            //ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            //ParametersDTO subsidyTransport = getParametersById(parameters, 48);
            Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
            Map<String, Double> salaryParameters = new ConcurrentHashMap<>();
            createCache(salaryPralist, salaryMap, salaryParameters, (parameter, mapParameter) -> {});
            Map<String, ParametersDTO> subsidyTransportMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheSubsidyTransport = new ConcurrentHashMap<>();
            createCache(subsidyTransportList, subsidyTransportMap, cacheSubsidyTransport,  (parameter, mapParameter) -> {});
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO subsidyTransport = subsidyTransportMap.get(nextPeriod);
            ParametersDTO legalSalaryMin = salaryMap.get(nextPeriod);
            double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            double subsidyTransportValue = subsidyTransport != null ? subsidyTransport.getValue() : 0.0;
            // Obtén los componentes necesarios para el cálculo
            List<String> transportSubsidyComponents = Arrays.asList("SALARY_PRA", "HHEE", "SURCHARGES", "COMMISSION");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> transportSubsidyComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY_PRA");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalAmountBase = salary + overtime + surcharges + commission;
            // Crear un nuevo PaymentComponentDTO para el Auxilio de Transporte Aprendiz Sena
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("AUXILIO_TRANSPORTE_APRENDIZ_SENA");
            if (totalAmountBase > 0){
                transportSubsidyComponent.setAmount(totalAmountBase <= 2 * legalSalaryMinInternal ? BigDecimal.valueOf(subsidyTransportValue) : BigDecimal.ZERO);
                // Calcular el Auxilio de Transporte Aprendiz Sena para cada proyección
                double lastTransportSubsidyValue = subsidyTransportValue;
                double lastLegalSalaryMinInternal = legalSalaryMinInternal;
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection monthProjection : componentMap.get("SALARY_PRA").getProjections()) {
                    BigDecimal totalAmount = transportSubsidyComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(monthProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    // Calcular el Auxilio de Transporte Aprendiz Sena
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(monthProjection.getMonth());
                    ParametersDTO subsidyTransportProj = subsidyTransportMap.get(monthProjection.getMonth());
                    ParametersDTO legalSalaryMinProj = salaryMap.get(monthProjection.getMonth());
                    double legalSalaryMinInternalProj;
                    if (legalSalaryMinProj != null) {
                        legalSalaryMinInternalProj = legalSalaryMinProj.getValue();
                        lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                    } else {
                        legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                    }
                    double subsidyTransportProjValue;
                    if (subsidyTransportProj != null) {
                        subsidyTransportProjValue = subsidyTransportProj.getValue();
                        lastTransportSubsidyValue = subsidyTransportProjValue;
                    } else {
                        subsidyTransportProjValue = lastTransportSubsidyValue;
                    }
                    projection.setAmount(BigDecimal.valueOf(totalAmount.doubleValue() <= 2 * legalSalaryMinInternalProj ? subsidyTransportProjValue : 0));
                    projections.add(projection);
                }
                transportSubsidyComponent.setProjections(projections);
            }else {
                transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
                transportSubsidyComponent.setProjections(Shared.generateMonthProjectionV2(period,range,transportSubsidyComponent.getAmount()));
            }
            component.add(transportSubsidyComponent);
        }else {
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("AUXILIO_TRANSPORTE_APRENDIZ_SENA");
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjectionV2(period,range,transportSubsidyComponent.getAmount()));
            component.add(transportSubsidyComponent);
        }
        log.debug("component -> {}", "AuxilioDeTransporteAprendizSena");
    }
    public void AuxilioConectividadDigital(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range, String position, List<RangeBuDetailDTO> excludedPositions, List<ParametersDTO> digitalConnectivityList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        Map<String, ParametersDTO> digitalConnectivityMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheDigitalConnectivity = new ConcurrentHashMap<>();
        createCache(digitalConnectivityList, digitalConnectivityMap, cacheDigitalConnectivity,  (parameter, mapParameter) -> {});
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO digitalConnectivityBase = digitalConnectivityMap.get(nextPeriod);
        double digitalConnectivityValueBase = digitalConnectivityBase != null ? digitalConnectivityBase.getValue() : 0.0;
        String category = findCategory(classEmployee);
        PaymentComponentDTO digitalConnectivityAidComponent = new PaymentComponentDTO();
        digitalConnectivityAidComponent.setPaymentComponent("AUXILIO_CONECTIVIDAD_DIGITAL");
        if (category.equals("P")) {
            double digitalConnectivityAidValue = findExcludedPositions(position, digitalConnectivityValueBase, excludedPositions);
            digitalConnectivityAidComponent.setAmount(BigDecimal.valueOf(digitalConnectivityAidValue));
            List<MonthProjection> projections = new ArrayList<>();
            double lastDigitalConnectivityAidValueProjection = digitalConnectivityAidValue;
            for (MonthProjection monthProjection : salaryComponent.getProjections()) {
                ParametersDTO digitalConnectivity = digitalConnectivityMap.get(monthProjection.getMonth());
                double digitalConnectivityAmount;
                if (digitalConnectivity != null) {
                    digitalConnectivityAmount = digitalConnectivity.getValue();
                    //digitalConnectivityAmount = findExcludedPositions(position, digitalConnectivityValue, excludedPositions);
                    lastDigitalConnectivityAidValueProjection = digitalConnectivityAmount;
                }else {
                    digitalConnectivityAmount = lastDigitalConnectivityAidValueProjection;
                }
                double amountDigitalConnectivity = findExcludedPositions(position, digitalConnectivityAmount, excludedPositions);
                MonthProjection projection = new MonthProjection();
                projection.setMonth(monthProjection.getMonth());
                projection.setAmount(BigDecimal.valueOf(amountDigitalConnectivity));
                projections.add(projection);
            }
            digitalConnectivityAidComponent.setProjections(projections);
        } else {
            digitalConnectivityAidComponent.setAmount(BigDecimal.valueOf(0));
            digitalConnectivityAidComponent.setProjections(Shared.generateMonthProjectionV2(period,range,digitalConnectivityAidComponent.getAmount()));
        }
        component.add(digitalConnectivityAidComponent);
    }
    public Double findExcludedPositions (String position, Double digitalConnectivityValue, List<RangeBuDetailDTO> excludedPositions) {
        // Verificar si la posición está en la lista de posiciones excluidas
        boolean isExcluded = excludedPositions.stream()
                .anyMatch(excludedPosition -> excludedPosition.getRange().equalsIgnoreCase(position) && excludedPosition.getValue() == 0);
        //log.debug("isExcluded: " + isExcluded + ", position: " + position + ", digitalConnectivityValue: " + digitalConnectivityValue);
        if (isExcluded) {
            return 0.0;
        } else {
            return digitalConnectivityValue;
        }
    }
    public void commissionTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission, List<ParametersDTO> commissionList, Map<String, Map<String, Object>> dataMapTemporal, String position) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            Map<String, Double> cacheCommission = new ConcurrentHashMap<>();
            createCommissionCache(commissionList, period, range, cacheCommission);
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO pc938003Component = componentMap.get(PC938003);
            PaymentComponentDTO pc938012Component = componentMap.get(PC938012);
            PaymentComponentDTO commissionComponent = new PaymentComponentDTO();
            commissionComponent.setPaymentComponent(COMMISSION_TEMP);
            double commission1 = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
            double commission2 = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
            double maxCommission = Math.max(commission1, commission2);
            commissionComponent.setAmount(BigDecimal.valueOf(maxCommission));
            commissionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,commissionComponent.getAmount()));
            component.add(commissionComponent);
        }else {
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("COMMISSION_TEMP");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjectionV2(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        log.debug("component -> {}", "commissionTemporal");
    }
    public void prodMonthPrimeTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            List<String> primaComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> primaComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get(COMMISSION_TEMP);
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalbase = salary + overtime + surcharges + commission;
            BigDecimal monthlyProvisionBase = BigDecimal.valueOf(totalbase / 12);
            PaymentComponentDTO monthlyPrimeComponent = new PaymentComponentDTO();
            monthlyPrimeComponent.setPaymentComponent("PRIMA_MENSUAL_TEMPORAL");
            if (salaryComponent != null) {
                monthlyPrimeComponent.setAmount(monthlyProvisionBase);
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = primaComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double amount = totalAmount.doubleValue() / 12;
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(projection);
                }
                monthlyPrimeComponent.setProjections(projections);
            }else {
                monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
                monthlyPrimeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,monthlyPrimeComponent.getAmount()));
            }
            component.add(monthlyPrimeComponent);
        }else {
            PaymentComponentDTO monthlyPrimeComponent = new PaymentComponentDTO();
            monthlyPrimeComponent.setPaymentComponent("PRIMA_MENSUAL_TEMPORAL");
            monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
            monthlyPrimeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,monthlyPrimeComponent.getAmount()));
            component.add(monthlyPrimeComponent);
        }
        log.debug("component -> {}", "prodMonthPrimeTemporal");
    }
    public void consolidatedVacationTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            List<String> vacationComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
            log.debug("component: " + component);
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get(COMMISSION_TEMP);
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalAmountBase = salary + overtime + surcharges + commission;
            PaymentComponentDTO vacationComponent = new PaymentComponentDTO();
            vacationComponent.setPaymentComponent("CONSOLIDADO_VACACIONES_TEMPORAL");
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                BigDecimal result = BigDecimal.valueOf(totalAmountBase / 2 / 12);
                vacationComponent.setAmount(result);
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = vacationComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double amount = totalAmount.doubleValue() / 2 / 12;
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(projection);
                }
                vacationComponent.setProjections(projections);
            }else {
                vacationComponent.setAmount(BigDecimal.valueOf(0));
                vacationComponent.setProjections(Shared.generateMonthProjectionV2(period,range,vacationComponent.getAmount()));
            }
            component.add(vacationComponent);
        }else {
            PaymentComponentDTO vacationComponent = new PaymentComponentDTO();
            vacationComponent.setPaymentComponent("CONSOLIDADO_VACACIONES_TEMPORAL");
            vacationComponent.setAmount(BigDecimal.valueOf(0));
            vacationComponent.setProjections(Shared.generateMonthProjectionV2(period,range,vacationComponent.getAmount()));
            component.add(vacationComponent);
        }
        log.debug("component -> {}", "consolidatedVacationTemporal");
    }
    public void consolidatedSeveranceTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
    String category = findCategory(classEmployee);
    // Obtén los componentes necesarios para el cálculo
    List<String> severanceComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
    Map<String, PaymentComponentDTO> componentMap = component.stream()
            .filter(c -> severanceComponents.contains(c.getPaymentComponent()))
            .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
    PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
        if (category.equals("T")) {
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get(COMMISSION_TEMP);
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Consolidado de Cesantías Temporal
            PaymentComponentDTO severanceComponent = new PaymentComponentDTO();
            severanceComponent.setPaymentComponent("CONSOLIDADO_CESANTIAS_TEMPORAL");
            // Calcular el Consolidado de Cesantías Temporal para cada proyección
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                double totalAmountBase = salary + overtime + surcharges + commission;
                BigDecimal result = BigDecimal.valueOf(totalAmountBase / 12);
                severanceComponent.setAmount(result);
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = severanceComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double amount = totalAmount.doubleValue() / 12;
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(projection);
                }
                severanceComponent.setProjections(projections);
            }else {
                severanceComponent.setAmount(BigDecimal.valueOf(0));
                severanceComponent.setProjections(Shared.generateMonthProjectionV2(period,range,severanceComponent.getAmount()));
            }
            component.add(severanceComponent);
        }else {
            PaymentComponentDTO severanceComponent = new PaymentComponentDTO();
            severanceComponent.setPaymentComponent("CONSOLIDADO_CESANTIAS_TEMPORAL");
            severanceComponent.setAmount(BigDecimal.valueOf(0));
            severanceComponent.setProjections(Shared.generateMonthProjectionV2(period,range,severanceComponent.getAmount()));
            component.add(severanceComponent);
        }
        log.debug("component -> {}", "consolidatedSeveranceTemporal");
    }
    public void consolidatedSeveranceInterestTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO severanceComponent = componentMap.get("CONSOLIDADO_CESANTIAS_TEMPORAL");
            PaymentComponentDTO severanceInterestComponent = new PaymentComponentDTO();
            severanceInterestComponent.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS_TEMPORAL");
            if (severanceComponent != null) {
                double severance = severanceComponent.getAmount().doubleValue();
                double severanceInterest = severance * 0.12;
                severanceInterestComponent.setAmount(BigDecimal.valueOf(severanceInterest));
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : severanceComponent.getProjections()) {
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    double amount = primeProjection.getAmount().doubleValue() * 0.12;
                    projection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(projection);
                }
                severanceInterestComponent.setProjections(projections);
            }else {
                severanceInterestComponent.setAmount(BigDecimal.valueOf(0));
                severanceInterestComponent.setProjections(Shared.generateMonthProjectionV2(period,range,severanceInterestComponent.getAmount()));
            }
            component.add(severanceInterestComponent);
        }else {
            PaymentComponentDTO severanceInterestComponent = new PaymentComponentDTO();
            severanceInterestComponent.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS_TEMPORAL");
            severanceInterestComponent.setAmount(BigDecimal.valueOf(0));
            severanceInterestComponent.setProjections(Shared.generateMonthProjectionV2(period,range,severanceInterestComponent.getAmount()));
            component.add(severanceInterestComponent);
        }
        log.debug("component -> {}", "consolidatedSeveranceInterestTemporal");
    }
    public void transportSubsidyTemporaries(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, String classEmployee, String period, Integer range, List<ParametersDTO> subsidyTransportList) {
    String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Obtén los componentes necesarios para el cálculo
            List<String> subsidyComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> subsidyComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get(COMMISSION_TEMP);
            HashMap<String, ParametersDTO> legalSalaryMinMap = new HashMap<>();
            HashMap<String, Double> cacheLegalSalaryMin = new HashMap<>();
            createCache(salaryList, legalSalaryMinMap, cacheLegalSalaryMin, (parameter, mapParameter) -> {
            });
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO legalSalaryMin = legalSalaryMinMap.get(nextPeriod);
            double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
            Map<String, ParametersDTO>  subsidyMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheSubsidy = new ConcurrentHashMap<>();
            createCache(subsidyTransportList, subsidyMap, cacheSubsidy,  (parameter, mapParameter) -> {});
            ParametersDTO subsidyMin = subsidyMap.get(nextPeriod);
            double subsidyMinInternal = subsidyMin!=null ? subsidyMin.getValue() : 0.0;
            //total base
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Crear un nuevo PaymentComponentDTO para el Subsidio de Transporte
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("SUBSIDIO_TRANSPORTE_TEMPORALES");
            double totalAmountBase = salary + overtime + surcharges + commission;
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(totalAmountBase <= 2 * legalSalaryMinInternal ? subsidyMinInternal : 0));
            List<MonthProjection> projections = new ArrayList<>();
            double lastSubsidyMinInternal = subsidyMinInternal;
            double lastLegalSalaryMinInternal = legalSalaryMinInternal;
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = subsidyComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                ParametersDTO subsidyProj = subsidyMap.get(primeProjection.getMonth());
                double subsidyProjValue;
                if (subsidyProj != null) {
                    subsidyProjValue = subsidyProj.getValue();
                    lastSubsidyMinInternal = subsidyProjValue;
                } else {
                    subsidyProjValue = lastSubsidyMinInternal;
                }
                ParametersDTO legalSalaryMinProj = legalSalaryMinMap.get(primeProjection.getMonth());
                double legalSalaryMinInternalProj;
                if (legalSalaryMinProj != null) {
                    legalSalaryMinInternalProj = legalSalaryMinProj.getValue();
                    lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                } else {
                    legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                }
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                double amount;
                if (totalAmount.doubleValue() <= 2 * legalSalaryMinInternalProj) {
                    amount = subsidyProjValue;
                } else {
                    amount = 0.0;
                }
                projection.setAmount(BigDecimal.valueOf(amount));
                projections.add(projection);
            }
            transportSubsidyComponent.setProjections(projections);
            component.add(transportSubsidyComponent);
        }else {
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("SUBSIDIO_TRANSPORTE_TEMPORALES");
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjectionV2(period,range,transportSubsidyComponent.getAmount()));
            component.add(transportSubsidyComponent);
        }
        log.debug("component -> {}", "transportSubsidyTemporaries");
    }
    public void companyHealthContributionTemporals(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryList, String period, Integer range) {
        String category = findCategory(classEmployee);
        Map<String, ParametersDTO>  salaryListMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
        createCache(salaryList, salaryListMap, cacheSalary,  (parameter, mapParameter) -> {});
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO legalSalaryMin = salaryListMap.get(nextPeriod);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        if (category.equals("T")) {
            List<String> healthComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> healthComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalAmountBase = salary + overtime + surcharges + commission;
            PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
            healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA_TEMP");
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                if (totalAmountBase > 25 * legalSalaryMinInternal) {
                    healthContributionComponent.setAmount(BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085));
                } else if (totalAmountBase > 10 * legalSalaryMinInternal) {
                    healthContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.085));
                } else {
                    healthContributionComponent.setAmount(BigDecimal.ZERO);
                }
                List<MonthProjection> projections = new ArrayList<>();
                double lastLegalSalaryMinInternal = legalSalaryMinInternal;
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = healthComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    ParametersDTO salaryProj = salaryListMap.get(primeProjection.getMonth());
                    double legalSalaryMinInternalProj;
                    if (salaryProj != null) {
                        legalSalaryMinInternalProj = salaryProj.getValue();
                        lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                    } else {
                        legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                    }
                    BigDecimal healthContribution;
                    if (totalAmount > 25 * legalSalaryMinInternalProj) {
                        healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternalProj * 0.085);
                    } else if (totalAmount > 10 * legalSalaryMinInternalProj) {
                        healthContribution = BigDecimal.valueOf(totalAmount * 0.085);
                    } else {
                        healthContribution = BigDecimal.ZERO;
                    }
                    projection.setAmount(healthContribution);
                    projections.add(projection);
                }
                healthContributionComponent.setProjections(projections);
            } else {
                healthContributionComponent.setAmount(BigDecimal.valueOf(0));
                healthContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, healthContributionComponent.getAmount()));
            }
            component.add(healthContributionComponent);
        }else {
            PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
            healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA_TEMP");
            healthContributionComponent.setAmount(BigDecimal.valueOf(0));
            healthContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, healthContributionComponent.getAmount()));
            component.add(healthContributionComponent);
        }
        log.debug("component -> {}", "companyHealthContributionTemporals");
    }
    public void companyRiskContributionTemporals(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryList, String period, Integer range) {
        String category = findCategory(classEmployee);
        //ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        Map<String, ParametersDTO>  salaryListMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
        createCache(salaryList, salaryListMap, cacheSalary,  (parameter, mapParameter) -> {});
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO legalSalaryMin = salaryListMap.get(nextPeriod);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        if (category.equals("T")) {
            List<String> riskComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

            PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");

            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();

            double totalAmountBase = salary + overtime + surcharges + commission;

            PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
            riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_TEMP");

            if (totalAmountBase > 25 * legalSalaryMinInternal) {
                riskContributionComponent.setAmount(BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881));
            } else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.00881));
            }

            List<MonthProjection> projections = new ArrayList<>();
            double lastLegalSalaryMinInternal = legalSalaryMinInternal;
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    ParametersDTO salaryProj = salaryListMap.get(primeProjection.getMonth());
                    double legalSalaryMinInternalProj;
                    if (salaryProj != null) {
                        legalSalaryMinInternalProj = salaryProj.getValue();
                        lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                    } else {
                        legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                    }
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    BigDecimal riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternalProj) {
                        riskContribution = BigDecimal.valueOf(25 * legalSalaryMinInternalProj * 0.00881);
                    } else {
                        riskContribution = BigDecimal.valueOf(totalAmount * 0.00881);
                    }
                    projection.setAmount(riskContribution);
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            } else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, riskContributionComponent.getAmount()));
            }
            component.add(riskContributionComponent);
        }else {
            PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
            riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_TEMP");
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, riskContributionComponent.getAmount()));
            component.add(riskContributionComponent);
        }
        log.debug("component -> {}", "companyRiskContributionTemporals");
    }
   public void contributionBoxTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Get the necessary components for the calculation
       if (category.equals("T")) {
           List<String> boxComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
           Map<String, PaymentComponentDTO> componentMap = component.stream()
                   .filter(c -> boxComponents.contains(c.getPaymentComponent()))
                   .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
           PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
           PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
           PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
           PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");
           double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
           double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
           double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
           double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
           // Create a new PaymentComponentDTO for the Box Contribution
           PaymentComponentDTO boxContributionComponent = new PaymentComponentDTO();
           boxContributionComponent.setPaymentComponent("APORTE_CAJA_TEMPORALES");
           double totalAmountBase = salary + overtime + surcharges + commission;
           boxContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.04));
           // Calculate the Box Contribution for each projection
           List<MonthProjection> projections = new ArrayList<>();
           if (salaryComponent != null && salaryComponent.getProjections() != null){
               for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                   double totalAmount = boxComponents.stream()
                           .map(componentMap::get)
                           .filter(Objects::nonNull)
                           .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                           .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                           .mapToDouble(projection -> projection.getAmount().doubleValue())
                           .sum();
                   MonthProjection projection = new MonthProjection();
                   projection.setMonth(primeProjection.getMonth());
                   projection.setAmount(BigDecimal.valueOf(totalAmount * 0.04));
                   projections.add(projection);
               }
               boxContributionComponent.setProjections(projections);
           }else {
                boxContributionComponent.setAmount(BigDecimal.valueOf(0));
                boxContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, boxContributionComponent.getAmount()));
           }
           component.add(boxContributionComponent);
       }else {
              PaymentComponentDTO boxContributionComponent = new PaymentComponentDTO();
              boxContributionComponent.setPaymentComponent("APORTE_CAJA_TEMPORALES");
              boxContributionComponent.setAmount(BigDecimal.valueOf(0));
              boxContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, boxContributionComponent.getAmount()));
              component.add(boxContributionComponent);
       }
       log.debug("component -> {}", "contributionBoxTemporaries");
    }
    public void icbfContributionTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryList, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Get the necessary components for the calculation
            List<String> icbfComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> icbfComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Create a new PaymentComponentDTO for the ICBF Contribution
            PaymentComponentDTO icbfContributionComponent = new PaymentComponentDTO();
            icbfContributionComponent.setPaymentComponent("APORTE_ICBF_TEMPORALES");
            double totalAmountBase = salary + overtime + surcharges + commission;
            Map<String, ParametersDTO>  salaryListMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
            createCache(salaryList, salaryListMap, cacheSalary,  (parameter, mapParameter) -> {});
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO legalSalaryMin = salaryListMap.get(nextPeriod);
            double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            icbfContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase >= 10 * legalSalaryMinInternal ? totalAmountBase * 0.03 : 0));
            // Calculate the ICBF Contribution for each projection
            List<MonthProjection> projections = new ArrayList<>();
            double lastLegalSalaryMinInternal = legalSalaryMinInternal;
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = icbfComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    ParametersDTO salaryProj = salaryListMap.get(primeProjection.getMonth());
                    double legalSalaryMinInternalProj;
                    if (salaryProj != null) {
                        legalSalaryMinInternalProj = salaryProj.getValue();
                        lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                    } else {
                        legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                    }
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf( totalAmount >= 10 * legalSalaryMinInternalProj ? totalAmount * 0.03 : 0));
                    projections.add(projection);
                }
            }else {
                icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
                icbfContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, icbfContributionComponent.getAmount()));
            }
            icbfContributionComponent.setProjections(projections);
            component.add(icbfContributionComponent);
        }else {
            PaymentComponentDTO icbfContributionComponent = new PaymentComponentDTO();
            icbfContributionComponent.setPaymentComponent("APORTE_ICBF_TEMPORALES");
            icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
            icbfContributionComponent.setProjections(Shared.generateMonthProjectionV2(period, range, icbfContributionComponent.getAmount()));
            component.add(icbfContributionComponent);
        }
        log.debug("component -> {}", "icbfContributionTemporaries");
    }
    public void senaContributionTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> salaryList, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Get the necessary components for the calculation
            List<String> senaComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> senaComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            // Create a new PaymentComponentDTO for the Sena Contribution
            PaymentComponentDTO senaContributionComponent = new PaymentComponentDTO();
            senaContributionComponent.setPaymentComponent("APORTE_SENA_TEMPORALES");
            double totalAmountBase = salary + overtime + surcharges + commission;
            Map<String, ParametersDTO>  salaryListMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheSalary = new ConcurrentHashMap<>();
            createCache(salaryList, salaryListMap, cacheSalary,  (parameter, mapParameter) -> {});
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO legalSalaryMin = salaryListMap.get(nextPeriod);
            double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
            senaContributionComponent.setAmount(BigDecimal.valueOf( totalAmountBase >= 10 * legalSalaryMinInternal ? totalAmountBase * 0.02 : 0));
            // Calculate the Sena Contribution for each projection
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                List<MonthProjection> projections = new ArrayList<>();
                double lastLegalSalaryMinInternal = legalSalaryMinInternal;
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                        BigDecimal totalAmount = senaComponents.stream()
                                .map(componentMap::get)
                                .filter(Objects::nonNull)
                                .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                                .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                                .map(MonthProjection::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    ParametersDTO salaryProj = salaryListMap.get(primeProjection.getMonth());
                    double legalSalaryMinInternalProj;
                    if (salaryProj != null) {
                        legalSalaryMinInternalProj = salaryProj.getValue();
                        lastLegalSalaryMinInternal = legalSalaryMinInternalProj;
                    } else {
                        legalSalaryMinInternalProj = lastLegalSalaryMinInternal;
                    }
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    double amount;
                    if (totalAmount.doubleValue() >= 10 * legalSalaryMinInternalProj) {
                        amount = totalAmount.doubleValue() * 0.02;
                    } else {
                        amount = 0.0;
                    }
                    projection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(projection);
                }
                senaContributionComponent.setProjections(projections);
                component.add(senaContributionComponent);
            }else {
                senaContributionComponent.setAmount(BigDecimal.valueOf(0));
                senaContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,BigDecimal.ZERO));
                component.add(senaContributionComponent);
            }
        }else {
            PaymentComponentDTO senaContributionComponent = new PaymentComponentDTO();
            senaContributionComponent.setPaymentComponent("APORTE_SENA_TEMPORALES");
            senaContributionComponent.setAmount(BigDecimal.valueOf(0));
            senaContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,senaContributionComponent.getAmount()));
            component.add(senaContributionComponent);
        }
        log.debug("component -> {}", "senaContributionTemporaries");
    }
    public void companyPensionContributionTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            // Get the necessary components for the calculation
            List<String> pensionComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> pensionComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO salaryComponent = componentMap.get("TEMPORAL_SALARY");
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");
            double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
            double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
            double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
            double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();
            double totalAmountBase = salary + overtime + surcharges + commission;
            // Create a new PaymentComponentDTO for the Company Pension Contribution
            PaymentComponentDTO pensionContributionComponent = new PaymentComponentDTO();
            pensionContributionComponent.setPaymentComponent("APORTE_PENSION_EMPRESA_TEMPORALES");
            pensionContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase > 25 * legalSalaryMinInternal ? 25 * legalSalaryMinInternal * 0.12 : totalAmountBase * 0.12));
            // Calculate the Company Pension Contribution for each projection
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = pensionComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(totalAmount > 25 * legalSalaryMinInternal ? 25 * legalSalaryMinInternal * 0.12 : totalAmount * 0.12));
                    projections.add(projection);
                }
                pensionContributionComponent.setProjections(projections);
            }else {
                pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
                pensionContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,pensionContributionComponent.getAmount()));
            }
            component.add(pensionContributionComponent);
        }else {
            PaymentComponentDTO pensionContributionComponent = new PaymentComponentDTO();
            pensionContributionComponent.setPaymentComponent("APORTE_PENSION_EMPRESA_TEMPORALES");
            pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
            pensionContributionComponent.setProjections(Shared.generateMonthProjectionV2(period,range,pensionContributionComponent.getAmount()));
            component.add(pensionContributionComponent);
        }
        log.debug("component -> {}", "companyPensionContributionTemporaries");
    }
    public void feeTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")){
            // Get the necessary components for the calculation
            /*(Salario Base[temporales] + Comisiones[temporales] + Provisión Mensual Prima[temporales] + Consolidado de Vacaciones[temporales] + Consolidado De Cesantias[temporales] + Consolidado De Intereses De Cesantias[temporales] + Subsidio de Transporte[temporales] + Aporte Salud Empresa[temporales] + Aporte Riesgo Empresa[temporales] + Aporte Caja[temporales] + Aporte ICBF[temporales] + Aporte Sena[temporales] + Aporte Pension Empresa[temporales]) * 7,35%"*/
            List<String> feeComponents = Arrays.asList("TEMPORAL_SALARY", "COMMISSION_TEMP", "PRIMA_MENSUAL_TEMPORAL", "CONSOLIDADO_VACACIONES_TEMPORAL", "CONSOLIDADO_CESANTIAS_TEMPORAL", "CONSOLIDADO_INTERESES_CESANTIAS_TEMPORAL", "SUBSIDIO_TRANSPORTE_TEMPORALES", "APORTE_SALUD_EMPRESA_TEMP", "APORTE_RIESGO_EMPRESA_TEMP", "APORTE_CAJA_TEMPORALES", "APORTE_ICBF_TEMPORALES", "APORTE_SENA_TEMPORALES", "APORTE_PENSION_EMPRESA_TEMPORALES");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> feeComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            // Calculate the total base
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            double totalAmountBase = feeComponents.stream()
                    .map(componentMap::get)
                    .filter(Objects::nonNull)
                    .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                    .filter(projection -> projection.getMonth().equals(nextPeriod))
                    .mapToDouble(projection -> projection.getAmount().doubleValue())
                    .sum();
            // Create a new PaymentComponentDTO for the Fee
            PaymentComponentDTO feeComponent = new PaymentComponentDTO();
            feeComponent.setPaymentComponent("FEE_TEMP");
            feeComponent.setAmount(BigDecimal.valueOf(totalAmountBase * 0.0735));
            // Calculate the Fee for each projection
            List<MonthProjection> projections = new ArrayList<>();
            if (componentMap.get("TEMPORAL_SALARY") != null && componentMap.get("TEMPORAL_SALARY").getProjections() != null) {
                for (MonthProjection primeProjection : componentMap.get("TEMPORAL_SALARY").getProjections()) {
                    double totalAmount = feeComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(totalAmount * 0.0735));
                    projections.add(projection);
                }
                feeComponent.setProjections(projections);
            }else {
                feeComponent.setAmount(BigDecimal.valueOf(0));
                feeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,feeComponent.getAmount()));
            }
            component.add(feeComponent);
        }else{
            PaymentComponentDTO feeComponent = new PaymentComponentDTO();
            feeComponent.setPaymentComponent("FEE_TEMP");
            feeComponent.setAmount(BigDecimal.valueOf(0));
            feeComponent.setProjections(Shared.generateMonthProjectionV2(period,range,feeComponent.getAmount()));
            component.add(feeComponent);
        }
        //log.debug("component -> {}", "feeTemporaries");
    }
    public void socialSecurityUniqueBonus(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range, List<ParametersDTO> ssBonusList) {
            Map<String, ParametersDTO> ssBonusMap = new ConcurrentHashMap<>();
            Map<String, Double> cacheBonus = new ConcurrentHashMap<>();
            createCache(ssBonusList, ssBonusMap, cacheBonus, ((parameter, mapParameter) -> {}));
            // Obtener el valor de "Bonificación Única"
            //Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            //PaymentComponentDTO uniqueBonusComponent = componentMap.get("UNIQUE_BONUS");
            List<String> vacationComponents = Arrays.asList("UNIQUE_BONUS");
            //log.info("vacationComponents -> {}", component);
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            PaymentComponentDTO uniqueBonusComponent = componentMap.get("UNIQUE_BONUS");
            // Use the period to get the ParametersDTO from the sodexoMap
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            // Obtener el valor del parámetro {SS Bono}
            ParametersDTO ssBonusParameterBase = ssBonusMap.get(nextPeriod);
            double ssBonus = ssBonusParameterBase != null ? ssBonusParameterBase.getValue()/100 : 0.0;
            //log.info("ssBonus -> {}", ssBonus);
            // Calcular el valor de "Seguridad Social Bonificación Única"
            double socialSecurityUniqueBonusValue = uniqueBonusComponent.getAmount().doubleValue() * ssBonus;
            PaymentComponentDTO socialSecurityUniqueBonusComponent = new PaymentComponentDTO();
            socialSecurityUniqueBonusComponent.setPaymentComponent("SOCIAL_SECURITY_UNIQUE_BONUS");
            socialSecurityUniqueBonusComponent.setAmount(BigDecimal.valueOf(socialSecurityUniqueBonusValue));
            String category = findCategory(classEmployee);
            double lastValidSocialSecurityUniqueBonus = ssBonus;
            if (category.equals("P")) {
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : uniqueBonusComponent.getProjections()) {
                // Crear el componente de pago "Seguridad Social Bonificación Única"
                    ParametersDTO bonusParameter = ssBonusMap.get(primeProjection.getMonth());
                    double bonus;
                    if(bonusParameter != null){
                        bonus = bonusParameter.getValue()/100;
                        lastValidSocialSecurityUniqueBonus = bonus;
                    }else{
                        bonus = lastValidSocialSecurityUniqueBonus;
                    }
                    BigDecimal  amount = BigDecimal.valueOf(primeProjection.getAmount().doubleValue() * bonus);
                   /* BigDecimal amount;
                    if(bonusParameter != null){
                        amount = BigDecimal.valueOf(uniqueBonusComponent.getAmount().doubleValue() * bonus);
                        lastValidSocialSecurityUniqueBonus = amount;
                    }else{
                        amount = lastValidSocialSecurityUniqueBonus;
                    }*/
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(amount);
                    projections.add(projection);
                }
                socialSecurityUniqueBonusComponent.setProjections(projections);
            }else {
                socialSecurityUniqueBonusComponent.setAmount(BigDecimal.valueOf(0));
                socialSecurityUniqueBonusComponent.setProjections(Shared.generateMonthProjectionV2(period, range, socialSecurityUniqueBonusComponent.getAmount()));
            }
            // Añadir el componente de pago a la lista de componentes
            component.add(socialSecurityUniqueBonusComponent);
    }
}