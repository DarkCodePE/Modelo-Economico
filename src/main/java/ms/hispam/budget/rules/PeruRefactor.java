package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.entity.mysql.SeniorityAndQuinquennium;
import ms.hispam.budget.util.Shared;
import org.springframework.stereotype.Component;
import org.apache.commons.text.similarity.LevenshteinDistance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "Peru")
public class PeruRefactor {
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

    // Método para calcular el salario teórico
    /*public void calculateTheoreticalSalary(List<PaymentComponentDTO> components, List<ParametersDTO> salaryIncreaseList, String poName, String period, Integer range, List<ParametersDTO> executiveSalaryIncreaseList, List<ParametersDTO> directorSalaryIncreaseList, Map<String, EmployeeClassification> classificationMap) {
        String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        String typeEmp = "EMP";
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            typeEmp = employeeClassification.getTypeEmp();
        }else {
            log.error("Employee classification not found for classEmployee: {}", poName);
        }
        Map<String, ParametersDTO> salaryIncreaseyMap = new HashMap<>();
        Map<String, Double> salaryIncreaseCache = new HashMap<>();
        createCache(salaryIncreaseList, salaryIncreaseyMap, salaryIncreaseCache, (parameter, mapParameter) -> {
        });

        Map<String, ParametersDTO> executiveSalaryIncreaseListMap = new HashMap<>();
        Map<String, Double> executiveSalaryIncreaseCache = new HashMap<>();
        createCache(executiveSalaryIncreaseList, executiveSalaryIncreaseListMap, executiveSalaryIncreaseCache, (parameter, mapParameter) -> {
        });

        Map<String, ParametersDTO> directorSalaryIncreaseListMap = new HashMap<>();
        Map<String, Double> directorSalaryIncreaseCache = new HashMap<>();
        createCache(directorSalaryIncreaseList, directorSalaryIncreaseListMap, directorSalaryIncreaseCache, (parameter, mapParameter) -> {
        });

        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO componentPC960400 = componentMap.get("PC960400");
        PaymentComponentDTO componentPC960401 = componentMap.get("PC960401");
        //MAX($AA5/14;$Z5)
        double salaryBase = Math.max(componentPC960400.getAmount().doubleValue() / 14, componentPC960401.getAmount().doubleValue());

        // Siguiente período
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double salaryIncrease = salaryIncreaseCache.get(nextPeriod) == null ? 0 : salaryIncreaseCache.get(nextPeriod);
        double executiveSalaryIncrease = executiveSalaryIncreaseCache.get(nextPeriod) == null ? 0 : executiveSalaryIncreaseCache.get(nextPeriod);
        double directorSalaryIncrease = directorSalaryIncreaseCache.get(nextPeriod) == null ? 0 : directorSalaryIncreaseCache.get(nextPeriod);
        //SI($W5<>"FLJ";
        //CB35*(1+SI($W5="emp";CC$3;
        //SI(O($W5="DIR";$W5="DPZ");CC$5;CC$4)))
        //$W5 = typeEmp
        //$BT5 = componentPromotionDate
        //CB35 = salaryBase
        //CC$3 = Rev Salarial EMP %
        //CC$4 = Rev Salarial EJC/GER %
        //CC$5 = Rev Salarial DIR/DPZ %
        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("THEORETICAL-SALARY");
        double adjustmentBase;
        if (typeEmp.equals("EMP")) {
            adjustmentBase = salaryIncrease;
        } else if (typeEmp.equals("DIR") || typeEmp.equals("DPZ")) {
            adjustmentBase = directorSalaryIncrease;
        } else {
            adjustmentBase = executiveSalaryIncrease;
        }
        salaryComponent.setAmount(BigDecimal.valueOf(salaryBase * (1 + adjustmentBase)));
        salaryComponent.setProjections(Shared.generateMonthProjection(period, range, salaryComponent.getAmount()));

        double ultimaIncremento = 0;
        double ultimaExecutivoIncremento = 0;
        double ultimaDirectorIncremento = 0;
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            String month = projection.getMonth();
            ParametersDTO incrementoMensual = salaryIncreaseyMap.get(month);
            double incremento;
            if (incrementoMensual != null) {
                incremento = incrementoMensual.getValue();
                ultimaIncremento = incremento;
            } else {
                incremento = ultimaIncremento;
            }
            ParametersDTO ExecutivoIncrementoMensual = executiveSalaryIncreaseListMap.get(month);
            double incrementoExecutivo;
            if (ExecutivoIncrementoMensual != null) {
                incrementoExecutivo = ExecutivoIncrementoMensual.getValue();
                ultimaExecutivoIncremento = incrementoExecutivo;
            } else {
                incrementoExecutivo = ultimaExecutivoIncremento;
            }
            ParametersDTO DirectorIncrementoMensual = directorSalaryIncreaseListMap.get(month);
            double incrementoDirector;
            if (DirectorIncrementoMensual != null) {
                incrementoDirector = DirectorIncrementoMensual.getValue();
                ultimaDirectorIncremento = incrementoDirector;
            } else {
                incrementoDirector = ultimaDirectorIncremento;
            }
            double adjustment;
            if (typeEmp.equals("EMP")) {
                adjustment = incremento;
            } else if (typeEmp.equals("DIR") || typeEmp.equals("DPZ")) {
                adjustment = incrementoDirector;
            } else {
                adjustment = incrementoExecutivo;
            }
            double salary = salaryBase * (1 + adjustment);
            //(1+SI($BT5<>"";SI($BT5<=CC$34;SI(MES($BT5)=MES(CC$34);$BU5;0);0);0))
            //CC$34 = month
            //$BU5 = % Promoción
            //$BT5 = Mes Promoción
            PaymentComponentDTO promoComponent = componentMap.get("promo");
            double promo;
            if (promoComponent != null){
                DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                        .appendPattern(TYPEMONTH)
                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                        .toFormatter();
                LocalDate promoDate;
                try {
                    promoDate = LocalDate.parse(promoComponent.getAmountString(), dateFormat);
                } catch (DateTimeParseException e) {
                    int excelDate = Integer.parseInt(promoComponent.getAmountString());
                    promoDate = LocalDate.of(1900, 1, 1).plusDays(excelDate - 2);
                }
                promoDate = promoDate.withDayOfMonth(1);
                LocalDate date = LocalDate.parse(month, dateFormat);
                if (!promoComponent.getAmountString().isEmpty() && !promoDate.isAfter(date) && promoDate.getMonthValue() == date.getMonthValue()) {
                    promo = salary * promoComponent.getAmount().doubleValue();
                }else {
                    promo = 0;
                }
            }else {
                promo = 0;
            }
            double totalSalary = salary * (1 + promo);
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(totalSalary));
            projections.add(monthProjection);
        }
        salaryComponent.setProjections(projections);
        components.add(salaryComponent);
    }*/
    public void calculateTheoreticalSalary(List<PaymentComponentDTO> components, List<ParametersDTO> salaryIncreaseList, String poName, String period, Integer range, List<ParametersDTO> executiveSalaryIncreaseList, List<ParametersDTO> directorSalaryIncreaseList, Map<String, EmployeeClassification> classificationMap) {
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
            //log.info("No se encontró una coincidencia exacta para '{}'. Usando '{}' en su lugar.", poName, mostSimilarPosition);
        }
        String typeEmp = "EMP";
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            typeEmp = employeeClassification.getTypeEmp();
        } else {
            log.error("Employee classification not found for classEmployee: {}", poName);
        }

        Map<String, ParametersDTO> salaryIncreaseyMap = createCacheMap(salaryIncreaseList);
        Map<String, ParametersDTO> executiveSalaryIncreaseListMap = createCacheMap(executiveSalaryIncreaseList);
        Map<String, ParametersDTO> directorSalaryIncreaseListMap = createCacheMap(directorSalaryIncreaseList);

        Map<String, PaymentComponentDTO> componentMap = createComponentMap(components);
        PaymentComponentDTO componentPC960400 = componentMap.get("PC960400");
        PaymentComponentDTO componentPC960401 = componentMap.get("PC960401");

        double salaryBase = Math.max(componentPC960400.getAmount().doubleValue() / 14, componentPC960401.getAmount().doubleValue());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);

        double salaryIncrease = getCachedValue(salaryIncreaseyMap, nextPeriod);
        double executiveSalaryIncrease = getCachedValue(executiveSalaryIncreaseListMap, nextPeriod);
        double directorSalaryIncrease = getCachedValue(directorSalaryIncreaseListMap, nextPeriod);

        PaymentComponentDTO salaryComponent = new PaymentComponentDTO();
        salaryComponent.setPaymentComponent("THEORETICAL-SALARY");

        double adjustmentBase = switch (typeEmp) {
            case "EMP" -> salaryIncrease;
            case "DIR", "DPZ" -> directorSalaryIncrease;
            default -> executiveSalaryIncrease;
        };

        salaryComponent.setAmount(BigDecimal.valueOf(salaryBase * (1 + adjustmentBase)));
        salaryComponent.setProjections(Shared.generateMonthProjection(period, range, salaryComponent.getAmount()));

        List<MonthProjection> projections = calculateProjections(salaryComponent, salaryBase, salaryIncreaseyMap, executiveSalaryIncreaseListMap, directorSalaryIncreaseListMap, typeEmp, componentMap);

        salaryComponent.setProjections(projections);
        components.add(salaryComponent);
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

    private List<MonthProjection> calculateProjections(PaymentComponentDTO salaryComponent, double salaryBase, Map<String, ParametersDTO> salaryIncreaseyMap, Map<String, ParametersDTO> executiveSalaryIncreaseListMap, Map<String, ParametersDTO> directorSalaryIncreaseListMap, String typeEmp, Map<String, PaymentComponentDTO> componentMap) {
        List<MonthProjection> projections = new ArrayList<>();
        double ultimaIncremento = 0;
        double ultimaExecutivoIncremento = 0;
        double ultimaDirectorIncremento = 0;

        for (MonthProjection projection : salaryComponent.getProjections()) {
            String month = projection.getMonth();
            double incremento = getCachedOrPreviousValue(salaryIncreaseyMap, month, ultimaIncremento);
            double incrementoExecutivo = getCachedOrPreviousValue(executiveSalaryIncreaseListMap, month, ultimaExecutivoIncremento);
            double incrementoDirector = getCachedOrPreviousValue(directorSalaryIncreaseListMap, month, ultimaDirectorIncremento);

            double adjustment = switch (typeEmp) {
                case "EMP" -> incremento;
                case "DIR", "DPZ" -> incrementoDirector;
                default -> incrementoExecutivo;
            };

            double salary = salaryBase * (1 + adjustment);
            double promo = calculatePromoAdjustment(salary, month, componentMap);

            double totalSalary = salary * (1 + promo);
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(totalSalary));
            projections.add(monthProjection);
        }

        return projections;
    }

    private double getCachedOrPreviousValue(Map<String, ParametersDTO> cacheMap, String period, double previousValue) {
        ParametersDTO parameter = cacheMap.get(period);
        if (parameter != null) {
            return parameter.getValue();
        } else {
            return previousValue;
        }
    }

    private double calculatePromoAdjustment(double salary, String month, Map<String, PaymentComponentDTO> componentMap) {
        PaymentComponentDTO promoComponent = componentMap.get("promo");
        if (promoComponent != null) {
            DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                    .appendPattern(TYPEMONTH)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter();
            LocalDate promoDate;
            try {
                promoDate = LocalDate.parse(promoComponent.getAmountString(), dateFormat);
            } catch (DateTimeParseException e) {
                int excelDate = Integer.parseInt(promoComponent.getAmountString());
                promoDate = LocalDate.of(1900, 1, 1).plusDays(excelDate - 2);
            }
            promoDate = promoDate.withDayOfMonth(1);
            LocalDate date = LocalDate.parse(month, dateFormat);
            if (!promoComponent.getAmountString().isEmpty() && !promoDate.isAfter(date) && promoDate.getMonthValue() == date.getMonthValue()) {
                return salary * promoComponent.getAmount().doubleValue();
            }
        }
        return 0;
    }

    public String findMostSimilarPosition(String targetPosition, Set<String> knownPositions) {
        LevenshteinDistance levenshtein = new LevenshteinDistance();
        String closestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String position : knownPositions) {
            int distance = levenshtein.apply(targetPosition, position);
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = position;
            }
        }
        return closestMatch;
    }

    /*public void fiesta(List<PaymentComponentDTO> component, String period, Integer range) {
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
    }*/
    //Compensación por Mudanza
    public void relocation(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO relocationBaseComponent = componentMap.get("RELOCATION_BASE");
        if (relocationBaseComponent != null) {
            double relocationBase = relocationBaseComponent.getAmount().doubleValue();
            PaymentComponentDTO relocationComponent = new PaymentComponentDTO();
            relocationComponent.setPaymentComponent("RELOCATION");
            relocationComponent.setAmount(BigDecimal.valueOf(relocationBase));
            relocationComponent.setProjections(Shared.generateMonthProjection(period, range, relocationComponent.getAmount()));
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
                relocationComponent.setProjections(Shared.generateMonthProjection(period, range, relocationComponent.getAmount()));
            }
            component.add(relocationComponent);
        } else {
            PaymentComponentDTO relocationComponent = new PaymentComponentDTO();
            relocationComponent.setPaymentComponent("RELOCATION");
            relocationComponent.setAmount(BigDecimal.valueOf(0));
            relocationComponent.setProjections(Shared.generateMonthProjection(period, range, relocationComponent.getAmount()));
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
            housingComponent.setProjections(Shared.generateMonthProjection(period, range, housingComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (housingBaseComponent.getProjections() != null) {
                for (MonthProjection projection : housingBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double housingPerMonth = housingBaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(housingPerMonth));
                    projections.add(monthProjection);
                }
                housingComponent.setProjections(projections);
            } else {
                housingComponent.setAmount(BigDecimal.valueOf(0));
                housingComponent.setProjections(Shared.generateMonthProjection(period, range, housingComponent.getAmount()));
            }
            component.add(housingComponent);
        } else {
            PaymentComponentDTO housingComponent = new PaymentComponentDTO();
            housingComponent.setPaymentComponent("HOUSING");
            housingComponent.setAmount(BigDecimal.valueOf(0));
            housingComponent.setProjections(Shared.generateMonthProjection(period, range, housingComponent.getAmount()));
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
            increaseSNPComponent.setProjections(Shared.generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
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
                increaseSNPComponent.setProjections(Shared.generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
            }
            component.add(increaseSNPComponent);
        } else {
            PaymentComponentDTO increaseSNPComponent = new PaymentComponentDTO();
            increaseSNPComponent.setPaymentComponent("INCREASE_SNP");
            increaseSNPComponent.setAmount(BigDecimal.valueOf(0));
            increaseSNPComponent.setProjections(Shared.generateMonthProjection(period, range, increaseSNPComponent.getAmount()));
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
            increaseComponent.setProjections(Shared.generateMonthProjection(period, range, increaseComponent.getAmount()));
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
                increaseComponent.setProjections(Shared.generateMonthProjection(period, range, increaseComponent.getAmount()));
            }
            component.add(increaseComponent);
        } else {
            PaymentComponentDTO increaseComponent = new PaymentComponentDTO();
            increaseComponent.setPaymentComponent("INCREASE");
            increaseComponent.setAmount(BigDecimal.valueOf(0));
            increaseComponent.setProjections(Shared.generateMonthProjection(period, range, increaseComponent.getAmount()));
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
        increaseSNPAndIncreaseComponent.setProjections(Shared.generateMonthProjection(period, range, increaseSNPAndIncreaseComponent.getAmount()));
        component.add(increaseSNPAndIncreaseComponent);
    }

    //Incremento AFP 10,23%
    public void increaseAFP1023(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO increaseAFP1023BaseComponent = componentMap.get("INCREASE_AFP_1023_BASE");
        if (increaseAFP1023BaseComponent != null) {
            double increaseAFP1023Base = increaseAFP1023BaseComponent.getAmount().doubleValue();
            PaymentComponentDTO increaseAFP1023Component = new PaymentComponentDTO();
            increaseAFP1023Component.setPaymentComponent("INCREASE_AFP_1023");
            increaseAFP1023Component.setAmount(BigDecimal.valueOf(increaseAFP1023Base));
            increaseAFP1023Component.setProjections(Shared.generateMonthProjection(period, range, increaseAFP1023Component.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            if (increaseAFP1023BaseComponent.getProjections() != null) {
                for (MonthProjection projection : increaseAFP1023BaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    double increaseAFP1023PerMonth = increaseAFP1023BaseComponent.getAmount().doubleValue();
                    MonthProjection monthProjection = new MonthProjection();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(increaseAFP1023PerMonth));
                    projections.add(monthProjection);
                }
                increaseAFP1023Component.setProjections(projections);
            } else {
                increaseAFP1023Component.setAmount(BigDecimal.valueOf(0));
                increaseAFP1023Component.setProjections(Shared.generateMonthProjection(period, range, increaseAFP1023Component.getAmount()));
            }
            component.add(increaseAFP1023Component);
        } else {
            PaymentComponentDTO increaseAFP1023Component = new PaymentComponentDTO();
            increaseAFP1023Component.setPaymentComponent("INCREASE_AFP_1023");
            increaseAFP1023Component.setAmount(BigDecimal.valueOf(0));
            increaseAFP1023Component.setProjections(Shared.generateMonthProjection(period, range, increaseAFP1023Component.getAmount()));
            component.add(increaseAFP1023Component);
        }
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
            housingExpatriatesComponent.setProjections(Shared.generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
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
                housingExpatriatesComponent.setProjections(Shared.generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
            }
            component.add(housingExpatriatesComponent);
        } else {
            PaymentComponentDTO housingExpatriatesComponent = new PaymentComponentDTO();
            housingExpatriatesComponent.setPaymentComponent("HOUSING_EXPATRIATES");
            housingExpatriatesComponent.setAmount(BigDecimal.valueOf(0));
            housingExpatriatesComponent.setProjections(Shared.generateMonthProjection(period, range, housingExpatriatesComponent.getAmount()));
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
            double vacationPerMonth = (vacationPerDay * vacationDays * goceVacacionesBase) * vacationSeasonality;
            vacationEnjoymentComponent.setAmount(BigDecimal.valueOf(vacationPerMonth * -1));
            vacationEnjoymentComponent.setProjections(Shared.generateMonthProjection(period, range, vacationEnjoymentComponent.getAmount()));
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
                    vacationSeasonalityValue = vacationSeasonalityParameter.getValue();
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
            vacationEnjoymentComponent.setProjections(Shared.generateMonthProjection(period, range, vacationEnjoymentComponent.getAmount()));
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
            overtimeComponent.setProjections(Shared.generateMonthProjection(period, range, overtimeComponent.getAmount()));
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
            overtimeComponent.setProjections(Shared.generateMonthProjection(period, range, overtimeComponent.getAmount()));
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
            double commissionsPerMonth = (commissionsBase / totalCommissions) * commissionsValue / 12;
            commissionsComponent.setAmount(BigDecimal.valueOf(commissionsPerMonth));
            commissionsComponent.setProjections(Shared.generateMonthProjection(period, range, commissionsComponent.getAmount()));
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
                double commissionsPerMonthProjection = (commissionsBaseProjection / totalCommissions) * commissionsValueValue / 12;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(commissionsPerMonthProjection));
                projections.add(monthProjection);
            }
            commissionsComponent.setProjections(projections);
        } else {
            commissionsComponent.setAmount(BigDecimal.valueOf(0));
            commissionsComponent.setProjections(Shared.generateMonthProjection(period, range, commissionsComponent.getAmount()));
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
            incentivesComponent.setProjections(Shared.generateMonthProjection(period, range, incentivesComponent.getAmount()));
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
            incentivesComponent.setProjections(Shared.generateMonthProjection(period, range, incentivesComponent.getAmount()));
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
            nightBonusComponent.setProjections(Shared.generateMonthProjection(period, range, nightBonusComponent.getAmount()));
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
            nightBonusComponent.setProjections(Shared.generateMonthProjection(period, range, nightBonusComponent.getAmount()));
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
            availabilityPlusComponent.setProjections(Shared.generateMonthProjection(period, range, availabilityPlusComponent.getAmount()));
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
            availabilityPlusComponent.setProjections(Shared.generateMonthProjection(period, range, availabilityPlusComponent.getAmount()));
        }
        component.add(availabilityPlusComponent);
    }

    //Bono Cierre Pliego Sindical
    //=SI($W5="Emp";CC$28/CONTAR.SI.CONJUNTO($W$5:$W$11;"EMP");0)
    //W5= headcount poName
    //W$5:$W$11 = cantidad de EMP
    //CC$28 = PARAMETRO Valor Bono Cierre Pliego Sindical
    public void unionClosingBonus(List<PaymentComponentDTO> component, String period, Integer range, String poName, List<ParametersDTO> unionClosingBonusValueList, long countEMP, Map<String, EmployeeClassification> classificationMap) {
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }

        if (optionalEmployeeClassification.isPresent() && optionalEmployeeClassification.get().getTypeEmp().equals("EMP")) {
            Map<String, ParametersDTO> unionClosingBonusValueMap = createCacheMap(unionClosingBonusValueList);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO unionClosingBonusComponent = new PaymentComponentDTO();
            unionClosingBonusComponent.setPaymentComponent("UNION_CLOSING_BONUS");
            double unionClosingBonusValue = getCachedValue(unionClosingBonusValueMap, nextPeriod);
            double unionClosingBonusPerEMP = countEMP > 0 ? unionClosingBonusValue / countEMP : 0;
            if (unionClosingBonusValue > 0) {
                unionClosingBonusComponent.setAmount(BigDecimal.valueOf(unionClosingBonusPerEMP));
                unionClosingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
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
                unionClosingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
            }
            component.add(unionClosingBonusComponent);
        } else {
            PaymentComponentDTO unionClosingBonusComponent = new PaymentComponentDTO();
            unionClosingBonusComponent.setPaymentComponent("UNION_CLOSING_BONUS");
            unionClosingBonusComponent.setAmount(BigDecimal.valueOf(0));
            unionClosingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, unionClosingBonusComponent.getAmount()));
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
            vacationIncreaseBonusComponent.setProjections(Shared.generateMonthProjection(period, range, vacationIncreaseBonusComponent.getAmount()));
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
            vacationIncreaseBonusComponent.setProjections(Shared.generateMonthProjection(period, range, vacationIncreaseBonusComponent.getAmount()));
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
            vacationBonusComponent.setProjections(Shared.generateMonthProjection(period, range, vacationBonusComponent.getAmount()));
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
            vacationBonusComponent.setProjections(Shared.generateMonthProjection(period, range, vacationBonusComponent.getAmount()));
        }
        component.add(vacationBonusComponent);
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
            travelExpensesComponent.setProjections(Shared.generateMonthProjection(period, range, travelExpensesComponent.getAmount()));
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
            travelExpensesComponent.setProjections(Shared.generateMonthProjection(period, range, travelExpensesComponent.getAmount()));
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
            coinvComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, coinvComponentEmpty.getAmount()));
            component.add(coinvComponentEmpty);
        }
    }

    //PSP(Base Externa)
    //$BW5 = BaseExternaPSP
    public void psp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pspComponent = componentMap.get("PSP");
        if (pspComponent != null) {
            component.add(pspComponent);
        } else {
            PaymentComponentDTO pspComponentEmpty = new PaymentComponentDTO();
            pspComponentEmpty.setPaymentComponent("PSP");
            pspComponentEmpty.setAmount(BigDecimal.valueOf(0));
            pspComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, pspComponentEmpty.getAmount()));
            component.add(pspComponentEmpty);
        }
    }

    //RSP(Base Externa)
    //$BX5
    public void rsp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO rspComponent = componentMap.get("RSP");
        if (rspComponent != null) {
            component.add(rspComponent);
        } else {
            PaymentComponentDTO rspComponentEmpty = new PaymentComponentDTO();
            rspComponentEmpty.setPaymentComponent("RSP");
            rspComponentEmpty.setAmount(BigDecimal.valueOf(0));
            rspComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, rspComponentEmpty.getAmount()));
            component.add(rspComponentEmpty);
        }
    }

    //TFSP(Base Externa)
    //=$BV5
    public void tfsp(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO tfsComponent = componentMap.get("TFSP");
        if (tfsComponent != null) {
            component.add(tfsComponent);
        } else {
            PaymentComponentDTO tfsComponentEmpty = new PaymentComponentDTO();
            tfsComponentEmpty.setPaymentComponent("TFSP");
            tfsComponentEmpty.setAmount(BigDecimal.valueOf(0));
            tfsComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, tfsComponentEmpty.getAmount()));
            component.add(tfsComponentEmpty);
        }
    }

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
            specialDaysBonusComponent.setProjections(Shared.generateMonthProjection(period, range, specialDaysBonusComponent.getAmount()));
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
            specialDaysBonusComponent.setProjections(Shared.generateMonthProjection(period, range, specialDaysBonusComponent.getAmount()));
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
            housingAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, housingAssignmentComponent.getAmount()));
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
            housingAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, housingAssignmentComponent.getAmount()));
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
            complementaryBonusComponent.setProjections(Shared.generateMonthProjection(period, range, complementaryBonusComponent.getAmount()));
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
            complementaryBonusComponent.setProjections(Shared.generateMonthProjection(period, range, complementaryBonusComponent.getAmount()));
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
            groupResponsibleBonusComponent.setProjections(Shared.generateMonthProjection(period, range, groupResponsibleBonusComponent.getAmount()));
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
            groupResponsibleBonusComponent.setProjections(Shared.generateMonthProjection(period, range, groupResponsibleBonusComponent.getAmount()));
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
            basicSalaryComplementComponent.setProjections(Shared.generateMonthProjection(period, range, basicSalaryComplementComponent.getAmount()));
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
            basicSalaryComplementComponent.setProjections(Shared.generateMonthProjection(period, range, basicSalaryComplementComponent.getAmount()));
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
            judicialMandateConceptsComponent.setProjections(Shared.generateMonthProjection(period, range, judicialMandateConceptsComponent.getAmount()));
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
            judicialMandateConceptsComponent.setProjections(Shared.generateMonthProjection(period, range, judicialMandateConceptsComponent.getAmount()));
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
            storeDayComponent.setProjections(Shared.generateMonthProjection(period, range, storeDayComponent.getAmount()));
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
            storeDayComponent.setProjections(Shared.generateMonthProjection(period, range, storeDayComponent.getAmount()));
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
            transferBonusComponent.setProjections(Shared.generateMonthProjection(period, range, transferBonusComponent.getAmount()));
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
            transferBonusComponent.setProjections(Shared.generateMonthProjection(period, range, transferBonusComponent.getAmount()));
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
            clothingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, clothingBonusComponent.getAmount()));
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
            clothingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, clothingBonusComponent.getAmount()));
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
            teleworkLawComponent.setProjections(Shared.generateMonthProjection(period, range, teleworkLawComponent.getAmount()));
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
            teleworkLawComponent.setProjections(Shared.generateMonthProjection(period, range, teleworkLawComponent.getAmount()));
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
            mobilityAndRefreshmentComponent.setProjections(Shared.generateMonthProjection(period, range, mobilityAndRefreshmentComponent.getAmount()));
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
                double mobilityAndRefreshmentProjection = Math.min(mobilityAndRefreshmentBaseProjection, mobilityAndRefreshmentAmountValue);
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(mobilityAndRefreshmentProjection));
                projections.add(monthProjection);
            }
            mobilityAndRefreshmentComponent.setProjections(projections);
        } else {
            mobilityAndRefreshmentComponent.setAmount(BigDecimal.valueOf(0));
            mobilityAndRefreshmentComponent.setProjections(Shared.generateMonthProjection(period, range, mobilityAndRefreshmentComponent.getAmount()));
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
            double familyAssignmentBase = familyAssignmentBaseComponent.getAmount().doubleValue();
            double familyAssignmentPercentage = getCachedValue(familyAssignmentPercentageMap, nextPeriod) / 100;
            double minimumSalary = getCachedValue(minimumSalaryMap, period);
            double familyAssignment = familyAssignmentBase > 0 ? familyAssignmentPercentage * minimumSalary : 0;
            familyAssignmentComponent.setAmount(BigDecimal.valueOf(familyAssignment));
            familyAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, familyAssignmentComponent.getAmount()));
            List<MonthProjection> projections = new ArrayList<>();
            double lastFamilyAssignmentPercentage = 0;
            for (MonthProjection projection : familyAssignmentBaseComponent.getProjections()) {
                ParametersDTO familyAssignmentPercentageParameter = familyAssignmentPercentageMap.get(projection.getMonth());
                double familyAssignmentPercentageValue;
                if (familyAssignmentPercentageParameter != null) {
                    familyAssignmentPercentageValue = familyAssignmentPercentageParameter.getValue() / 100;
                    lastFamilyAssignmentPercentage = familyAssignmentPercentageValue;
                } else {
                    familyAssignmentPercentageValue = lastFamilyAssignmentPercentage;
                }
                String month = projection.getMonth();
                double familyAssignmentBaseProjection = projection.getAmount().doubleValue();
                double familyAssignmentProjection = familyAssignmentBaseProjection > 0 ? familyAssignmentPercentageValue * minimumSalary : 0;
                MonthProjection monthProjection = new MonthProjection();
                monthProjection.setMonth(month);
                monthProjection.setAmount(BigDecimal.valueOf(familyAssignmentProjection));
                projections.add(monthProjection);
            }
            familyAssignmentComponent.setProjections(projections);
        } else {
            familyAssignmentComponent.setAmount(BigDecimal.valueOf(0));
            familyAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, familyAssignmentComponent.getAmount()));
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
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }

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
            lumpSumBonusComponent.setProjections(Shared.generateMonthProjection(period, range, lumpSumBonusComponent.getAmount()));
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
            lumpSumBonusComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, lumpSumBonusComponentEmpty.getAmount()));
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
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }

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
            signingBonusComponent.setProjections(Shared.generateMonthProjection(period, range, signingBonusComponent.getAmount()));
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
            signingBonusComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, signingBonusComponentEmpty.getAmount()));
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
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }

        if (optionalEmployeeClassification.isPresent() && optionalEmployeeClassification.get().getTypeEmp().equals("Emp")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            PaymentComponentDTO extraordinaryConventionBonusComponent = new PaymentComponentDTO();
            extraordinaryConventionBonusComponent.setPaymentComponent("EXTRAORDINARY_CONVENTION_BONUS");
            double extraordinaryConventionBonusAmount = getCachedValue(extraordinaryConventionBonusAmountMap, nextPeriod);
            double extraordinaryConventionBonus = countEMP > 0 ? extraordinaryConventionBonusAmount / countEMP : 0;
            extraordinaryConventionBonusComponent.setAmount(BigDecimal.valueOf(extraordinaryConventionBonus));
            extraordinaryConventionBonusComponent.setProjections(Shared.generateMonthProjection(period, range, extraordinaryConventionBonusComponent.getAmount()));
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
            extraordinaryConventionBonusComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, extraordinaryConventionBonusComponentEmpty.getAmount()));
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
        extraordinaryBonusComponent.setProjections(Shared.generateMonthProjection(period, range, extraordinaryBonusComponent.getAmount()));
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
                epsContributionComponent.setProjections(Shared.generateMonthProjection(period, range, epsContributionComponent.getAmount()));
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
                epsContributionComponent.setProjections(Shared.generateMonthProjection(period, range, epsContributionComponent.getAmount()));
            }
            component.add(epsContributionComponent);
        } else {
            PaymentComponentDTO epsContributionComponentEmpty = new PaymentComponentDTO();
            epsContributionComponentEmpty.setPaymentComponent("EPS_CONTRIBUTION");
            epsContributionComponentEmpty.setAmount(BigDecimal.valueOf(0));
            epsContributionComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, epsContributionComponentEmpty.getAmount()));
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
                schoolAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, schoolAssignmentComponent.getAmount()));
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
                schoolAssignmentComponent.setProjections(Shared.generateMonthProjection(period, range, schoolAssignmentComponent.getAmount()));
            }
            component.add(schoolAssignmentComponent);
        } else {
            PaymentComponentDTO schoolAssignmentComponentEmpty = new PaymentComponentDTO();
            schoolAssignmentComponentEmpty.setPaymentComponent("SCHOOL_ASSIGNMENT");
            schoolAssignmentComponentEmpty.setAmount(BigDecimal.valueOf(0));
            schoolAssignmentComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, schoolAssignmentComponentEmpty.getAmount()));
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
                studiesBonusComponent.setProjections(Shared.generateMonthProjection(period, range, studiesBonusComponent.getAmount()));
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
                studiesBonusComponent.setProjections(Shared.generateMonthProjection(period, range, studiesBonusComponent.getAmount()));
            }
            component.add(studiesBonusComponent);
        } else {
            PaymentComponentDTO studiesBonusComponentEmpty = new PaymentComponentDTO();
            studiesBonusComponentEmpty.setPaymentComponent("STUDIES_BONUS");
            studiesBonusComponentEmpty.setAmount(BigDecimal.valueOf(0));
            studiesBonusComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, studiesBonusComponentEmpty.getAmount()));
            component.add(studiesBonusComponentEmpty);
        }
    }

    //Prestaciones Alimentarias
    //=ARRAY_CONSTRAIN(ARRAYFORMULA(SI.ERROR(INDICE($D$3:$D$59;COINCIDIR(VERDADERO;SI(ESNUMERO(ENCONTRAR($B$3:$B$59;$V6));VERDADERO;FALSO);0));0)/12); 1; 1)
    //D3:D59 = classificationMap
    //B3:B59 = classificationMap
    //V6 = PoName
    public void foodBenefits(List<PaymentComponentDTO> component, String period, Integer range, String poName, Map<String, EmployeeClassification> classificationMap) {
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        // Si no hay coincidencia exacta, buscar la posición más similar
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }
        if (optionalEmployeeClassification.isPresent()) {
            EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
            //String categoryLocal = employeeClassification.getTypeEmp();
            PaymentComponentDTO foodBenefitsComponent = new PaymentComponentDTO();
            foodBenefitsComponent.setPaymentComponent("FOOD_BENEFITS");
            double foodBenefits = employeeClassification.getValueAllowance() / 12;
            foodBenefitsComponent.setAmount(BigDecimal.valueOf(foodBenefits));
            foodBenefitsComponent.setProjections(Shared.generateMonthProjection(period, range, foodBenefitsComponent.getAmount()));
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
            foodBenefitsComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, foodBenefitsComponentEmpty.getAmount()));
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
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        Map<String, ParametersDTO> youngExecutiveSalaryMap = createCacheMap(youngExecutiveSalaryList);
        Map<String, ParametersDTO> internSalaryMap = createCacheMap(internSalaryList);

        PaymentComponentDTO internsBaseComponent = componentMap.get("INTERNS_BASE");
        if (internsBaseComponent != null) {
            Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
            // Si no hay coincidencia exacta, buscar la posición más similar
            if (optionalEmployeeClassification.isEmpty() && optionalEmployeeClassification.get().getTypeEmp().equals("FLJ")) {
                String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
                optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
            }
            if (optionalEmployeeClassification.isPresent()) {
                EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
                PaymentComponentDTO internsComponent = new PaymentComponentDTO();
                internsComponent.setPaymentComponent("INTERNS");
                double internsBase = internsBaseComponent.getAmount().doubleValue();
                double youngExecutiveSalary = getCachedValue(youngExecutiveSalaryMap, period);
                double internSalary = getCachedValue(internSalaryMap, period);
                double interns = 0;
                if (poName.equals("Joven ejecutivo")) {
                    interns = youngExecutiveSalary * (1 + (1 / 12.0));
                } else if (poName.equals("Practicante")) {
                    interns = internSalary * (1 + (1 / 12.0));
                }
                internsComponent.setAmount(BigDecimal.valueOf(Math.max(interns, internsBase)));
                internsComponent.setProjections(Shared.generateMonthProjection(period, range, internsComponent.getAmount()));
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : internsBaseComponent.getProjections()) {
                    String month = projection.getMonth();
                    MonthProjection monthProjection = new MonthProjection();
                    double internsBaseProjection = projection.getAmount().doubleValue();
                    monthProjection.setMonth(month);
                    monthProjection.setAmount(BigDecimal.valueOf(internsBaseProjection));
                    projections.add(monthProjection);
                }
                internsComponent.setProjections(projections);
                component.add(internsComponent);
            } else {
                PaymentComponentDTO internsComponentEmpty = new PaymentComponentDTO();
                internsComponentEmpty.setPaymentComponent("INTERNS");
                internsComponentEmpty.setAmount(BigDecimal.valueOf(0));
                internsComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, internsComponentEmpty.getAmount()));
                component.add(internsComponentEmpty);
            }
        } else {
            PaymentComponentDTO internsComponentEmpty = new PaymentComponentDTO();
            internsComponentEmpty.setPaymentComponent("INTERNS");
            internsComponentEmpty.setAmount(BigDecimal.valueOf(0));
            internsComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, internsComponentEmpty.getAmount()));
            component.add(internsComponentEmpty);
        }
    }

    //Seguro Medico
    //=$BR5
    public void medicalInsurance(List<PaymentComponentDTO> component, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        PaymentComponentDTO medicalInsuranceBaseComponent = componentMap.get("seguro_medico");
        if (medicalInsuranceBaseComponent != null) {
            PaymentComponentDTO medicalInsuranceComponent = new PaymentComponentDTO();
            medicalInsuranceComponent.setPaymentComponent("MEDICAL_INSURANCE");
            double medicalInsuranceBase = medicalInsuranceBaseComponent.getAmount().doubleValue();
            medicalInsuranceComponent.setAmount(BigDecimal.valueOf(medicalInsuranceBase));
            medicalInsuranceComponent.setProjections(Shared.generateMonthProjection(period, range, medicalInsuranceComponent.getAmount()));
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
            medicalInsuranceComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, medicalInsuranceComponentEmpty.getAmount()));
            component.add(medicalInsuranceComponentEmpty);
        }
    }

    //Provisión de Vacaciones}
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
            vacationProvisionComponent.setProjections(Shared.generateMonthProjection(period, range, vacationProvisionComponent.getAmount()));
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
            vacationProvisionComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, vacationProvisionComponentEmpty.getAmount()));
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

        if (theoricSalaryComponent != null) {
            Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
            if (optionalEmployeeClassification.isEmpty()) {
                String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
                optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
            }

            if (optionalEmployeeClassification.isPresent()) {
                EmployeeClassification employeeClassification = optionalEmployeeClassification.get();
                if ("EMP".equals(employeeClassification.getTypeEmp())) {
                    long seniorityYears = calculateSeniorityYears(hiringDate, period);
                    BigDecimal quinquenniumValue = getQuinquenniumValue(seniorityYears, quinquenniumMap);

                    double theoricSalary = theoricSalaryComponent.getAmount().doubleValue();
                    double seniority = quinquenniumValue.doubleValue() * theoricSalary;

                    PaymentComponentDTO seniorityComponent = new PaymentComponentDTO();
                    seniorityComponent.setPaymentComponent("SENIORITY");
                    seniorityComponent.setAmount(BigDecimal.valueOf(seniority));
                    seniorityComponent.setProjections(Shared.generateMonthProjection(period, range, seniorityComponent.getAmount()));

                    List<MonthProjection> projections = new ArrayList<>();
                    for (MonthProjection projection : theoricSalaryComponent.getProjections()) {
                        String month = projection.getMonth();
                        long projectionSeniorityYears = calculateSeniorityYears(hiringDate, month);
                        BigDecimal quinquenniumProjectionValue = getQuinquenniumValue(projectionSeniorityYears, quinquenniumMap);
                        double theoricSalaryProjection = projection.getAmount().doubleValue();
                        double seniorityProjection = quinquenniumProjectionValue.doubleValue() * theoricSalaryProjection;

                        MonthProjection monthProjection = new MonthProjection();
                        monthProjection.setMonth(month);
                        monthProjection.setAmount(BigDecimal.valueOf(seniorityProjection));
                        projections.add(monthProjection);
                    }

                    seniorityComponent.setProjections(projections);
                    components.add(seniorityComponent);
                }
            }else {
                PaymentComponentDTO seniorityComponentEmpty = new PaymentComponentDTO();
                seniorityComponentEmpty.setPaymentComponent("SENIORITY");
                seniorityComponentEmpty.setAmount(BigDecimal.valueOf(0));
                seniorityComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, seniorityComponentEmpty.getAmount()));
                components.add(seniorityComponentEmpty);
            }
        }else {
            PaymentComponentDTO seniorityComponentEmpty = new PaymentComponentDTO();
            seniorityComponentEmpty.setPaymentComponent("SENIORITY");
            seniorityComponentEmpty.setAmount(BigDecimal.valueOf(0));
            seniorityComponentEmpty.setProjections(Shared.generateMonthProjection(period, range, seniorityComponentEmpty.getAmount()));
            components.add(seniorityComponentEmpty);
        }
    }
    //[Auxiliar en excel para calcular bono]
    //==SI(MES(CL$34)=10;CL35*14*MAX($AB5;$AC5)/12;0)
    //CL$34 = Mes actual
    //CL35 = TheoricSalary
    //$AB5 = componentBonoBase PC960451
    //$AC5 = componentBonoBase PC960452
    public double calculateBonus(double theoricSalary, double bonusBase1, double bonusBase2, String period) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        if (yearMonth.getMonthValue() == 10) {
            return theoricSalary * 14 * Math.max(bonusBase1, bonusBase2) / 12;
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
        PaymentComponentDTO PC960451Component = componentMap.get("PC960451");
        PaymentComponentDTO PC960452Component = componentMap.get("PC960452");
        double bonusBase1 = PC960451Component != null ? PC960451Component.getAmount().doubleValue() : 0;
        double bonusBase2 = PC960452Component != null ? PC960452Component.getAmount().doubleValue() : 0;
        double theoricSalary = theoricSalaryComponent != null ? theoricSalaryComponent.getAmount().doubleValue() : 0;
        PaymentComponentDTO srdBonusComponent = new PaymentComponentDTO();
        srdBonusComponent.setPaymentComponent("SRD_BONUS");
        double srdBonus = calculateBonus(theoricSalary, bonusBase1, bonusBase2, period);
        srdBonusComponent.setAmount(BigDecimal.valueOf(srdBonus));
        srdBonusComponent.setProjections(Shared.generateMonthProjection(period, range, srdBonusComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : srdBonusComponent.getProjections()) {
            String month = projection.getMonth();
            double srdBonusProjection = calculateBonus(theoricSalary, bonusBase1, bonusBase2, month);
            MonthProjection monthProjection = new MonthProjection();
            monthProjection.setMonth(month);
            monthProjection.setAmount(BigDecimal.valueOf(srdBonusProjection));
            projections.add(monthProjection);
        }
        components.add(srdBonusComponent);
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
        topPerformerBonusComponent.setProjections(Shared.generateMonthProjection(period, range, topPerformerBonusComponent.getAmount()));
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
        Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(poName));
        if (optionalEmployeeClassification.isEmpty()) {
            String mostSimilarPosition = findMostSimilarPosition(poName, classificationMap.keySet());
            optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(mostSimilarPosition));
        }

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
        topPerformerBonusComponent.setProjections(Shared.generateMonthProjection(period, range, topPerformerBonusComponent.getAmount()));

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
        epsCreditComponent.setProjections(Shared.generateMonthProjection(period, range, epsCreditComponent.getAmount()));
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
        voluntaryContributionComponent.setProjections(Shared.generateMonthProjection(period, range, voluntaryContributionComponent.getAmount()));
        List<MonthProjection> projections = new ArrayList<>();
        if (theoricSalaryComponent != null){
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
        }else {
            voluntaryContributionComponent.setAmount(BigDecimal.valueOf(0));
            voluntaryContributionComponent.setProjections(Shared.generateMonthProjection(period, range, voluntaryContributionComponent.getAmount()));
        }
        components.add(voluntaryContributionComponent);
    }
}