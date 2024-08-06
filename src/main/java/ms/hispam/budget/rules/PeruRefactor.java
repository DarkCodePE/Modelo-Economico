package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.entity.mysql.ConceptoPresupuestal;
import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.entity.mysql.SeniorityAndQuinquennium;
import ms.hispam.budget.util.Shared;
import ms.hispam.budget.util.Tuple;
import org.springframework.stereotype.Component;
import org.apache.commons.text.similarity.LevenshteinDistance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "Peru")
public class PeruRefactor {
    static final String TYPEMONTH = "yyyyMM";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Map<String, YearMonth> PROMO_DATE_CACHE = new ConcurrentHashMap<>();
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

    public static List<MonthProjection> generateMonthProjection(String monthBase, int range, BigDecimal baseAmount) {
        List<MonthProjection> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter);

        // Add the initial month with the adjusted salary
        dates.add(MonthProjection.builder()
                .month(fechaActual.format(formatter))
                .amount(baseAmount)
                .build());

        // Generate projections for subsequent months starting with the base salary
        fechaActual = fechaActual.plusMonths(1);
        for (int i = 0; i < range; i++) {
            dates.add(MonthProjection.builder()
                    .month(fechaActual.format(formatter))
                    .amount(baseAmount)
                    .build());
            fechaActual = fechaActual.plusMonths(1);
        }
        return dates;
    }

    public static List<MonthProjection> generateMonthProjectionDefault(String monthBase, int range, BigDecimal amount) {
        List<MonthProjection> dates = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TYPEMONTH);
        YearMonth fechaActual = YearMonth.parse(monthBase, formatter).plusMonths(1);

        for (int i = 0; i < range; i++) {
            dates.add(MonthProjection.builder()
                    .month(fechaActual.format(formatter))
                    .amount(amount)
                    .build());
            fechaActual = fechaActual.plusMonths(1);
        }
        return dates;
    }

    public List<MonthProjection> calculateProjections(PaymentComponentDTO salaryComponent,
                                                      Map<String, ParametersDTO> salaryIncreaseMap,
                                                      Map<String, ParametersDTO> executiveSalaryIncreaseMap,
                                                      Map<String, ParametersDTO> directorSalaryIncreaseMap,
                                                      String typeEmp,
                                                      Map<String, PaymentComponentDTO> componentMap) {
        return salaryComponent.getProjections()
                .stream()
                .map(projection -> calculateMonthProjection(projection, salaryIncreaseMap, executiveSalaryIncreaseMap, directorSalaryIncreaseMap, typeEmp, componentMap))
                .collect(Collectors.toList());
    }

    private MonthProjection calculateMonthProjection(MonthProjection projection,
                                                     Map<String, ParametersDTO> salaryIncreaseMap,
                                                     Map<String, ParametersDTO> executiveSalaryIncreaseMap,
                                                     Map<String, ParametersDTO> directorSalaryIncreaseMap,
                                                     String typeEmp,
                                                     Map<String, PaymentComponentDTO> componentMap) {
        String month = projection.getMonth();
        // Get the next month for applying adjustments
        //String nextMonth = getNextPeriod(month);

        double adjustment = getAdjustment(typeEmp, month, salaryIncreaseMap, executiveSalaryIncreaseMap, directorSalaryIncreaseMap);
        //log.info("Adjustment: {}", adjustment);
        //log.info("maxAdjustments: {}", maxAdjustments);
        double adjustmentPercentage = adjustment / 100;
        //log.info("Adjustment percentage: {}, month: {}", adjustmentPercentage, month);
        double salary = projection.getAmount().doubleValue() * (1 + adjustmentPercentage);
        //log.info("Salary: {}, moth {}", salary, month);
        BigDecimal promo = calculatePromoAdjustment(salary, month, componentMap);
        //log.info("Promo: {}, month: {}", promo, month);
        double totalSalary = salary * (1 + promo.doubleValue());
        //log.info("Total salary: {}, month: {}", totalSalary, month);
        return new MonthProjection(month, BigDecimal.valueOf(totalSalary));
    }
    private Map<String, AtomicReference<Double>> maxAdjustments = new HashMap<>();

    private double getAdjustment(String typeEmp, String month,
                                 Map<String, ParametersDTO> salaryIncreaseMap,
                                 Map<String, ParametersDTO> executiveSalaryIncreaseMap,
                                 Map<String, ParametersDTO> directorSalaryIncreaseMap) {
        double currentAdjustment = switch (typeEmp) {
            case "DIR", "DPZ" -> getCachedValue(directorSalaryIncreaseMap, month);
            case "EJC", "GER" -> getCachedValue(executiveSalaryIncreaseMap, month);
            default -> getCachedValue(salaryIncreaseMap, month);
        };

        return maxAdjustments.computeIfAbsent(typeEmp, k -> new AtomicReference<>(0.0))
                .updateAndGet(maxAdjustment -> Math.max(maxAdjustment, currentAdjustment));
    }

    private double getCachedValue(Map<String, ParametersDTO> map, String key) {
        return Optional.ofNullable(map.get(key))
                .map(ParametersDTO::getValue)
                .orElse(0.0);
    }

    public void calculateTheoreticalSalary(List<PaymentComponentDTO> components,
                                           List<ParametersDTO> salaryIncreaseList,
                                           String localCategory,
                                           String period,
                                           Integer range,
                                           List<ParametersDTO> executiveSalaryIncreaseList,
                                           List<ParametersDTO> directorSalaryIncreaseList,
                                           Map<String, EmployeeClassification> classificationMap,
                                           Set<String> annualizedPositions,
                                           String poName) {
        EmployeeClassification employeeClassification = classificationMap.get(localCategory.toUpperCase());
        if (employeeClassification == null) {
            addDefaultSalaryComponent(components, period, range);
            return;
        }

        Map<String, ParametersDTO> salaryIncreaseMap = createCacheMap(salaryIncreaseList);
        Map<String, ParametersDTO> executiveSalaryIncreaseMap = createCacheMap(executiveSalaryIncreaseList);
        Map<String, ParametersDTO> directorSalaryIncreaseMap = createCacheMap(directorSalaryIncreaseList);
        Map<String, PaymentComponentDTO> componentMap = components.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, c -> c));

        double salaryBase = calculateSalaryBase(componentMap, annualizedPositions, poName);
        //log.info("Salary base: {}", salaryBase);
        String nextPeriod = getNextPeriod(period);
        double adjustmentBase = getAdjustmentBase(employeeClassification.getTypeEmp(), nextPeriod,
                salaryIncreaseMap, executiveSalaryIncreaseMap, directorSalaryIncreaseMap);

        PaymentComponentDTO salaryComponent = createSalaryComponent(salaryBase, adjustmentBase, period, range);
        List<MonthProjection> projections = calculateProjections(salaryComponent, salaryIncreaseMap,
                executiveSalaryIncreaseMap, directorSalaryIncreaseMap, employeeClassification.getTypeEmp(), componentMap);
        salaryComponent.setProjections(projections);
        components.add(salaryComponent);
        // Reiniciar los ajustes máximos antes de cada cálculo
        maxAdjustments.clear();
    }

    private double calculateSalaryBase(Map<String, PaymentComponentDTO> componentMap) {
        return Math.max(
                Optional.ofNullable(componentMap.get("PC960400")).map(c -> c.getAmount().doubleValue()).orElse(0.0),
                Optional.ofNullable(componentMap.get("PC960401")).map(c -> c.getAmount().doubleValue() / 14).orElse(0.0)
        );
    }

    private double calculateSalaryBase(Map<String, PaymentComponentDTO> componentMap, Set<String> annualizedPositions, String poName) {
        //log.info("annualizedPositions: {}", annualizedPositions);
        //log.info("poName: {}", poName);
        double pc960400Salary = Optional.ofNullable(componentMap.get("PC960400"))
                .map(c -> c.getAmount().doubleValue())
                .orElse(0.0);

        double pc960401Salary = Optional.ofNullable(componentMap.get("PC960401"))
                .map(c -> c.getAmount().doubleValue())
                .orElse(0.0);

        if (annualizedPositions.contains(poName) && componentMap.containsKey("PC960400")) {
            //log.info("Annualized position: {}", poName);
            pc960400Salary /= 14;
        }

        return Math.max(pc960400Salary, pc960401Salary / 14);
    }

    private String getNextPeriod(String period) {
        return YearMonth.parse(period, MONTH_FORMATTER).plusMonths(1).format(MONTH_FORMATTER);
    }

    private double getAdjustmentBase(String typeEmp, String nextPeriod,
                                     Map<String, ParametersDTO> salaryIncreaseMap,
                                     Map<String, ParametersDTO> executiveSalaryIncreaseMap,
                                     Map<String, ParametersDTO> directorSalaryIncreaseMap) {
        return switch (typeEmp) {
            case "EMP" -> getCachedValue(salaryIncreaseMap, nextPeriod);
            case "DIR", "DPZ" -> getCachedValue(directorSalaryIncreaseMap, nextPeriod);
            default -> getCachedValue(executiveSalaryIncreaseMap, nextPeriod);
        };
    }

    private PaymentComponentDTO createSalaryComponent(double salaryBase, double adjustmentBase, String period, Integer range) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("THEORETICAL-SALARY");
        salaryComponent.setAmount(BigDecimal.valueOf(salaryBase * (1 + (adjustmentBase / 100))));
        salaryComponent.setProjections(generateMonthProjection(period, range, BigDecimal.valueOf(salaryBase)));
        return salaryComponent;
    }

    private void addDefaultSalaryComponent(List<PaymentComponentDTO> components, String period, Integer range) {
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setAmount(BigDecimal.ZERO);
        salaryComponent.setProjections(generateMonthProjection(period, range, BigDecimal.ZERO));
        components.add(salaryComponent);
    }

    private BigDecimal calculatePromoAdjustment(double salary, String month, Map<String, PaymentComponentDTO> componentMap) {
        PaymentComponentDTO promoMonthComponent = componentMap.get("mes_promo");
        PaymentComponentDTO promoComponent = componentMap.get("promo");
        if (promoMonthComponent == null || promoComponent == null || promoMonthComponent.getAmountString() == null) {
            return BigDecimal.ZERO;
        }

        YearMonth currentYearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        YearMonth promoYearMonth = parsePromoDate(promoMonthComponent.getAmountString());

        return (promoYearMonth != null && !promoYearMonth.isAfter(currentYearMonth))
                ? promoComponent.getAmount()
                : BigDecimal.ZERO;
    }

    private YearMonth parsePromoDate(String dateString) {
        return PROMO_DATE_CACHE.computeIfAbsent(dateString, key -> {
            try {
                return YearMonth.parse(key, MONTH_FORMATTER);
            } catch (DateTimeParseException e) {
                try {
                    int excelDate = Integer.parseInt(key);
                    LocalDate promoDate = LocalDate.of(1900, 1, 1).plusDays(excelDate - 2);
                    return YearMonth.from(promoDate);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        });
    }
    private Map<String, ParametersDTO> createCacheMap(List<ParametersDTO> parameterList) {
        Map<String, ParametersDTO> parameterMap = new HashMap<>();
        Map<String, Double> cache = new HashMap<>();
        createCache(parameterList, parameterMap, cache, (parameter, mapParameter) -> {
        });
        return parameterMap;
    }

    //Compensación por Mudanza
    public void relocation(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO relocationBaseComponent = componentMap.get("RELOCATION_BASE");
        if (relocationBaseComponent != null) {
            double relocationBase = relocationBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO relocationComponent = new PaymentComponentDTO();
            relocationComponent.setPaymentComponent("RELOCATION");
            relocationComponent.setAmount(BigDecimal.valueOf(relocationBase));
            relocationComponent.setProjections(generateMonthProjection(period, range, relocationComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (relocationBaseComponent.getProjections() != null) {
                for (MonthProjection projection : relocationBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double relocationPerMonth = relocationBaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(relocationPerMonth));
                    projections.add(monthProjection);
                }
                relocationComponent.setProjections(projections);
            } else {
                relocationComponent.setAmount(BigDecimal.valueOf(0));
                relocationComponent.setProjections(generateMonthProjection(period, range, relocationComponent.getAmount()));
            }
            component.add(relocationComponent);
        } else {
            PaymentComponentDTO relocationComponent = new PaymentComponentDTO();
            relocationComponent.setPaymentComponent("RELOCATION");
            relocationComponent.setAmount(BigDecimal.valueOf(0));
            relocationComponent.setProjections(generateMonthProjection(period, range, relocationComponent.getAmount()));
            component.add(relocationComponent);
        }
    }

    //Compensación Vivienda
    public void housing(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO housingBaseComponent = componentMap.get("HOUSING_BASE");
        if (housingBaseComponent != null) {
            double housingBase = housingBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO housingComponent = new PaymentComponentDTO();
            housingComponent.setPaymentComponent("HOUSING");
            housingComponent.setAmount(BigDecimal.valueOf(housingBase));
            housingComponent.setProjections(generateMonthProjection(period, range, housingComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (housingBaseComponent.getProjections() != null) {
                for (MonthProjection projection : housingBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double housingPerMonth = housingBaseComponent.getAmount().doubleValue() / 12;
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(housingPerMonth));
                    projections.add(monthProjection);
                }
                housingComponent.setProjections(projections);
            } else {
                housingComponent.setAmount(BigDecimal.valueOf(0));
                housingComponent.setProjections(generateMonthProjection(period, range, housingComponent.getAmount()));
            }
            component.add(housingComponent);
        } else {
            PaymentComponentDTO housingComponent = new PaymentComponentDTO();
            housingComponent.setPaymentComponent("HOUSING");
            housingComponent.setAmount(BigDecimal.valueOf(0));
            housingComponent.setProjections(generateMonthProjection(period, range, housingComponent.getAmount()));
            component.add(housingComponent);
        }
    }

    //Incremento SNP 3,3%
    public void increaseSNP(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO increaseSNPBaseComponent = componentMap.get("INCREASE_SNP_BASE");
        if (increaseSNPBaseComponent != null) {
            double increaseSNPBase = increaseSNPBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO increaseSNPComponent = new PaymentComponentDTO();
            increaseSNPComponent.setPaymentComponent("INCREASE_SNP");
            increaseSNPComponent.setAmount(BigDecimal.valueOf(increaseSNPBase));
            increaseSNPComponent.setProjections(generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (increaseSNPBaseComponent.getProjections() != null) {
                for (MonthProjection projection : increaseSNPBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double increaseSNPPerMonth = increaseSNPBaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(increaseSNPPerMonth));
                    projections.add(monthProjection);
                }
                increaseSNPComponent.setProjections(projections);
            } else {
                increaseSNPComponent.setAmount(BigDecimal.valueOf(0));
                increaseSNPComponent.setProjections(generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
            }
            component.add(increaseSNPComponent);
        } else {
            PaymentComponentDTO increaseSNPComponent = new PaymentComponentDTO();
            increaseSNPComponent.setPaymentComponent("INCREASE_SNP");
            increaseSNPComponent.setAmount(BigDecimal.valueOf(0));
            increaseSNPComponent.setProjections(generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
            component.add(increaseSNPComponent);
        }
    }

    //Incremento 3,3%
    public void increaseAFP(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO increaseBaseComponent = componentMap.get("INCREASE_AFP_BASE");
        if (increaseBaseComponent != null) {
            double increaseBase = increaseBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO increaseComponent = new PaymentComponentDTO();
            increaseComponent.setPaymentComponent("INCREASE_AFP");
            increaseComponent.setAmount(BigDecimal.valueOf(increaseBase));
            increaseComponent.setProjections(generateMonthProjection(period, range, increaseComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (increaseBaseComponent.getProjections() != null) {
                for (MonthProjection projection : increaseBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double increasePerMonth = increaseBaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(increasePerMonth));
                    projections.add(monthProjection);
                }
                increaseComponent.setProjections(projections);
            } else {
                increaseComponent.setAmount(BigDecimal.valueOf(0));
                increaseComponent.setProjections(generateMonthProjection(period, range, increaseComponent.getAmount()));
            }
            component.add(increaseComponent);
        } else {
            PaymentComponentDTO increaseComponent = new PaymentComponentDTO();
            increaseComponent.setPaymentComponent("INCREASE");
            increaseComponent.setAmount(BigDecimal.valueOf(0));
            increaseComponent.setProjections(generateMonthProjection(period, range, increaseComponent.getAmount()));
            component.add(increaseComponent);
        }
    }

    //Incremento 3,3% + Incremento SNP 3,3%
    public void increaseSNPAndIncrease(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO increaseSNP = componentMap.get("INCREASE_SNP");
        PaymentComponentDTO increaseAFP = componentMap.get("INCREASE_AFP");
        double increaseSNPBase = increaseSNP == null ? 0 : increaseSNP.getAmount().doubleValue();
        double increaseAFPBase = increaseAFP == null ? 0 : increaseAFP.getAmount().doubleValue();
        PaymentComponentDTO increaseSNPAndIncreaseComponent = new PaymentComponentDTO();
        increaseSNPAndIncreaseComponent.setPaymentComponent("INCREASE_SNP_AND_INCREASE");
        increaseSNPAndIncreaseComponent.setAmount(BigDecimal.valueOf(increaseSNPBase + increaseAFPBase));
        increaseSNPAndIncreaseComponent.setProjections(generateMonthProjection(period, range, increaseSNPAndIncreaseComponent.getAmount()));
        component.add(increaseSNPAndIncreaseComponent);
    }

    //Incremento AFP 10,23%
    public void increaseAFP1023(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO increaseAFP1023BaseComponent = componentMap.get("INCREASE_AFP_1023_BASE");
        PaymentComponentDTO increaseAFP1023Component = new PaymentComponentDTO();
        increaseAFP1023Component.setPaymentComponent("INCREASE_AFP_1023");

        if (increaseAFP1023BaseComponent != null) {
            double increaseAFP1023Base = increaseAFP1023BaseComponent.getAmount().doubleValue();
            increaseAFP1023Component.setAmount(BigDecimal.valueOf(increaseAFP1023Base));

            // Generate full range of projections
            List<MonthProjection> fullProjections = generateMonthProjection(period, range, BigDecimal.valueOf(increaseAFP1023Base));

            if (increaseAFP1023BaseComponent.getProjections() != null) {
                // Create a map of existing projections for easy lookup
                Map<String, BigDecimal> existingProjectionsMap = increaseAFP1023BaseComponent.getProjections().stream()
                        .collect(Collectors.toMap(MonthProjection::getMonth, MonthProjection::getAmount));

                // Update full projections with existing values where available
                for (MonthProjection projection : fullProjections) {
                    BigDecimal existingAmount = existingProjectionsMap.get(projection.getMonth());
                    if (existingAmount != null) {
                        projection.setAmount(existingAmount);
                    }
                }
            }

            increaseAFP1023Component.setProjections(fullProjections);
        } else {
            increaseAFP1023Component.setAmount(BigDecimal.ZERO);
            increaseAFP1023Component.setProjections(generateMonthProjectionDefault(period, range, BigDecimal.ZERO));
        }

        component.add(increaseAFP1023Component);
    }

    //Vivienda Expatriados
    public void housingExpatriates(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO housingExpatriatesBaseComponent = componentMap.get("HOUSING_EXPATRIATES_BASE");
        if (housingExpatriatesBaseComponent != null) {
            double housingExpatriatesBase = housingExpatriatesBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO housingExpatriatesComponent = new PaymentComponentDTO();
            housingExpatriatesComponent.setPaymentComponent("HOUSING_EXPATRIATES");
            housingExpatriatesComponent.setAmount(BigDecimal.valueOf(housingExpatriatesBase));
            housingExpatriatesComponent.setProjections(generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (housingExpatriatesBaseComponent.getProjections() != null) {
                for (MonthProjection projection : housingExpatriatesBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double housingExpatriatesPerMonth = housingExpatriatesBaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(housingExpatriatesPerMonth));
                    projections.add(monthProjection);
                }
                housingExpatriatesComponent.setProjections(projections);
            } else {
                housingExpatriatesComponent.setAmount(BigDecimal.valueOf(0));
                housingExpatriatesComponent.setProjections(generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
            }
            component.add(housingExpatriatesComponent);
        } else {
            PaymentComponentDTO housingExpatriatesComponent = new PaymentComponentDTO();
            housingExpatriatesComponent.setPaymentComponent("HOUSING_EXPATRIATES");
            housingExpatriatesComponent.setAmount(BigDecimal.valueOf(0));
            housingExpatriatesComponent.setProjections(generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
            component.add(housingExpatriatesComponent);
        }
    }

    //Goce de Vacaciones
    //=-((CC35/30)*$CC$6*$BS5)*CC$7
    //CC35 = Salario Teórico
    //CC$6 = parametro Días de Vacaciones
    //BS5 = componentGoceVacaciones
    //CC$7 = parametro Estacionalidad vacaciones
    // description: Para el cálculo del concepto "Goce de Vacaciones" se requiere el input  '%Goce Vacaciones' obtenido desde B. Externa en el periodo base.
    //Se requiere el valor de  [Salario Teórico]  calculado para la PO en el mes actual, y adicionalmente se requieren los parámetros {Días vacaciones disp anual}  y  {Estacionalidad vacaciones} cuyos valores aplican desde el periodo en que son ingresados, manteniéndose invariables hasta que se actualiza el valor de estos en un periodo futuro.
    //
    //El cálculo y ajuste se realiza de la siguiente manera:
    //El [Salario Teórico] calculado para el mes actual, es dividido entre 30 para obtener el valor diario. El valor diario obtenido se multiplica por la cantidad de {días vacaciones disp anual} * '%Goce Vacaciones' y se aplica la {Estacionalidad vacaciones} del mes en curso. El resultado obtenido es multiplicado por -1.
    //
    //~~ no se divide entre 12, porque el parámetro {estacionalidad vacaciones} ya prorratea el gasto durante el año
    public void vacationEnjoyment(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> vacationDaysList, List<ParametersDTO> vacationSeasonalityList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        Map<String, ParametersDTO> vacationDaysMap = createCacheMap(vacationDaysList);
        Map<String, ParametersDTO> vacationSeasonalityMap = createCacheMap(vacationSeasonalityList);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double vacationDays = getCachedValue(vacationDaysMap, nextPeriod);
        double vacationSeasonality = getCachedValue(vacationSeasonalityMap, nextPeriod);

        PaymentComponentDTO vacationEnjoymentComponent = new PaymentComponentDTO();
        vacationEnjoymentComponent.setPaymentComponent("VACATION_ENJOYMENT");
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        PaymentComponentDTO goceVacacionesComponent = componentMap.get("goce");

        if (theoreticalSalaryComponent != null && goceVacacionesComponent != null) {
            double theoreticalSalary = theoreticalSalaryComponent.getAmount().doubleValue();
            double goceVacacionesBase = goceVacacionesComponent.getAmount().doubleValue();
            double vacationPerDay = theoreticalSalary / 30;
            double vacationSeasonalityPercentage = vacationSeasonality / 100;
            double vacationPerMonth = (vacationPerDay * vacationDays * goceVacacionesBase) * vacationSeasonalityPercentage;
            vacationEnjoymentComponent.setAmount(BigDecimal.valueOf(vacationPerMonth * -1));
            vacationEnjoymentComponent.setProjections(generateMonthProjection(period, range, vacationEnjoymentComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastVacationDays = 0;
            double lastVacationSeasonality = 0;
            for (MonthProjection projection : theoreticalSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                double vacationPerDayProjection = projection.getAmount().doubleValue() / 30;
                ParametersDTO vacationDaysParameter = vacationDaysMap.get(month);
                double vacationDaysValue;
                if (vacationDaysParameter != null) {
                    vacationDaysValue = vacationDaysParameter.getValue();
                    lastVacationDays = vacationDaysValue;
                } else {
                    vacationDaysValue = lastVacationDays;
                }
                ParametersDTO vacationSeasonalityParameter = vacationSeasonalityMap.get(month);
                double vacationSeasonalityValue;
                if (vacationSeasonalityParameter != null) {
                    vacationSeasonalityValue = vacationSeasonalityParameter.getValue() / 100;
                    lastVacationSeasonality = vacationSeasonalityValue;
                } else {
                    vacationSeasonalityValue = lastVacationSeasonality;
                }
                double vacationPerMonthProjection = (vacationPerDayProjection * vacationDaysValue * goceVacacionesBase) * vacationSeasonalityValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(vacationPerMonthProjection * -1));
                projections.add(monthProjection);
            }
            vacationEnjoymentComponent.setProjections(projections);
        } else {
            vacationEnjoymentComponent.setAmount(BigDecimal.valueOf(0));
            vacationEnjoymentComponent.setProjections(generateMonthProjection(period, range, vacationEnjoymentComponent.getAmount()));
        }
        component.add(vacationEnjoymentComponent);
    }

    //Horas Extras
    //=$AM5/SUMA($AM$5:$AM$11)*CD$25*CD$24
    //AM5 = componentHorasExtras
    //AM$5:$AM$11 = sumaHorasExtras
    //CD$25 = Estacionalidad Horas Extras
    //CD$24 = Valor HHEE (anual)
    // description: El valor reflejado en input para "Horas Extras" es calculado previamente como la suma de los últimos 12 meses de nómina del concepto 'Horas Extras'  y se suma con el resto de inputs que comparten concepto presupuestal para mostrar en el mes base.
    //Adicionalmente se requieren los parámetros {Estacionalidad Horas Extras}  y  {Valor HHEE (anual)} cuyos valores aplican desde el periodo en que se ingresan, manteniendose invariables hasta que se actualiza el valor de estos en un periodo futuro.
    //
    //El cálculo y ajuste del concepto se realiza de la siguiente manera:
    //Se toma el costo de todas las horas extras de la PO en el mes base, y se divide entre el costo total de todas las horas extras del total de POS incluídas en la proyección (ocupadas, vacantes, BC).
    //El valor obtenido se multiplica * {Valor HHEE (anual)} y luego * {Estacionalidad Horas Extras} mes.
    //
    //De esta forma el valor ingresado en parámetros para las HHEE queda distribuido por PO según su proporción de HHEE en el mes base, junto con la aplicación de la estacionalidad del mes.
    //El valor del parámetro es distribuido equitativamente entre el total de POS incluídas en la proyección.
    public void overtime(List<PaymentComponentDTO> component, String period, Integer range, double totalHorasExtras, List<ParametersDTO> overtimeSeasonalityList, List<ParametersDTO> overtimeValueList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> overtimeSeasonalityMap = createCacheMap(overtimeSeasonalityList);
        Map<String, ParametersDTO> overtimeValueMap = createCacheMap(overtimeValueList);

        PaymentComponentDTO overtimeBaseComponent = componentMap.get("OVERTIME_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double overtimeSeasonality = getCachedValue(overtimeSeasonalityMap, nextPeriod);
        double overtimeValue = getCachedValue(overtimeValueMap, nextPeriod);

        PaymentComponentDTO overtimeComponent = new PaymentComponentDTO();
        overtimeComponent.setPaymentComponent("OVER_TIME");
        if (overtimeBaseComponent != null) {
            double overtimeBase = overtimeBaseComponent.getAmount().doubleValue();
            double overtimePerMonth = (overtimeBase / totalHorasExtras) * overtimeValue * overtimeSeasonality;
            overtimeComponent.setAmount(BigDecimal.valueOf(overtimePerMonth));
            overtimeComponent.setProjections(generateMonthProjection(period, range, overtimeComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastOvertimeSeasonality = 0;
            double lastOvertimeValue = 0;
            for (MonthProjection projection : overtimeBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double overtimeBaseProjection = projection.getAmount().doubleValue();
                ParametersDTO overtimeSeasonalityParameter = overtimeSeasonalityMap.get(month);
                double overtimeSeasonalityValue;
                if (overtimeSeasonalityParameter != null) {
                    overtimeSeasonalityValue = overtimeSeasonalityParameter.getValue();
                    lastOvertimeSeasonality = overtimeSeasonalityValue;
                } else {
                    overtimeSeasonalityValue = lastOvertimeSeasonality;
                }
                ParametersDTO overtimeValueParameter = overtimeValueMap.get(month);
                double overtimeValueValue;
                if (overtimeValueParameter != null) {
                    overtimeValueValue = overtimeValueParameter.getValue();
                    lastOvertimeValue = overtimeValueValue;
                } else {
                    overtimeValueValue = lastOvertimeValue;
                }
                double overtimePerMonthProjection = (overtimeBaseProjection / totalHorasExtras) * overtimeValueValue * overtimeSeasonalityValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(overtimePerMonthProjection));
                projections.add(monthProjection);
            }
            overtimeComponent.setProjections(projections);
        } else {
            overtimeComponent.setAmount(BigDecimal.valueOf(0));
            overtimeComponent.setProjections(generateMonthProjection(period, range, overtimeComponent.getAmount()));
        }
        component.add(overtimeComponent);
    }

    //Comisiones
    //=$AD6/SUMA($AD$5:$AD$11)*CC$26/12
    //AD6 = componentComisionesBase
    //AD$5:$AD$11 = sumaComisiones
    //CC$26 = PARAMETRO Valor Comisiones (anual)
    // description: El valor reflejado en input para "Comisiones" es calculado previamente como la suma de los últimos 12 meses de nómina del concepto 'Comisiones'  y se suma con el resto de inputs que comparten concepto presupuestal para mostrar en el mes base.
    //Adicionalmente se requieren el parámetro {Valor Comisiones (anual)} cuyo valor aplica desde el periodo en que se ingresa, manteniendose invariable hasta que se actualiza el valor de este en un periodo futuro.
    //
    //El cálculo y ajuste del concepto se realiza de la siguiente manera:
    //Se toma el costo de todas las comisiones de la PO en el mes base, y se divide entre el costo total de todas las comisiones del total de POS incluídas en la proyección (ocupadas, vacantes, BC).
    //El valor obtenido se multiplica * {Valor Comisiones (anual)} / 12
    //
    //De esta forma el valor ingresado en parámetros para las Comisiones queda distribuido por PO según su proporción de Comisiones en el mes base, junto con la mensualización del costo.
    //El valor del parámetro es distribuido entre el total de POS incluídas en la proyección, proporcionalmente a su participación en el código de nómina.
    public void commissions(List<PaymentComponentDTO> component, String period, Integer range, double totalCommissions, List<ParametersDTO> commissionsValueList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> commissionsValueMap = createCacheMap(commissionsValueList);

        PaymentComponentDTO commissionsBaseComponent = componentMap.get("COMMISSIONS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO commissionsComponent = new PaymentComponentDTO();
        commissionsComponent.setPaymentComponent("COMMISSIONS");
        if (commissionsBaseComponent != null) {
            double commissionsBase = commissionsBaseComponent.getAmount().doubleValue();
            double commissionsValue = getCachedValue(commissionsValueMap, nextPeriod);
            double commissionsPerMonth = (commissionsBase / totalCommissions) * (commissionsValue / 12);
            commissionsComponent.setAmount(BigDecimal.valueOf(commissionsPerMonth));
            commissionsComponent.setProjections(generateMonthProjection(period, range, commissionsComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastCommissionsValue = 0;
            for (MonthProjection projection : commissionsBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double commissionsBaseProjection = projection.getAmount().doubleValue();
                ParametersDTO commissionsValueParameter = commissionsValueMap.get(month);
                double commissionsValueValue;
                if (commissionsValueParameter != null) {
                    commissionsValueValue = commissionsValueParameter.getValue();
                    lastCommissionsValue = commissionsValueValue;
                } else {
                    commissionsValueValue = lastCommissionsValue;
                }
                double commissionsPerMonthProjection = (commissionsBaseProjection / totalCommissions) * (commissionsValueValue / 12);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(commissionsPerMonthProjection));
                projections.add(monthProjection);
            }
            commissionsComponent.setProjections(projections);
        } else {
            commissionsComponent.setAmount(BigDecimal.valueOf(0));
            commissionsComponent.setProjections(generateMonthProjection(period, range, commissionsComponent.getAmount()));
        }
        component.add(commissionsComponent);
    }

    //Incentivos
    //=($AE7+$AF7)/SUMA($AE$5:$AF$11)*CC$27/12
    //AE7+$AF7 = componentIncentivosBase
    //AE$5:$AF$11 = sumaIncentivos
    //CC$27 = PARAMETRO Valor Incentivos (anual)
    // description:El valor reflejado en input para "Incentivos" es calculado previamente como la suma de los últimos 12 meses de nómina del concepto 'Comisiones'  y se suma con el resto de inputs que comparten concepto presupuestal para mostrar en el mes base.
    //Adicionalmente se requieren el parámetro {Valor Incentivos (anual)} cuyo valor aplica desde el periodo en que se ingresa, manteniendose invariable hasta que se actualiza el valor de este en un periodo futuro.
    //
    //El cálculo y ajuste del concepto se realiza de la siguiente manera:
    //Se toma el costo de todas las comisiones de la PO en el mes base, y se divide entre el costo total de todas las comisiones del total de POS incluídas en la proyección (ocupadas, vacantes, BC).
    //El valor obtenido se multiplica * {Valor Comisiones (anual)} / 12
    //
    //De esta forma el valor ingresado en parámetros para las Comisiones queda distribuido por PO según su proporción de Comisiones en el mes base, junto con la mensualización del costo.
    //El valor del parámetro es distribuido entre el total de POS incluídas en la proyección, proporcionalmente a su participación en el código de nómina.
    public void incentives(List<PaymentComponentDTO> component, String period, Integer range, double totalIncentives, List<ParametersDTO> incentivesValueList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> incentivesValueMap = createCacheMap(incentivesValueList);

        PaymentComponentDTO incentivesBaseComponent = componentMap.get("INCENTIVES_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO incentivesComponent = new PaymentComponentDTO();
        incentivesComponent.setPaymentComponent("INCENTIVES");
        if (incentivesBaseComponent != null) {
            double incentivesBase = incentivesBaseComponent.getAmount().doubleValue();
            double incentivesValue = getCachedValue(incentivesValueMap, nextPeriod);
            double incentivesPerMonth = (incentivesBase / totalIncentives) * incentivesValue / 12;
            incentivesComponent.setAmount(BigDecimal.valueOf(incentivesPerMonth));
            incentivesComponent.setProjections(generateMonthProjection(period, range, incentivesComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastIncentivesValue = 0;
            for (MonthProjection projection : incentivesBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double incentivesBaseProjection = projection.getAmount().doubleValue();
                ParametersDTO incentivesValueParameter = incentivesValueMap.get(month);
                double incentivesValueValue;
                if (incentivesValueParameter != null) {
                    incentivesValueValue = incentivesValueParameter.getValue();
                    lastIncentivesValue = incentivesValueValue;
                } else {
                    incentivesValueValue = lastIncentivesValue;
                }
                double incentivesPerMonthProjection = (incentivesBaseProjection / totalIncentives) * incentivesValueValue / 12;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(incentivesPerMonthProjection));
                projections.add(monthProjection);
            }
            incentivesComponent.setProjections(projections);
        } else {
            incentivesComponent.setAmount(BigDecimal.valueOf(0));
            incentivesComponent.setProjections(generateMonthProjection(period, range, incentivesComponent.getAmount()));
        }
        component.add(incentivesComponent);
    }

    //Bono por trabajo nocturno
    //$BJ6 = componentBonoNocturnoBase
    public void nightBonus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO nightBonusBaseComponent = componentMap.get("NIGHT_BONUS_BASE");

        PaymentComponentDTO nightBonusComponent = new PaymentComponentDTO();
        nightBonusComponent.setPaymentComponent("NIGHT_BONUS");
        if (nightBonusBaseComponent != null) {
            double nightBonusBase = nightBonusBaseComponent.getAmount().doubleValue();
            nightBonusComponent.setAmount(BigDecimal.valueOf(nightBonusBase));
            nightBonusComponent.setProjections(generateMonthProjection(period, range, nightBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : nightBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double nightBonusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(nightBonusBaseProjection));
                projections.add(monthProjection);
            }
            nightBonusComponent.setProjections(projections);
        } else {
            nightBonusComponent.setAmount(BigDecimal.valueOf(0));
            nightBonusComponent.setProjections(generateMonthProjection(period, range, nightBonusComponent.getAmount()));
        }
        component.add(nightBonusComponent);
    }

    //Plus Disponibilidad
    //$BI6 = componentPlusDisponibilidadBase
    public void availabilityPlus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO availabilityPlusBaseComponent = componentMap.get("AVAILABILITY_PLUS_BASE");

        PaymentComponentDTO availabilityPlusComponent = new PaymentComponentDTO();
        availabilityPlusComponent.setPaymentComponent("AVAILABILITY_PLUS");
        if (availabilityPlusBaseComponent != null) {
            double availabilityPlusBase = availabilityPlusBaseComponent.getAmount().doubleValue();
            availabilityPlusComponent.setAmount(BigDecimal.valueOf(availabilityPlusBase));
            availabilityPlusComponent.setProjections(generateMonthProjection(period, range, availabilityPlusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : availabilityPlusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double availabilityPlusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(availabilityPlusBaseProjection));
                projections.add(monthProjection);
            }
            availabilityPlusComponent.setProjections(projections);
        } else {
            availabilityPlusComponent.setAmount(BigDecimal.valueOf(0));
            availabilityPlusComponent.setProjections(generateMonthProjection(period, range, availabilityPlusComponent.getAmount()));
        }
        component.add(availabilityPlusComponent);
    }

    //Bono Cierre Pliego Sindical
    //=SI($W5="Emp";CC$28/CONTAR.SI.CONJUNTO($W$5:$W$11;"EMP");0)
    //W5= headcount poName
    //W$5:$W$11 = cantidad de EMP
    //CC$28 = PARAMETRO Valor Bono Cierre Pliego Sindical
    public void unionClosingBonus(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> unionClosingBonusValueList, long countEMP, Map<String, EmployeeClassification> classificationMap) {
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        //log.info("countEMP: {}", countEMP);
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            Map<String, ParametersDTO> unionClosingBonusValueMap = createCacheMap(unionClosingBonusValueList);
            if("EMP".equals(employeeClassification.getTypeEmp())){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
                YearMonth yearMonth = YearMonth.parse(period, formatter);
                yearMonth = yearMonth.plusMonths(1);
                String nextPeriod = yearMonth.format(formatter);

                PaymentComponentDTO unionClosingBonusComponent = new PaymentComponentDTO();
                unionClosingBonusComponent.setPaymentComponent("UNION_CLOSING_BONUS");
                double unionClosingBonusValue = getCachedValue(unionClosingBonusValueMap, nextPeriod);
                //log.info("unionClosingBonusValue: {}", unionClosingBonusValue);
                double unionClosingBonusPerEMP = countEMP > 0 ? unionClosingBonusValue / countEMP : 0;
                if (unionClosingBonusValue > 0) {
                    unionClosingBonusComponent.setAmount(BigDecimal.valueOf(unionClosingBonusPerEMP));
                    unionClosingBonusComponent.setProjections(generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
                    List<MonthProjection> projections = new ArrayList<>();
                    for (MonthProjection projection : unionClosingBonusComponent.getProjections()) {
                        String month = projection.getMonth();
                        double unionClosingBonusPerMonth = countEMP > 0 ? unionClosingBonusValue / countEMP : 0;
                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.valueOf(unionClosingBonusPerMonth));
                        projections.add(monthProjection);
                    }
                    unionClosingBonusComponent.setProjections(projections);
                } else {
                    unionClosingBonusComponent.setAmount(BigDecimal.valueOf(0));
                    unionClosingBonusComponent.setProjections(generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
                }
                component.add(unionClosingBonusComponent);
            } else {
                PaymentComponentDTO unionClosingBonusComponent = new PaymentComponentDTO();
                unionClosingBonusComponent.setPaymentComponent("UNION_CLOSING_BONUS");
                unionClosingBonusComponent.setAmount(BigDecimal.valueOf(0));
                unionClosingBonusComponent.setProjections(generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
                component.add(unionClosingBonusComponent);
            }
        } else {
            PaymentComponentDTO unionClosingBonusComponent = new PaymentComponentDTO();
            unionClosingBonusComponent.setPaymentComponent("UNION_CLOSING_BONUS");
            unionClosingBonusComponent.setAmount(BigDecimal.valueOf(0));
            unionClosingBonusComponent.setProjections(generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
            component.add(unionClosingBonusComponent);
        }
    }

    //Bono Incremento de Vacaciones - IDV
    //=$BO6*CC$7
    //BO6 = componentBonoIncrementoVacacionesBase
    //CC$7 = parametro Estacionalidad vacaciones
    public void vacationIncreaseBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> vacationSeasonalityList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> vacationSeasonalityMap = createCacheMap(vacationSeasonalityList);

        PaymentComponentDTO vacationIncreaseBonusBaseComponent = componentMap.get("VACATION_INCREASE_BONUS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double vacationSeasonality = getCachedValue(vacationSeasonalityMap, nextPeriod);

        PaymentComponentDTO vacationIncreaseBonusComponent = new PaymentComponentDTO();
        vacationIncreaseBonusComponent.setPaymentComponent("VACATION_INCREASE_BONUS");
        if (vacationIncreaseBonusBaseComponent != null) {
            double vacationIncreaseBonusBase = vacationIncreaseBonusBaseComponent.getAmount().doubleValue();
            double vacationIncreaseBonusPerMonth = vacationIncreaseBonusBase * vacationSeasonality;
            vacationIncreaseBonusComponent.setAmount(BigDecimal.valueOf(vacationIncreaseBonusPerMonth));
            vacationIncreaseBonusComponent.setProjections(generateMonthProjection(period, range, vacationIncreaseBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastVacationSeasonality = 0;
            for (MonthProjection projection : vacationIncreaseBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double vacationIncreaseBonusBaseProjection = projection.getAmount().doubleValue();
                ParametersDTO vacationSeasonalityParameter = vacationSeasonalityMap.get(month);
                double vacationSeasonalityValue;
                if (vacationSeasonalityParameter != null) {
                    vacationSeasonalityValue = vacationSeasonalityParameter.getValue();
                    lastVacationSeasonality = vacationSeasonalityValue;
                } else {
                    vacationSeasonalityValue = lastVacationSeasonality;
                }
                double vacationIncreaseBonusPerMonthProjection = vacationIncreaseBonusBaseProjection * vacationSeasonalityValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(vacationIncreaseBonusPerMonthProjection));
                projections.add(monthProjection);
            }
            vacationIncreaseBonusComponent.setProjections(projections);
        } else {
            vacationIncreaseBonusComponent.setAmount(BigDecimal.valueOf(0));
            vacationIncreaseBonusComponent.setProjections(generateMonthProjection(period, range, vacationIncreaseBonusComponent.getAmount()));
        }
        component.add(vacationIncreaseBonusComponent);
    }

    //Bono por Vacaciones
    //=$BP7*CD$7
    //BP7 = componentBonoVacacionesBase
    //CD$7 = parametro Estacionalidad vacaciones
    public void vacationBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> vacationSeasonalityList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> vacationSeasonalityMap = createCacheMap(vacationSeasonalityList);

        PaymentComponentDTO vacationBonusBaseComponent = componentMap.get("VACATION_BONUS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double vacationSeasonality = getCachedValue(vacationSeasonalityMap, nextPeriod);

        PaymentComponentDTO vacationBonusComponent = new PaymentComponentDTO();
        vacationBonusComponent.setPaymentComponent("VACATION_BONUS");
        if (vacationBonusBaseComponent != null) {
            double vacationBonusBase = vacationBonusBaseComponent.getAmount().doubleValue();
            double vacationBonusPerMonth = vacationBonusBase * vacationSeasonality;
            vacationBonusComponent.setAmount(BigDecimal.valueOf(vacationBonusPerMonth));
            vacationBonusComponent.setProjections(generateMonthProjection(period, range, vacationBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastVacationSeasonality = 0;
            for (MonthProjection projection : vacationBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double vacationBonusBaseProjection = projection.getAmount().doubleValue();
                ParametersDTO vacationSeasonalityParameter = vacationSeasonalityMap.get(month);
                double vacationSeasonalityValue;
                if (vacationSeasonalityParameter != null) {
                    vacationSeasonalityValue = vacationSeasonalityParameter.getValue();
                    lastVacationSeasonality = vacationSeasonalityValue;
                } else {
                    vacationSeasonalityValue = lastVacationSeasonality;
                }
                double vacationBonusPerMonthProjection = vacationBonusBaseProjection * vacationSeasonalityValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(vacationBonusPerMonthProjection));
                projections.add(monthProjection);
            }
            vacationBonusComponent.setProjections(projections);
        } else {
            vacationBonusComponent.setAmount(BigDecimal.valueOf(0));
            vacationBonusComponent.setProjections(generateMonthProjection(period, range, vacationBonusComponent.getAmount()));
        }
        component.add(vacationBonusComponent);
    }

    //Bonificación por Destaque
    //=$BK5*CB$23
    //BK5 = componentBonificacionDestaqueBase
    //CB$23 = parametro Valor Bonificación Destaque
    public void detachmentBonus(List<PaymentComponentDTO> components, String period, Integer range, List<ParametersDTO> detachmentBonusValueList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        Map<String, ParametersDTO> detachmentBonusValueMap = createCacheMap(detachmentBonusValueList);

        PaymentComponentDTO detachmentBonusBaseComponent = componentMap.get("DETACHMENT_BONUS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO detachmentBonusComponent = new PaymentComponentDTO();
        detachmentBonusComponent.setPaymentComponent("DETACHMENT_BONUS");
        if (detachmentBonusBaseComponent != null) {
            double detachmentBonusBase = detachmentBonusBaseComponent.getAmount().doubleValue();
            double detachmentBonusValue = getCachedValue(detachmentBonusValueMap, nextPeriod);
            double detachmentBonusPerMonth = detachmentBonusBase * detachmentBonusValue;
            detachmentBonusComponent.setAmount(BigDecimal.valueOf(detachmentBonusPerMonth));
            detachmentBonusComponent.setProjections(generateMonthProjection(period, range, detachmentBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : detachmentBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double detachmentBonusBaseProjection = projection.getAmount().doubleValue();
                double detachmentBonusPerMonthProjection = detachmentBonusBaseProjection * detachmentBonusValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(detachmentBonusPerMonthProjection));
                projections.add(monthProjection);
            }
            detachmentBonusComponent.setProjections(projections);
        } else {
            detachmentBonusComponent.setAmount(BigDecimal.valueOf(0));
            detachmentBonusComponent.setProjections(generateMonthProjection(period, range, detachmentBonusComponent.getAmount()));
        }
        components.add(detachmentBonusComponent);
    }

    //Gratificación Extraordinaria por Traslado
    //=$BM6= componentGratificacionExtraordinariaTrasladoBase
    public void extraordinaryTransferBonus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO extraordinaryTransferBonusBaseComponent = componentMap.get("EXTRAORDINARY_TRANSFER_BONUS_BASE");

        PaymentComponentDTO extraordinaryTransferBonusComponent = new PaymentComponentDTO();
        extraordinaryTransferBonusComponent.setPaymentComponent("EXTRAORDINARY_TRANSFER_BONUS");
        if (extraordinaryTransferBonusBaseComponent != null) {
            double extraordinaryTransferBonusBase = extraordinaryTransferBonusBaseComponent.getAmount().doubleValue();
            extraordinaryTransferBonusComponent.setAmount(BigDecimal.valueOf(extraordinaryTransferBonusBase));
            extraordinaryTransferBonusComponent.setProjections(generateMonthProjection(period, range, extraordinaryTransferBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : extraordinaryTransferBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double extraordinaryTransferBonusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(extraordinaryTransferBonusBaseProjection));
                projections.add(monthProjection);
            }
            extraordinaryTransferBonusComponent.setProjections(projections);
        } else {
            extraordinaryTransferBonusComponent.setAmount(BigDecimal.valueOf(0));
            extraordinaryTransferBonusComponent.setProjections(generateMonthProjection(period, range, extraordinaryTransferBonusComponent.getAmount()));
        }
        component.add(extraordinaryTransferBonusComponent);
    }


    //Gastos de Desplazamiento
    //$BL5 = travelExpensesComponentBase
    public void travelExpenses(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO travelExpensesBaseComponent = componentMap.get("TRAVEL_EXPENSES_BASE");

        PaymentComponentDTO travelExpensesComponent = new PaymentComponentDTO();
        travelExpensesComponent.setPaymentComponent("TRAVEL_EXPENSES");
        if (travelExpensesBaseComponent != null) {
            double travelExpensesBase = travelExpensesBaseComponent.getAmount().doubleValue();
            travelExpensesComponent.setAmount(BigDecimal.valueOf(travelExpensesBase));
            travelExpensesComponent.setProjections(generateMonthProjection(period, range, travelExpensesComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : travelExpensesBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double travelExpensesBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(travelExpensesBaseProjection));
                projections.add(monthProjection);
            }
            travelExpensesComponent.setProjections(projections);
        } else {
            travelExpensesComponent.setAmount(BigDecimal.valueOf(0));
            travelExpensesComponent.setProjections(generateMonthProjection(period, range, travelExpensesComponent.getAmount()));
        }
        component.add(travelExpensesComponent);
    }

    //COINV(Base Externa)
    //$BY5 = BaseExternaCOINV
    public void coinv(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO coinvComponent = componentMap.get("COINV");
        if (coinvComponent != null) {
            component.add(coinvComponent);
        } else {
            PaymentComponentDTO coinvComponentEmpty = new PaymentComponentDTO();
            coinvComponentEmpty.setPaymentComponent("COINV");
            coinvComponentEmpty.setAmount(BigDecimal.valueOf(0));
            coinvComponentEmpty.setProjections(generateMonthProjection(period, range, coinvComponentEmpty.getAmount()));
            component.add(coinvComponentEmpty);
        }
    }

    //PSP(Base Externa)
    //$BW5 = BaseExternaPSP


    //RSP(Base Externa)
    //$BX5


    //TFSP(Base Externa)
    //=$BV5


    //Bonificación Días Especiales
    //=$BH5= componentBonificacionDiasEspecialesBase
    public void specialDaysBonus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO specialDaysBonusBaseComponent = componentMap.get("SPECIAL_DAYS_BONUS_BASE");

        PaymentComponentDTO specialDaysBonusComponent = new PaymentComponentDTO();
        specialDaysBonusComponent.setPaymentComponent("SPECIAL_DAYS_BONUS");
        if (specialDaysBonusBaseComponent != null) {
            double specialDaysBonusBase = specialDaysBonusBaseComponent.getAmount().doubleValue();
            specialDaysBonusComponent.setAmount(BigDecimal.valueOf(specialDaysBonusBase));
            specialDaysBonusComponent.setProjections(generateMonthProjection(period, range, specialDaysBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : specialDaysBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double specialDaysBonusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(specialDaysBonusBaseProjection));
                projections.add(monthProjection);
            }
            specialDaysBonusComponent.setProjections(projections);
        } else {
            specialDaysBonusComponent.setAmount(BigDecimal.valueOf(0));
            specialDaysBonusComponent.setProjections(generateMonthProjection(period, range, specialDaysBonusComponent.getAmount()));
        }
        component.add(specialDaysBonusComponent);
    }

    //Asignación de Vivienda
    //$BC8 = componentAsignacionViviendaBase
    public void housingAssignment(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO housingAssignmentBaseComponent = componentMap.get("HOUSING_ASSIGNMENT_BASE");

        PaymentComponentDTO housingAssignmentComponent = new PaymentComponentDTO();
        housingAssignmentComponent.setPaymentComponent("HOUSING_ASSIGNMENT");
        if (housingAssignmentBaseComponent != null) {
            double housingAssignmentBase = housingAssignmentBaseComponent.getAmount().doubleValue();
            housingAssignmentComponent.setAmount(BigDecimal.valueOf(housingAssignmentBase));
            housingAssignmentComponent.setProjections(generateMonthProjection(period, range, housingAssignmentComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : housingAssignmentBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double housingAssignmentBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(housingAssignmentBaseProjection));
                projections.add(monthProjection);
            }
            housingAssignmentComponent.setProjections(projections);
        } else {
            housingAssignmentComponent.setAmount(BigDecimal.valueOf(0));
            housingAssignmentComponent.setProjections(generateMonthProjection(period, range, housingAssignmentComponent.getAmount()));
        }
        component.add(housingAssignmentComponent);
    }

    // Bonificación Complementaria
    //$BE5 = componentBonificacionComplementariaBase
    public void complementaryBonus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO complementaryBonusBaseComponent = componentMap.get("COMPLEMENTARY_BONUS_BASE");

        PaymentComponentDTO complementaryBonusComponent = new PaymentComponentDTO();
        complementaryBonusComponent.setPaymentComponent("COMPLEMENTARY_BONUS");
        if (complementaryBonusBaseComponent != null) {
            double complementaryBonusBase = complementaryBonusBaseComponent.getAmount().doubleValue();
            complementaryBonusComponent.setAmount(BigDecimal.valueOf(complementaryBonusBase));
            complementaryBonusComponent.setProjections(generateMonthProjection(period, range, complementaryBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : complementaryBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double complementaryBonusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(complementaryBonusBaseProjection));
                projections.add(monthProjection);
            }
            complementaryBonusComponent.setProjections(projections);
        } else {
            complementaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            complementaryBonusComponent.setProjections(generateMonthProjection(period, range, complementaryBonusComponent.getAmount()));
        }
        component.add(complementaryBonusComponent);
    }

    //Bonificación Responsable Grupo
    //$BF5 = componentBonificacionResponsableGrupoBase
    public void groupResponsibleBonus(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO groupResponsibleBonusBaseComponent = componentMap.get("GROUP_RESPONSIBLE_BONUS_BASE");

        PaymentComponentDTO groupResponsibleBonusComponent = new PaymentComponentDTO();
        groupResponsibleBonusComponent.setPaymentComponent("GROUP_RESPONSIBLE_BONUS");
        if (groupResponsibleBonusBaseComponent != null) {
            double groupResponsibleBonusBase = groupResponsibleBonusBaseComponent.getAmount().doubleValue();
            groupResponsibleBonusComponent.setAmount(BigDecimal.valueOf(groupResponsibleBonusBase));
            groupResponsibleBonusComponent.setProjections(generateMonthProjection(period, range, groupResponsibleBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : groupResponsibleBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double groupResponsibleBonusBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(groupResponsibleBonusBaseProjection));
                projections.add(monthProjection);
            }
            groupResponsibleBonusComponent.setProjections(projections);
        } else {
            groupResponsibleBonusComponent.setAmount(BigDecimal.valueOf(0));
            groupResponsibleBonusComponent.setProjections(generateMonthProjection(period, range, groupResponsibleBonusComponent.getAmount()));
        }
        component.add(groupResponsibleBonusComponent);
    }

    //Complemento Sueldo Básico
    //=$AT5= componentComplementoSueldoBase
    public void basicSalaryComplement(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO basicSalaryComplementBaseComponent = componentMap.get("BASIC_SALARY_COMPLEMENT_BASE");

        PaymentComponentDTO basicSalaryComplementComponent = new PaymentComponentDTO();
        basicSalaryComplementComponent.setPaymentComponent("BASIC_SALARY_COMPLEMENT");
        if (basicSalaryComplementBaseComponent != null) {
            double basicSalaryComplementBase = basicSalaryComplementBaseComponent.getAmount().doubleValue();
            basicSalaryComplementComponent.setAmount(BigDecimal.valueOf(basicSalaryComplementBase));
            basicSalaryComplementComponent.setProjections(generateMonthProjection(period, range, basicSalaryComplementComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : basicSalaryComplementBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double basicSalaryComplementBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(basicSalaryComplementBaseProjection));
                projections.add(monthProjection);
            }
            basicSalaryComplementComponent.setProjections(projections);
        } else {
            basicSalaryComplementComponent.setAmount(BigDecimal.valueOf(0));
            basicSalaryComplementComponent.setProjections(generateMonthProjection(period, range, basicSalaryComplementComponent.getAmount()));
        }
        component.add(basicSalaryComplementComponent);
    }

    //Conceptos Mandato Judicial
    //=$BD5 = componentConceptosMandatoJudicialBase
    public void judicialMandateConcepts(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO judicialMandateConceptsBaseComponent = componentMap.get("JUDICIAL_MANDATE_CONCEPTS_BASE");

        PaymentComponentDTO judicialMandateConceptsComponent = new PaymentComponentDTO();
        judicialMandateConceptsComponent.setPaymentComponent("JUDICIAL_MANDATE_CONCEPTS");
        if (judicialMandateConceptsBaseComponent != null) {
            double judicialMandateConceptsBase = judicialMandateConceptsBaseComponent.getAmount().doubleValue();
            judicialMandateConceptsComponent.setAmount(BigDecimal.valueOf(judicialMandateConceptsBase));
            judicialMandateConceptsComponent.setProjections(generateMonthProjection(period, range, judicialMandateConceptsComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : judicialMandateConceptsBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double judicialMandateConceptsBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(judicialMandateConceptsBaseProjection));
                projections.add(monthProjection);
            }
            judicialMandateConceptsComponent.setProjections(projections);
        } else {
            judicialMandateConceptsComponent.setAmount(BigDecimal.valueOf(0));
            judicialMandateConceptsComponent.setProjections(generateMonthProjection(period, range, judicialMandateConceptsComponent.getAmount()));
        }
        component.add(judicialMandateConceptsComponent);
    }

    //Jornada Tienda
    //=$BG5 = componentJornadaTiendaBase
    public void storeDay(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO storeDayBaseComponent = componentMap.get("STORE_DAY_BASE");

        PaymentComponentDTO storeDayComponent = new PaymentComponentDTO();
        storeDayComponent.setPaymentComponent("STORE_DAY");
        if (storeDayBaseComponent != null) {
            double storeDayBase = storeDayBaseComponent.getAmount().doubleValue();
            storeDayComponent.setAmount(BigDecimal.valueOf(storeDayBase));
            storeDayComponent.setProjections(generateMonthProjection(period, range, storeDayComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : storeDayBaseComponent.getProjections()) {
                String month = projection.getMonth();
                double storeDayBaseProjection = projection.getAmount().doubleValue();
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(storeDayBaseProjection));
                projections.add(monthProjection);
            }
            storeDayComponent.setProjections(projections);
        } else {
            storeDayComponent.setAmount(BigDecimal.valueOf(0));
            storeDayComponent.setProjections(generateMonthProjection(period, range, storeDayComponent.getAmount()));
        }
        component.add(storeDayComponent);
    }

    //Bonificación Traslado
    //=$AX6 = componentBonificacionTrasladoBase
    //CB169 = $AX6
    //=SI(CB169>CC$15;CC$15;CB169)
    //CB169 = componentBonificacionTrasladoBase
    //CC$15 = parametro Monto tope Bonificación Traslado
    public void transferBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> transferBonusAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> transferBonusAmountMap = createCacheMap(transferBonusAmountList);

        PaymentComponentDTO transferBonusBaseComponent = componentMap.get("TRANSFER_BONUS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO transferBonusComponent = new PaymentComponentDTO();
        transferBonusComponent.setPaymentComponent("TRANSFER_BONUS");
        if (transferBonusBaseComponent != null) {
            double transferBonusBase = transferBonusBaseComponent.getAmount().doubleValue();
            double transferBonusAmount = getCachedValue(transferBonusAmountMap, nextPeriod);
            double transferBonus = Math.min(transferBonusBase, transferBonusAmount);
            transferBonusComponent.setAmount(BigDecimal.valueOf(transferBonus));
            transferBonusComponent.setProjections(generateMonthProjection(period, range, transferBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastTransferBonusAmount = 0;
            for (MonthProjection projection : transferBonusBaseComponent.getProjections()) {
                ParametersDTO transferBonusAmountParameter = transferBonusAmountMap.get(projection.getMonth());
                double transferBonusAmountValue;
                if (transferBonusAmountParameter != null) {
                    transferBonusAmountValue = transferBonusAmountParameter.getValue();
                    lastTransferBonusAmount = transferBonusAmountValue;
                } else {
                    transferBonusAmountValue = lastTransferBonusAmount;
                }
                String month = projection.getMonth();
                double transferBonusBaseProjection = projection.getAmount().doubleValue();
                double transferBonusProjection = Math.min(transferBonusBaseProjection, transferBonusAmountValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(transferBonusProjection));
                projections.add(monthProjection);
            }
            transferBonusComponent.setProjections(projections);
        } else {
            transferBonusComponent.setAmount(BigDecimal.valueOf(0));
            transferBonusComponent.setProjections(generateMonthProjection(period, range, transferBonusComponent.getAmount()));
        }
        component.add(transferBonusComponent);
    }

    //Bonificación Vestuario
    //=$AW5 = componentBonificacionVestuarioBase
    //=SI(CB161>CC$14;CC$14;CB161)
    //CB161 = componentBonificacionVestuarioBase
    //CC$14 = parametro Monto tope Bonificación Vestuario
    public void clothingBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> clothingBonusAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> clothingBonusAmountMap = createCacheMap(clothingBonusAmountList);

        PaymentComponentDTO clothingBonusBaseComponent = componentMap.get("CLOTHING_BONUS_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO clothingBonusComponent = new PaymentComponentDTO();
        clothingBonusComponent.setPaymentComponent("CLOTHING_BONUS");
        if (clothingBonusBaseComponent != null) {
            double clothingBonusBase = clothingBonusBaseComponent.getAmount().doubleValue();
            double clothingBonusAmount = getCachedValue(clothingBonusAmountMap, nextPeriod);
            double clothingBonus = Math.min(clothingBonusBase, clothingBonusAmount);
            clothingBonusComponent.setAmount(BigDecimal.valueOf(clothingBonus));
            clothingBonusComponent.setProjections(generateMonthProjection(period, range, clothingBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastClothingBonusAmount = 0;
            for (MonthProjection projection : clothingBonusBaseComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO clothingBonusAmountParameter = clothingBonusAmountMap.get(month);
                double clothingBonusAmountValue;
                if (clothingBonusAmountParameter != null) {
                    clothingBonusAmountValue = clothingBonusAmountParameter.getValue();
                    lastClothingBonusAmount = clothingBonusAmountValue;
                } else {
                    clothingBonusAmountValue = lastClothingBonusAmount;
                }
                double clothingBonusBaseProjection = projection.getAmount().doubleValue();
                double clothingBonusProjection = Math.min(clothingBonusBaseProjection, clothingBonusAmountValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(clothingBonusProjection));
                projections.add(monthProjection);
            }
            clothingBonusComponent.setProjections(projections);
        } else {
            clothingBonusComponent.setAmount(BigDecimal.valueOf(0));
            clothingBonusComponent.setProjections(generateMonthProjection(period, range, clothingBonusComponent.getAmount()));
        }
        component.add(clothingBonusComponent);
    }

    //ley de teletrabajo
    //=SI($AY6>CB$16;CB$16;$AY6)
    //AY6 = componentLeyTeletrabajoBase
    //CB$16 = parametro Monto tope Ley Teletrabajo
    public void teleworkLaw(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> teleworkLawAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> teleworkLawAmountMap = createCacheMap(teleworkLawAmountList);

        PaymentComponentDTO teleworkLawBaseComponent = componentMap.get("TELEWORK_LAW_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO teleworkLawComponent = new PaymentComponentDTO();
        teleworkLawComponent.setPaymentComponent("TELEWORK_LAW");
        if (teleworkLawBaseComponent != null) {
            double teleworkLawBase = teleworkLawBaseComponent.getAmount().doubleValue();
            double teleworkLawAmount = getCachedValue(teleworkLawAmountMap, nextPeriod);
            double teleworkLaw = Math.min(teleworkLawBase, teleworkLawAmount);
            teleworkLawComponent.setAmount(BigDecimal.valueOf(teleworkLaw));
            teleworkLawComponent.setProjections(generateMonthProjection(period, range, teleworkLawComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastTeleworkLawAmount = 0;
            for (MonthProjection projection : teleworkLawBaseComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO teleworkLawAmountParameter = teleworkLawAmountMap.get(projection.getMonth());
                double teleworkLawAmountValue;
                if (teleworkLawAmountParameter != null) {
                    teleworkLawAmountValue = teleworkLawAmountParameter.getValue();
                    lastTeleworkLawAmount = teleworkLawAmountValue;
                } else {
                    teleworkLawAmountValue = lastTeleworkLawAmount;
                }
                double teleworkLawBaseProjection = projection.getAmount().doubleValue();
                double teleworkLawProjection = Math.min(teleworkLawBaseProjection, teleworkLawAmountValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(teleworkLawProjection));
                projections.add(monthProjection);
            }
            teleworkLawComponent.setProjections(projections);
        } else {
            teleworkLawComponent.setAmount(BigDecimal.valueOf(0));
            teleworkLawComponent.setProjections(generateMonthProjection(period, range, teleworkLawComponent.getAmount()));
        }
        component.add(teleworkLawComponent);
    }

    //Movilidad y Refrigerio
    //=SI($AV6>CC$13;CC$13;$AV6)
    //AV6 = componentMovilidadRefrigerioBase
    //CC$13 = parametro Monto tope Movilidad y Refrigerio
    public void mobilityAndRefreshment(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> mobilityAndRefreshmentAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> mobilityAndRefreshmentAmountMap = createCacheMap(mobilityAndRefreshmentAmountList);

        PaymentComponentDTO mobilityAndRefreshmentBaseComponent = componentMap.get("MOBILITY_AND_REFRESHMENT_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO mobilityAndRefreshmentComponent = new PaymentComponentDTO();
        mobilityAndRefreshmentComponent.setPaymentComponent("MOBILITY_AND_REFRESHMENT");
        if (mobilityAndRefreshmentBaseComponent != null) {
            double mobilityAndRefreshmentBase = mobilityAndRefreshmentBaseComponent.getAmount().doubleValue();
            double mobilityAndRefreshmentAmount = getCachedValue(mobilityAndRefreshmentAmountMap, nextPeriod);
            double mobilityAndRefreshment = Math.min(mobilityAndRefreshmentBase, mobilityAndRefreshmentAmount);
            mobilityAndRefreshmentComponent.setAmount(BigDecimal.valueOf(mobilityAndRefreshment));
            mobilityAndRefreshmentComponent.setProjections(generateMonthProjection(period, range, mobilityAndRefreshmentComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastMobilityAndRefreshmentAmount = 0;
            for (MonthProjection projection : mobilityAndRefreshmentBaseComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO mobilityAndRefreshmentAmountParameter = mobilityAndRefreshmentAmountMap.get(month);
                double mobilityAndRefreshmentAmountValue;
                if (mobilityAndRefreshmentAmountParameter != null) {
                    mobilityAndRefreshmentAmountValue = mobilityAndRefreshmentAmountParameter.getValue();
                    lastMobilityAndRefreshmentAmount = mobilityAndRefreshmentAmountValue;
                } else {
                    mobilityAndRefreshmentAmountValue = lastMobilityAndRefreshmentAmount;
                }
                double mobilityAndRefreshmentBaseProjection = projection.getAmount().doubleValue();
                double mobilityAndRefreshmentProjection = Math.max(mobilityAndRefreshmentBaseProjection, mobilityAndRefreshmentAmountValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(mobilityAndRefreshmentProjection));
                projections.add(monthProjection);
            }
            mobilityAndRefreshmentComponent.setProjections(projections);
        } else {
            mobilityAndRefreshmentComponent.setAmount(BigDecimal.valueOf(0));
            mobilityAndRefreshmentComponent.setProjections(generateMonthProjection(period, range, mobilityAndRefreshmentComponent.getAmount()));
        }
        component.add(mobilityAndRefreshmentComponent);
    }

    //Asignación Familiar
    //=SI($AU6>0;CC$12*CC$11;0)
    //AU6 = componentAsignacionFamiliarBase
    //CC$12 = parametro % Asignación Familiar
    //CC$11 = parametro Salario Mínimo
    public void familyAssignment(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> familyAssignmentPercentageList, List<ParametersDTO> minimumSalaryList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> familyAssignmentPercentageMap = createCacheMap(familyAssignmentPercentageList);
        Map<String, ParametersDTO> minimumSalaryMap = createCacheMap(minimumSalaryList);

        PaymentComponentDTO familyAssignmentBaseComponent = componentMap.get("FAMILY_ASSIGNMENT_BASE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO familyAssignmentComponent = new PaymentComponentDTO();
        familyAssignmentComponent.setPaymentComponent("FAMILY_ASSIGNMENT");
        if (familyAssignmentBaseComponent != null) {
            double familyAssignmentPercentage = getCachedValue(familyAssignmentPercentageMap, nextPeriod) / 100;
            double minimumSalary = getCachedValue(minimumSalaryMap, nextPeriod);
            double familyAssignment = familyAssignmentBaseComponent.getAmount().doubleValue() > 0 ?  familyAssignmentPercentage * minimumSalary : 0;
            familyAssignmentComponent.setAmount(BigDecimal.valueOf(familyAssignment));
            familyAssignmentComponent.setProjections(generateMonthProjection(period, range, familyAssignmentComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastFamilyAssignmentPercentage = 0;
            for (MonthProjection projection : familyAssignmentBaseComponent.getProjections()) {
                ParametersDTO familyAssignmentPercentageParameter = familyAssignmentPercentageMap.get(projection.getMonth());
                ParametersDTO minimumSalaryParameter = minimumSalaryMap.get(projection.getMonth());
                double familyAssignmentPercentageValue;
                if (familyAssignmentPercentageParameter != null) {
                    familyAssignmentPercentageValue = familyAssignmentPercentageParameter.getValue() / 100;
                    lastFamilyAssignmentPercentage = familyAssignmentPercentageValue;
                } else {
                    familyAssignmentPercentageValue = lastFamilyAssignmentPercentage;
                }
                double minimumSalaryProjection;
                if (minimumSalaryParameter != null) {
                    minimumSalaryProjection = getCachedValue(minimumSalaryMap, period);
                } else {
                    minimumSalaryProjection = minimumSalary;
                }
                String month = projection.getMonth();
                double familyAssignmentProjection = projection.getAmount().doubleValue() > 0 ? familyAssignmentPercentageValue * minimumSalaryProjection : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(familyAssignmentProjection));
                projections.add(monthProjection);
            }
            familyAssignmentComponent.setProjections(projections);
        } else {
            familyAssignmentComponent.setAmount(BigDecimal.valueOf(0));
            familyAssignmentComponent.setProjections(generateMonthProjection(period, range, familyAssignmentComponent.getAmount()));
        }
        component.add(familyAssignmentComponent);
    }

    //Bono Lump Sum
    //=SI(O($W6="EJC";$W6="GER");CC$29/(CONTAR.SI($W$5:$W$11;"EJC")+CONTAR.SI($W$5:$W$11;"GER"));0)
    //W6 = PoName
    //($W$5:$W$11;"EJC") = long countEJC
    //($W$5:$W$11;"GER") = long countGER
    //CC$29 = parametro Monto Bono Lump Sum
    public void lumpSumBonus(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> lumpSumBonusAmountList, long countEJC, long countGER, Map<String, EmployeeClassification> classificationMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> lumpSumBonusAmountMap = createCacheMap(lumpSumBonusAmountList);
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        // Si no hay coincidencia exacta, buscar la posición más similar
      /*  if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }*/

        if (optionalEmployeeClassification.isPresent() && (optionalEmployeeClassification.get().getTypeEmp().equals("EJC") || optionalEmployeeClassification.get().getTypeEmp().equals("GER"))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO lumpSumBonusComponent = new PaymentComponentDTO();
            lumpSumBonusComponent.setPaymentComponent("LUMP_SUM_BONUS");
            double lumpSumBonusAmount = getCachedValue(lumpSumBonusAmountMap, nextPeriod);
            double lumpSumBonus = (countEJC > 0 || countGER > 0) ? lumpSumBonusAmount / (countEJC + countGER) : 0;
            lumpSumBonusComponent.setAmount(BigDecimal.valueOf(lumpSumBonus));
            lumpSumBonusComponent.setProjections(generateMonthProjection(period, range, lumpSumBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastLumpSumBonusAmount = 0;
            for (MonthProjection projection : lumpSumBonusComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO lumpSumBonusAmountParameter = lumpSumBonusAmountMap.get(month);
                double lumpSumBonusAmountValue;
                if (lumpSumBonusAmountParameter != null) {
                    lumpSumBonusAmountValue = lumpSumBonusAmountParameter.getValue();
                    lastLumpSumBonusAmount = lumpSumBonusAmountValue;
                } else {
                    lumpSumBonusAmountValue = lastLumpSumBonusAmount;
                }
                double lumpSumBonusProjection = (countEJC > 0 || countGER > 0) ? lumpSumBonusAmountValue / (countEJC + countGER) : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(lumpSumBonusProjection));
                projections.add(monthProjection);
            }
            lumpSumBonusComponent.setProjections(projections);
            component.add(lumpSumBonusComponent);
        } else {
            PaymentComponentDTO lumpSumBonusComponentEmpty = new PaymentComponentDTO();
            lumpSumBonusComponentEmpty.setPaymentComponent("LUMP_SUM_BONUS");
            lumpSumBonusComponentEmpty.setAmount(BigDecimal.valueOf(0));
            lumpSumBonusComponentEmpty.setProjections(generateMonthProjection(period, range, lumpSumBonusComponentEmpty.getAmount()));
            component.add(lumpSumBonusComponentEmpty);
        }
    }

    //Signing Bonus
    //=SI(O($W6="EJC";$W6="GER");CC$30/(CONTAR.SI($W$5:$W$11;"EJC")+CONTAR.SI($W$5:$W$11;"GER"));0)
    //W6 = PoName
    //($W$5:$W$11;"EJC") = long countEJC
    //($W$5:$W$11;"GER") = long countGER
    //CC$30 = parametro Monto Signing Bonus
    public void signingBonus(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> signingBonusAmountList, long countEJC, long countGER, Map<String, EmployeeClassification> classificationMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> signingBonusAmountMap = createCacheMap(signingBonusAmountList);
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        // Si no hay coincidencia exacta, buscar la posición más similar
        /*if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }*/

        if (optionalEmployeeClassification.isPresent() && (optionalEmployeeClassification.get().getTypeEmp().equals("EJC") || optionalEmployeeClassification.get().getTypeEmp().equals("GER"))) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO signingBonusComponent = new PaymentComponentDTO();
            signingBonusComponent.setPaymentComponent("SIGNING_BONUS");
            double signingBonusAmount = getCachedValue(signingBonusAmountMap, nextPeriod);
            double signingBonus = (countEJC > 0 || countGER > 0) ? signingBonusAmount / (countEJC + countGER) : 0;
            signingBonusComponent.setAmount(BigDecimal.valueOf(signingBonus));
            signingBonusComponent.setProjections(generateMonthProjection(period, range, signingBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastSigningBonusAmount = 0;
            for (MonthProjection projection : signingBonusComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO signingBonusAmountParameter = signingBonusAmountMap.get(month);
                double signingBonusAmountValue;
                if (signingBonusAmountParameter != null) {
                    signingBonusAmountValue = signingBonusAmountParameter.getValue();
                    lastSigningBonusAmount = signingBonusAmountValue;
                } else {
                    signingBonusAmountValue = lastSigningBonusAmount;
                }
                double signingBonusProjection = (countEJC > 0 || countGER > 0) ? signingBonusAmountValue / (countEJC + countGER) : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(signingBonusProjection));
                projections.add(monthProjection);
            }
            signingBonusComponent.setProjections(projections);
            component.add(signingBonusComponent);
        } else {
            PaymentComponentDTO signingBonusComponentEmpty = new PaymentComponentDTO();
            signingBonusComponentEmpty.setPaymentComponent("SIGNING_BONUS");
            signingBonusComponentEmpty.setAmount(BigDecimal.valueOf(0));
            signingBonusComponentEmpty.setProjections(generateMonthProjection(period, range, signingBonusComponentEmpty.getAmount()));
            component.add(signingBonusComponentEmpty);
        }
    }

    //Gratificación Extraordinaria Convenio
    //=SI($W6="Emp";CC$31/CONTAR.SI.CONJUNTO($W$5:$W$11;"EMP");0)
    //W6 = PoName
    //($W$5:$W$11;"EMP") = long countEMP
    //CC$31 = parametro Monto Gratificación Extraordinaria Convenio
    public void extraordinaryConventionBonus(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> extraordinaryConventionBonusAmountList, long countEMP, Map<String, EmployeeClassification> classificationMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> extraordinaryConventionBonusAmountMap = createCacheMap(extraordinaryConventionBonusAmountList);
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        // Si no hay coincidencia exacta, buscar la posición más similar
        /*if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }*/

        if (optionalEmployeeClassification.isPresent() && optionalEmployeeClassification.get().getTypeEmp().equals("EMP")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO extraordinaryConventionBonusComponent = new PaymentComponentDTO();
            extraordinaryConventionBonusComponent.setPaymentComponent("EXTRAORDINARY_CONVENTION_BONUS");
            double extraordinaryConventionBonusAmount = getCachedValue(extraordinaryConventionBonusAmountMap, nextPeriod);
            double extraordinaryConventionBonus = countEMP > 0 ? extraordinaryConventionBonusAmount / countEMP : 0;
            extraordinaryConventionBonusComponent.setAmount(BigDecimal.valueOf(extraordinaryConventionBonus));
            extraordinaryConventionBonusComponent.setProjections(generateMonthProjection(period, range, extraordinaryConventionBonusComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastExtraordinaryConventionBonusAmount = 0;
            for (MonthProjection projection : extraordinaryConventionBonusComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO extraordinaryConventionBonusAmountParameter = extraordinaryConventionBonusAmountMap.get(month);
                double extraordinaryConventionBonusAmountValue;
                if (extraordinaryConventionBonusAmountParameter != null) {
                    extraordinaryConventionBonusAmountValue = extraordinaryConventionBonusAmountParameter.getValue();
                    lastExtraordinaryConventionBonusAmount = extraordinaryConventionBonusAmountValue;
                } else {
                    extraordinaryConventionBonusAmountValue = lastExtraordinaryConventionBonusAmount;
                }
                double extraordinaryConventionBonusProjection = countEMP > 0 ? extraordinaryConventionBonusAmountValue / countEMP : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(extraordinaryConventionBonusProjection));
                projections.add(monthProjection);
            }
            extraordinaryConventionBonusComponent.setProjections(projections);
            component.add(extraordinaryConventionBonusComponent);
        } else {
            PaymentComponentDTO extraordinaryConventionBonusComponentEmpty = new PaymentComponentDTO();
            extraordinaryConventionBonusComponentEmpty.setPaymentComponent("EXTRAORDINARY_CONVENTION_BONUS");
            extraordinaryConventionBonusComponentEmpty.setAmount(BigDecimal.valueOf(0));
            extraordinaryConventionBonusComponentEmpty.setProjections(generateMonthProjection(period, range, extraordinaryConventionBonusComponentEmpty.getAmount()));
            component.add(extraordinaryConventionBonusComponentEmpty);
        }
    }

    //Bonificación Extraordinaria
    //=CC$32/CONTARA($S$5:$S$11)
    //CC$32 = parametro Monto Bonificación Extraordinaria
    //CONTARA($S$5:$S$11) = long total positions
    public void extraordinaryBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> extraordinaryBonusAmountList, long totalPositions) {

        Map<String, ParametersDTO> extraordinaryBonusAmountMap = createCacheMap(extraordinaryBonusAmountList);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        PaymentComponentDTO extraordinaryBonusComponent = new PaymentComponentDTO();
        extraordinaryBonusComponent.setPaymentComponent("EXTRAORDINARY_BONUS");
        double extraordinaryBonusAmount = getCachedValue(extraordinaryBonusAmountMap, nextPeriod);
        double extraordinaryBonus = totalPositions > 0 ? extraordinaryBonusAmount / totalPositions : 0;
        extraordinaryBonusComponent.setAmount(BigDecimal.valueOf(extraordinaryBonus));
        extraordinaryBonusComponent.setProjections(generateMonthProjection(period, range, extraordinaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        double lastExtraordinaryBonusAmount = 0;
        for (MonthProjection projection : extraordinaryBonusComponent.getProjections()) {
            String month = projection.getMonth();
            ParametersDTO extraordinaryBonusAmountParameter = extraordinaryBonusAmountMap.get(month);
            double extraordinaryBonusAmountValue;
            if (extraordinaryBonusAmountParameter != null) {
                extraordinaryBonusAmountValue = extraordinaryBonusAmountParameter.getValue();
                lastExtraordinaryBonusAmount = extraordinaryBonusAmountValue;
            } else {
                extraordinaryBonusAmountValue = lastExtraordinaryBonusAmount;
            }
            double extraordinaryBonusProjection = totalPositions > 0 ? extraordinaryBonusAmountValue / totalPositions : 0;
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(extraordinaryBonusProjection));
            projections.add(monthProjection);
        }
        extraordinaryBonusComponent.setProjections(projections);
        component.add(extraordinaryBonusComponent);
    }

    //Contribución EPS
    //=$BN6 = componentContribucionEPSBase
    public void epsContribution(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO epsContributionBaseComponent = componentMap.get("EPS_CONTRIBUTION_BASE");
        if (epsContributionBaseComponent != null) {
            PaymentComponentDTO epsContributionComponent = new PaymentComponentDTO();
            epsContributionComponent.setPaymentComponent("EPS_CONTRIBUTION");
            if (epsContributionBaseComponent != null) {
                double epsContributionBase = epsContributionBaseComponent.getAmount().doubleValue();
                epsContributionComponent.setAmount(BigDecimal.valueOf(epsContributionBase));
                epsContributionComponent.setProjections(generateMonthProjection(period, range, epsContributionComponent.getAmount()));
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : epsContributionBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double epsContributionBaseProjection = projection.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(epsContributionBaseProjection));
                    projections.add(monthProjection);
                }
                epsContributionComponent.setProjections(projections);
            } else {
                epsContributionComponent.setAmount(BigDecimal.valueOf(0));
                epsContributionComponent.setProjections(generateMonthProjection(period, range, epsContributionComponent.getAmount()));
            }
            component.add(epsContributionComponent);
        } else {
            PaymentComponentDTO epsContributionComponentEmpty = new PaymentComponentDTO();
            epsContributionComponentEmpty.setPaymentComponent("EPS_CONTRIBUTION");
            epsContributionComponentEmpty.setAmount(BigDecimal.valueOf(0));
            epsContributionComponentEmpty.setProjections(generateMonthProjection(period, range, epsContributionComponentEmpty.getAmount()));
            component.add(epsContributionComponentEmpty);

        }
    }

    //Asignación Escolar
    //=$BF5*CC$21
    //BF5 = componentAsignacionEscolarBase
    //CC$21 = parametro Estacionalidad Asignación escolar
    public void schoolAssignment(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> schoolAssignmentAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> schoolAssignmentAmountMap = createCacheMap(schoolAssignmentAmountList);

        PaymentComponentDTO schoolAssignmentBaseComponent = componentMap.get("SCHOOL_ASSIGNMENT_BASE");
        if (schoolAssignmentBaseComponent != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO schoolAssignmentComponent = new PaymentComponentDTO();
            schoolAssignmentComponent.setPaymentComponent("SCHOOL_ASSIGNMENT");
            if (schoolAssignmentBaseComponent != null) {
                double schoolAssignmentBase = schoolAssignmentBaseComponent.getAmount().doubleValue();
                double schoolAssignmentAmount = getCachedValue(schoolAssignmentAmountMap, nextPeriod);
                double schoolAssignment = schoolAssignmentBase * schoolAssignmentAmount;
                schoolAssignmentComponent.setAmount(BigDecimal.valueOf(schoolAssignment));
                schoolAssignmentComponent.setProjections(generateMonthProjection(period, range, schoolAssignmentComponent.getAmount()));
                List<MonthProjection> projections = new ArrayList<>();
                double lastSchoolAssignmentAmount = 0;
                for (MonthProjection projection : schoolAssignmentBaseComponent.getProjections()) {
                    ParametersDTO schoolAssignmentAmountParameter = schoolAssignmentAmountMap.get(projection.getMonth());
                    double schoolAssignmentAmountValue;
                    if (schoolAssignmentAmountParameter != null) {
                        schoolAssignmentAmountValue = schoolAssignmentAmountParameter.getValue();
                        lastSchoolAssignmentAmount = schoolAssignmentAmountValue;
                    } else {
                        schoolAssignmentAmountValue = lastSchoolAssignmentAmount;
                    }
                    String month = projection.getMonth();
                    double schoolAssignmentBaseProjection = projection.getAmount().doubleValue();
                    double schoolAssignmentProjection = schoolAssignmentBaseProjection * schoolAssignmentAmountValue;
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(schoolAssignmentProjection));
                    projections.add(monthProjection);
                }
                schoolAssignmentComponent.setProjections(projections);
            } else {
                schoolAssignmentComponent.setAmount(BigDecimal.valueOf(0));
                schoolAssignmentComponent.setProjections(generateMonthProjection(period, range, schoolAssignmentComponent.getAmount()));
            }
            component.add(schoolAssignmentComponent);
        } else {
            PaymentComponentDTO schoolAssignmentComponentEmpty = new PaymentComponentDTO();
            schoolAssignmentComponentEmpty.setPaymentComponent("SCHOOL_ASSIGNMENT");
            schoolAssignmentComponentEmpty.setAmount(BigDecimal.valueOf(0));
            schoolAssignmentComponentEmpty.setProjections(generateMonthProjection(period, range, schoolAssignmentComponentEmpty.getAmount()));
            component.add(schoolAssignmentComponentEmpty);
        }
    }

    //Bonificación Estudios Pre Escolar y Superior
    //=$BG6*CC$22
    //BG6 = componentBonificacionEstudiosBase
    //CC$22 = parametro Estacionalidad Bon. Pre Escolar y Superior
    public void studiesBonus(List<PaymentComponentDTO> component, String period, Integer range, List<ParametersDTO> studiesBonusAmountList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> studiesBonusAmountMap = createCacheMap(studiesBonusAmountList);

        PaymentComponentDTO studiesBonusBaseComponent = componentMap.get("STUDIES_BONUS_BASE");
        if (studiesBonusBaseComponent != null) {

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO studiesBonusComponent = new PaymentComponentDTO();

            studiesBonusComponent.setPaymentComponent("STUDIES_BONUS");
            if (studiesBonusBaseComponent != null) {
                double studiesBonusBase = studiesBonusBaseComponent.getAmount().doubleValue();
                double studiesBonusAmount = getCachedValue(studiesBonusAmountMap, nextPeriod);
                double studiesBonus = studiesBonusBase * studiesBonusAmount;
                studiesBonusComponent.setAmount(BigDecimal.valueOf(studiesBonus));
                studiesBonusComponent.setProjections(generateMonthProjection(period, range, studiesBonusComponent.getAmount()));
                List<MonthProjection> projections = new ArrayList<>();
                double lastStudiesBonusAmount = 0;
                for (MonthProjection projection : studiesBonusBaseComponent.getProjections()) {
                    ParametersDTO studiesBonusAmountParameter = studiesBonusAmountMap.get(projection.getMonth());
                    double studiesBonusAmountValue;
                    if (studiesBonusAmountParameter != null) {
                        studiesBonusAmountValue = studiesBonusAmountParameter.getValue();
                        lastStudiesBonusAmount = studiesBonusAmountValue;
                    } else {
                        studiesBonusAmountValue = lastStudiesBonusAmount;
                    }
                    String month = projection.getMonth();
                    double studiesBonusBaseProjection = projection.getAmount().doubleValue();
                    double studiesBonusProjection = studiesBonusBaseProjection * studiesBonusAmountValue;
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(studiesBonusProjection));
                    projections.add(monthProjection);
                }
                studiesBonusComponent.setProjections(projections);
            } else {
                studiesBonusComponent.setAmount(BigDecimal.valueOf(0));
                studiesBonusComponent.setProjections(generateMonthProjection(period, range, studiesBonusComponent.getAmount()));
            }
            component.add(studiesBonusComponent);
        } else {
            PaymentComponentDTO studiesBonusComponentEmpty = new PaymentComponentDTO();
            studiesBonusComponentEmpty.setPaymentComponent("STUDIES_BONUS");
            studiesBonusComponentEmpty.setAmount(BigDecimal.valueOf(0));
            studiesBonusComponentEmpty.setProjections(generateMonthProjection(period, range, studiesBonusComponentEmpty.getAmount()));
            component.add(studiesBonusComponentEmpty);
        }
    }

    //Prestaciones Alimentarias
    //=ARRAY_CONSTRAIN(ARRAYFORMULA(SI.ERROR(INDICE($D$3:$D$59;COINCIDIR(VERDADERO;SI(ESNUMERO(ENCONTRAR($B$3:$B$59;$V6));VERDADERO;FALSO);0));0)/12); 1; 1)
    //D3:D59 = classificationMap
    //B3:B59 = classificationMap
    //V6 = PoName
    public void foodBenefits(List<PaymentComponentDTO> component, String period, Integer range, String poName, Map<String, EmployeeClassification> classificationMap) {
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        // Si no hay coincidencia exacta, buscar la posición más similar
        /*if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }*/
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            //String categoryLocal = employeeClassification.getTypeEmp();
            PaymentComponentDTO foodBenefitsComponent = new PaymentComponentDTO();
            foodBenefitsComponent.setPaymentComponent("FOOD_BENEFITS");
            double foodBenefits = employeeClassification.getValueAllowance() / 12;
            foodBenefitsComponent.setAmount(BigDecimal.valueOf(foodBenefits));
            foodBenefitsComponent.setProjections(generateMonthProjection(period, range, foodBenefitsComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : foodBenefitsComponent.getProjections()) {
                String month = projection.getMonth();
                MonthProjection monthProjection = new MonthProjection();
                double foodBenefitsProjection = projection.getAmount().doubleValue();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(foodBenefitsProjection));
                projections.add(monthProjection);
            }
            foodBenefitsComponent.setProjections(projections);
            component.add(foodBenefitsComponent);
        } else {
            PaymentComponentDTO foodBenefitsComponentEmpty = new PaymentComponentDTO();
            foodBenefitsComponentEmpty.setPaymentComponent("FOOD_BENEFITS");
            foodBenefitsComponentEmpty.setAmount(BigDecimal.valueOf(0));
            foodBenefitsComponentEmpty.setProjections(generateMonthProjection(period, range, foodBenefitsComponentEmpty.getAmount()));
            component.add(foodBenefitsComponentEmpty);
        }
    }

    //BECARIOS
    //===SI($W6="FLJ";SI($V6="Joven ejecutivo";$CB$9*(1+1/12);SI($V6="Practicante";$CB$10*(1+1/12);0));0)
    //$W6= classificationMap FLJ
    //$V6= PoName
    //CD$9 = parametro Salario Joven Ejecutivo
    //CD$10 = parametro Salario Practicante
    public void interns(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> youngExecutiveSalaryList, List<ParametersDTO> internSalaryList, Map<String, EmployeeClassification> classificationMap) {
        Map<String, ParametersDTO> youngExecutiveSalaryMap = createCacheMap(youngExecutiveSalaryList);
        Map<String, ParametersDTO> internSalaryMap = createCacheMap(internSalaryList);

        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        if (optionalEmployeeClassification.isPresent() && (optionalEmployeeClassification.get().getTypeEmp().equals("FLJ") || optionalEmployeeClassification.get().getTypeEmp().equals("PRA"))) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            PaymentComponentDTO internsComponent = new PaymentComponentDTO();
            internsComponent.setPaymentComponent("INTERNS");
            double youngExecutiveSalary = getCachedValue(youngExecutiveSalaryMap, period);
            double internSalary = getCachedValue(internSalaryMap, period);
            double interns = 0;
            if (employeeClassification.getTypeEmp().equals("FLJ")) {
                interns = youngExecutiveSalary * (1 + (1 / 12.0));
            } else if (employeeClassification.getTypeEmp().equals("PRA")) {
                interns = internSalary * (1 + (1 / 12.0));
            }
            internsComponent.setAmount(BigDecimal.valueOf(interns));
            internsComponent.setProjections(generateMonthProjection(period, range, internsComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastYoungExecutiveSalary = 0;
            double lastInternSalary = 0;
            for (MonthProjection projection : internsComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO youngExecutiveSalaryParameter = youngExecutiveSalaryMap.get(month);
                double youngExecutiveSalaryProjection;
                if (youngExecutiveSalaryParameter != null) {
                    youngExecutiveSalaryProjection = youngExecutiveSalaryParameter.getValue();
                    lastYoungExecutiveSalary = youngExecutiveSalaryProjection;
                } else {
                    youngExecutiveSalaryProjection = lastYoungExecutiveSalary;
                }
                ParametersDTO internSalaryParameter = internSalaryMap.get(month);
                double internSalaryProjection;
                if (internSalaryParameter != null) {
                    internSalaryProjection = internSalaryParameter.getValue();
                    lastInternSalary = internSalaryProjection;
                } else {
                    internSalaryProjection = lastInternSalary;
                }
                double internsProjection = 0;
                if (employeeClassification.getTypeEmp().equals("FLJ")) {
                    internsProjection = youngExecutiveSalaryProjection * (1 + (1 / 12.0));
                } else if (employeeClassification.getTypeEmp().equals("PRA")) {
                    internsProjection = internSalaryProjection * (1 + (1 / 12.0));
                }
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(internsProjection));
                projections.add(monthProjection);
            }
            internsComponent.setProjections(projections);
            component.add(internsComponent);
        } else {
            PaymentComponentDTO internsComponentEmpty = new PaymentComponentDTO();
            internsComponentEmpty.setPaymentComponent("INTERNS");
            internsComponentEmpty.setAmount(BigDecimal.valueOf(0));
            internsComponentEmpty.setProjections(generateMonthProjection(period, range, internsComponentEmpty.getAmount()));
            component.add(internsComponentEmpty);
        }
    }

    //Seguro Medico
    //=$BR5
   /* public void medicalInsurance(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO medicalInsuranceBaseComponent = componentMap.get("seguro_medico");
        if (medicalInsuranceBaseComponent != null) {
            PaymentComponentDTO medicalInsuranceComponent = new PaymentComponentDTO();
            medicalInsuranceComponent.setPaymentComponent("MEDICAL_INSURANCE");
            double medicalInsuranceBase = medicalInsuranceBaseComponent.getAmount().doubleValue();
            medicalInsuranceComponent.setAmount(BigDecimal.valueOf(medicalInsuranceBase));
            medicalInsuranceComponent.setProjections(generateMonthProjection(period, range, medicalInsuranceComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : medicalInsuranceBaseComponent.getProjections()) {
                String month = projection.getMonth();
                MonthProjection monthProjection = new MonthProjection();
                double medicalInsuranceBaseProjection = projection.getAmount().doubleValue();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(medicalInsuranceBaseProjection));
                projections.add(monthProjection);
            }
            medicalInsuranceComponent.setProjections(projections);
            component.add(medicalInsuranceComponent);
        } else {
            PaymentComponentDTO medicalInsuranceComponentEmpty = new PaymentComponentDTO();
            medicalInsuranceComponentEmpty.setPaymentComponent("MEDICAL_INSURANCE");
            medicalInsuranceComponentEmpty.setAmount(BigDecimal.valueOf(0));
            medicalInsuranceComponentEmpty.setProjections(generateMonthProjection(period, range, medicalInsuranceComponentEmpty.getAmount()));
            component.add(medicalInsuranceComponentEmpty);
        }
    }*/

    //Provisión de Vacaciones
    //==CC35/30*$CC$6/12
    //CC35 = theoricSalary
    //CC$6 = Dias de vacaciones disp anual
    public void vacationProvision(List<PaymentComponentDTO> component, String period, Integer range,
                                  List<ParametersDTO> annualVacationDaysList) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        if (theoricSalaryComponent != null) {
            double theoricSalary = theoricSalaryComponent.getAmount().doubleValue();
            PaymentComponentDTO vacationProvisionComponent = new PaymentComponentDTO();
            vacationProvisionComponent.setPaymentComponent("VACATION_PROVISION");
            //nextPeriod
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            Map<String, ParametersDTO> annualVacationDaysMap = createCacheMap(annualVacationDaysList);
            double annualVacationDays = getCachedValue(annualVacationDaysMap, nextPeriod);
            double vacationProvision = theoricSalary / 30 * annualVacationDays / 12;
            vacationProvisionComponent.setAmount(BigDecimal.valueOf(vacationProvision));
            vacationProvisionComponent.setProjections(generateMonthProjection(period, range, vacationProvisionComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastVacationProvisionAmount = 0;
            for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                ParametersDTO annualVacationDaysParameter = annualVacationDaysMap.get(month);
                double annualVacationDaysValue;
                if (annualVacationDaysParameter != null) {
                    annualVacationDaysValue = annualVacationDaysParameter.getValue();
                    lastVacationProvisionAmount = annualVacationDaysValue;
                } else {
                    annualVacationDaysValue = lastVacationProvisionAmount;
                }
                MonthProjection monthProjection = new MonthProjection();
                double theoricSalaryProjection = projection.getAmount().doubleValue();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(theoricSalaryProjection / 30 * annualVacationDaysValue / 12));
                projections.add(monthProjection);
            }
            vacationProvisionComponent.setProjections(projections);
            component.add(vacationProvisionComponent);
        } else {
            PaymentComponentDTO vacationProvisionComponentEmpty = new PaymentComponentDTO();
            vacationProvisionComponentEmpty.setPaymentComponent("VACATION_PROVISION");
            vacationProvisionComponentEmpty.setAmount(BigDecimal.valueOf(0));
            vacationProvisionComponentEmpty.setProjections(generateMonthProjection(period, range, vacationProvisionComponentEmpty.getAmount()));
            component.add(vacationProvisionComponentEmpty);
        }
    }

    //calcular la antigüedad en años de la PO
    //=SIFECHA($Y5;CC$34;"y")
    //Y5 = Fecha de Contrato
    //CC$34 = Mes actual
    public long calculateSeniorityYears(LocalDate hiringDate, String period) {
        if (hiringDate == null) {
            return 0;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);

        // Si el mes de la proyección no coincide con el mes de contratación, devolver 0
        if (yearMonth.getMonth() != hiringDate.getMonth()) {
            return 0;
        }

        return ChronoUnit.YEARS.between(hiringDate, yearMonth.atEndOfMonth());
    }

    //Auxiliar en excel para calcular quinquenios ganados
    //= SI.ERROR(BUSCARV(CC913;$P$3:$Q$11;2;0);0)
    //CC913 =SENIORITY_YEARS_PO
    //P3:Q11 = quinquenniumMap
    // Map<String, EmployeeClassification> classificationMap
    public BigDecimal getQuinquenniumValue(long seniorityYears, Map<Integer, BigDecimal> quinquenniumMap) {
        return quinquenniumMap.getOrDefault((int) seniorityYears, BigDecimal.ZERO);
    }

    //CLASIFICAR PO


    //Antigüedad
    //=CC919*CC37*CC926
    //CC919 = auxQuinquennium
    //CC913 = =SIFECHA($Y8;CC$34;"y")
    //CC37 = TheoricSalary
    //CC926 = SI(MES(CC$34)=MES($Y5);1;0)
    //CC$34 = Mes actual
    //Y5 = Fecha de Contrato
    //P3:Q11 = quinquenniumMap
    //period = Mes base proyección - formato yyyyMM
    //hiringDate = Fecha de Contrato - formato yyyy-MM-dd
    public void seniority(List<PaymentComponentDTO> components, String period, Integer range, LocalDate hiringDate, Map<Integer, BigDecimal> quinquenniumMap, Map<String, EmployeeClassification> classificationMap, String poName) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            if (theoricSalaryComponent != null && "EMP".equals(employeeClassification.getTypeEmp())) {
                PaymentComponentDTO seniorityComponent = new PaymentComponentDTO();
                seniorityComponent.setPaymentComponent("SENIORITY");
                seniorityComponent.setAmount(BigDecimal.ZERO);

                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                    String month = projection.getMonth();
                    long seniorityYears = calculateSeniorityYears(hiringDate, month);

                    if (seniorityYears > 0) {
                        BigDecimal quinquenniumValue = getQuinquenniumValue(seniorityYears, quinquenniumMap);
                        double theoricSalaryProjection = projection.getAmount().doubleValue();
                        double seniorityProjection = quinquenniumValue.doubleValue() * theoricSalaryProjection;

                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.valueOf(seniorityProjection));
                        projections.add(monthProjection);

                        // Si este es el mes actual, actualizamos el monto principal
                        /*if (month.equals(period)) {
                            seniorityComponent.setAmount(BigDecimal.valueOf(seniorityProjection));
                        }*/

                   /*     log.info("Mes de coincidencia: {}, Fecha de contratacion {}, Años de antigüedad: {}, Valor quinquenio: {}, Salario teórico: {}, Antigüedad calculada: {}",
                                month, hiringDate, seniorityYears, quinquenniumValue, theoricSalaryProjection, seniorityProjection);*/
                    } else {
                        // Para los meses que no coinciden, establecemos el valor en cero
                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.ZERO);
                        projections.add(monthProjection);
                    }
                }

                seniorityComponent.setProjections(projections);
                components.add(seniorityComponent);
            } else {
                // Si no es un empleado o no hay componente de salario teórico, añadimos un componente de antigüedad vacío
                PaymentComponentDTO seniorityComponentEmpty = new PaymentComponentDTO();
                seniorityComponentEmpty.setPaymentComponent("SENIORITY");
                seniorityComponentEmpty.setAmount(BigDecimal.ZERO);
                seniorityComponentEmpty.setProjections(generateMonthProjection(period, range, BigDecimal.ZERO));
                components.add(seniorityComponentEmpty);
            }
        }else {
            // Manejar el caso cuando no se encuentra la clasificación del empleado
            addEmptySeniorityComponent(components, period, range);
        }
    }

    public void seniority2(List<PaymentComponentDTO> components, String period, Integer range, LocalDate hiringDate, Map<Integer, BigDecimal> quinquenniumMap, Map<String, EmployeeClassification> classificationMap, String poName) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        log.info("classificationMap: {}", classificationMap);
        log.info("employeeClassification: {}", classificationMap.get(poName.toUpperCase()));
        if (theoricSalaryComponent != null) {
            if ("EMP".equals(poName)) {
                PaymentComponentDTO seniorityComponent = new PaymentComponentDTO();
                seniorityComponent.setPaymentComponent("SENIORITY");
                seniorityComponent.setAmount(BigDecimal.ZERO);
                List<MonthProjection> projections = new ArrayList<>();

                for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                    String month = projection.getMonth();
                    LocalDate currentDate = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM")).atEndOfMonth();
                    log.info("currentDate: {}", currentDate);
                    log.info("hiringDate: {}", hiringDate);
                    // Verificar si es el mes de aniversario
                    boolean isAnniversaryMonth = currentDate.getMonth() == hiringDate.getMonth() && currentDate.getDayOfMonth() >= hiringDate.getDayOfMonth();

                    if (isAnniversaryMonth) {
                        long seniorityYears = ChronoUnit.YEARS.between(hiringDate, currentDate);
                        BigDecimal quinquenniumValue = getQuinquenniumValue(seniorityYears, quinquenniumMap);
                        double theoricSalary = projection.getAmount().doubleValue();
                        double seniority = quinquenniumValue.doubleValue() * theoricSalary;

                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.valueOf(seniority));
                        projections.add(monthProjection);

                        // Actualizar el monto total si es el mes actual
                        if (month.equals(period)) {
                            seniorityComponent.setAmount(BigDecimal.valueOf(seniority));
                        }
                    } else {
                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.ZERO);
                        projections.add(monthProjection);
                    }
                }

                seniorityComponent.setProjections(projections);
                components.add(seniorityComponent);
            }
        } else {
            // Manejar el caso cuando no se encuentra la clasificación del empleado
            addEmptySeniorityComponent(components, period, range);
        }
    }

    private void addEmptySeniorityComponent(List<PaymentComponentDTO> components, String period, Integer range) {
        PaymentComponentDTO seniorityComponentEmpty = new PaymentComponentDTO();
        seniorityComponentEmpty.setPaymentComponent("SENIORITY");
        seniorityComponentEmpty.setAmount(BigDecimal.ZERO);
        seniorityComponentEmpty.setProjections(generateMonthProjection(period, range, BigDecimal.ZERO));
        components.add(seniorityComponentEmpty);
    }

    //[Auxiliar en excel para calcular bono]
    //==SI(MES(CL$34)=10;CL35*14*MAX($AB5;$AC5)/12;0)
    //CL$34 = Mes actual
    //CL35 = TheoricSalary
    //$AB5 = componentBonoBase PC960451
    //$AC5 = componentBonoBase PC960452
    public double calculateBonus(double theoricSalary, double maxBonusBase, String month) {
        YearMonth monthYearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        if (monthYearMonth.getMonthValue() == 10) {
            return theoricSalary * 14 * maxBonusBase / 12;
        }
        return 0;
    }

    //Bono SRD (100%)
    //=SI(AÑO(CE$34)=AÑO($CL$34);$CL126;$CX126)
    //CE$34 = Mes actual
    //CL$34 = Mes Octubre
    //$CL126 = calculateBonus para el año actual
    //$CX126 = calculateBonus para el año siguiente
    public void srdBonus(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        double maxBonusBase = getMaxBonusBase(componentMap);

        PaymentComponentDTO srdBonusComponent = new PaymentComponentDTO();
        srdBonusComponent.setPaymentComponent("SRD_BONUS");

        YearMonth startMonth = YearMonth.parse(period, MONTH_FORMATTER);
        Map<Integer, Double> yearlyBonuses = calculateYearlyBonuses(theoricSalaryComponent, maxBonusBase, startMonth, range);

        List<MonthProjection> projections = generateProjections(startMonth, range, yearlyBonuses);

        srdBonusComponent.setProjections(projections);
        srdBonusComponent.setAmount(projections.get(0).getAmount());

        components.add(srdBonusComponent);
    }

    private double getMaxBonusBase(Map<String, PaymentComponentDTO> componentMap) {
        double bonusBase1 = getComponentValue(componentMap.get("PC960451"));
        double bonusBase2 = getComponentValue(componentMap.get("PC960452"));
        return Math.max(bonusBase1, bonusBase2);
    }

    private double getComponentValue(PaymentComponentDTO component) {
        return component != null ? component.getAmount().doubleValue() / 100 : 0;
    }

    private Map<Integer, Double> calculateYearlyBonuses(PaymentComponentDTO theoricSalaryComponent,
                                                        double maxBonusBase,
                                                        YearMonth startMonth,
                                                        int range) {
        Map<Integer, Double> yearlyBonuses = new HashMap<>();
        YearMonth endMonth = startMonth.plusMonths(range - 1);

        for (int year = startMonth.getYear(); year <= endMonth.getYear(); year++) {
            YearMonth octoberOfYear = YearMonth.of(year, 10);
            if (octoberOfYear.compareTo(startMonth) >= 0 && octoberOfYear.compareTo(endMonth) <= 0) {
                double octoberSalary = theoricSalaryComponent.getProjections().stream()
                        .filter(p -> YearMonth.parse(p.getMonth(), MONTH_FORMATTER).equals(octoberOfYear))
                        .findFirst()
                        .map(p -> p.getAmount().doubleValue())
                        .orElse(0.0);
                //log.info("octoberSalary: {}", octoberSalary);
                //log.info("maxBonusBase: {}", maxBonusBase);
                //double bonusBasePerMonth = (1 + maxBonusBase) / 12;
                double bonusBasePerMonth = maxBonusBase / 12;
                //log.info("bonusBasePerMonth: {}", bonusBasePerMonth);
                double yearlyBonus = (octoberSalary * 14) * bonusBasePerMonth;
                yearlyBonuses.put(year, yearlyBonus);
            }
        }

        return yearlyBonuses;
    }

    private List<MonthProjection> generateProjections(YearMonth startMonth, int range, Map<Integer, Double> yearlyBonuses) {
        List<MonthProjection> projections = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            YearMonth currentMonth = startMonth.plusMonths(i);
            double bonusAmount = yearlyBonuses.getOrDefault(currentMonth.getYear(), 0.0);
            projections.add(new MonthProjection(currentMonth.format(MONTH_FORMATTER), BigDecimal.valueOf(bonusAmount)));
        }
        return projections;
    }

    //Bono Top Performer
    //=CC134*SI($W6="EJC";CC$17*CC$18;SI($W6="DIR";CC$19*CC$20;0))*12
    //CC134 = Bono SRD
    //W6 = PoName
    //CC$17 = parametro BTP: % de Personas(EJC)
    //CC$18 = parametro BTP: % de Bono(EJC)
    //CC$19 = parametro BTP: % de Personas(Dir)
    //CC$20 = parametro BTP: % de Bono(Dir)
    /*public void topPerformerBonus(List<PaymentComponentDTO> components, String period, Integer range, String poName, List<ParametersDTO> ejcPeopleBTPList, List<ParametersDTO> ejcBonusBTPList, List<ParametersDTO> dirPeopleBTPList, List<ParametersDTO> dirBonusBTPList, Map<String, EmployeeClassification> classificationMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO srdBonusComponent = componentMap.get("SRD_BONUS");
        Map<String, ParametersDTO> ejcPeopleBTPMap = createCacheMap(ejcPeopleBTPList);
        Map<String, ParametersDTO> ejcBonusBTPMap = createCacheMap(ejcBonusBTPList);
        Map<String, ParametersDTO> dirPeopleBTPMap = createCacheMap(dirPeopleBTPList);
        Map<String, ParametersDTO> dirBonusBTPMap = createCacheMap(dirBonusBTPList);
        double srdBonus = srdBonusComponent != null ? srdBonusComponent.getAmount().doubleValue() : 0;
        double ejcPeopleBTP = getCachedValue(ejcPeopleBTPMap, period);
        double ejcBonusBTP = getCachedValue(ejcBonusBTPMap, period);
        double dirPeopleBTP = getCachedValue(dirPeopleBTPMap, period);
        double dirBonusBTP = getCachedValue(dirBonusBTPMap, period);
        double topPerformerBonus = 0;
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            if (employeeClassification.getTypeEmp().equals("EJC")) {
                topPerformerBonus = srdBonus * ejcPeopleBTP * ejcBonusBTP * 12;
            } else if (employeeClassification.getTypeEmp().equals("DIR")) {
                topPerformerBonus = srdBonus * dirPeopleBTP * dirBonusBTP * 12;
            }else {
                topPerformerBonus = 0;
            }
        }
        PaymentComponentDTO topPerformerBonusComponent = new PaymentComponentDTO();
        topPerformerBonusComponent.setPaymentComponent("TOP_PERFORMER_BONUS");
        topPerformerBonusComponent.setAmount(BigDecimal.valueOf(topPerformerBonus));
        topPerformerBonusComponent.setProjections(generateMonthProjection(period, range, topPerformerBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        double lastEjcPeopleBTP = 0;
        double lastEjcBonusBTP = 0;
        double lastDirPeopleBTP = 0;
        double lastDirBonusBTP = 0;
        for (MonthProjection projection : topPerformerBonusComponent.getProjections()) {
            String month = projection.getMonth();
            double topPerformerBonusProjection = 0;
            double ejcPeopleBTPProjection;
            if (ejcPeopleBTPMap.get(month) != null) {
                ejcPeopleBTPProjection = ejcPeopleBTPMap.get(month).getValue();
                lastEjcPeopleBTP = ejcPeopleBTPProjection;
            } else {
                ejcPeopleBTPProjection = lastEjcPeopleBTP;
            }
            double ejcBonusBTPProjection;
            if (ejcBonusBTPMap.get(month) != null) {
                ejcBonusBTPProjection = ejcBonusBTPMap.get(month).getValue();
                lastEjcBonusBTP = ejcBonusBTPProjection;
            } else {
                ejcBonusBTPProjection = lastEjcBonusBTP;
            }
            double dirPeopleBTPProjection;
            if (dirPeopleBTPMap.get(month) != null) {
                dirPeopleBTPProjection = dirPeopleBTPMap.get(month).getValue();
                lastDirPeopleBTP = dirPeopleBTPProjection;
            } else {
                dirPeopleBTPProjection = lastDirPeopleBTP;
            }
            double dirBonusBTPProjection;
            if (dirBonusBTPMap.get(month) != null) {
                dirBonusBTPProjection = dirBonusBTPMap.get(month).getValue();
                lastDirBonusBTP = dirBonusBTPProjection;
            } else {
                dirBonusBTPProjection = lastDirBonusBTP;
            }
            Optional<EmployeeClassification> optionalEmployeeClassificationProjection = Optional.ofNullable(classificationMap.get(poName));
            if (optionalEmployeeClassificationProjection.isPresent()) {
                EmployeeClassification employeeClassificationProjection = optionalEmployeeClassificationProjection.get();
                if (employeeClassificationProjection.getTypeEmp().equals("EJC")) {
                    topPerformerBonusProjection = srdBonus * ejcPeopleBTPProjection * ejcBonusBTPProjection * 12;
                } else if (employeeClassificationProjection.getTypeEmp().equals("DIR")) {
                    topPerformerBonusProjection = srdBonus * dirPeopleBTPProjection * dirBonusBTPProjection * 12;
                }else {
                    topPerformerBonusProjection = 0;
                }
            }
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(topPerformerBonusProjection));
            projections.add(monthProjection);
        }
        topPerformerBonusComponent.setProjections(projections);
        components.add(topPerformerBonusComponent);
    }*/
    public void topPerformerBonus(List<PaymentComponentDTO> components, String period, Integer range, String poName,
                                  List<ParametersDTO> ejcPeopleBTPList, List<ParametersDTO> ejcBonusBTPList,
                                  List<ParametersDTO> dirPeopleBTPList, List<ParametersDTO> dirBonusBTPList,
                                  Map<String, EmployeeClassification> classificationMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO srdBonusComponent = componentMap.get("SRD_BONUS");

        // Crear cachés para los parámetros
        Map<String, ParametersDTO> ejcPeopleBTPMap = createCacheMap(ejcPeopleBTPList);
        Map<String, ParametersDTO> ejcBonusBTPMap = createCacheMap(ejcBonusBTPList);
        Map<String, ParametersDTO> dirPeopleBTPMap = createCacheMap(dirPeopleBTPList);
        Map<String, ParametersDTO> dirBonusBTPMap = createCacheMap(dirBonusBTPList);

        // Obtener el valor del bono SRD
        double srdBonus = srdBonusComponent != null ? srdBonusComponent.getAmount().doubleValue() : 0;

        // Obtener la clase de empleado
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName.toUpperCase()));
        /*if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }*/

        double topPerformerBonus = 0;
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            String typeEmp = employeeClassification.getTypeEmp();

            if ("EJC".equals(typeEmp)) {
                double ejcPeopleBTP = getCachedValue(ejcPeopleBTPMap, period);
                double ejcBonusBTP = getCachedValue(ejcBonusBTPMap, period);
                topPerformerBonus = srdBonus * ejcPeopleBTP * ejcBonusBTP * 12;
            } else if ("DIR".equals(typeEmp)) {
                double dirPeopleBTP = getCachedValue(dirPeopleBTPMap, period);
                double dirBonusBTP = getCachedValue(dirBonusBTPMap, period);
                topPerformerBonus = srdBonus * dirPeopleBTP * dirBonusBTP * 12;
            }
        }

        PaymentComponentDTO topPerformerBonusComponent = new PaymentComponentDTO();
        topPerformerBonusComponent.setPaymentComponent("TOP_PERFORMER_BONUS");
        topPerformerBonusComponent.setAmount(BigDecimal.valueOf(topPerformerBonus));
        topPerformerBonusComponent.setProjections(generateMonthProjection(period, range, topPerformerBonusComponent.getAmount()));

        // Generar proyecciones
        List<MonthProjection> projections = new ArrayList<>();
        double lastEjcPeopleBTP = 0;
        double lastEjcBonusBTP = 0;
        double lastDirPeopleBTP = 0;
        double lastDirBonusBTP = 0;

        for (MonthProjection projection : topPerformerBonusComponent.getProjections()) {
            String month = projection.getMonth();
            double topPerformerBonusProjection = 0;

            double ejcPeopleBTPProjection = getCachedOrLastValue(ejcPeopleBTPMap, month, lastEjcPeopleBTP);
            lastEjcPeopleBTP = ejcPeopleBTPProjection;

            double ejcBonusBTPProjection = getCachedOrLastValue(ejcBonusBTPMap, month, lastEjcBonusBTP);
            lastEjcBonusBTP = ejcBonusBTPProjection;

            double dirPeopleBTPProjection = getCachedOrLastValue(dirPeopleBTPMap, month, lastDirPeopleBTP);
            lastDirPeopleBTP = dirPeopleBTPProjection;

            double dirBonusBTPProjection = getCachedOrLastValue(dirBonusBTPMap, month, lastDirBonusBTP);
            lastDirBonusBTP = dirBonusBTPProjection;

            if (optionalEmployeeClassification.isPresent()) {
                EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
                String typeEmp = employeeClassification.getTypeEmp();

                if ("EJC".equals(typeEmp)) {
                    topPerformerBonusProjection = srdBonus * ejcPeopleBTPProjection * ejcBonusBTPProjection * 12;
                } else if ("DIR".equals(typeEmp)) {
                    topPerformerBonusProjection = srdBonus * dirPeopleBTPProjection * dirBonusBTPProjection * 12;
                }
            }

            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(topPerformerBonusProjection));
            projections.add(monthProjection);
        }

        topPerformerBonusComponent.setProjections(projections);
        components.add(topPerformerBonusComponent);
    }

    private double getCachedOrLastValue(Map<String, ParametersDTO> map, String period, double lastValue) {
        return map.get(period) != null ? map.get(period).getValue() : lastValue;
    }

    //Crédito EPS (devolución ESSALUD)
    //=-1*CC897
    //CC897 = componentCreditEPS
    public void epsCredit(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO creditEPSComponent = componentMap.get("CREDIT_EPS");
        double creditEPS = creditEPSComponent != null ? -1 * creditEPSComponent.getAmount().doubleValue() : 0;
        PaymentComponentDTO epsCreditComponent = new PaymentComponentDTO();
        epsCreditComponent.setPaymentComponent("EPS_CREDIT");
        epsCreditComponent.setAmount(BigDecimal.valueOf(creditEPS));
        epsCreditComponent.setProjections(generateMonthProjection(period, range, epsCreditComponent.getAmount()));
        components.add(epsCreditComponent);
    }

    //Plan Prev Dir Aport Vol Emp
    //=$BQ5 VOLUNTARY_CONTRIBUTION_BASE
    //=SI(CC35<>0;CB1001*(CC35/CB35);0)
    //CB1001 = componentVoluntaryContributionBase
    //CC35 = theoricSalary
    //CB35 = Math.max(componentPC960400.getAmount().doubleValue() / 14, componentPC960401.getAmount().doubleValue());
    public void voluntaryContribution(List<PaymentComponentDTO> components, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        PaymentComponentDTO PC960400Component = componentMap.get("PC960400");
        PaymentComponentDTO PC960401Component = componentMap.get("PC960401");
        PaymentComponentDTO voluntaryContributionBaseComponent = componentMap.get("VOLUNTARY_CONTRIBUTION_BASE");
        double theoricSalary = theoricSalaryComponent != null ? theoricSalaryComponent.getAmount().doubleValue() : 0;
        double voluntaryContributionBase = voluntaryContributionBaseComponent != null ? voluntaryContributionBaseComponent.getAmount().doubleValue() : 0;
        double voluntaryContribution = 0;
        if (theoricSalary != 0) {
            double max = Math.max(PC960400Component.getAmount().doubleValue() / 14, PC960401Component.getAmount().doubleValue());
            voluntaryContribution = voluntaryContributionBase * (theoricSalary / max);
        }
        PaymentComponentDTO voluntaryContributionComponent = new PaymentComponentDTO();
        voluntaryContributionComponent.setPaymentComponent("VOLUNTARY_CONTRIBUTION");
        voluntaryContributionComponent.setAmount(BigDecimal.valueOf(voluntaryContribution));
        voluntaryContributionComponent.setProjections(generateMonthProjection(period, range, voluntaryContributionComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoricSalaryComponent != null) {
            for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                double voluntaryContributionProjection = 0;
                if (theoricSalary != 0) {
                    double max = Math.max(PC960400Component.getAmount().doubleValue() / 14, PC960401Component.getAmount().doubleValue());
                    voluntaryContributionProjection = voluntaryContributionBase * (theoricSalary / max);
                }
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(voluntaryContributionProjection));
                projections.add(monthProjection);
            }
            voluntaryContributionComponent.setProjections(projections);
        } else {
            voluntaryContributionComponent.setAmount(BigDecimal.valueOf(0));
            voluntaryContributionComponent.setProjections(generateMonthProjection(period, range, voluntaryContributionComponent.getAmount()));
        }
        components.add(voluntaryContributionComponent);
    }

    //Gratificación - Salario Teórico
    //='Modelo PERU'!CC35*$K$3
    //CC35 = theoricSalary
    //$K$3 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void theoreticalSalaryGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-TEORIC-SALARY");
        double theoricSalary = theoricSalaryComponent != null ? theoricSalaryComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Salario Teórico");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = theoricSalary * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoricSalaryComponent != null) {
            for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Provisión de Vacaciones
    //==CC49*$K$4
    //CC49 = vacationProvision
    //$K$4 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void vacationProvisionGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO vacationProvisionComponent = componentMap.get("VACATION_PROVISION");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-VACATION_PROVISION");
        double vacationProvision = vacationProvisionComponent != null ? vacationProvisionComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Provisión de Vacaciones");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = vacationProvision * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (vacationProvisionComponent != null) {
            for (MonthProjection projection : vacationProvisionComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Compensación por Vivienda
    //=CC63*$K$5
    //CC63 = housingCompensation
    //$K$5 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingCompensationGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingCompensationComponent = componentMap.get("HOUSING");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-HOUSING");
        double housingCompensation = housingCompensationComponent != null ? housingCompensationComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Compensación por Vivienda");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = housingCompensation * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingCompensationComponent != null) {
            for (MonthProjection projection : housingCompensationComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Incremento AFP 10,23%
    //=CC77*$K$6
    //CC77 = afpIncrement
    //$K$6 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void afpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO afpIncrementComponent = componentMap.get("INCREASE_AFP_1023");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-AFP_INCREMENT");
        double afpIncrement = afpIncrementComponent != null ? afpIncrementComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Incremento AFP 10,23%");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = afpIncrement * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (afpIncrementComponent != null) {
            for (MonthProjection projection : afpIncrementComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Incremento 3,3% + Incremento SNP 3,3%
    //==+CC85*$K$7
    //CC85 = snpIncrement
    //$K$7 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void snpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO snpIncrementComponent = componentMap.get("INCREASE_SNP_AND_INCREASE");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-INCREASE_SNP_AND_INCREASE");
        double snpIncrement = snpIncrementComponent != null ? snpIncrementComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Incremento 3,3% + Incremento SNP 3,3%");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = snpIncrement * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (snpIncrementComponent != null) {
            for (MonthProjection projection : snpIncrementComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Complemento Sueldo Basico
    //==CC140*$K$10
    //CC140 = basicSalaryComplement
    //$K$10 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void basicSalaryComplementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO basicSalaryComplementComponent = componentMap.get("BASIC_SALARY_COMPLEMENT");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-BASIC_SALARY_COMPLEMENT");

        double basicSalaryComplement = basicSalaryComplementComponent != null ? basicSalaryComplementComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Complemento Sueldo Basico");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double initialGratification = basicSalaryComplement * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(initialGratification));

        // Generate full range of projections with initial gratification
        List<MonthProjection> fullProjections = generateMonthProjection(period, range, BigDecimal.valueOf(initialGratification));

        if (basicSalaryComplementComponent != null && basicSalaryComplementComponent.getProjections() != null) {
            // Create a map of existing projections for easy lookup
            Map<String, BigDecimal> basicSalaryComplementProjections = basicSalaryComplementComponent.getProjections().stream()
                    .collect(Collectors.toMap(MonthProjection::getMonth, MonthProjection::getAmount));

            // Update full projections with calculated gratification values
            for (MonthProjection projection : fullProjections) {
                BigDecimal basicSalaryComplementAmount = basicSalaryComplementProjections.get(projection.getMonth());
                if (basicSalaryComplementAmount != null) {
                    double gratificationProjection = basicSalaryComplementAmount.doubleValue() * gratificationConceptValue;
                    projection.setAmount(BigDecimal.valueOf(gratificationProjection));
                }
            }
        }

        gratificationComponent.setProjections(fullProjections);
        components.add(gratificationComponent);
    }

    //Gratificación - Asignación Familiar
    //=CC147*$K$11
    //CC147 = familyAllowance
    //$K$11 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void familyAllowanceGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO familyAllowanceComponent = componentMap.get("FAMILY_ASSIGNMENT");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-FAMILY_ASSIGNMENT");
        double familyAllowance = familyAllowanceComponent != null ? familyAllowanceComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Asignación Familiar");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = familyAllowance * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (familyAllowanceComponent != null) {
            for (MonthProjection projection : familyAllowanceComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Ley Teletrabajo
    //=CC148*$K$11
    //CC148 = teleworkLaw
    //$K$11 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void teleworkLawGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO teleworkLawComponent = componentMap.get("TELEWORK_LAW");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-TELEWORK_LAW");
        double teleworkLaw = teleworkLawComponent != null ? teleworkLawComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Ley Teletrabajo");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = teleworkLaw * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (teleworkLawComponent != null) {
            for (MonthProjection projection : teleworkLawComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bono Top Performer
    //=CC182*$K$13
    //CC182 = topPerformerBonus
    //$K$13 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void topPerformerBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO topPerformerBonusComponent = componentMap.get("TOP_PERFORMER_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-TOP_PERFORMER_BONUS");
        double topPerformerBonus = topPerformerBonusComponent != null ? topPerformerBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bono Top Performer");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = topPerformerBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (topPerformerBonusComponent != null) {
            for (MonthProjection projection : topPerformerBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bonificación Responsable Grupo
    //=CC183*$K$14
    //CC183 = groupResponsibleBonus
    //$K$14 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void groupResponsibleBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO groupResponsibleBonusComponent = componentMap.get("GROUP_RESPONSIBLE_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-GROUP_RESPONSIBLE_BONUS");
        double groupResponsibleBonus = groupResponsibleBonusComponent != null ? groupResponsibleBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bonificación Responsable Grupo");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = groupResponsibleBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (groupResponsibleBonusComponent != null) {
            for (MonthProjection projection : groupResponsibleBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Jornada Tienda
    //=CC198*$K$15
    //CC198 = storeDay
    //$K$15 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void storeDayGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO storeDayComponent = componentMap.get("STORE_DAY");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-STORE_DAY");
        double storeDay = storeDayComponent != null ? storeDayComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Jornada Tienda");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = storeDay * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (storeDayComponent != null) {
            for (MonthProjection projection : storeDayComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Asignación de Vivienda
    //=CC199*$K$16
    //CC199 = housingAssignment
    //$K$16 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingAssignmentGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingAssignmentComponent = componentMap.get("HOUSING_ASSIGNMENT");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-HOUSING_ASSIGNMENT");
        double housingAssignment = housingAssignmentComponent != null ? housingAssignmentComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Asignación de Vivienda");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = housingAssignment * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingAssignmentComponent != null) {
            for (MonthProjection projection : housingAssignmentComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Conceptos Mandato Judicial
    //=CC200*$K$17
    //CC200 = judicialMandateConcepts
    //$K$17 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void judicialMandateConceptsGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO judicialMandateConceptsComponent = componentMap.get("JUDICIAL_MANDATE_CONCEPTS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-JUDICIAL_MANDATE_CONCEPTS");
        double judicialMandateConcepts = judicialMandateConceptsComponent != null ? judicialMandateConceptsComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Conceptos Mandato Judicial");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = judicialMandateConcepts * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (judicialMandateConceptsComponent != null) {
            for (MonthProjection projection : judicialMandateConceptsComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bonificación Complementaria
    //=CC201*$K$18
    //CC201 = complementaryBonus
    //$K$18 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void complementaryBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO complementaryBonusComponent = componentMap.get("COMPLEMENTARY_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-COMPLEMENTARY_BONUS");
        double complementaryBonus = complementaryBonusComponent != null ? complementaryBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bonificación Complementaria");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = complementaryBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (complementaryBonusComponent != null) {
            for (MonthProjection projection : complementaryBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bonificación Días Especiales
    //=CC202*$K$19
    //CC202 = specialDaysBonus
    //$K$19 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void specialDaysBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO specialDaysBonusComponent = componentMap.get("SPECIAL_DAYS_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-SPECIAL_DAYS_BONUS");
        double specialDaysBonus = specialDaysBonusComponent != null ? specialDaysBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bonificación Días Especiales");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = specialDaysBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (specialDaysBonusComponent != null) {
            for (MonthProjection projection : specialDaysBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bono disponibilidad
    //=CC203*$K$20
    //CC203 = availabilityBonus
    //$K$20 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void availabilityBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO availabilityBonusComponent = componentMap.get("AVAILABILITY_PLUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-AVAILABILITY_PLUS");
        double availabilityBonus = availabilityBonusComponent != null ? availabilityBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bono disponibilidad");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = availabilityBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (availabilityBonusComponent != null) {
            for (MonthProjection projection : availabilityBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bono por trabajo nocturno
    //=CC204*$K$21
    //CC204 = nightWorkBonus
    //$K$21 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void nightWorkBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO nightWorkBonusComponent = componentMap.get("NIGHT_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-NIGHT_BONUS");
        double nightWorkBonus = nightWorkBonusComponent != null ? nightWorkBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bono por trabajo nocturno");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = nightWorkBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (nightWorkBonusComponent != null) {
            for (MonthProjection projection : nightWorkBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Gratificación - Bonificación por Destaque
    //=CC205*$K$22
    //=CC205*$K$22
    //CC205 = detachmentBonus
    //$K$22 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void detachmentBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO detachmentBonusComponent = componentMap.get("DETACHMENT_BONUS");
        PaymentComponentDTO gratificationComponent = new PaymentComponentDTO();
        gratificationComponent.setPaymentComponent("GRATIFICATION-DETACHMENT_BONUS");
        double detachmentBonus = detachmentBonusComponent != null ? detachmentBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal gratificationConcept = conceptoPresupuestalMap.get("Bonificación por Destaque");
        double gratificationConceptValue = gratificationConcept != null ? gratificationConcept.getGratificacion().doubleValue() : 0;
        double gratification = detachmentBonus * gratificationConceptValue;
        gratificationComponent.setAmount(BigDecimal.valueOf(gratification));
        gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (detachmentBonusComponent != null) {
            for (MonthProjection projection : detachmentBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double gratificationProjection = projection.getAmount().doubleValue() * gratificationConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(gratificationProjection));
                projections.add(monthProjection);
            }
            gratificationComponent.setProjections(projections);
        } else {
            gratificationComponent.setAmount(BigDecimal.valueOf(0));
            gratificationComponent.setProjections(generateMonthProjection(period, range, gratificationComponent.getAmount()));
        }
        components.add(gratificationComponent);
    }

    //Bonif. Ext. Temp. - Salario Teórico
    //=CC36*$N$3
    //CC36 = theoreticalSalary
    //$N$3 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void theoreticalSalaryTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoreticalSalaryComponent = componentMap.get("THEORETICAL_SALARY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-THEORETICAL_SALARY");
        double theoreticalSalary = theoreticalSalaryComponent != null ? theoreticalSalaryComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Salario Teórico");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = theoreticalSalary * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoreticalSalaryComponent != null) {
            for (MonthProjection projection : theoreticalSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Provisión de Vacaciones
    //=CC37*$N$4
    //CC37 = vacationProvision
    //$N$4 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void vacationProvisionTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO vacationProvisionComponent = componentMap.get("VACATION_PROVISION");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-VACATION_PROVISION");
        double vacationProvision = vacationProvisionComponent != null ? vacationProvisionComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Provisión de Vacaciones");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = vacationProvision * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (vacationProvisionComponent != null) {
            for (MonthProjection projection : vacationProvisionComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Compensación por Vivienda
    //=CC38*$N$5
    //CC38 = housingCompensation
    //$N$5 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingCompensationTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingCompensationComponent = componentMap.get("HOUSING");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-HOUSING");
        double housingCompensation = housingCompensationComponent != null ? housingCompensationComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Compensación por Vivienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = housingCompensation * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingCompensationComponent != null) {
            for (MonthProjection projection : housingCompensationComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Incremento AFP 10,23%
    //=CC39*$N$6
    //CC39 = afpIncrement
    //$N$6 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void afpIncrementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO afpIncrementComponent = componentMap.get("INCREASE_AFP_1023");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-AFP_INCREMENT");
        double afpIncrement = afpIncrementComponent != null ? afpIncrementComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Incremento AFP 10,23%");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = afpIncrement * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (afpIncrementComponent != null) {
            for (MonthProjection projection : afpIncrementComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Incremento 3,3% + Incremento SNP 3,3%
    //=CC40*$N$7
    //CC40 = INCREASE_SNP_AND_INCREASE
    //$N$7 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void increaseSNPAndIncreaseTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO increaseSNPAndIncreaseComponent = componentMap.get("INCREASE_SNP_AND_INCREASE");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-INCREASE_SNP_AND_INCREASE");
        double increaseSNPAndIncrease = increaseSNPAndIncreaseComponent != null ? increaseSNPAndIncreaseComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Incremento 3,3% + Incremento SNP 3,3%");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = increaseSNPAndIncrease * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (increaseSNPAndIncreaseComponent != null) {
            for (MonthProjection projection : increaseSNPAndIncreaseComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Complemento Sueldo Basico
    //=CC41*$N$8
    //CC41 = basicSalaryComplement
    //$N$8 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void basicSalaryComplementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO basicSalaryComplementComponent = componentMap.get("BASIC_SALARY_COMPLEMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-BASIC_SALARY_COMPLEMENT");
        double basicSalaryComplement = basicSalaryComplementComponent != null ? basicSalaryComplementComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Complemento Sueldo Basico");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = basicSalaryComplement * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (basicSalaryComplementComponent != null) {
            for (MonthProjection projection : basicSalaryComplementComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Asignación Familiar
    //=CC42*$N$9
    //CC42 = familyAssignment
    //$N$9 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void familyAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO familyAssignmentComponent = componentMap.get("FAMILY_ASSIGNMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-FAMILY_ASSIGNMENT");
        double familyAssignment = familyAssignmentComponent != null ? familyAssignmentComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Asignación Familiar");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = familyAssignment * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (familyAssignmentComponent != null) {
            for (MonthProjection projection : familyAssignmentComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Ley Teletrabajo
    //=CC43*$N$10
    //CC43 = teleworkLaw
    //$N$10 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void teleworkLawTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO teleworkLawComponent = componentMap.get("TELEWORK_LAW");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-TELEWORK_LAW");
        double teleworkLaw = teleworkLawComponent != null ? teleworkLawComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Ley Teletrabajo");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = teleworkLaw * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (teleworkLawComponent != null) {
            for (MonthProjection projection : teleworkLawComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bono Top Performer
    //=CC44*$N$11
    //CC44 = topPerformerBonus
    //$N$11 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void topPerformerBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO topPerformerBonusComponent = componentMap.get("TOP_PERFORMER_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-TOP_PERFORMER_BONUS");
        double topPerformerBonus = topPerformerBonusComponent != null ? topPerformerBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono Top Performer");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = topPerformerBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (topPerformerBonusComponent != null) {
            for (MonthProjection projection : topPerformerBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Jornada Tienda
    //=CC45*$N$12
    //CC45 = storeDay
    //$N$12 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void storeDayTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO storeDayComponent = componentMap.get("STORE_DAY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-STORE_DAY");
        double storeDay = storeDayComponent != null ? storeDayComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Jornada Tienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = storeDay * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (storeDayComponent != null) {
            for (MonthProjection projection : storeDayComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Asignación de Vivienda
    //=CC46*$N$13
    //CC46 = housingAssignment
    //$N$13 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingAssignmentComponent = componentMap.get("HOUSING_ASSIGNMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-HOUSING_ASSIGNMENT");
        double housingAssignment = housingAssignmentComponent != null ? housingAssignmentComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Asignación de Vivienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = housingAssignment * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingAssignmentComponent != null) {
            for (MonthProjection projection : housingAssignmentComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Conceptos Mandato Judicial
    //=CC47*$N$14
    //CC47 = judicialMandateConcepts
    //$N$14 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void judicialMandateConceptsTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range,
                                                       Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO judicialMandateConceptsComponent = componentMap.get("JUDICIAL_MANDATE_CONCEPTS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-JUDICIAL_MANDATE_CONCEPTS");

        double judicialMandateConcepts = judicialMandateConceptsComponent != null ? judicialMandateConceptsComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Conceptos Mandato Judicial");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double initialTemporaryBonus = judicialMandateConcepts * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(initialTemporaryBonus));

        // Generate full range of projections with initial bonus
        List<MonthProjection> fullProjections = generateMonthProjection(period, range, BigDecimal.valueOf(initialTemporaryBonus));

        if (judicialMandateConceptsComponent != null && judicialMandateConceptsComponent.getProjections() != null) {
            // Create a map of existing projections for easy lookup
            Map<String, BigDecimal> sourceProjections = judicialMandateConceptsComponent.getProjections().stream()
                    .collect(Collectors.toMap(MonthProjection::getMonth, MonthProjection::getAmount));

            // Update full projections with calculated bonus values
            for (MonthProjection projection : fullProjections) {
                BigDecimal sourceAmount = sourceProjections.get(projection.getMonth());
                if (sourceAmount != null) {
                    double bonusProjection = sourceAmount.doubleValue() * temporaryBonusConceptValue;
                    projection.setAmount(BigDecimal.valueOf(bonusProjection));
                }
            }
        }

        temporaryBonusComponent.setProjections(fullProjections);
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bonificación Complementaria
    //=CC48*$N$15
    //CC48 = complementaryBonus
    //$N$15 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void complementaryBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO complementaryBonusComponent = componentMap.get("COMPLEMENTARY_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-COMPLEMENTARY_BONUS");
        double complementaryBonus = complementaryBonusComponent != null ? complementaryBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Complementaria");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = complementaryBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (complementaryBonusComponent != null) {
            for (MonthProjection projection : complementaryBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bonificación Días Especiales
    //=CC49*$N$16
    //CC49 = specialDaysBonus
    //$N$16 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void specialDaysBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO specialDaysBonusComponent = componentMap.get("SPECIAL_DAYS_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-SPECIAL_DAYS_BONUS");
        double specialDaysBonus = specialDaysBonusComponent != null ? specialDaysBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Días Especiales");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = specialDaysBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (specialDaysBonusComponent != null) {
            for (MonthProjection projection : specialDaysBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bono disponibilidad
    //=CC50*$N$17
    //CC50 = availabilityBonus
    //$N$17 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void availabilityBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO availabilityBonusComponent = componentMap.get("AVAILABILITY_PLUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-AVAILABILITY_PLUS");
        double availabilityBonus = availabilityBonusComponent != null ? availabilityBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono disponibilidad");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = availabilityBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (availabilityBonusComponent != null) {
            for (MonthProjection projection : availabilityBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bono por trabajo nocturno
    //=CC51*$N$18
    //CC51 = nightWorkBonus
    //$N$18 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void nightWorkBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO nightWorkBonusComponent = componentMap.get("NIGHT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-NIGHT_BONUS");
        double nightWorkBonus = nightWorkBonusComponent != null ? nightWorkBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono por trabajo nocturno");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = nightWorkBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (nightWorkBonusComponent != null) {
            for (MonthProjection projection : nightWorkBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Bonif. Ext. Temp. - Bonificación por Destaque
    //=CC52*$N$19
    //CC52 = detachmentBonus
    //$N$19 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void detachmentBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO detachmentBonusComponent = componentMap.get("DETACHMENT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("TEMPORARY_BONUS-DETACHMENT_BONUS");
        double detachmentBonus = detachmentBonusComponent != null ? detachmentBonusComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación por Destaque");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getBonifExtTemp().doubleValue() : 0;
        double temporaryBonus = detachmentBonus * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (detachmentBonusComponent != null) {
            for (MonthProjection projection : detachmentBonusComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Salario Teórico
    //=CC53*$N$20
    //CC53 = theoreticalSalary
    //$N$20 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void theoreticalSalaryCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoreticalSalaryCTSComponent = componentMap.get("THEORETICAL_SALARY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-THEORETICAL_SALARY");
        double theoreticalSalaryCTS = theoreticalSalaryCTSComponent != null ? theoreticalSalaryCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Salario Teórico");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = theoreticalSalaryCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoreticalSalaryCTSComponent != null) {
            for (MonthProjection projection : theoreticalSalaryCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Provisión de Vacaciones
    //=CC54*$N$21
    //CC54 = vacationProvision
    //$N$21 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void vacationProvisionCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO vacationProvisionComponent = componentMap.get("VACATION_PROVISION");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-VACATION_PROVISION");
        double vacationProvision = vacationProvisionComponent != null ? vacationProvisionComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Provisión de Vacaciones");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = vacationProvision * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (vacationProvisionComponent != null) {
            for (MonthProjection projection : vacationProvisionComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Ley Teletrabajo
    //=CC55*$N$22
    //CC55 = teleworkLawCTS
    //$N$22 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void teleworkLawCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO teleworkLawCTSComponent = componentMap.get("TELEWORK_LAW");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-TELEWORK_LAW");
        double teleworkLawCTS = teleworkLawCTSComponent != null ? teleworkLawCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Ley Teletrabajo");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = teleworkLawCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (teleworkLawCTSComponent != null) {
            for (MonthProjection projection : teleworkLawCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bono Top Performer
    //=CC56*$N$23
    //CC56 = topPerformerBonus
    //$N$23 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void topPerformerBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO topPerformerBonusCTSComponent = componentMap.get("TOP_PERFORMER_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-TOP_PERFORMER_BONUS");
        double topPerformerBonusCTS = topPerformerBonusCTSComponent != null ? topPerformerBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono Top Performer");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = topPerformerBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (topPerformerBonusCTSComponent != null) {
            for (MonthProjection projection : topPerformerBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bonificación Responsable Grupo
    //=CC57*$N$24
    //CC57 = groupResponsibleBonus
    //$N$24 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void groupResponsibleBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO groupResponsibleBonusCTSComponent = componentMap.get("GROUP_RESPONSIBLE_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-GROUP_RESPONSIBLE_BONUS");
        double groupResponsibleBonusCTS = groupResponsibleBonusCTSComponent != null ? groupResponsibleBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Responsable Grupo");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = groupResponsibleBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (groupResponsibleBonusCTSComponent != null) {
            for (MonthProjection projection : groupResponsibleBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Jornada Tienda
    //=CC58*$N$25
    //CC58 = storeDay
    //$N$25 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void storeDayCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO storeDayCTSComponent = componentMap.get("STORE_DAY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-STORE_DAY");
        double storeDayCTS = storeDayCTSComponent != null ? storeDayCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Jornada Tienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = storeDayCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (storeDayCTSComponent != null) {
            for (MonthProjection projection : storeDayCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Asignación de Vivienda
    //=CC59*$N$26
    //CC59 = housingAssignment
    //$N$26 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingAssignmentCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingAssignmentCTSComponent = componentMap.get("HOUSING_ASSIGNMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-HOUSING_ASSIGNMENT");
        double housingAssignmentCTS = housingAssignmentCTSComponent != null ? housingAssignmentCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Asignación de Vivienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = housingAssignmentCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingAssignmentCTSComponent != null) {
            for (MonthProjection projection : housingAssignmentCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Conceptos Mandato Judicial
    //=CC60*$N$27
    //CC60 = judicialMandateConcepts
    //$N$27 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void judicialMandateConceptsCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO judicialMandateConceptsCTSComponent = componentMap.get("JUDICIAL_MANDATE_CONCEPTS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-JUDICIAL_MANDATE_CONCEPTS");
        double judicialMandateConceptsCTS = judicialMandateConceptsCTSComponent != null ? judicialMandateConceptsCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Conceptos Mandato Judicial");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = judicialMandateConceptsCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (judicialMandateConceptsCTSComponent != null) {
            for (MonthProjection projection : judicialMandateConceptsCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bonificación Complementaria
    //=CC61*$N$28
    //CC61 = complementaryBonus
    //$N$28 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void complementaryBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO complementaryBonusCTSComponent = componentMap.get("COMPLEMENTARY_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-COMPLEMENTARY_BONUS");
        double complementaryBonusCTS = complementaryBonusCTSComponent != null ? complementaryBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Complementaria");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = complementaryBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (complementaryBonusCTSComponent != null) {
            for (MonthProjection projection : complementaryBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bonificación Días Especiales
    //=CC62*$N$29
    //CC62 = specialDaysBonus
    //$N$29 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void specialDaysBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO specialDaysBonusCTSComponent = componentMap.get("SPECIAL_DAYS_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-SPECIAL_DAYS_BONUS");
        double specialDaysBonusCTS = specialDaysBonusCTSComponent != null ? specialDaysBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Días Especiales");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = specialDaysBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (specialDaysBonusCTSComponent != null) {
            for (MonthProjection projection : specialDaysBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bono disponibilidad
    //=CC63*$N$30
    //CC63 = availabilityBonus
    //$N$30 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void availabilityBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO availabilityBonusCTSComponent = componentMap.get("AVAILABILITY_PLUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-AVAILABILITY_PLUS");
        double availabilityBonusCTS = availabilityBonusCTSComponent != null ? availabilityBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono disponibilidad");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = availabilityBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (availabilityBonusCTSComponent != null) {
            for (MonthProjection projection : availabilityBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bono por trabajo nocturno
    //=CC64*$N$31
    //CC64 = nightWorkBonus
    //$N$31 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void nightWorkBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO nightWorkBonusCTSComponent = componentMap.get("NIGHT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-NIGHT_BONUS");
        double nightWorkBonusCTS = nightWorkBonusCTSComponent != null ? nightWorkBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono por trabajo nocturno");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = nightWorkBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (nightWorkBonusCTSComponent != null) {
            for (MonthProjection projection : nightWorkBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - Bonificación por Destaque
    //=CC65*$N$32
    //CC65 = detachmentBonus
    //$N$32 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void detachmentBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO detachmentBonusCTSComponent = componentMap.get("DETACHMENT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-DETACHMENT_BONUS");
        double detachmentBonusCTS = detachmentBonusCTSComponent != null ? detachmentBonusCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación por Destaque");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = detachmentBonusCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (detachmentBonusCTSComponent != null) {
            for (MonthProjection projection : detachmentBonusCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - TFSP
    //==+CC288*$L$23
    //CC288 = tfsp
    //$L$23 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void tfspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO tfspCTSComponent = componentMap.get("TFSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-TFSP");
        double tfspCTS = tfspCTSComponent != null ? tfspCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("TFSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = tfspCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (tfspCTSComponent != null) {
            for (MonthProjection projection : tfspCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - PSP
    //==+CC289*$L$24
    //CC289 = psp
    //$L$24 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void pspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO pspCTSComponent = componentMap.get("PSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-PSP");
        double pspCTS = pspCTSComponent != null ? pspCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("PSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = pspCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (pspCTSComponent != null) {
            for (MonthProjection projection : pspCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - RSP
    //==+CC290*$L$25
    //CC290 = rsp
    //$L$25 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void rspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO rspCTSComponent = componentMap.get("RSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-RSP");
        double rspCTS = rspCTSComponent != null ? rspCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("RSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = rspCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (rspCTSComponent != null) {
            for (MonthProjection projection : rspCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //CTS - COINV
    //==+CC291*$L$26
    //CC291 = coinv
    //$L$26 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void coinvCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO coinvCTSComponent = componentMap.get("COINV");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("CTS-COINV");
        double coinvCTS = coinvCTSComponent != null ? coinvCTSComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("COINV");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getCts().doubleValue() : 0;
        double temporaryBonus = coinvCTS * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (coinvCTSComponent != null) {
            for (MonthProjection projection : coinvCTSComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Salario Teórico
    //=CC53*$N$20
    //CC53 = theoreticalSalary
    //$N$20 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void theoreticalSalaryEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoreticalSalaryEssaludComponent = componentMap.get("THEORETICAL_SALARY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-THEORETICAL_SALARY");
        double theoreticalSalaryEssalud = theoreticalSalaryEssaludComponent != null ? theoreticalSalaryEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Salario Teórico");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = theoreticalSalaryEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoreticalSalaryEssaludComponent != null) {
            for (MonthProjection projection : theoreticalSalaryEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Provisión de Vacaciones
    //=CC54*$N$21
    //CC54 = vacationProvision
    //$N$21 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void vacationProvisionEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO vacationProvisionEssaludComponent = componentMap.get("VACATION_PROVISION");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-VACATION_PROVISION");
        double vacationProvisionEssalud = vacationProvisionEssaludComponent != null ? vacationProvisionEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Provisión de Vacaciones");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = vacationProvisionEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (vacationProvisionEssaludComponent != null) {
            for (MonthProjection projection : vacationProvisionEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Compensación por Vivienda
    //=CC55*$N$22
    //CC55 = housingCompensation
    //$N$22 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingCompensationEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingCompensationEssaludComponent = componentMap.get("HOUSING");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-HOUSING");
        double housingCompensationEssalud = housingCompensationEssaludComponent != null ? housingCompensationEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Compensación por Vivienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = housingCompensationEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingCompensationEssaludComponent != null) {
            for (MonthProjection projection : housingCompensationEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Incremento AFP 10,23%
    //=CC56*$N$23
    //CC56 = afpIncrease
    //$N$23 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void afpIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO afpIncreaseEssaludComponent = componentMap.get("INCREASE_AFP_1023");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-AFP_INCREASE");
        double afpIncreaseEssalud = afpIncreaseEssaludComponent != null ? afpIncreaseEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Incremento AFP 10,23%");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = afpIncreaseEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (afpIncreaseEssaludComponent != null) {
            for (MonthProjection projection : afpIncreaseEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Incremento 3,3% + Incremento SNP 3,3%
    //=CC66*$N$33
    //CC66 = INCREASE_SNP_AND_INCREASE
    //$N$33 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void increaseSNPAndIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO increaseSNPAndIncreaseEssaludComponent = componentMap.get("INCREASE_SNP_AND_INCREASE");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-INCREASE_SNP_AND_INCREASE");
        double increaseSNPAndIncreaseEssalud = increaseSNPAndIncreaseEssaludComponent != null ? increaseSNPAndIncreaseEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Incremento 3,3% + Incremento SNP 3,3%");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = increaseSNPAndIncreaseEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (increaseSNPAndIncreaseEssaludComponent != null) {
            for (MonthProjection projection : increaseSNPAndIncreaseEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Bono SRD (100%)
    //=CC67*$N$34
    //CC67 = srdBonus
    //$N$34 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void srdBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO srdBonusEssaludComponent = componentMap.get("SRD_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-SRD_BONUS");
        double srdBonusEssalud = srdBonusEssaludComponent != null ? srdBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono SRD (100%)");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = srdBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (srdBonusEssaludComponent != null) {
            for (MonthProjection projection : srdBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Complemento Sueldo Basico
    //=CC68*$N$35
    //CC68 = basicSalaryComplement
    //$N$35 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void basicSalaryComplementEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO basicSalaryComplementEssaludComponent = componentMap.get("BASIC_SALARY_COMPLEMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-BASIC_SALARY_COMPLEMENT");
        double basicSalaryComplementEssalud = basicSalaryComplementEssaludComponent != null ? basicSalaryComplementEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Complemento Sueldo Basico");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = basicSalaryComplementEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (basicSalaryComplementEssaludComponent != null) {
            for (MonthProjection projection : basicSalaryComplementEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Asignación Familiar
    //=CC69*$N$36
    //CC69 = familyAllowance
    //$N$36 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void familyAllowanceEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO familyAllowanceEssaludComponent = componentMap.get("FAMILY_ASSIGNMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-FAMILY_ASSIGNMENT");
        double familyAllowanceEssalud = familyAllowanceEssaludComponent != null ? familyAllowanceEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Asignación Familiar");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = familyAllowanceEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (familyAllowanceEssaludComponent != null) {
            for (MonthProjection projection : familyAllowanceEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Ley Teletrabajo
    //=CC70*$N$37
    //CC70 = teleworkLaw
    //$N$37 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void teleworkLawEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO teleworkLawEssaludComponent = componentMap.get("TELEWORK_LAW");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-TELEWORK_LAW");
        double teleworkLawEssalud = teleworkLawEssaludComponent != null ? teleworkLawEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Ley Teletrabajo");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = teleworkLawEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (teleworkLawEssaludComponent != null) {
            for (MonthProjection projection : teleworkLawEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Bono Top Performer
    //=CC71*$N$38
    //CC71 = topPerformerBonus
    //$N$38 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void topPerformerBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO topPerformerBonusEssaludComponent = componentMap.get("TOP_PERFORMER_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-TOP_PERFORMER_BONUS");
        double topPerformerBonusEssalud = topPerformerBonusEssaludComponent != null ? topPerformerBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono Top Performer");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = topPerformerBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (topPerformerBonusEssaludComponent != null) {
            for (MonthProjection projection : topPerformerBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Bonificación Responsable Grupo
    //=CC72*$N$39
    //CC72 = groupResponsibleBonus
    //$N$39 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void groupResponsibleBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO groupResponsibleBonusEssaludComponent = componentMap.get("GROUP_RESPONSIBLE_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-GROUP_RESPONSIBLE_BONUS");
        double groupResponsibleBonusEssalud = groupResponsibleBonusEssaludComponent != null ? groupResponsibleBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Responsable Grupo");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = groupResponsibleBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (groupResponsibleBonusEssaludComponent != null) {
            for (MonthProjection projection : groupResponsibleBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Jornada Tienda
    //=CC73*$N$40
    //CC73 = storeDay
    //$N$40 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void storeDayEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO storeDayEssaludComponent = componentMap.get("STORE_DAY");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-STORE_DAY");
        double storeDayEssalud = storeDayEssaludComponent != null ? storeDayEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Jornada Tienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = storeDayEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (storeDayEssaludComponent != null) {
            for (MonthProjection projection : storeDayEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Asignación de Vivienda
    //=CC74*$N$41
    //CC74 = housingAssignment
    //$N$41 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void housingAssignmentEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO housingAssignmentEssaludComponent = componentMap.get("HOUSING_ASSIGNMENT");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-HOUSING_ASSIGNMENT");
        double housingAssignmentEssalud = housingAssignmentEssaludComponent != null ? housingAssignmentEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Asignación de Vivienda");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = housingAssignmentEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (housingAssignmentEssaludComponent != null) {
            for (MonthProjection projection : housingAssignmentEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Conceptos Mandato Judicial
    //=CC75*$N$42
    //CC75 = judicialMandateConcepts
    //$N$42 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void judicialMandateConceptsEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO judicialMandateConceptsEssaludComponent = componentMap.get("JUDICIAL_MANDATE_CONCEPTS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-JUDICIAL_MANDATE_CONCEPTS");
        double judicialMandateConceptsEssalud = judicialMandateConceptsEssaludComponent != null ? judicialMandateConceptsEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Conceptos Mandato Judicial");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = judicialMandateConceptsEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (judicialMandateConceptsEssaludComponent != null) {
            for (MonthProjection projection : judicialMandateConceptsEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Bonificación Complementaria
    //=+CC217*$M$18
    //CC217 = complementaryBonus
    //$M$18 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void complementaryBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO complementaryBonusEssaludComponent = componentMap.get("COMPLEMENTARY_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-COMPLEMENTARY_BONUS");
        double complementaryBonusEssalud = complementaryBonusEssaludComponent != null ? complementaryBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Complementaria");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = complementaryBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (complementaryBonusEssaludComponent != null) {
            for (MonthProjection projection : complementaryBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }

    //Essalud - Bonificación Días Especiales
    //=+CC238*$M$19
    //CC238 = specialDaysBonus
    //$M$19 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void specialDaysBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO specialDaysBonusEssaludComponent = componentMap.get("SPECIAL_DAYS_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-SPECIAL_DAYS_BONUS");
        double specialDaysBonusEssalud = specialDaysBonusEssaludComponent != null ? specialDaysBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación Días Especiales");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = specialDaysBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (specialDaysBonusEssaludComponent != null) {
            for (MonthProjection projection : specialDaysBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - Bonificación por Destaque
    //=+CC239*$M$20
    //CC239 = detachmentBonus
    //$M$20 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void detachmentBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO detachmentBonusEssaludComponent = componentMap.get("DETACHMENT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-DETACHMENT_BONUS");
        double detachmentBonusEssalud = detachmentBonusEssaludComponent != null ? detachmentBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bonificación por Destaque");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = detachmentBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (detachmentBonusEssaludComponent != null) {
            for (MonthProjection projection : detachmentBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - Bono disponibilidad
    //=+CC240*$M$21
    //CC240 = availabilityBonus
    //$M$21 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void availabilityBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO availabilityBonusEssaludComponent = componentMap.get("AVAILABILITY_PLUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-AVAILABILITY_PLUS");
        double availabilityBonusEssalud = availabilityBonusEssaludComponent != null ? availabilityBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono disponibilidad");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = availabilityBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (availabilityBonusEssaludComponent != null) {
            for (MonthProjection projection : availabilityBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - Bono por trabajo nocturno
    //=+CC241*$M$22
    //CC241 = nightWorkBonus
    //$M$22 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void nightWorkBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO nightWorkBonusEssaludComponent = componentMap.get("NIGHT_BONUS");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-NIGHT_BONUS");
        double nightWorkBonusEssalud = nightWorkBonusEssaludComponent != null ? nightWorkBonusEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("Bono por trabajo nocturno");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = nightWorkBonusEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (nightWorkBonusEssaludComponent != null) {
            for (MonthProjection projection : nightWorkBonusEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - COINV
    //=+CC242*$M$23
    //CC242 = COINV
    //$M$23 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void COINVEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO COINVEssaludComponent = componentMap.get("COINV");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-COINV");
        double COINVEssalud = COINVEssaludComponent != null ? COINVEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("COINV");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = COINVEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (COINVEssaludComponent != null) {
            for (MonthProjection projection : COINVEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - PSP
    //=+CC243*$M$24
    //CC243 = PSP
    //$M$24 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void PSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO PSPEssaludComponent = componentMap.get("PSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-PSP");
        double PSPEssalud = PSPEssaludComponent != null ? PSPEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("PSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = PSPEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (PSPEssaludComponent != null) {
            for (MonthProjection projection : PSPEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - RSP
    //=+CC244*$M$25
    //CC244 = RSP
    //$M$25 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void RSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO RSPEssaludComponent = componentMap.get("RSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-RSP");
        double RSPEssalud = RSPEssaludComponent != null ? RSPEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("RSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = RSPEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (RSPEssaludComponent != null) {
            for (MonthProjection projection : RSPEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Essalud - TFSP
    //=+CC245*$M$26
    //CC245 = TFSP
    //$M$26 = Map<String, ConceptoPresupuestal> conceptoPresupuestalMap
    public void TFSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO TFSPEssaludComponent = componentMap.get("TFSP");
        PaymentComponentDTO temporaryBonusComponent = new PaymentComponentDTO();
        temporaryBonusComponent.setPaymentComponent("ESSALUD-TFSP");
        double TFSPEssalud = TFSPEssaludComponent != null ? TFSPEssaludComponent.getAmount().doubleValue() : 0;
        ConceptoPresupuestal temporaryBonusConcept = conceptoPresupuestalMap.get("TFSP");
        double temporaryBonusConceptValue = temporaryBonusConcept != null ? temporaryBonusConcept.getEssalud().doubleValue() : 0;
        double temporaryBonus = TFSPEssalud * temporaryBonusConceptValue;
        temporaryBonusComponent.setAmount(BigDecimal.valueOf(temporaryBonus));
        temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (TFSPEssaludComponent != null) {
            for (MonthProjection projection : TFSPEssaludComponent.getProjections()) {
                String month = projection.getMonth();
                double temporaryBonusProjection = projection.getAmount().doubleValue() * temporaryBonusConceptValue;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(temporaryBonusProjection));
                projections.add(monthProjection);
            }
            temporaryBonusComponent.setProjections(projections);
        } else {
            temporaryBonusComponent.setAmount(BigDecimal.valueOf(0));
            temporaryBonusComponent.setProjections(generateMonthProjection(period, range, temporaryBonusComponent.getAmount()));
        }
        components.add(temporaryBonusComponent);
    }
    //Plan Prev Dir Aport Vol Emp.
    //=SI(CC35<>0;CB1001*(CC35/CB35);0)
    //CB1001 = PLAN_PREV_DIR_APORT_VOL_EMP_BASE
    //CC35 = salaryCurrentMonth
    //CB35 = salaryPreviousMonth
    public void calculatePlanPrevDirAportVolEmp(List<PaymentComponentDTO> components, String period, Integer range, Map<Tuple<Integer, Integer>, Double> ageSVMap) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);

        PaymentComponentDTO planPrevDirAportVolEmpBase = componentMap.get("PLAN_PREV_DIR_APORT_VOL_EMP_BASE");
        //log.info("planPrevDirAportVolEmpBase: " + planPrevDirAportVolEmpBase);
        /*if (planPrevDirAportVolEmpBase.getAmount().doubleValue() > 0){
            log.info("planPrevDirAportVolEmpBase: " + planPrevDirAportVolEmpBase.getAmount().doubleValue());
        }*/
        PaymentComponentDTO salaryComponent = componentMap.get("THEORETICAL-SALARY");
        PaymentComponentDTO planPrevDirAportVolEmp = new PaymentComponentDTO();
        planPrevDirAportVolEmp.setPaymentComponent("PLAN_PREV_DIR_APORT_VOL_EMP");

        if (planPrevDirAportVolEmpBase != null && salaryComponent != null) {
            //planPrevDirAportVolEmp.setAmount(planPrevDirAportVolEmpBase.getAmount());
            List<MonthProjection> baseProjections = planPrevDirAportVolEmpBase.getProjections();
            List<MonthProjection> salaryProjections = salaryComponent.getProjections();
            List<MonthProjection> resultProjections = new ArrayList<>();

            for (int i = 0; i < baseProjections.size() - 1; i++) {
                double baseAmount = baseProjections.get(i).getAmount().doubleValue();
                double currentSalary = salaryProjections.get(i).getAmount().doubleValue();
                double previousSalary = salaryProjections.get(i + 1).getAmount().doubleValue();
               /* log.info("baseAmount: " + baseAmount);
                log.info("currentSalary: " + currentSalary);
                log.info("previousSalary: " + previousSalary);*/
                double result = (baseAmount != 0) ? baseAmount * (1 + ( previousSalary - currentSalary )) : 0;

                MonthProjection resultProjection = new MonthProjection();
                resultProjection.setMonth(baseProjections.get(i).getMonth());
                resultProjection.setAmount(BigDecimal.valueOf(result));
                resultProjections.add(resultProjection);
            }

            planPrevDirAportVolEmp.setProjections(resultProjections);
            planPrevDirAportVolEmp.setAmount(resultProjections.get(0).getAmount());
        } else {
            planPrevDirAportVolEmp.setProjections(generateMonthProjection(period, range, BigDecimal.ZERO));
            planPrevDirAportVolEmp.setAmount(BigDecimal.ZERO);
        }

        components.add(planPrevDirAportVolEmp);
    }
    //[Auxiliar en excel para calcular edad ]
    //=calculateSeniorityYears
    public long calculateAge(LocalDate birthDate, String period) {
        if (birthDate == null) {
            return 0;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);

        return ChronoUnit.YEARS.between(birthDate, yearMonth.atEndOfMonth());
    }
    //Seguro de Vida aux para cálculo del factor de seguro segun la tabla de factores por rango de edad
    //=SI(CC98<=$G$3;$H$3;SI(CC98<=$G$4;$H$4;SI(CC98<=$G$5;$H$5;SI(CC98<=$G$6;$H$6;SI(CC98<=$G$7;$H$7;SI(CC98<=$G$8;$H$8;SI(CC98<=$G$9;$H$9;SI(CC98<=$G$10;$H$10;SI(CC98<=$G$11;$H$11)))))))))
    public BigDecimal getAgeFactorValue(Integer age, Map<Tuple<Integer, Integer>, Double> ageSVMap) {
        BigDecimal ageFactor = BigDecimal.ZERO;
        for (Map.Entry<Tuple<Integer, Integer>, Double> entry : ageSVMap.entrySet()) {
            Tuple<Integer, Integer> ageRange = entry.getKey();
            if (age >= ageRange.getFirst() && age <= ageRange.getSecond()) {
                ageFactor = BigDecimal.valueOf(entry.getValue());
                break;
            }
        }
        return ageFactor;
    }
    //Seguro de Vida
    //=CC35*(CC105+CC$8)
    //CC35 = salaryCurrentMonth
    //CC105 = ageFactor
    //CC$8 = groupSVList parameter
    public void calculateLifeInsurance(List<PaymentComponentDTO> components, String period, Integer range, Map<Tuple<Integer, Integer>, Double> ageSVMap, List<ParametersDTO> groupSVList, LocalDate birthDate) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO theoricSalaryComponent = componentMap.get("THEORETICAL-SALARY");
        Map<String, ParametersDTO> groupSVMap = createCacheMap(groupSVList);

        if (theoricSalaryComponent != null) {
            long age = calculateAge(birthDate, period);
            //log.info("age: " + age);
            BigDecimal ageFactor = getAgeFactorValue((int) age, ageSVMap);
            //log.info("ageFactor: " + ageFactor);
            double salary = theoricSalaryComponent.getAmount().doubleValue();
            double groupSV = getCachedValue(groupSVMap, period);
            double lifeInsurance = salary * (ageFactor.doubleValue() + groupSV);

            PaymentComponentDTO lifeInsuranceComponent = new PaymentComponentDTO();
            lifeInsuranceComponent.setPaymentComponent("MEDICAL_INSURANCE");
            lifeInsuranceComponent.setAmount(BigDecimal.valueOf(lifeInsurance));
            lifeInsuranceComponent.setProjections(generateMonthProjection(period, range, lifeInsuranceComponent.getAmount()));

            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                String month = projection.getMonth();
                long ageProjection = calculateAge(birthDate, month);
                double groupSVP = getCachedValue(groupSVMap, month);
                BigDecimal ageFactorProjection = getAgeFactorValue((int) ageProjection, ageSVMap);
                double lifeInsuranceProjection = projection.getAmount().doubleValue() * (ageFactorProjection.doubleValue() + groupSVP);

                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(lifeInsuranceProjection));
                projections.add(monthProjection);

            }
            lifeInsuranceComponent.setProjections(projections);
            components.add(lifeInsuranceComponent);
        }else {
            PaymentComponentDTO lifeInsuranceComponent = new PaymentComponentDTO();
            lifeInsuranceComponent.setPaymentComponent("MEDICAL_INSURANCE");
            lifeInsuranceComponent.setAmount(BigDecimal.ZERO);
            lifeInsuranceComponent.setProjections(generateMonthProjection(period, range, lifeInsuranceComponent.getAmount()));
            components.add(lifeInsuranceComponent);
        }
    }
}