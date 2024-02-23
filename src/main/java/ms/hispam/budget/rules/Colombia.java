package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.MonthProjection;
import ms.hispam.budget.dto.ParametersDTO;
import ms.hispam.budget.dto.PaymentComponentDTO;
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
        salaryComponent.setProjections(Shared.generateMonthProjection(period,range,salaryComponent.getAmount()));
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
        ////log.debug("maxCommission -> {}", maxCommission);
        ////log.debug("period -> {}", period);
        ////log.debug("sumCommission -> {}", sumCommission);
        ////log.debug("period -> {}", period);
        ////log.debug("cacheCommission -> {}", cacheCommission);
        BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(period) == null ? 0.0 : cacheCommission.get(period));
        ////log.debug("commission -> {}", commission);
        if (!classEmployee.equals("T") && maxCommission != 0.0) {
            commissionComponent.setAmount(commission.multiply(BigDecimal.valueOf(maxCommission / sumCommission.doubleValue())));
        } else {
            commissionComponent.setAmount(BigDecimal.ZERO);
        }
        commissionComponent.setProjections(Shared.generateMonthProjection(period,range,commissionComponent.getAmount()));
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
        yearMonth = yearMonth.plusMonths(1);
        period = yearMonth.format(formatter);
        BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(period) == null ? 0.0 : cacheCommission.get(period));
        if (!classEmployee.equals("T") && maxCommission != 0.0) {
            commissionComponent.setAmount(commission.multiply(BigDecimal.valueOf(maxCommission / sumCommission.doubleValue())));
        } else {
            commissionComponent.setAmount(BigDecimal.ZERO);
        }
        commissionComponent.setProjections(Shared.generateMonthProjection(period,range,commissionComponent.getAmount()));
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
        salaryComponent.setProjections(Shared.generateMonthProjection(period,range,salaryComponent.getAmount()));
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
           // //log.debug("endMonth -> {}", endMonth);
           for (int m = startMonth; m <= endMonth; m++) {
               int yearOffset = (m - 1) / 12;
               int monthOffset = (m - 1) % 12 + 1;
               ////log.debug("yearOffset -> {}", yearOffset);
               ////log.debug("monthOffset -> {}", monthOffset);
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
        for (int i = 0; i < range + 1; i++) {
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
        double highestAmountSoFar = Math.max(pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue());
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, "P", SALARY).get("salaryComponent");
        if (category.equals("P")){
            if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
                String salaryType = paymentComponentDTO.getSalaryType();
                if (salaryType.equals("INTEGRAL")) {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        ////log.debug("projection.getMonth() -> {}", projection.getMonth());
                        ////log.debug("cacheSalaryIntegralMin.get(projection.getMonth()) -> {}", cacheSalaryIntegralMin.get(projection.getMonth()));
                        Double salaryIntegralMinMap = cacheSalaryIntegralMin.get(projection.getMonth());
                        double salaryIntegralMin = salaryIntegralMinMap != null ? salaryIntegralMinMap : 0.0;
                        ////log.debug("salaryIntegralMin -> {}", salaryIntegralMin);
                        if (salaryIntegralMin != 0.0){
                            if (projection.getAmount().doubleValue() <= salaryIntegralMin) {
                                highestAmountSoFar = Math.max(highestAmountSoFar, salaryIntegralMin);
                                projection.setAmount(BigDecimal.valueOf(salaryIntegralMin));
                            } else {
                                projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                            }
                        }else{
                            projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                        }
                    }
                } else {
                    for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                        Double salaryMinMap = cacheSalaryMin.get(projection.getMonth());
                        double salaryMin = salaryMinMap != null ? salaryMinMap : 0.0;
                        //("salaryMin -> {}", salaryMin);
                        if (salaryMin != 0.0){
                            if (projection.getAmount().doubleValue() <= salaryMin) {
                                highestAmountSoFar = Math.max(highestAmountSoFar, salaryMin);
                                projection.setAmount(BigDecimal.valueOf(salaryMin));
                            } else {
                                projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                            }
                        }else{
                            projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                        }
                    }
                }
            }
        }else {
            paymentComponentDTO.setAmount(BigDecimal.ZERO);
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
        }
        component.add(paymentComponentDTO);
        ////log.debug("component -> {}","salary");
    }

    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    public void temporalSalary(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> legalSalaryMinList, List<ParametersDTO> revisionSalaryMinList, List<ParametersDTO> revisionSalaryMinEttList, List<ParametersDTO> legalSalaryIntegralMinList){
        // Crear los mapas como variables locales
        Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
        createCache(legalSalaryMinList, salaryMap, cacheSalaryMin, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938001Component = componentMap.get(PC938001);
        PaymentComponentDTO pc938005Component = componentMap.get(PC938005);
        String category = findCategory(classEmployee);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO)createSalaryComponent(pc938001Component, pc938005Component, category, period, range, "T", TEMPORAL_SALARY).get("salaryComponent");
        if (paymentComponentDTO != null && paymentComponentDTO.getAmount().doubleValue() != 0.0) {
            double highestAmountSoFar = Math.max(pc938001Component == null ? 0.0 : pc938001Component.getAmount().doubleValue(), pc938005Component == null ? 0.0 : pc938005Component.getAmount().doubleValue());
            if (paymentComponentDTO.getProjections() != null){
                for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                    Double salaryMinMap = cacheSalaryMin.get(projection.getMonth());
                    double salaryMin = salaryMinMap != null ? salaryMinMap : 0.0;
                    if (salaryMin != 0.0){
                        if (projection.getAmount().doubleValue() <= salaryMin) {
                            highestAmountSoFar = Math.max(highestAmountSoFar, salaryMin);
                            projection.setAmount(BigDecimal.valueOf(salaryMin));
                        } else {
                            projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                        }
                    }else{
                        projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
                    }
                }
            }
        }
        component.add(paymentComponentDTO);
        ////log.debug("component -> {}", "temporalSalary");
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
       // //log.debug("component -> {}", "salaryPra");
    }

    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters,String period, Integer range, String classEmployee){
        // Obtén los componentes necesarios para el cálculo
        List<String> salaryComponents = Arrays.asList("SALARY", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> salaryComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

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
                                       ////log.debug("differPercent si es menor -> {}", differPercent);
                                       differPercent = percent - differPercent;
                                   } else {
                                       differPercent = 0;
                                   }
                               } else {
                                   differPercent = percent;
                               }
                               ////log.debug("differPercent -> {}", differPercent);
                               revisionSalaryAmount = amount * (1 + (differPercent));
                               ////log.debug("revisionSalaryAmount -> {}", revisionSalaryAmount);
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
                               ////log.debug("differPercent -> {}", differPercent);
                               revisionSalaryAmount = amount * (1 + (differPercent));
                               ////log.debug("revisionSalaryAmount -> {}", revisionSalaryAmount);
                               paymentComponentDTO.getProjections().get(i).setAmount(BigDecimal.valueOf(revisionSalaryAmount));
                           }
                       }
                   }
               }
           }
        }
    }
    public void commission(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission, List<ParametersDTO> commissionList) {
        //commission param
        ////log.debug("sumCommission -> {}", sumCommission);
        Map<String, Double> cacheCommission = new ConcurrentHashMap<>();
        createCommissionCache(commissionList, period, range, cacheCommission);
        ////log.debug("cacheCommission -> {}", cacheCommission);
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc938003Component = componentMap.get(PC938003);
        PaymentComponentDTO pc938012Component = componentMap.get(PC938012);
        ////log.debug("pc938003Component -> {}", pc938003Component);
        ////log.debug("pc938012Component -> {}", pc938012Component);
        String category = findCategory(classEmployee);
        double pc938003Amount = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
        double pc938012Amount = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
        double maxCommission = Math.max(pc938003Amount, pc938012Amount);
        PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO) createCommissionComponent(pc938003Component, pc938012Component, category, period, range, cacheCommission, sumCommission).get("commissionComponent");
        ////log.debug("paymentComponentDTO -> {}", paymentComponentDTO);
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
           //SI($I5<>"T";//AJ$10/12*($M5/SUMA($M$4:$M$15));0)
           // //log.debug("projection.getMonth() -> {}", projection.getMonth());
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
        ////log.debug("component -> {}", "commission");
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
        ////log.debug("surcharges -> {}", surcharges);
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
                monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
            }
        }else {
            monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
            monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
        }
        component.add(monthlyPrimeComponent);
       // //log.debug("component -> {}", "prodMonthPrime");
    }

    public void consolidatedVacation(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> vacationComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION","SALARY_PRA","TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
            //double totalAmountBase = salary + overtime + surcharges + commission;
            BigDecimal totalAmountBase = salary.add(overtime).add(surcharges).add(commission);
            BigDecimal result = totalAmountBase.divide(BigDecimal.valueOf(24), 2, RoundingMode.HALF_UP);
            vacationComponent.setAmount(result);
            // Calcular el Consolidado de Vacaciones para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : componentMap.get("SALARY").getProjections()) {
                BigDecimal totalAmount = vacationComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular el costo del Consolidado de Vacaciones
                BigDecimal vacationCost = BigDecimal.valueOf(totalAmount.doubleValue() / 24);
                ////log.debug("vacationCost -> {}", vacationCost);
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
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        ////log.debug("component -> {}", "consolidatedVacation");
    }
    public void consolidatedSeverance(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        List<String> severanceComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION","SALARY_PRA","TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> severanceComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        ////log.debug("component -> {}", "consolidatedSeverance");
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
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        ////log.debug("component -> {}", "consolidatedSeveranceInterest");
    }

    public void transportSubsidy(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, List<ParametersDTO> subsidyMinList) {
        String category = findCategory(classEmployee);
        Map<String, ParametersDTO>  subsidyMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSubsidy = new ConcurrentHashMap<>();
        createCache(subsidyMinList, subsidyMap, cacheSubsidy,  (parameter, mapParameter) -> {});
        // Obtén los componentes necesarios para el cálculo
        List<String> subsidyComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> subsidyComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        //String periodSubsidy = subsidyMin!=null ? subsidyMin.getPeriod() : "";
        //int subsidyMonth = Integer.parseInt(periodSubsidy.substring(4, 6));
        //int subsidyYear = Integer.parseInt(periodSubsidy.substring(0, 4));
        //total base
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
        double subsidyMinNextValue = subsidyMinNext != null ? subsidyMinNext.getValue() : 0.0;
        double totalAmountBase = salary + overtime + surcharges + commission;
        transportSubsidyComponent.setAmount(BigDecimal.valueOf(totalAmountBase < 2 * legalSalaryMinInternal ? subsidyMinNextValue : 0));
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        BigDecimal lastValidSubsidyValue = BigDecimal.ZERO;
        if (category.equals("P") && salaryType.equals("BASE")) {
            // Calcular el Subsidio de Transporte para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                BigDecimal totalAmount = subsidyComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                        .map(MonthProjection::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                ParametersDTO subsidyMinInternalParam = subsidyMap.get(primeProjection.getMonth());
                double subsidyMinInternalValue = subsidyMinInternalParam != null ? subsidyMinInternalParam.getValue() : 0.0;
                // Calcular el costo del Subsidio de Transporte
                BigDecimal transportSubsidyCost;
                if (totalAmount.doubleValue() < 2 * legalSalaryMinInternal) {
                    transportSubsidyCost = BigDecimal.valueOf(subsidyMinInternalValue);
                    lastValidSubsidyValue = transportSubsidyCost;
                } else {
                    transportSubsidyCost = lastValidSubsidyValue;
                }
                // Crear una proyección para este mes
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(transportSubsidyCost);
                projections.add(projection);
            }
            transportSubsidyComponent.setProjections(projections);
        }else {
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
        }
        component.add(transportSubsidyComponent);
        ////log.debug("component -> {}", "transportSubsidy");
    }
    public void contributionBox(List<PaymentComponentDTO> component, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Obtén los componentes necesarios para el cálculo
        List<String> boxComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> boxComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
            boxContributionComponent.setProjections(Shared.generateMonthProjection(period,range,boxContributionComponent.getAmount()));
        }
        component.add(boxContributionComponent);
        ////log.debug("component -> {}", "contributionBox");
    }
    public void companyHealthContribution(List<PaymentComponentDTO> component, String classEmployee,  List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        List<String> healthComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION", "SALARY_PRA", "TEMPORAL_SALARY");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> healthComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
        // Crear un nuevo PaymentComponentDTO para el Aporte Salud Empresa
        PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
        healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA");
        double totalAmountBase = salary + overtime + surcharges + commission;
        ////log.debug("totalAmountBase -> {}", totalAmountBase);
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        BigDecimal healthContributionBase;
        if (category.equals("APR") || category.equals("PRA")) {
            healthContributionBase = BigDecimal.valueOf(totalAmountBase * 0.04);
        } else if (category.equals("P")) {
            if (salaryType.equals("BASE")) {
                if (totalAmountBase > 25 * legalSalaryMinInternal) {
                    healthContributionBase = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                } else if (totalAmountBase > 10 * legalSalaryMinInternal) {
                    healthContributionBase = BigDecimal.valueOf(totalAmountBase * 0.085);
                } else {
                    healthContributionBase = BigDecimal.ZERO;
                }
            } else { // salaryType is INTEGRAL
                BigDecimal seventyPercentTotal = BigDecimal.valueOf(totalAmountBase * 0.70);
                double seventyPercentTotalDouble = seventyPercentTotal.doubleValue();
                if (seventyPercentTotalDouble > 25 * legalSalaryMinInternal) {
                    //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                    healthContributionBase = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                } else {
                    //c = seventyPercentTotal.multiply(BigDecimal.valueOf(0.085));
                    healthContributionBase = BigDecimal.valueOf(seventyPercentTotalDouble * 0.085);
                }
            }
        } else {
            healthContributionBase = BigDecimal.ZERO;
        }
        healthContributionComponent.setAmount(healthContributionBase);
        if (!category.equals("T")) {
            // Calcular el Aporte Salud Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    BigDecimal totalAmount = healthComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .map(MonthProjection::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Calcular el Aporte Salud Empresa
                    BigDecimal healthContribution;
                    if (category.equals("APR") || category.equals("PRA")) {
                        healthContribution = totalAmount.multiply(BigDecimal.valueOf(0.04));
                    } else if (category.equals("P")) {
                        if (salaryComponent.getSalaryType().equals("BASE")) {
                            if (totalAmount.doubleValue() > 25 * legalSalaryMinInternal) {
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                            } else if (totalAmount.doubleValue() > 10 * legalSalaryMinInternal) {
                                healthContribution = BigDecimal.valueOf(totalAmount.doubleValue() * 0.085);
                            } else {
                                healthContribution = BigDecimal.ZERO;
                            }
                        } else { // salaryType is INTEGRAL
                            BigDecimal seventyPercentTotal = totalAmount.multiply(BigDecimal.valueOf(0.70));
                            double seventyPercentTotalDouble = seventyPercentTotal.doubleValue();
                            if (seventyPercentTotalDouble > 25 * legalSalaryMinInternal) {
                                //healthContribution = legalSalaryMinInternal.multiply(BigDecimal.valueOf(25)).multiply(BigDecimal.valueOf(0.085));
                                healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
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
            }else {
                healthContributionComponent.setAmount(BigDecimal.valueOf(0));
                healthContributionComponent.setProjections(Shared.generateMonthProjection(period,range,healthContributionComponent.getAmount()));
            }
        }else {
            healthContributionComponent.setAmount(BigDecimal.valueOf(0));
            healthContributionComponent.setProjections(Shared.generateMonthProjection(period,range,healthContributionComponent.getAmount()));
        }
        component.add(healthContributionComponent);
        //log.debug("component -> {}", "companyHealthContribution");
    }
    public void companyRiskContribution(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> riskComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
        double riskContributionBase;
        if (salaryType.equals("BASE")) {
            if (totalAmountBase > 25 * legalSalaryMinInternal) {
                riskContributionBase = 25 * legalSalaryMinInternal * 0.00881;
            } else {
                riskContributionBase = totalAmountBase * 0.00881;
            }
        } else { // salaryType is INTEGRAL
            double adjustedTotalAmount = totalAmountBase * 0.7;
            if (adjustedTotalAmount > 25 * legalSalaryMinInternal) {
                riskContributionBase = 25 * legalSalaryMinInternal * 0.00881;
            } else {
                riskContributionBase = adjustedTotalAmount * 0.00881;
            }
        }
        riskContributionComponent.setAmount(BigDecimal.valueOf(riskContributionBase));
        if (category.equals("P")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (salaryComponent.getSalaryType().equals("BASE")) {
                        if (totalAmount > 25 * legalSalaryMinInternal) {
                            riskContribution = 25 * legalSalaryMinInternal * 0.00881;
                        } else {
                            riskContribution = totalAmount * 0.00881;
                        }
                    } else { // salaryType is INTEGRAL
                        double adjustedTotalAmount = totalAmount * 0.7;
                        if (adjustedTotalAmount > 25 * legalSalaryMinInternal) {
                            riskContribution = 25 * legalSalaryMinInternal * 0.00881;
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
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
        //log.debug("component -> {}", "companyRiskContribution");
    }
    public void companyRiskContributionTrainee(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> riskComponents = Arrays.asList("SALARY_PRA", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
        riskContributionComponent.setAmount(totalAmountBase > 25 * legalSalaryMinInternal ? BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881) : BigDecimal.valueOf(totalAmountBase * 0.00881));
        if (category.equals("APR") || category.equals("PRA")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        riskContribution = 25 * legalSalaryMinInternal * 0.00881;
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
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
        //log.debug("component -> {}", "companyRiskContributionTrainee");
    }
    public void companyRiskContributionTemporaries(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        //TODO : CAMBIAR COMMISION POR COMMISSION TEMPORAL
        List<String> riskComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
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
        riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_TEMPORALES");
        riskContributionComponent.setAmount(totalAmountBase > 25 * legalSalaryMinInternal ? BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881) : BigDecimal.valueOf(totalAmountBase * 0.00881));
        if (category.equals("T")) {
            // Calcular el Aporte Riesgo Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection riskProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(riskProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Riesgo Empresa
                    double riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        riskContribution = 25 * legalSalaryMinInternal * 0.00881;
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
            }else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
            }
        }else {
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period,range,riskContributionComponent.getAmount()));
        }
        component.add(riskContributionComponent);
        //log.debug("component -> {}", "companyRiskContributionTemporaries");
    }
    public void icbfContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> icbfComponents = Arrays.asList(SALARY, "HHEE", "SURCHARGES", "COMMISSION", SALARY_PRA);
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> icbfComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                icbfContributionComponent.setProjections(Shared.generateMonthProjection(period,range,icbfContributionComponent.getAmount()));
            }
        }else {
            icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
            icbfContributionComponent.setProjections(Shared.generateMonthProjection(period,range,icbfContributionComponent.getAmount()));
        }
        component.add(icbfContributionComponent);
        //log.debug("component -> {}", "icbfContribution");
    }
    public void senaContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> senaComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> senaComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
            }
        }else {
            senaContributionComponent.setAmount(BigDecimal.valueOf(0));
            senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
        }
        component.add(senaContributionComponent);
        //log.debug("component -> {}", "senaContribution");
    }
    public void companyPensionContribution(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> pensionComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> pensionComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
        String salaryType = salaryComponent == null ? "" : salaryComponent.getSalaryType();
        double pensionContributionBase;
        if (salaryType.equals("BASE") && totalAmountBase > 25 * legalSalaryMinInternal) {
            pensionContributionBase = 25 * legalSalaryMinInternal * 0.12;
        } else if (salaryType.equals("INTEGRAL") &&  0.70 * totalAmountBase > 25 * legalSalaryMinInternal) {
            pensionContributionBase = 25 * legalSalaryMinInternal * 0.12;
        } else if (salaryType.equals("BASE")) {
            pensionContributionBase = totalAmountBase * 0.12;
        } else {
            pensionContributionBase = 0.70 * totalAmountBase * 0.12;
        }
        pensionContributionComponent.setAmount(BigDecimal.valueOf(pensionContributionBase));
        if (category.equals("P")) {
            // Calcular el Aporte Pensión Empresa para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = pensionComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();

                    // Calcular el Aporte Pensión Empresa
                    double pensionContribution;
                    if (salaryComponent.getSalaryType().equals("BASE") && totalAmount > 25 * legalSalaryMinInternal) {
                        pensionContribution = 25 * legalSalaryMinInternal * 0.12;
                    } else if (!salaryComponent.getSalaryType().equals("BASE")) {
                        pensionContribution = totalAmount * 0.70 * 0.12;
                    } else {
                        pensionContribution = totalAmount * 0.12;
                    }

                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(pensionContribution));
                    projections.add(projection);
                }
                pensionContributionComponent.setProjections(projections);
            }else {
                pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
                pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
            }
        }else {
            pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
            pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
        }
        component.add(pensionContributionComponent);
        //log.debug("component -> {}", "companyPensionContribution");
    }

    public void sodexo(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range, List<ParametersDTO> sodexoList) {
        Map<String, ParametersDTO> sodexoMap = new ConcurrentHashMap<>();
        Map<String, Double> cacheSodexo = new ConcurrentHashMap<>();
        createCache(sodexoList, sodexoMap, cacheSodexo,  (parameter, mapParameter) -> {});
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        // Obtén los componentes necesarios para el cálculo
        List<String> sodexoComponents = Arrays.asList("SALARY", "COMMISSION", "HHEE", "SURCHARGES");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> sodexoComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
        ParametersDTO sodexoBase = sodexoMap.get(nextPeriod);
        double sodexoValueBase = sodexoBase != null ? sodexoBase.getValue() : 0.0;
        // Crear un nuevo PaymentComponentDTO para Sodexo
        PaymentComponentDTO sodexoComponent = new PaymentComponentDTO();
        sodexoComponent.setPaymentComponent("SODEXO");
        sodexoComponent.setAmount(BigDecimal.valueOf((category.equals("P") || category.equals("APR") || category.equals("PRA")) && totalAmountBase < 2 * legalSalaryMinInternal ? sodexoValueBase : 0));
        if (!category.equals("T")) {
            // Calcular el valor de Sodexo para cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            double lastValidSodexoValue = 0.0;
            if (salaryComponent != null && salaryComponent.getProjections() != null){
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    ParametersDTO sodexo = sodexoMap.get(primeProjection.getMonth());
                    String periodSodexo = sodexo != null ? sodexo.getPeriod() : "";
                    double sodexoValue = sodexo != null ? sodexo.getValue() : 0.0;
                    int sodexoMonth = 0;
                    if (periodSodexo.length() >= 6) {
                        // Obtén el mes del período sodexo
                        sodexoMonth = Integer.parseInt(periodSodexo.substring(4, 6));
                    }
                    double totalAmount = sodexoComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    double sodexoContribution;
                    if (sodexoMonth != 0 && Integer.parseInt(primeProjection.getMonth().substring(4, 6)) >= sodexoMonth) {
                        if ((category.equals("P") || category.equals("APR") || category.equals("PRA")) && totalAmount < 2 * legalSalaryMinInternal) {
                            sodexoContribution = sodexoValue;
                            lastValidSodexoValue = sodexoValue;
                        } else {
                            sodexoContribution = 0;
                            lastValidSodexoValue = 0;
                        }
                    } else {
                        sodexoContribution = lastValidSodexoValue;
                    }
                    // Crear una proyección para este mes
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(sodexoContribution));
                    projections.add(projection);
                }
                sodexoComponent.setProjections(projections);
            }else {
                sodexoComponent.setAmount(BigDecimal.valueOf(0));
                sodexoComponent.setProjections(Shared.generateMonthProjection(period,range,sodexoComponent.getAmount()));
            }
        }else {
            sodexoComponent.setAmount(BigDecimal.valueOf(0));
            sodexoComponent.setProjections(Shared.generateMonthProjection(period,range,sodexoComponent.getAmount()));
        }
        component.add(sodexoComponent);
        //log.debug("component -> {}", "sodexo");
    }
    public void sena(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;

        // Obtén los componentes necesarios para el cálculo
        List<String> newComponents = Arrays.asList("SALARY", "HHEE", "SURCHARGES", "COMMISSION");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> newComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

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
            newComponent.setProjections(Shared.generateMonthProjection(period,range,newComponent.getAmount()));
        }
        newComponent.setProjections(projections);
        component.add(newComponent);
        //log.debug("component -> {}", "sena");
    }
    public void senaTemporales(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;

        // Obtén los componentes necesarios para el cálculo
        List<String> newComponents = Arrays.asList("SALARY_TEMP", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> newComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

        PaymentComponentDTO salaryComponent = componentMap.get("SALARY_TEMP");
        PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
        PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
        PaymentComponentDTO commissionComponent = componentMap.get("COMMISSION_TEMP");

        double salary = salaryComponent == null ? 0.0 : salaryComponent.getAmount().doubleValue();
        double overtime = overtimeComponent == null ? 0.0 : overtimeComponent.getAmount().doubleValue();
        double surcharges = surchargesComponent == null ? 0.0 : surchargesComponent.getAmount().doubleValue();
        double commission = commissionComponent == null ? 0.0 : commissionComponent.getAmount().doubleValue();

        double totalAmountBase = salary + overtime + surcharges + commission;

        // Crear un nuevo PaymentComponentDTO para el nuevo componente
        PaymentComponentDTO newComponent = new PaymentComponentDTO();
        newComponent.setPaymentComponent("SENA_TEMP");
        double amount = 0;
        if (category.equals("T") && totalAmountBase >= 10 * legalSalaryMinInternal) {
            amount = totalAmountBase * 0.02;
        }
        newComponent.setAmount(BigDecimal.valueOf(amount));
        List<MonthProjection> projections = new ArrayList<>();

        if (salaryComponent != null && salaryComponent.getProjections() != null && newComponent.getAmount().doubleValue() > 0) {
            for (MonthProjection monthProjection : salaryComponent.getProjections()) {
                double totalAmount = newComponents.stream()
                        .map(componentMap::get)
                        .filter(Objects::nonNull)
                        .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                        .filter(projection -> projection.getMonth().equals(monthProjection.getMonth()))
                        .mapToDouble(projection -> projection.getAmount().doubleValue())
                        .sum();
                MonthProjection projection = new MonthProjection();
                projection.setMonth(monthProjection.getMonth());
                if (category.equals("T") && totalAmount >= 10 * legalSalaryMinInternal) {
                    projection.setAmount(BigDecimal.valueOf(totalAmount * 0.02));
                }else {
                    projection.setAmount(BigDecimal.valueOf(0));
                }
                projections.add(projection);
            }
            newComponent.setProjections(projections);
        }else {
            newComponent.setAmount(BigDecimal.valueOf(0));
            newComponent.setProjections(Shared.generateMonthProjection(period,range,newComponent.getAmount()));
        }
        component.add(newComponent);
        //log.debug("component -> {}", "senaTemporales");
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
                uniqueBonusComponent.setProjections(Shared.generateMonthProjection(period,range,uniqueBonusComponent.getAmount()));
            }
        }else {
            uniqueBonusComponent.setAmount(BigDecimal.valueOf(0));
            uniqueBonusComponent.setProjections(Shared.generateMonthProjection(period,range,uniqueBonusComponent.getAmount()));
        }
        component.add(uniqueBonusComponent);
        //log.debug("component -> {}", "uniqueBonus");
    }

    private double calculateUniqueBonus(String salaryType, double baseSalary, double bonusTarget) {
        Map<String, Double> bonusMultipliers = new HashMap<>();
        bonusMultipliers.put("BASE", 14.12);
        bonusMultipliers.put("INTEGRAL", 12.0);
        double multiplier = bonusMultipliers.getOrDefault(salaryType, 0.0);
        //log.debug("salaryType: " + salaryType + ", baseSalary: " + baseSalary + ", bonusTarget: " + bonusTarget + ", multiplier: " + multiplier);
        if (bonusTarget == 0) {
            bonusTarget = 0.00;
        }
        double bonusTargetPercent = bonusTarget / 100;
        return (baseSalary * multiplier * bonusTargetPercent) / 12;
    }
    public void AuxilioDeTransporteAprendizSena(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("PRA") || category.equals("APR")) {
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            ParametersDTO subsidyTransport = getParametersById(parameters, 48);
            double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            // Obtén los componentes necesarios para el cálculo
            List<String> transportSubsidyComponents = Arrays.asList("SALARY_PRA", "HHEE", "SURCHARGES", "COMMISSION");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> transportSubsidyComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                transportSubsidyComponent.setAmount(totalAmountBase <= 2 * legalSalaryMinInternal ? BigDecimal.valueOf(legalSalaryMinInternal) : BigDecimal.ZERO);
                // Calcular el Auxilio de Transporte Aprendiz Sena para cada proyección
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
                    BigDecimal transportSubsidy;
                    if (totalAmount.doubleValue() <= 2 * legalSalaryMinInternal) {
                        transportSubsidy = subsidyTransport != null ? BigDecimal.valueOf(subsidyTransport.getValue()) : BigDecimal.ZERO;
                    } else {
                        transportSubsidy = BigDecimal.ZERO;
                    }
                    projection.setAmount(transportSubsidy);
                    projections.add(projection);
                }
                transportSubsidyComponent.setProjections(projections);
            }else {
                transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
                transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
            }
            component.add(transportSubsidyComponent);
        }else {
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("AUXILIO_TRANSPORTE_APRENDIZ_SENA");
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
            component.add(transportSubsidyComponent);
        }
        //log.debug("component -> {}", "AuxilioDeTransporteAprendizSena");
    }
    public void AuxilioConectividadDigital(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range, String position) {
        ////log.debug("position: " + position);
        String category = findCategory(classEmployee);
        PaymentComponentDTO digitalConnectivityAidComponent = new PaymentComponentDTO();
        digitalConnectivityAidComponent.setPaymentComponent("AUXILIO_CONECTIVIDAD_DIGITAL");
        if (category.equals("PRA") || category.equals("APR")) {
            double digitalConnectivityAidValue = findExcludedPositions(position, parameters);
            digitalConnectivityAidComponent.setAmount(BigDecimal.valueOf(digitalConnectivityAidValue));
            digitalConnectivityAidComponent.setProjections(Shared.generateMonthProjection(period,range,digitalConnectivityAidComponent.getAmount()));
        }else {
            digitalConnectivityAidComponent.setAmount(BigDecimal.valueOf(0));
            digitalConnectivityAidComponent.setProjections(Shared.generateMonthProjection(period,range,digitalConnectivityAidComponent.getAmount()));
        }
        ////log.debug("digitalConnectivityAidComponent: " + digitalConnectivityAidComponent);
        component.add(digitalConnectivityAidComponent);
        //log.debug("component -> {}", "AuxilioConectividadDigital");
    }
    public Double findExcludedPositions (String position, List<ParametersDTO> parameters) {
        ParametersDTO digitalConnectivityAid = getParametersById(parameters, 51);
        double digitalConnectivityAidValue = digitalConnectivityAid != null ? digitalConnectivityAid.getValue() : 0.0;
        String[] excludedPositions = {
                "Puestos Excluídos Conectividad Digital",
                "Analista Centro de Experiencia",
                "Analista asesor comercial PAP",
                "Analista inventarios",
                "Analista asesor comercial fuerza de venta prepago",
                "profesional lider centro de experiencia",
                "analista integral centro de experiencia"
        };
        if (Arrays.asList(excludedPositions).contains(position)) {
            return 0.0;
        } else {
            return digitalConnectivityAidValue;
        }
    }
    public void commissionTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, BigDecimal sumCommission, List<ParametersDTO> commissionList) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            Map<String, Double> cacheCommission = new ConcurrentHashMap<>();
            createCommissionCache(commissionList, period, range, cacheCommission);
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO pc938003Component = componentMap.get(PC938003);
            PaymentComponentDTO pc938012Component = componentMap.get(PC938012);
            double pc938003Amount = pc938003Component == null ? 0.0 : pc938003Component.getAmount().doubleValue();
            double pc938012Amount = pc938012Component == null ? 0.0 : pc938012Component.getAmount().doubleValue();
            double maxCommission = Math.max(pc938003Amount, pc938012Amount);
            PaymentComponentDTO paymentComponentDTO = (PaymentComponentDTO) createTemporalCommissionComponent(pc938003Component, pc938012Component, category, period, range, cacheCommission, sumCommission).get("commissionComponent");
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : paymentComponentDTO.getProjections()) {
                projection.setMonth(projection.getMonth());
                BigDecimal commission = BigDecimal.valueOf(cacheCommission.get(projection.getMonth()) == null ? 0.0 : cacheCommission.get(projection.getMonth()));
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
            PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
            paymentComponentDTO.setPaymentComponent("COMMISSION_TEMP");
            paymentComponentDTO.setAmount(BigDecimal.valueOf(0));
            paymentComponentDTO.setProjections(Shared.generateMonthProjection(period,range,paymentComponentDTO.getAmount()));
            component.add(paymentComponentDTO);
        }
        //log.debug("component -> {}", "commissionTemporal");
    }
    public void prodMonthPrimeTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
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
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(monthlyProvisionBase);
                    projections.add(projection);
                }
                monthlyPrimeComponent.setProjections(projections);
            }else {
                monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
                monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
            }
            component.add(monthlyPrimeComponent);
        }else {
            PaymentComponentDTO monthlyPrimeComponent = new PaymentComponentDTO();
            monthlyPrimeComponent.setPaymentComponent("PRIMA_MENSUAL_TEMPORAL");
            monthlyPrimeComponent.setAmount(BigDecimal.valueOf(0));
            monthlyPrimeComponent.setProjections(Shared.generateMonthProjection(period,range,monthlyPrimeComponent.getAmount()));
            component.add(monthlyPrimeComponent);
        }
        //log.debug("component -> {}", "prodMonthPrimeTemporal");
    }
    public void consolidatedVacationTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            List<String> vacationComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
            //log.debug("component: " + component);
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> vacationComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> existing));
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
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(result);
                    projections.add(projection);
                }
                vacationComponent.setProjections(projections);
            }else {
                vacationComponent.setAmount(BigDecimal.valueOf(0));
                vacationComponent.setProjections(Shared.generateMonthProjection(period,range,vacationComponent.getAmount()));
            }
            component.add(vacationComponent);
        }else {
            PaymentComponentDTO vacationComponent = new PaymentComponentDTO();
            vacationComponent.setPaymentComponent("CONSOLIDADO_VACACIONES_TEMPORAL");
            vacationComponent.setAmount(BigDecimal.valueOf(0));
            vacationComponent.setProjections(Shared.generateMonthProjection(period,range,vacationComponent.getAmount()));
            component.add(vacationComponent);
        }
        //log.debug("component -> {}", "consolidatedVacationTemporal");
    }
    public void consolidatedSeveranceTemporal(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
    String category = findCategory(classEmployee);
    // Obtén los componentes necesarios para el cálculo
    List<String> severanceComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", COMMISSION_TEMP);
    Map<String, PaymentComponentDTO> componentMap = component.stream()
            .filter(c -> severanceComponents.contains(c.getPaymentComponent()))
            .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(result);
                    projections.add(projection);
                }
                severanceComponent.setProjections(projections);
            }else {
                severanceComponent.setAmount(BigDecimal.valueOf(0));
                severanceComponent.setProjections(Shared.generateMonthProjection(period,range,severanceComponent.getAmount()));
            }
            component.add(severanceComponent);
        }else {
            PaymentComponentDTO severanceComponent = new PaymentComponentDTO();
            severanceComponent.setPaymentComponent("CONSOLIDADO_CESANTIAS_TEMPORAL");
            severanceComponent.setAmount(BigDecimal.valueOf(0));
            severanceComponent.setProjections(Shared.generateMonthProjection(period,range,severanceComponent.getAmount()));
            component.add(severanceComponent);
        }
        //log.debug("component -> {}", "consolidatedSeveranceTemporal");
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
                    projection.setAmount(severanceInterestComponent.getAmount());
                    projections.add(projection);
                }
                severanceInterestComponent.setProjections(projections);
            }else {
                severanceInterestComponent.setAmount(BigDecimal.valueOf(0));
                severanceInterestComponent.setProjections(Shared.generateMonthProjection(period,range,severanceInterestComponent.getAmount()));
            }
            component.add(severanceInterestComponent);
        }else {
            PaymentComponentDTO severanceInterestComponent = new PaymentComponentDTO();
            severanceInterestComponent.setPaymentComponent("CONSOLIDADO_INTERESES_CESANTIAS_TEMPORAL");
            severanceInterestComponent.setAmount(BigDecimal.valueOf(0));
            severanceInterestComponent.setProjections(Shared.generateMonthProjection(period,range,severanceInterestComponent.getAmount()));
            component.add(severanceInterestComponent);
        }
        //log.debug("component -> {}", "consolidatedSeveranceInterestTemporal");
    }
    public void transportSubsidyTemporaries(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range) {
    String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Obtén los componentes necesarios para el cálculo
            List<String> subsidyComponents = Arrays.asList(TEMPORAL_SALARY, "HHEE", "SURCHARGES", "COMMISSION");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> subsidyComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
            PaymentComponentDTO salaryComponent = componentMap.get(TEMPORAL_SALARY);
            PaymentComponentDTO overtimeComponent = componentMap.get("HHEE");
            PaymentComponentDTO surchargesComponent = componentMap.get("SURCHARGES");
            PaymentComponentDTO commissionComponent = componentMap.get(COMMISSION_TEMP);
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
            ParametersDTO subsidyMin = getParametersById(parameters, 48);
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
            transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                MonthProjection projection = new MonthProjection();
                projection.setMonth(primeProjection.getMonth());
                projection.setAmount(transportSubsidyComponent.getAmount());
                projections.add(projection);
            }
            transportSubsidyComponent.setProjections(projections);
            component.add(transportSubsidyComponent);
        }else {
            PaymentComponentDTO transportSubsidyComponent = new PaymentComponentDTO();
            transportSubsidyComponent.setPaymentComponent("SUBSIDIO_TRANSPORTE_TEMPORALES");
            transportSubsidyComponent.setAmount(BigDecimal.valueOf(0));
            transportSubsidyComponent.setProjections(Shared.generateMonthProjection(period,range,transportSubsidyComponent.getAmount()));
            component.add(transportSubsidyComponent);
        }
        //log.debug("component -> {}", "transportSubsidyTemporaries");
    }
    public void companyHealthContributionTemporals(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        if (category.equals("T")) {
            List<String> healthComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> healthComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                    BigDecimal healthContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        healthContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.085);
                    } else if (totalAmount > 10 * legalSalaryMinInternal) {
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
                healthContributionComponent.setProjections(Shared.generateMonthProjection(period, range, healthContributionComponent.getAmount()));
            }
            component.add(healthContributionComponent);
        }else {
            PaymentComponentDTO healthContributionComponent = new PaymentComponentDTO();
            healthContributionComponent.setPaymentComponent("APORTE_SALUD_EMPRESA_TEMP");
            healthContributionComponent.setAmount(BigDecimal.valueOf(0));
            healthContributionComponent.setProjections(Shared.generateMonthProjection(period, range, healthContributionComponent.getAmount()));
            component.add(healthContributionComponent);
        }
        //log.debug("component -> {}", "companyHealthContributionTemporals");
    }
    public void companyRiskContributionTemporals(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
        double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
        if (category.equals("T")) {
            List<String> riskComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> riskComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

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
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = riskComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    BigDecimal riskContribution;
                    if (totalAmount > 25 * legalSalaryMinInternal) {
                        riskContribution = BigDecimal.valueOf(25 * legalSalaryMinInternal * 0.00881);
                    } else {
                        riskContribution = BigDecimal.valueOf(totalAmount * 0.00881);
                    }
                    projection.setAmount(riskContribution);
                    projections.add(projection);
                }
                riskContributionComponent.setProjections(projections);
            } else {
                riskContributionComponent.setAmount(BigDecimal.valueOf(0));
                riskContributionComponent.setProjections(Shared.generateMonthProjection(period, range, riskContributionComponent.getAmount()));
            }
            component.add(riskContributionComponent);
        }else {
            PaymentComponentDTO riskContributionComponent = new PaymentComponentDTO();
            riskContributionComponent.setPaymentComponent("APORTE_RIESGO_EMPRESA_TEMP");
            riskContributionComponent.setAmount(BigDecimal.valueOf(0));
            riskContributionComponent.setProjections(Shared.generateMonthProjection(period, range, riskContributionComponent.getAmount()));
            component.add(riskContributionComponent);
        }
        //log.debug("component -> {}", "companyRiskContributionTemporals");
    }
   public void contributionBoxTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        // Get the necessary components for the calculation
       if (category.equals("T")) {
           List<String> boxComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
           Map<String, PaymentComponentDTO> componentMap = component.stream()
                   .filter(c -> boxComponents.contains(c.getPaymentComponent()))
                   .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                boxContributionComponent.setProjections(Shared.generateMonthProjection(period, range, boxContributionComponent.getAmount()));
           }
           component.add(boxContributionComponent);
       }else {
              PaymentComponentDTO boxContributionComponent = new PaymentComponentDTO();
              boxContributionComponent.setPaymentComponent("APORTE_CAJA_TEMPORALES");
              boxContributionComponent.setAmount(BigDecimal.valueOf(0));
              boxContributionComponent.setProjections(Shared.generateMonthProjection(period, range, boxContributionComponent.getAmount()));
              component.add(boxContributionComponent);
       }
       //log.debug("component -> {}", "contributionBoxTemporaries");
    }
    public void icbfContributionTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Get the necessary components for the calculation
            List<String> icbfComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> icbfComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            double legalSalaryMinInternal = legalSalaryMin != null ? legalSalaryMin.getValue() : 0.0;
            icbfContributionComponent.setAmount(BigDecimal.valueOf(totalAmountBase >= 10 * legalSalaryMinInternal ? totalAmountBase * 0.03 : 0));
            // Calculate the ICBF Contribution for each projection
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
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf( totalAmount >= 10 * legalSalaryMinInternal ? totalAmount * 0.03 : 0));
                    projections.add(projection);
                }
            }else {
                icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
                icbfContributionComponent.setProjections(Shared.generateMonthProjection(period, range, icbfContributionComponent.getAmount()));
            }
            icbfContributionComponent.setProjections(projections);
            component.add(icbfContributionComponent);
        }else {
            PaymentComponentDTO icbfContributionComponent = new PaymentComponentDTO();
            icbfContributionComponent.setPaymentComponent("APORTE_ICBF_TEMPORALES");
            icbfContributionComponent.setAmount(BigDecimal.valueOf(0));
            icbfContributionComponent.setProjections(Shared.generateMonthProjection(period, range, icbfContributionComponent.getAmount()));
            component.add(icbfContributionComponent);
        }
        //log.debug("component -> {}", "icbfContributionTemporaries");
    }
    public void senaContributionTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")) {
            // Get the necessary components for the calculation
            List<String> senaComponents = Arrays.asList("TEMPORAL_SALARY", "HHEE", "SURCHARGES", "COMMISSION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> senaComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
            ParametersDTO legalSalaryMin = getParametersById(parameters, 47);
            double legalSalaryMinInternal = legalSalaryMin!=null ? legalSalaryMin.getValue() : 0.0;
            senaContributionComponent.setAmount(BigDecimal.valueOf( totalAmountBase >= 10 * legalSalaryMinInternal ? totalAmountBase * 0.02 : 0));
            // Calculate the Sena Contribution for each projection
            if (salaryComponent != null && salaryComponent.getProjections() != null) {
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection primeProjection : salaryComponent.getProjections()) {
                    double totalAmount = senaComponents.stream()
                            .map(componentMap::get)
                            .filter(Objects::nonNull)
                            .flatMap(paymentComponentDTO -> paymentComponentDTO.getProjections().stream())
                            .filter(projection -> projection.getMonth().equals(primeProjection.getMonth()))
                            .mapToDouble(projection -> projection.getAmount().doubleValue())
                            .sum();
                    MonthProjection projection = new MonthProjection();
                    projection.setMonth(primeProjection.getMonth());
                    projection.setAmount(BigDecimal.valueOf(totalAmount >= 10 * legalSalaryMinInternal ? totalAmount * 0.02 : 0));
                    projections.add(projection);
                }
                senaContributionComponent.setProjections(projections);
            }else {
                senaContributionComponent.setAmount(BigDecimal.valueOf(0));
                senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
            }
            component.add(senaContributionComponent);
        }else {
            PaymentComponentDTO senaContributionComponent = new PaymentComponentDTO();
            senaContributionComponent.setPaymentComponent("APORTE_SENA_TEMPORALES");
            senaContributionComponent.setAmount(BigDecimal.valueOf(0));
            senaContributionComponent.setProjections(Shared.generateMonthProjection(period,range,senaContributionComponent.getAmount()));
            component.add(senaContributionComponent);
        }
        //log.debug("component -> {}", "senaContributionTemporaries");
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
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
                pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
            }
            component.add(pensionContributionComponent);
        }else {
            PaymentComponentDTO pensionContributionComponent = new PaymentComponentDTO();
            pensionContributionComponent.setPaymentComponent("APORTE_PENSION_EMPRESA_TEMPORALES");
            pensionContributionComponent.setAmount(BigDecimal.valueOf(0));
            pensionContributionComponent.setProjections(Shared.generateMonthProjection(period,range,pensionContributionComponent.getAmount()));
            component.add(pensionContributionComponent);
        }
        //log.debug("component -> {}", "companyPensionContributionTemporaries");
    }
    public void feeTemporaries(List<PaymentComponentDTO> component, String classEmployee, List<ParametersDTO> parameters, String period, Integer range) {
        String category = findCategory(classEmployee);
        if (category.equals("T")){
            // Get the necessary components for the calculation
            List<String> feeComponents = Arrays.asList("TEMPORAL_SALARY", "COMMISSION_TEMP", "MONTHLY_PRIME_PROVISION_TEMP", "CONSOLIDATED_VACATION_TEMP", "CONSOLIDATED_SEVERANCE_TEMP", "CONSOLIDATED_SEVERANCE_INTEREST_TEMP", "TRANSPORT_SUBSIDY_TEMP", "COMPANY_HEALTH_CONTRIBUTION_TEMP", "COMPANY_RISK_CONTRIBUTION_TEMP", "CONTRIBUTION_BOX_TEMP", "ICBF_CONTRIBUTION_TEMP", "SENA_CONTRIBUTION_TEMP", "COMPANY_PENSION_CONTRIBUTION_TEMP");
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .filter(c -> feeComponents.contains(c.getPaymentComponent()))
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
            // Calculate the total base
            double totalAmountBase = feeComponents.stream()
                    .map(componentMap::get)
                    .filter(Objects::nonNull)
                    .mapToDouble(paymentComponentDTO -> paymentComponentDTO.getAmount().doubleValue())
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
                feeComponent.setProjections(Shared.generateMonthProjection(period,range,feeComponent.getAmount()));
            }
            component.add(feeComponent);
        }else{
            PaymentComponentDTO feeComponent = new PaymentComponentDTO();
            feeComponent.setPaymentComponent("FEE_TEMP");
            feeComponent.setAmount(BigDecimal.valueOf(0));
            feeComponent.setProjections(Shared.generateMonthProjection(period,range,feeComponent.getAmount()));
            component.add(feeComponent);
        }
        //log.debug("component -> {}", "feeTemporaries");
    }
}