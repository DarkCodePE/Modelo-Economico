package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.DateCache;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;
import ms.hispam.budget.repository.mysql.ConvenioBonoRepository;
import ms.hispam.budget.repository.mysql.ConvenioRepository;
import ms.hispam.budget.service.MexicoService;
import ms.hispam.budget.util.DaysVacationInfo;
import ms.hispam.budget.util.Shared;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "Mexico")
public class Mexico {
    private final MexicoService mexicoService;
    private Map<String, ParametersDTO> salaryMap = new ConcurrentHashMap<>();
    private Map<String, ParametersDTO> incrementMap = new ConcurrentHashMap<>();
    private Map<String, ParametersDTO> salaryRevisionMap = new ConcurrentHashMap<>();
    static final String TYPEMONTH="yyyyMM";
    private DateCache dateCache = new DateCache(3);
    private static final String SALARY_MINIMUM_MEXICO = "Salario Mínimo Mexico";
    private static final String SALARY_INCREMENT = "Increm Salario Mín";
    private static final String SALARY_REVISION = "Revision Salarial";
    private static final String SALARY = "SALARY";
    private static final String AGUINALDO = "AGUINALDO";
    private static final String VACACIONES = "VACACIONES";
    private static final String  PC320001 = "PC320001";
    private static final String  PC320002 = "PC320002";
    private List<RangeBuDetailDTO> allDaysVacation;
    private List<String> sortedSalaryRevisions;

    private static final List<DaysVacationInfo> daysVacationList = Arrays.asList(
            new DaysVacationInfo(1, 1, 12),
            new DaysVacationInfo(2, 2, 14),
            new DaysVacationInfo(3, 3, 16),
            new DaysVacationInfo(4, 4, 19),
            new DaysVacationInfo(9, 9, 22),
            new DaysVacationInfo(14, 14, 24),
            new DaysVacationInfo(19, 19, 26),
            new DaysVacationInfo(24, 24, 28),
            new DaysVacationInfo(30, 30, 30),
            new DaysVacationInfo(35, 35, 32)
    );

    private Map<String, Integer> vacationsDaysCache = new ConcurrentHashMap<>();
    @Autowired
    public Mexico(MexicoService mexicoService, ConvenioRepository convenioRepository, ConvenioBonoRepository convenioBonoRepository) {
        this.mexicoService = mexicoService;
    }

    public List<RangeBuDetailDTO> getAllDaysVacation(List<RangeBuDetailDTO> rangeBu, Integer idBu) {
        return mexicoService.getAllDaysVacation(rangeBu, idBu);
    }

    public void init(List<ParametersDTO> salaryList, List<ParametersDTO> incrementList) {
        for (ParametersDTO salaryParam : salaryList) {
            salaryMap.put(salaryParam.getPeriod(), salaryParam);
        }
        for (ParametersDTO incrementParam : incrementList) {
            incrementMap.put(incrementParam.getPeriod(), incrementParam);
        }
    }

    /*public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, List<ParametersDTO> incrementList, String period, Integer range) {
        double baseSalary = getValueByComponentName(component, PC320001);
        double baseSalaryIntegral = getValueByComponentName(component, PC320002);
        PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
        //Map<String, Pair<Double, Double>> cache = createCacheNoSecure(new ArrayList<>(salaryMap.values()), new ArrayList<>(incrementMap.values()));
        Map<String, Pair<Double, Double>> cache = createCache(salaryList, incrementList);
        updateProjections(paymentComponentDTO, cache);
        component.add(paymentComponentDTO);
    }*/

    private double getValueByComponentName(List<PaymentComponentDTO> component, String name) {
        return component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase(name))
                .findFirst()
                .map(PaymentComponentDTO::getAmount)
                .map(BigDecimal::doubleValue)
                .orElse(0.0);
    }

    private PaymentComponentDTO createPaymentComponent(double baseSalary, double baseSalaryIntegral, String period, Integer range) {
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent(SALARY);
        paymentComponentDTO.setAmount(BigDecimal.valueOf(Math.max(baseSalary, baseSalaryIntegral)));
        ////log.debug("baseSalary: {} , baseSalaryIntegral: {}", baseSalary, baseSalaryIntegral);
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(period, range, paymentComponentDTO.getAmount()));
        return paymentComponentDTO;
    }
    private Map<String, Pair<Double, Double>> createCacheWithRevision(List<ParametersDTO> salaryList, List<ParametersDTO> revisionList) {
        Map<String, Pair<Double, Double>> cache = new ConcurrentHashMap<>();
        List<ParametersDTO> sortedSalaryList = new ArrayList<>(salaryList);
        sortedSalaryList.sort(Comparator.comparing(ParametersDTO::getPeriod));
        for (ParametersDTO revisionParam : revisionList) {
            ParametersDTO salaryParam = salaryMap.get(revisionParam.getPeriod());
            if (salaryParam == null) {
                salaryParam = findLatestSalaryForPeriod(sortedSalaryList, revisionParam.getPeriod());
            }
            if (salaryParam != null) {
                cache.put(revisionParam.getPeriod(), Pair.of(salaryParam.getValue(), revisionParam.getValue()));
            }
        }
        return cache;
    }
    private Map<String, Pair<Double, Double>> createCache(List<ParametersDTO> salaryList, List<ParametersDTO> incrementList) {
        Map<String, Pair<Double, Double>> cache = new ConcurrentHashMap<>();
        List<ParametersDTO> sortedSalaryList = new ArrayList<>(salaryList);
        sortedSalaryList.sort(Comparator.comparing(ParametersDTO::getPeriod));
        for (ParametersDTO incrementParam : incrementList) {
            ParametersDTO salaryParam = salaryMap.get(incrementParam.getPeriod());
            if (salaryParam == null) {
                salaryParam = findLatestSalaryForPeriod(sortedSalaryList, incrementParam.getPeriod());
            }
            if (salaryParam != null) {
                cache.put(incrementParam.getPeriod(), Pair.of(salaryParam.getValue(), incrementParam.getValue()));
            }
        }
        return cache;
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
    private ParametersDTO findLatestSalaryForPeriod(List<ParametersDTO> salaryList, String period) {
        for (int i = salaryList.size() - 1; i >= 0; i--) {
            if (salaryList.get(i).getPeriod().compareTo(period) <= 0) {
                return salaryList.get(i);
            }
        }
        return null;
    }
    public void updateSortedSalaryRevisions() {
        sortedSalaryRevisions = new ArrayList<>(salaryRevisionMap.keySet());
        Collections.sort(sortedSalaryRevisions);
    }
    public Pair<ParametersDTO, Double> findClosestSalaryRevision(PaymentComponentDTO salaryComponent, String projectionMonth, Map<String, Pair<Double, Double>> cacheWithRevision, boolean isCp, double highestAmountSoFar) {
    String closestPeriod = salaryRevisionMap.keySet().stream()
            .min((period1, period2) -> {
                if (period1.compareTo(projectionMonth) <= 0 && period2.compareTo(projectionMonth) <= 0) {
                    return period2.compareTo(period1);
                } else if (period1.compareTo(projectionMonth) <= 0) {
                    return -1;
                } else if (period2.compareTo(projectionMonth) <= 0) {
                    return 1;
                } else {
                    return period1.compareTo(period2);
                }
            })
            .orElse(null);
    ParametersDTO closestSalaryRevision = closestPeriod != null ? salaryRevisionMap.get(closestPeriod) : null;
    if (closestSalaryRevision != null) {
        double percent = 0.0;
        if(Boolean.TRUE.equals(isCp) ){
            Pair<Double, Double> salaryAndRevision = cacheWithRevision.get(projectionMonth);
            double salaryParam = salaryAndRevision != null ? salaryAndRevision.getKey() : 0.0;
            double salaryFirst;
            if (Boolean.TRUE.equals(closestSalaryRevision.getIsRetroactive())){
                String[] periodRevisionSalary = closestSalaryRevision.getPeriodRetroactive().split("-");
                int idxStart = Shared.getIndex(salaryComponent.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
                salaryFirst = salaryComponent.getProjections().get(idxStart).getAmount().doubleValue();
            }else {
                int idxStart = Shared.getIndex(salaryComponent.getProjections().stream()
                        .map(MonthProjection::getMonth).collect(Collectors.toList()), projectionMonth);
                salaryFirst = salaryComponent.getProjections().get(idxStart).getAmount().doubleValue();
            }
            if (highestAmountSoFar > salaryParam) {
                percent = closestSalaryRevision.getValue() / 100;
            }
        }else {
            percent = closestSalaryRevision.getValue() / 100;
        }
        return Pair.of(closestSalaryRevision, percent);
    }
    return null;
}
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, List<ParametersDTO> incrementList, List<ParametersDTO>revisionList, String period, Integer range, String poName) {
        //double baseSalary = getValueByComponentName(component, PC320001);
        //double baseSalaryIntegral = getValueByComponentName(component, PC320002);
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc320001Component = componentMap.get(PC320001);
        PaymentComponentDTO pc320002Component = componentMap.get(PC320002);
        double baseSalary = pc320001Component == null ? 0.0 : pc320001Component.getAmount().doubleValue() / 12;
        double baseSalaryIntegral = pc320002Component == null ? 0.0 : pc320002Component.getAmount().doubleValue() / 12;
        for (ParametersDTO salaryRevision : revisionList) {
            salaryRevisionMap.put(salaryRevision.getPeriod(), salaryRevision);
        }
        PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
        Map<String, Pair<Double, Double>> cache = createCache(salaryList, incrementList);
        //log.info("cache: {}", cache);
        double lastDifferPercent = 0;
        double highestAmountSoFar = Math.max(baseSalary, baseSalaryIntegral);
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
            String month = projection.getMonth();
            Pair<Double, Double> salaryAndIncrement = cache.get(month);
            double incrementPercent;
            double lastSalary = 0.0;
            boolean isCp = poName != null && poName.contains("CP");
            //log.debug("isCp: {}", isCp);
            ////log.debug("isCp: {}", isCp);
            if (salaryAndIncrement != null) {
                incrementPercent = isCp ? salaryAndIncrement.getValue() :  0.0 ;
                double minSalary = salaryAndIncrement.getKey();
                if (highestAmountSoFar < minSalary && minSalary != 0.0) {
                    lastSalary = highestAmountSoFar * (1 + incrementPercent);
                }else {
                    lastSalary =  highestAmountSoFar ;
                }
            }else {
                lastSalary = highestAmountSoFar;
            }
            if (salaryRevisionMap.containsKey(month)) {
                Map<String, Pair<Double, Double>> cacheWithRevision = createCacheWithRevision(salaryList, revisionList);
                Pair<ParametersDTO, Double> closestSalaryRevisionAndDifferPercent = findClosestSalaryRevision(paymentComponentDTO, month, cacheWithRevision, isCp, lastSalary);
                if (closestSalaryRevisionAndDifferPercent != null && month.equals(closestSalaryRevisionAndDifferPercent.getKey().getPeriod())) {
                    lastDifferPercent = closestSalaryRevisionAndDifferPercent.getValue();
                } else {
                    lastDifferPercent = 0.0;
                }
            }else{
                lastDifferPercent = 0.0;
            }
            double revisedAmount = highestAmountSoFar * (1 + lastDifferPercent);
            //highestAmountSoFar = Math.max(lastSalary, revisedAmount);
            projection.setAmount(BigDecimal.valueOf(revisedAmount));
        }
        component.add(paymentComponentDTO);
    }
   public void salary2(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, List<ParametersDTO> incrementList, List<ParametersDTO>revisionList, String period, Integer range, String poName) {
       //double baseSalary = getValueByComponentName(component, PC320001);
       //double baseSalaryIntegral = getValueByComponentName(component, PC320002);
       Map<String, ParametersDTO> incrementMap = new ConcurrentHashMap<>();
       Map<String, ParametersDTO> salaryRevisionMap = new ConcurrentHashMap<>();
       Map<String, ParametersDTO> salaryMinList = new ConcurrentHashMap<>();
       Map<String, Double> cacheIncrement = new ConcurrentHashMap<>();
       Map<String, Double> cacheRevision = new ConcurrentHashMap<>();
       Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
       createCache(incrementList, incrementMap, cacheIncrement, (parameter, mapParameter) -> {});
       createCache(revisionList, salaryRevisionMap, cacheRevision, (parameter, mapParameter) -> {});
        createCache(salaryList, salaryMinList, cacheSalaryMin, (parameter, mapParameter) -> {});
       Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
       PaymentComponentDTO pc320001Component = componentMap.get(PC320001);
       PaymentComponentDTO pc320002Component = componentMap.get(PC320002);
       double baseSalary = pc320001Component == null ? 0.0 : pc320001Component.getAmount().doubleValue() / 12;
       double baseSalaryIntegral = pc320002Component == null ? 0.0 : pc320002Component.getAmount().doubleValue() / 12;
       PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
       paymentComponentDTO.setPaymentComponent(SALARY);
       paymentComponentDTO.setAmount(BigDecimal.valueOf(Math.max(baseSalary, baseSalaryIntegral)));
       paymentComponentDTO.setProjections(Shared.generateMonthProjection(period, range, paymentComponentDTO.getAmount()));
       boolean isCp = poName != null && poName.contains("CP");
       double lastIncrement = 0.0;
        double lastSalary = 0.0;
        double lastSalaryIncrementValue = paymentComponentDTO.getAmount().doubleValue();
        double lastRevision = 0.0;
        double lastSalaryRevision = 0.0;
        double lastRevisionNoCp = 0.0;
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
            String month = projection.getMonth();
            ParametersDTO salaryIncrementParam = incrementMap.get(month);
            ParametersDTO salaryMinParam = salaryMinList.get(month);
            double increment;
            if (salaryIncrementParam != null) {
                increment = salaryIncrementParam.getValue() / 100;
                lastIncrement = increment;
            }else {
                increment = lastIncrement;
            }
            double minSalary;
            if (salaryMinParam != null) {
                minSalary = salaryMinParam.getValue();
                lastSalary = minSalary;
            }else {
                minSalary = lastSalary;
            }
            ParametersDTO closestSalaryRevision = salaryRevisionMap.get(month);
            double revisionParam;
            if (closestSalaryRevision != null) {
                revisionParam = closestSalaryRevision.getValue() / 100;
                lastRevision = revisionParam;
            }else {
                revisionParam = lastRevision;
            }
            double salary;
            if (isCp) {
                double salaryIncrementValue;
                if (projection.getAmount().doubleValue() < minSalary) {
                    salaryIncrementValue =  projection.getAmount().doubleValue() * (1 + increment);
                    lastSalaryIncrementValue = salaryIncrementValue;
                } else {
                    salaryIncrementValue = lastSalaryIncrementValue;
                }
                double revisedAmount;
                if (projection.getAmount().doubleValue() > minSalary) {
                    revisedAmount = projection.getAmount().doubleValue() * (1 + revisionParam);
                    lastSalaryRevision = revisedAmount;
                } else {
                    revisedAmount = lastSalaryRevision;
                }
                salary = Math.max(salaryIncrementValue, revisedAmount);
            } else {
                double revisedAmountNoCp = projection.getAmount().doubleValue() * (1 + revisionParam);
                salary = Math.max(projection.getAmount().doubleValue(), revisedAmountNoCp);
            }
            MonthProjection newProjection = new MonthProjection();
            newProjection.setMonth(month);
            newProjection.setAmount(BigDecimal.valueOf(salary));
            projections.add(newProjection);
        }
        paymentComponentDTO.setProjections(projections);
       component.add(paymentComponentDTO);
   }
    private double calculateSalaryForNonCp(MonthProjection projection, double minSalary, double revisionParam, double lastSalaryNonmin) {
        double salary;
        if(projection.getAmount().doubleValue() < minSalary || projection.getAmount().doubleValue()  > minSalary){
            salary = minSalary * (1 + revisionParam);
            lastSalaryNonmin = salary;
        } else {
            salary = lastSalaryNonmin;
        }
        return salary;
    }
    private double calculateSalaryForCp(MonthProjection projection, double minSalary, double increment, double revisionParam, double lastSalaryIncrementValue, double lastSalaryRevision) {
        double salaryIncrementValue;
        if (projection.getAmount().doubleValue() < minSalary) {
            salaryIncrementValue = minSalary * (1 + increment);
            lastSalaryIncrementValue = salaryIncrementValue;
        } else {
            salaryIncrementValue = lastSalaryIncrementValue;
        }

        double revisedAmount;
        if (salaryIncrementValue > minSalary) {
            revisedAmount = minSalary * (1 + revisionParam);
            lastSalaryRevision = revisedAmount;
        } else {
            revisedAmount = lastSalaryRevision;
        }

        return Math.max(salaryIncrementValue, revisedAmount);
    }
    //Salary -> SI(ESNUMERO(HALLAR("CP";$P4));
    //SI(MES(AK$22)=3;AJ23*(1+SI(AH23<AK$2;0;AK$4));AJ23*(1+SI(AJ23<AK$2;AK$3;0)));
    //AJ23*(1+AK$4))
    //poName -> $P4
    //mes proyeccion -> AK$22
    //incremento -> AK$4
    //AH23 -> salario proyeccion anterior
    //AK$2 -> parametro salario minimo
    //AJ23 -> salario proyeccion actual
    //AK$3 -> % Rev Salarial
    //AK$4 -> %Increm Salario Mín
    public void salary3(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, List<ParametersDTO> incrementList, List<ParametersDTO>revisionList, String period, Integer range, String poName) {
        Map<String, ParametersDTO> incrementMap = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> salaryRevisionMap = new ConcurrentHashMap<>();
        Map<String, ParametersDTO> salaryMinList = new ConcurrentHashMap<>();
        Map<String, Double> cacheIncrement = new ConcurrentHashMap<>();
        Map<String, Double> cacheRevision = new ConcurrentHashMap<>();
        Map<String, Double> cacheSalaryMin = new ConcurrentHashMap<>();
        createCache(incrementList, incrementMap, cacheIncrement, (parameter, mapParameter) -> {});
        createCache(revisionList, salaryRevisionMap, cacheRevision, (parameter, mapParameter) -> {});
        createCache(salaryList, salaryMinList, cacheSalaryMin, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO pc320001Component = componentMap.get(PC320001);
        PaymentComponentDTO pc320002Component = componentMap.get(PC320002);
        double baseSalary = pc320001Component == null ? 0.0 : pc320001Component.getAmount().doubleValue() / 12;
        double baseSalaryIntegral = pc320002Component == null ? 0.0 : pc320002Component.getAmount().doubleValue() / 12;
        PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
        boolean isCp = poName != null && poName.contains("CP");
        double lastIncrement = 0.0;
        double lastSalary = 0.0;
        double lastSalaryIncrementValue = paymentComponentDTO.getAmount().doubleValue();
        double lastRevision = 0.0;
        double lastSalaryRevision = 0.0;
        double lastRevisionNoCp = 0.0;
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
            String month = projection.getMonth();
            ParametersDTO salaryIncrementParam = incrementMap.get(month);
            ParametersDTO salaryMinParam = salaryMinList.get(month);
            double increment;
            if (salaryIncrementParam != null) {
                increment = salaryIncrementParam.getValue() / 100;
                lastIncrement = increment;
            }else {
                increment = lastIncrement;
            }
            double minSalary;
            if (salaryMinParam != null) {
                minSalary = salaryMinParam.getValue();
                lastSalary = minSalary;
            }else {
                minSalary = lastSalary;
            }
            ParametersDTO closestSalaryRevision = salaryRevisionMap.get(month);
            double revisionParam;
            if (closestSalaryRevision != null) {
                revisionParam = closestSalaryRevision.getValue() / 100;
                lastRevision = revisionParam;
            }else {
                revisionParam = lastRevision;
            }
            double salary;
            if (isCp) {
                double salaryIncrementValue;
                if (projection.getAmount().doubleValue() < minSalary) {
                    salaryIncrementValue =  projection.getAmount().doubleValue() * (1 + increment);
                    lastSalaryIncrementValue = salaryIncrementValue;
                } else {
                    salaryIncrementValue = lastSalaryIncrementValue;
                }
                double revisedAmount;
                if (projection.getAmount().doubleValue() > minSalary) {
                    revisedAmount = projection.getAmount().doubleValue() * (1 + revisionParam);
                    lastSalaryRevision = revisedAmount;
                } else {
                    revisedAmount = lastSalaryRevision;
                }
                salary = Math.max(salaryIncrementValue, revisedAmount);
            } else {
                double revisedAmountNoCp = projection.getAmount().doubleValue() * (1 + revisionParam);
                salary = Math.max(projection.getAmount().doubleValue(), revisedAmountNoCp);
            }
            MonthProjection newProjection = new MonthProjection();
            newProjection.setMonth(month);
            newProjection.setAmount(BigDecimal.valueOf(salary));
            projections.add(newProjection);
        }
        paymentComponentDTO.setProjections(projections);
        component.add(paymentComponentDTO);
    }
    public void provAguinaldo(List<PaymentComponentDTO> component, String period, Integer range) {
        // Convierte la lista de componentes a un mapa
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        // Busca el componente de pago "SALARY" en el mapa
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        // Si el componente de pago "SALARY" se encuentra, calcula el aguinaldo
        if (salaryComponent != null) {
            // Crea un nuevo objeto PaymentComponentDTO para el aguinaldo
            PaymentComponentDTO aguinaldoComponent = new PaymentComponentDTO();
            aguinaldoComponent.setPaymentComponent(AGUINALDO);
            // Calcula el monto del aguinaldo
            BigDecimal aguinaldoAmount =BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / 30) * 1.25);
            aguinaldoComponent.setAmount(aguinaldoAmount);
            // Aplica la fórmula a cada proyección
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double amountProj = projection.getAmount().doubleValue();
                BigDecimal newAmount = BigDecimal.valueOf((amountProj / 30) * 1.25);
                MonthProjection bonusProjection = new MonthProjection();
                bonusProjection.setMonth(projection.getMonth());
                bonusProjection.setAmount(newAmount);
                projections.add(bonusProjection);
            }
            aguinaldoComponent.setProjections(projections);
            // Agrega el componente de aguinaldo a la lista de componentes
            component.add(aguinaldoComponent);
        }
    }
    public void provVacacionesRefactor(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, LocalDate dateContract, LocalDate dateBirth, RangeBuDTO rangeBuByBU, Integer idBu, String poName) {
       // log.debug("po name: {}", poName);

        if (dateContract != null) {
            LocalDate dateActual = LocalDate.now();
            long seniority = Math.max(ChronoUnit.YEARS.between(dateContract, dateActual), 0);
            List<RangeBuDetailDTO> daysVacations;
            if (rangeBuByBU != null) {
                daysVacations = rangeBuByBU.getRangeBuDetails();
            }else {
                daysVacations = getDaysVacationList();
            }
            //if (daysVacations.isEmpty()) daysVacations = getDaysVacationList();
            Map<String, RangeBuDetailDTO> daysVacationsMap = daysVacations.stream()
                    .collect(Collectors.toMap(RangeBuDetailDTO::getRange, Function.identity()));
            int vacationsDays = getCachedVacationsDays(seniority, daysVacationsMap);
            // Convierte la lista de componentes a un mapa
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
            // Busca el componente de pago "SALARY" en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
            if (salaryComponent != null) {
                // Crea un nuevo objeto PaymentComponentDTO para las vacaciones
                PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO();
                paymentComponentProvVacations.setPaymentComponent(VACACIONES);
                BigDecimal vacationAmount = BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / 30) * ((double) vacationsDays / 12));
                paymentComponentProvVacations.setAmount(vacationAmount);
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
//                    log.info("projection: {}", projection.getMonth());

                    // Convertir el mes de la proyección a LocalDate
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
                    YearMonth yearMonth = YearMonth.parse(projection.getMonth(), formatter);
                    LocalDate dateProjection = yearMonth.atDay(1);

                    double amountProj = projection.getAmount().doubleValue() / 30;
                    long seniorityProjection = Math.max(ChronoUnit.YEARS.between(dateContract, dateProjection), 0);
                    //log.info("dateContract: {}", dateContract);
//                    log.info("seniorityProjection: {}", seniorityProjection);
                    int vacationsDaysProjection = getCachedVacationsDays(seniorityProjection, daysVacationsMap);
//                    log.info("vacationsDaysProjection: {}", vacationsDaysProjection);
                    double vacationsDaysPerMonth =  (double) vacationsDaysProjection / 12;
                    BigDecimal newAmount = BigDecimal.valueOf(amountProj *  vacationsDaysPerMonth);
                    MonthProjection vacationProvisionProjection = new MonthProjection();
                    vacationProvisionProjection.setMonth(projection.getMonth());
                    vacationProvisionProjection.setAmount(newAmount);
                    projections.add(vacationProvisionProjection);
                }
                paymentComponentProvVacations.setProjections(projections);
                // Agrega el componente de vacaciones a la lista de componentes
                component.add(paymentComponentProvVacations);
            }else {
                PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO();
                paymentComponentProvVacations.setPaymentComponent(VACACIONES);
                paymentComponentProvVacations.setAmount(BigDecimal.valueOf(0.0));
                paymentComponentProvVacations.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(paymentComponentProvVacations);
            }
        }else {
            PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO();
            paymentComponentProvVacations.setPaymentComponent(VACACIONES);
            paymentComponentProvVacations.setAmount(BigDecimal.valueOf(0.0));
            paymentComponentProvVacations.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(paymentComponentProvVacations);
        }
    }
    //TODO: REFACTOR FALLBACK --> URGENTE
    private List<RangeBuDetailDTO> getDaysVacationList() {
        // Puedes inicializar tu lista aquí con los valores proporcionados
        return Arrays.asList(
                new RangeBuDetailDTO(1, "1", 10, 12.0),
                new RangeBuDetailDTO(1, "2",11, 14.0),
                new RangeBuDetailDTO(1, "3", 12,16.0),
                new RangeBuDetailDTO(1, "4", 13,19.0),
                new RangeBuDetailDTO(1, "5 a 9",14, 22.0)
        );
    }
    private int getCachedVacationsDays(long seniority, Map<String, RangeBuDetailDTO> daysVacationsMap) {
        return vacationsDaysCache.computeIfAbsent(String.valueOf(seniority), key -> {
            for (Map.Entry<String, RangeBuDetailDTO> entry : daysVacationsMap.entrySet()) {
                if (isSeniorityInRange(seniority, entry.getKey())) {
                    return entry.getValue().getValue().intValue();
                }
            }
            return 0;
        });
    }
    private boolean isSeniorityInRange(long seniority, String range) {
        String[] bounds = range.split(" a ");
        if (bounds.length == 2) {
            long lowerBound = Long.parseLong(bounds[0]);
            long upperBound = Long.parseLong(bounds[1]);
            return seniority >= lowerBound && seniority <= upperBound;
        } else if (bounds.length == 1) {
            return seniority == Long.parseLong(bounds[0]);
        }
        return false;
    }
    public void participacionTrabajadores(List<PaymentComponentDTO> component,  List<ParametersDTO>  employeeParticipationList, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        PaymentComponentDTO participacionComponent = new PaymentComponentDTO();
        participacionComponent.setPaymentComponent("PARTICIPACION");
        if ( totalSalaries > 0 ){
            Map<String, ParametersDTO> employeeParticipationMap = new ConcurrentHashMap<>();
            Map<String, Double> cache = new ConcurrentHashMap<>();
            createCache(employeeParticipationList, employeeParticipationMap, cache, (parameter, mapParameter) -> {});
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO participacionTrabajadoresParamNext = employeeParticipationMap.get(nextPeriod);
            double participacionTrabajadoresNext = participacionTrabajadoresParamNext == null ? 0.0 : participacionTrabajadoresParamNext.getValue();
            participacionComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * participacionTrabajadoresNext));
            List<MonthProjection> projections = new ArrayList<>();
            double lastParticipacionTrabajadores = participacionComponent.getAmount().doubleValue();
            for (MonthProjection projection : salaryComponent.getProjections()) {
                ParametersDTO participacionTrabajadoresParamProj = employeeParticipationMap.get(projection.getMonth());
                double participacionTrabajadoresProj;
                if (participacionTrabajadoresParamProj != null) {
                    participacionTrabajadoresProj = participacionTrabajadoresParamProj.getValue();
                    lastParticipacionTrabajadores = participacionTrabajadoresProj;
                } else {
                    participacionTrabajadoresProj = lastParticipacionTrabajadores;
                }
                double proportion = projection.getAmount().doubleValue() / totalSalaries;
                double participacion = proportion * participacionTrabajadoresProj;
                MonthProjection participacionProjection = new MonthProjection();
                participacionProjection.setMonth(projection.getMonth());
                participacionProjection.setAmount(BigDecimal.valueOf(participacion));
                projections.add(participacionProjection);
            }
            participacionComponent.setProjections(projections);
        }else {
            participacionComponent.setAmount(BigDecimal.valueOf(0.0));
            participacionComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
        }
        // Agregar el componente de "Participación de los Trabajadores" a la lista de componentes
        component.add(participacionComponent);
    }

    public void seguroDental(List<PaymentComponentDTO> component, List<ParametersDTO> dentalInsuranceList, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        PaymentComponentDTO seguroDentalComponent = new PaymentComponentDTO();
        seguroDentalComponent.setPaymentComponent("SEGURO_DENTAL");
        if ( totalSalaries > 0 ){
            Map<String, ParametersDTO> dentalInsuranceMap = new ConcurrentHashMap<>();
            Map<String, Double> cache = new ConcurrentHashMap<>();
            createCache(dentalInsuranceList, dentalInsuranceMap, cache, (parameter, mapParameter) -> {});
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            //PaymentComponentDTO seguroDentalComponent = new PaymentComponentDTO();
            //seguroDentalComponent.setPaymentComponent("SEGURO_DENTAL");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO seguroDentalParamNext = dentalInsuranceMap.get(nextPeriod);
            double seguroDentalNext = seguroDentalParamNext == null ? 0.0 : seguroDentalParamNext.getValue();
            seguroDentalComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * seguroDentalNext));
            // Apply the formula for each month of projection
            List<MonthProjection> projections = new ArrayList<>();
            double lastSeguroDental = seguroDentalComponent.getAmount().doubleValue();
            for (MonthProjection projection : salaryComponent.getProjections()) {
                //TODO: HACER QUE TRAIGA EL SALARIO DE CADA MES
                ParametersDTO seguroDentalParamProj = dentalInsuranceMap.get(projection.getMonth());
                double seguroDentalProj;
                if (seguroDentalParamProj != null) {
                    seguroDentalProj = seguroDentalParamProj.getValue();
                    lastSeguroDental = seguroDentalProj;
                } else {
                    seguroDentalProj = lastSeguroDental;
                }
                double proportion = projection.getAmount().doubleValue() / totalSalaries;
                //double seguroDentalAmount = proportion * seguroDentalProj;
                double seguroDentalAmount = proportion * seguroDentalProj;
                MonthProjection seguroDentalProjection = new MonthProjection();
                seguroDentalProjection.setMonth(projection.getMonth());
                seguroDentalProjection.setAmount(BigDecimal.valueOf(seguroDentalAmount));
                projections.add(seguroDentalProjection);
            }
            seguroDentalComponent.setProjections(projections);
        }else {
            seguroDentalComponent.setAmount(BigDecimal.valueOf(0.0));
            seguroDentalComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
        }
        // Add the "Seguro Dental" component to the component list
        component.add(seguroDentalComponent);
    }
    public void seguroVida(List<PaymentComponentDTO> component, List<ParametersDTO> lifeInsuranceList, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        if (totalSalaries == 0) {
            PaymentComponentDTO seguroVidaComponent = new PaymentComponentDTO();
            seguroVidaComponent.setPaymentComponent("SEGURO_VIDA");
            seguroVidaComponent.setAmount(BigDecimal.valueOf(0.0));
            seguroVidaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(seguroVidaComponent);
            return;
        }
        Map<String, ParametersDTO> lifeInsuranceMap = new ConcurrentHashMap<>();
        Map<String, Double> cache = new ConcurrentHashMap<>();
        createCache(lifeInsuranceList, lifeInsuranceMap, cache, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        PaymentComponentDTO seguroVidaComponent = new PaymentComponentDTO();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO seguroVidaParamNext = lifeInsuranceMap.get(nextPeriod);
        double seguroVidaNext = seguroVidaParamNext == null ? 0.0 : seguroVidaParamNext.getValue();
        seguroVidaComponent.setPaymentComponent("SEGURO_VIDA");
        seguroVidaComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * seguroVidaNext));
        // Apply the formula for each month of projection
        List<MonthProjection> projections = new ArrayList<>();
        double lastSeguroVida = seguroVidaComponent.getAmount().doubleValue();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            ParametersDTO seguroVidaParamProj = lifeInsuranceMap.get(projection.getMonth());
            double seguroVidaProj;
            if (seguroVidaParamProj != null) {
                seguroVidaProj = seguroVidaParamProj.getValue();
                lastSeguroVida = seguroVidaProj;
            } else {
                seguroVidaProj = lastSeguroVida;
            }
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double seguroVidaAmount = proportion * seguroVidaProj;
            MonthProjection seguroVidaProjection = new MonthProjection();
            seguroVidaProjection.setMonth(projection.getMonth());
            seguroVidaProjection.setAmount(BigDecimal.valueOf(seguroVidaAmount));
            projections.add(seguroVidaProjection);
        }
        seguroVidaComponent.setProjections(projections);

        // Add the "Seguro Vida" component to the component list
        component.add(seguroVidaComponent);
    }

  public void provisionSistemasComplementariosIAS(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        PaymentComponentDTO provRetiroIASComponent = new PaymentComponentDTO();
        provRetiroIASComponent.setPaymentComponent("PROV_RETIRO_IAS");
        if ( totalSalaries > 0 ) {
          Map<String, ParametersDTO> provisionSistemasComplementariosIASMap = new ConcurrentHashMap<>();
          Map<String, Double> cache = new ConcurrentHashMap<>();
          createCache(parameters, provisionSistemasComplementariosIASMap, cache, (parameter, mapParameter) -> {});
          Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
          PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
          // Get the "Prov Retiro (IAS)" amount from parameters
        /*ParametersDTO provRetiroIASParam = parameters.stream()
            .filter(p -> p.getParameter().getDescription().equals("Prov Retiro (IAS)"))
            .findFirst()
            .orElse(null);
        double provRetiroIAS = provRetiroIASParam == null ? 0.0 : provRetiroIASParam.getValue();*/
          // Create a new PaymentComponentDTO for "Prov Retiro (IAS)"
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
          YearMonth yearMonth = YearMonth.parse(period, formatter);
          yearMonth = yearMonth.plusMonths(1);
          String nextPeriod = yearMonth.format(formatter);
          ParametersDTO provRetiroIASParamNext = provisionSistemasComplementariosIASMap.get(nextPeriod);
          double provRetiroIASNext = provRetiroIASParamNext == null ? 0.0 : provRetiroIASParamNext.getValue();
          provRetiroIASComponent.setAmount(BigDecimal.valueOf((componentMap.get("SALARY").getAmount().doubleValue() / totalSalaries) * provRetiroIASNext));
          // Apply the formula for each month of projection
          List<MonthProjection> projections = new ArrayList<>();
          double lastProvRetiroIAS = provRetiroIASNext;
          for (MonthProjection projection : salaryComponent.getProjections()) {
              ParametersDTO provRetiroIASParamProj = provisionSistemasComplementariosIASMap.get(projection.getMonth());
              double provRetiroIASProj;
              if (provRetiroIASParamProj != null) {
                  provRetiroIASProj = provRetiroIASParamProj.getValue();
                  lastProvRetiroIAS = provRetiroIASProj;
              } else {
                  provRetiroIASProj = lastProvRetiroIAS;
              }
              double proportion = projection.getAmount().doubleValue() / totalSalaries;
              double provRetiroIASAmount = proportion * provRetiroIASProj;
              MonthProjection provRetiroIASProjection = new MonthProjection();
              provRetiroIASProjection.setMonth(projection.getMonth());
              provRetiroIASProjection.setAmount(BigDecimal.valueOf(provRetiroIASAmount));
              projections.add(provRetiroIASProjection);
          }
          provRetiroIASComponent.setProjections(projections);
        }else {
          provRetiroIASComponent.setAmount(BigDecimal.valueOf(0.0));
          provRetiroIASComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
        }
        // Add the "Prov Retiro (IAS)" component to the component list
        component.add(provRetiroIASComponent);
    }
    public void SGMM (List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, String poName, double totalSalaries) {
        PaymentComponentDTO SGMMComponent = new PaymentComponentDTO();
        SGMMComponent.setPaymentComponent("SGMM");
        if(totalSalaries > 0) {
            Map<String, ParametersDTO> SGMMMap = new ConcurrentHashMap<>();
            Map<String, Double> cache = new ConcurrentHashMap<>();
            createCache(parameters, SGMMMap, cache, (parameter, mapParameter) -> {});
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            // Get the "SGMM" amount from parameters
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO SGMMParam = SGMMMap.get(nextPeriod);

            double SGMM = SGMMParam == null ? 0.0 : SGMMParam.getValue();
            boolean isCp = poName != null && poName.contains("CP");

            if (!isCp) {
                // Create a new PaymentComponentDTO for "SGMM"
                SGMMComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * SGMM));
                // Apply the formula for each month of projection
                List<MonthProjection> projections = new ArrayList<>();
                double lastSGMM = SGMMComponent.getAmount().doubleValue();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    ParametersDTO SGMMParamProj = SGMMMap.get(projection.getMonth());
                    double SGMMProj;
                    if (SGMMParamProj != null) {
                        SGMMProj = SGMMParamProj.getValue();
                        lastSGMM = SGMMProj;
                    } else {
                        SGMMProj = lastSGMM;
                    }
                    double proportion = projection.getAmount().doubleValue() / totalSalaries;
                    double SGMMAmount = proportion * SGMMProj;
             /*   if(SGMMParamProj != null) {
                    SGMMAmount = proportion * SGMMProj;
                    lastSGMM = SGMMAmount;
                }else {
                    SGMMAmount = lastSGMM;
                }*/
                    MonthProjection SGMMProjection = new MonthProjection();
                    SGMMProjection.setMonth(projection.getMonth());
                    SGMMProjection.setAmount(BigDecimal.valueOf(SGMMAmount));
                    projections.add(SGMMProjection);
                }
                SGMMComponent.setProjections(projections);
                // Add the "SGMM" component to the component list
            }else {
                //ZERO
                SGMMComponent.setAmount(BigDecimal.valueOf(0.0));
                SGMMComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            }
        }else {
            //ZERO
            SGMMComponent.setAmount(BigDecimal.valueOf(0.0));
            SGMMComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
        }
        component.add(SGMMComponent);
    }

    public void valesDeDespensa(List<PaymentComponentDTO> component, List<ParametersDTO> valeList, List<ParametersDTO> umaList, String period, Integer range) {
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de salario
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        // Verificar si el componente de salario existe
        if (salaryComponent != null) {
            // Obtener los parámetros
            Map<String, ParametersDTO> topeValesDespensaMap = new ConcurrentHashMap<>();
            Map<String, Double> topeValesDespensaCache = new ConcurrentHashMap<>();
            createCache(valeList, topeValesDespensaMap, topeValesDespensaCache, (parameter, mapParameter) -> {});
            Map<String, ParametersDTO> umaMensualMap = new ConcurrentHashMap<>();
            Map<String, Double> umaMensualCache = new ConcurrentHashMap<>();
            createCache(umaList, umaMensualMap, umaMensualCache, (parameter, mapParameter) -> {});
            //buscar le parametro para el periodo siguiente
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO topeValesDespensaParamBase = topeValesDespensaMap.get(nextPeriod);
            ParametersDTO umaMensualParamBase = umaMensualMap.get(nextPeriod);
            double topeValesDespensaBase = topeValesDespensaParamBase == null ? 0.0 : topeValesDespensaParamBase.getValue()/100;
            double umaMensualBase = umaMensualParamBase == null ? 0.0 : umaMensualParamBase.getValue();
            double valesDeDespensaBase = salaryComponent.getAmount().doubleValue() * topeValesDespensaBase;
            if (valesDeDespensaBase > umaMensualBase) {
                valesDeDespensaBase = umaMensualBase;
            }
            // Crear un nuevo PaymentComponentDTO para "VALES_DESPENSA"
            PaymentComponentDTO valesDeDespensaComponent = new PaymentComponentDTO();
            valesDeDespensaComponent.setPaymentComponent("VALES_DESPENSA");
            valesDeDespensaComponent.setAmount(BigDecimal.valueOf(valesDeDespensaBase));
            List<MonthProjection> projections = new ArrayList<>();
            // Calcular el vales de despensa para cada mes de la proyección
            double lastValesDeDespensa = valesDeDespensaBase;
            double lastUmaMensual = umaMensualBase;
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double baseSalary = projection.getAmount().doubleValue();
                ParametersDTO topeValesDespensaParam = topeValesDespensaMap.get(projection.getMonth());
                ParametersDTO umaMensualParam = umaMensualMap.get(projection.getMonth());
                // Calcular el vales de despensa
                double topeValesDespensa;
                if (topeValesDespensaParam == null) {
                    topeValesDespensa = lastValesDeDespensa;
                } else {
                    topeValesDespensa = topeValesDespensaParam.getValue() / 100;
                    lastValesDeDespensa = topeValesDespensa;
                }

                double umaMensual;
                if (umaMensualParam == null) {
                    umaMensual = lastUmaMensual;
                } else {
                    umaMensual = umaMensualParam.getValue();
                    lastUmaMensual = umaMensual;
                }

                double valesDeDespensa = baseSalary * topeValesDespensa;

                // Comparar con UMA mensual
                if (valesDeDespensa > umaMensual) {
                    valesDeDespensa = umaMensual;
                }

                MonthProjection valesDeDespensaProjection = new MonthProjection();
                valesDeDespensaProjection.setMonth(projection.getMonth());
                valesDeDespensaProjection.setAmount(BigDecimal.valueOf(valesDeDespensa));
                projections.add(valesDeDespensaProjection);
            }
            valesDeDespensaComponent.setProjections(projections);
            // Agregar el componente "VALES_DESPENSA" a la lista de componentes
            component.add(valesDeDespensaComponent);
        }else {
            //ZERO
            PaymentComponentDTO valesDeDespensaComponent = new PaymentComponentDTO();
            valesDeDespensaComponent.setPaymentComponent("VALES_DESPENSA");
            valesDeDespensaComponent.setAmount(BigDecimal.valueOf(0.0));
            valesDeDespensaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(valesDeDespensaComponent);
        }
    }
    public void performanceBonus(List<PaymentComponentDTO> component, String poName, String convenioBono, String period, Integer range, Map<String, ConvenioBono> convenioBonoCache) {
        /*log.debug("ConvenioBono: {}", convenioBono);
        log.debug("ConvenioBonoCache: {}", convenioBonoCache);*/
        //TODO: QUE PASA SI EL CONVENIO CAMBIA DE UN MES A OTRO
        ConvenioBono convenioBonoData = convenioBonoCache.get(convenioBono);
        //log.debug("ConvenioBono: {}", convenioBonoData);
        //log.debug("ConvenioBono: {}", convenioBonoData);
        if (convenioBonoData != null) {
            // Convert the component list to a map
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

            // Find the "SALARY" payment component in the map
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            //TODO:AGREGAR VALIDACION PARA CONVENIO TIPO C
            boolean isV = !convenioBonoData.getConvenioNivel().contains("V");
            // If the level is 'V', no bonus is applied
            if (isV) {
              /*  el porcentaje de bono que le corresponde se asigna de acuerdo a la combinación entre su convenio y su nivel, los cuales se encuentran definidos en la {tabla parámetrica %Bono} y este se aplica sobre el salario mensual de la posición.
                        el valor obtenido se divide entre 12, para proratear la cantidad              */
                // Get the bonus percentage from the ConvenioBono object
                double bonusPercent = convenioBonoData.getBonoPercentage() / 100.0;
                double monthlyBonusBase = salaryComponent.getAmount().doubleValue() * bonusPercent;

                // Create a new PaymentComponentDTO for the bonus
                PaymentComponentDTO bonusComponent = new PaymentComponentDTO();
                bonusComponent.setPaymentComponent("PERFORMANCE_BONUS");
                bonusComponent.setAmount(BigDecimal.valueOf(monthlyBonusBase));

                // Apply the formula for each month of projection
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    MonthProjection bonusProjection = new MonthProjection();
                    double monthlyBonus = projection.getAmount().doubleValue() * bonusPercent;
                    bonusProjection.setMonth(projection.getMonth());
                    bonusProjection.setAmount(BigDecimal.valueOf(monthlyBonus));
                    projections.add(bonusProjection);
                }
                bonusComponent.setProjections(projections);

                // Add the bonus component to the component list
                component.add(bonusComponent);
            } else {
                //ZERO
                PaymentComponentDTO bonusComponent = new PaymentComponentDTO();
                bonusComponent.setPaymentComponent("PERFORMANCE_BONUS");
                bonusComponent.setAmount(BigDecimal.valueOf(0.0));
                bonusComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(bonusComponent);
            }
        }else {
            //ZERO
            PaymentComponentDTO bonusComponent = new PaymentComponentDTO();
            bonusComponent.setPaymentComponent("PERFORMANCE_BONUS");
            bonusComponent.setAmount(BigDecimal.valueOf(0.0));
            bonusComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(bonusComponent);
        }
    }
    public void seguroSocial(List<PaymentComponentDTO> component, String convenioName, String period, Integer range, Map<String, Convenio> convenioCache) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        //log.debug("Convenio: {}", convenio);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social
        if (convenio != null) {
            double seguroSocial = convenio.getImssPercentage() / 100.0;
            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social
                PaymentComponentDTO seguroSocialComponent = new PaymentComponentDTO();
                seguroSocialComponent.setPaymentComponent("IMSS");
                seguroSocialComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * seguroSocial));
                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialCost = projection.getAmount().doubleValue() * seguroSocial;
                    MonthProjection seguroSocialProjection = new MonthProjection();
                    seguroSocialProjection.setMonth(projection.getMonth());
                    seguroSocialProjection.setAmount(BigDecimal.valueOf(seguroSocialCost));
                    projections.add(seguroSocialProjection);
                }
                seguroSocialComponent.setProjections(projections);

                // Añade el componente de Seguro Social a la lista de componentes
                component.add(seguroSocialComponent);
            }else {
                //ZERO
                PaymentComponentDTO seguroSocialComponent = new PaymentComponentDTO();
                seguroSocialComponent.setPaymentComponent("IMSS");
                seguroSocialComponent.setAmount(BigDecimal.valueOf(0.0));
                seguroSocialComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(seguroSocialComponent);
            }
        }else {
            //ZERO
            PaymentComponentDTO seguroSocialComponent = new PaymentComponentDTO();
            seguroSocialComponent.setPaymentComponent("IMSS");
            seguroSocialComponent.setAmount(BigDecimal.valueOf(0.0));
            seguroSocialComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(seguroSocialComponent);
        }
    }
    public void seguroSocialRetiro(List<PaymentComponentDTO> component, String convenioName, String period, Integer range, Map<String, Convenio> convenioCache) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social (Retiro)
        if (convenio != null) {
            double seguroSocialRetiro = convenio.getRetiroPercentage() / 100.0;
            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social (Retiro)
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social (Retiro)
                PaymentComponentDTO seguroSocialRetiroComponent = new PaymentComponentDTO();
                seguroSocialRetiroComponent.setPaymentComponent("IMSS_RETIRO");
                seguroSocialRetiroComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * seguroSocialRetiro));
                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialRetiroCost = projection.getAmount().doubleValue() * seguroSocialRetiro;
                    MonthProjection seguroSocialRetiroProjection = new MonthProjection();
                    seguroSocialRetiroProjection.setMonth(projection.getMonth());
                    seguroSocialRetiroProjection.setAmount(BigDecimal.valueOf(seguroSocialRetiroCost));
                    projections.add(seguroSocialRetiroProjection);
                }
                seguroSocialRetiroComponent.setProjections(projections);

                // Añade el componente de Seguro Social (Retiro) a la lista de componentes
                component.add(seguroSocialRetiroComponent);
            } else {
                //ZERO
                PaymentComponentDTO seguroSocialRetiroComponent = new PaymentComponentDTO();
                seguroSocialRetiroComponent.setPaymentComponent("IMSS_RETIRO");
                seguroSocialRetiroComponent.setAmount(BigDecimal.valueOf(0.0));
                seguroSocialRetiroComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(seguroSocialRetiroComponent);
            }
        } else {
            //ZERO
            PaymentComponentDTO seguroSocialRetiroComponent = new PaymentComponentDTO();
            seguroSocialRetiroComponent.setPaymentComponent("IMSS_RETIRO");
            seguroSocialRetiroComponent.setAmount(BigDecimal.valueOf(0.0));
            seguroSocialRetiroComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(seguroSocialRetiroComponent);
        }
    }
    public void seguroSocialInfonavit(List<PaymentComponentDTO> component, String convenioName, String period, Integer range, Map<String, Convenio> convenioCache) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social (Infonavit)
        if (convenio != null) {
            //double seguroSocialInfonavit = convenio.getInfonavitPercentage();
            double seguroSocialInfonavit = convenio.getInfonavitPercentage() / 100.0;
            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social (Infonavit)
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social (Infonavit)
                PaymentComponentDTO seguroSocialInfonavitComponent = new PaymentComponentDTO();
                seguroSocialInfonavitComponent.setPaymentComponent("IMSS_INFONAVIT");
                seguroSocialInfonavitComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * seguroSocialInfonavit));
                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialInfonavitCost = projection.getAmount().doubleValue() * seguroSocialInfonavit;
                    MonthProjection seguroSocialInfonavitProjection = new MonthProjection();
                    seguroSocialInfonavitProjection.setMonth(projection.getMonth());
                    seguroSocialInfonavitProjection.setAmount(BigDecimal.valueOf(seguroSocialInfonavitCost));
                    projections.add(seguroSocialInfonavitProjection);
                }
                seguroSocialInfonavitComponent.setProjections(projections);

                // Añade el componente de Seguro Social (Infonavit) a la lista de componentes
                component.add(seguroSocialInfonavitComponent);
            } else {
                //ZERO
                PaymentComponentDTO seguroSocialInfonavitComponent = new PaymentComponentDTO();
                seguroSocialInfonavitComponent.setPaymentComponent("IMSS_INFONAVIT");
                seguroSocialInfonavitComponent.setAmount(BigDecimal.valueOf(0.0));
                seguroSocialInfonavitComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(seguroSocialInfonavitComponent);
            }
        } else {
            //ZERO
            PaymentComponentDTO seguroSocialInfonavitComponent = new PaymentComponentDTO();
            seguroSocialInfonavitComponent.setPaymentComponent("IMSS_INFONAVIT");
            seguroSocialInfonavitComponent.setAmount(BigDecimal.valueOf(0.0));
            seguroSocialInfonavitComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(seguroSocialInfonavitComponent);
        }
    }

    /**
     * Calcula la prima vacacional :
     * Para el cálculo de la Provisión prima vacacional, se requiere el monto mensual calculado para la posición en el concepto 'Provisión Vacaciones' y el parámetro {%Provision Prima Vacacional}.
     * El {%Provision Prima Vacacional} ingresado en parámetros, se mantiene invariable hasta que se actualiza el valor de éste desde el periodo que se indique.
     * El costo de provisión prima vacacional para la posición se calcula mediante la multiplicación del monto mensual obtenido en 'Provisión Vacaciones' por {%Provision Prima Vacacional}.
     * @param component: baseline data
     * @param parameters : provision prima vacacional
     * @param period : initial month
     * @param range : number of months to projected in the simulation
        */
    public void primaVacacional(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, ParametersDTO> provisionPrimaVacacionalMap = new ConcurrentHashMap<>();
        Map<String, Double> cache = new ConcurrentHashMap<>();
        createCache(parameters, provisionPrimaVacacionalMap, cache, (parameter, mapParameter) -> {});
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de 'Provisión Vacaciones'
        PaymentComponentDTO provisionVacacionesComponent = componentMap.get("VACACIONES");

        // Verificar si el componente de 'Provisión Vacaciones' existe
        if (provisionVacacionesComponent != null) {
            // Obtener el parámetro '{%Provision Prima Vacacional}'
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);

            ParametersDTO provisionPrimaVacacionalParam = provisionPrimaVacacionalMap.get(nextPeriod) ;
            double provisionPrimaVacacionalBase = provisionPrimaVacacionalParam == null ? 0.0 : provisionPrimaVacacionalParam.getValue() / 100.0;
            double primaVacacionalBase = provisionVacacionesComponent.getAmount().doubleValue() * provisionPrimaVacacionalBase;

            // Crear un nuevo PaymentComponentDTO para 'PRIMA_VACACIONAL'
            PaymentComponentDTO primaVacacionalComponent = new PaymentComponentDTO();
            primaVacacionalComponent.setPaymentComponent("PRIMA_VACACIONAL");
            primaVacacionalComponent.setAmount(BigDecimal.valueOf(primaVacacionalBase));

            List<MonthProjection> projections = new ArrayList<>();
            // Calcular la prima vacacional para cada mes de la proyección
            double lastPrimaVacacional = primaVacacionalBase;
            for (MonthProjection projection : provisionVacacionesComponent.getProjections()) {
                ParametersDTO provisionPrimaVacacionalParamProj = provisionPrimaVacacionalMap.get(projection.getMonth());
                double provisionPrimaVacacionalProj;
                if (provisionPrimaVacacionalParamProj != null) {
                    provisionPrimaVacacionalProj = provisionPrimaVacacionalParamProj.getValue() / 100.0;
                    lastPrimaVacacional = provisionPrimaVacacionalProj;
                } else {
                    provisionPrimaVacacionalProj = lastPrimaVacacional;
                }
                double provisionVacaciones = projection.getAmount().doubleValue();
                double primaVacacional = provisionVacaciones * provisionPrimaVacacionalProj;
                /*double primaVacacional;
                if (provisionPrimaVacacionalParamProj != null) {
                    primaVacacional = provisionVacaciones * provisionPrimaVacacionalProj;
                    lastPrimaVacacional = primaVacacional;
                }else {
                    primaVacacional = lastPrimaVacacional;
                }*/
                MonthProjection primaVacacionalProjection = new MonthProjection();
                primaVacacionalProjection.setMonth(projection.getMonth());
                primaVacacionalProjection.setAmount(BigDecimal.valueOf(primaVacacional));
                projections.add(primaVacacionalProjection);
            }
            primaVacacionalComponent.setProjections(projections);

            // Agregar el componente 'PRIMA_VACACIONAL' a la lista de componentes
            component.add(primaVacacionalComponent);
        } else {
            //ZERO
            PaymentComponentDTO primaVacacionalComponent = new PaymentComponentDTO();
            primaVacacionalComponent.setPaymentComponent("PRIMA_VACACIONAL");
            primaVacacionalComponent.setAmount(BigDecimal.valueOf(0.0));
            primaVacacionalComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(primaVacacionalComponent);
        }
    }
    public void aportacionCtaSEREmpresa(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, String poName, LocalDate birthDate, LocalDate hiringDate) {
        Map<String, ParametersDTO>  parametersMap = new ConcurrentHashMap<>();
        Map<String, Double> cache = new ConcurrentHashMap<>();
        createCache(parameters, parametersMap, cache, (parameter, mapParameter) -> {});
       // //log.debug("birthDate: {}", birthDate);
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de 'SALARY'
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        // Verificar si el componente de 'SALARY' existe
        if (salaryComponent != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            // Obtener el parámetro '{%Aporte Cta SER empresa}'
            ParametersDTO aporteCtaSEREmpresaParamBase = parametersMap.get(nextPeriod);
            double aporteCtaSEREmpresaBase = aporteCtaSEREmpresaParamBase == null ? 0.0 : aporteCtaSEREmpresaParamBase.getValue() / 100.0;
            double aporteCtaSEREmpresaAmountBase = 0.0;
            int age = 0;
            if (birthDate != null) {
                age = Period.between(birthDate, LocalDate.now()).getYears();
            }
            int seniority = 0;
            if (hiringDate != null) {
                seniority = Period.between(hiringDate, LocalDate.now()).getYears();
            }
            if (age != 0){
                boolean isCp = poName != null && poName.contains("CP");
                // Verificar el título del puesto y la edad del empleado
                if (!isCp && (age >= 30 || seniority >= 3)) {
                    aporteCtaSEREmpresaAmountBase = salaryComponent.getAmount().doubleValue() * aporteCtaSEREmpresaBase;
                }
                // Crear un nuevo PaymentComponentDTO para 'APORTACION_CTA_SER_EMPRESA'
                PaymentComponentDTO aportacionCtaSEREmpresaComponent = new PaymentComponentDTO();
                aportacionCtaSEREmpresaComponent.setPaymentComponent("APORTACION_CTA_SER_EMPRESA");
                aportacionCtaSEREmpresaComponent.setAmount(BigDecimal.valueOf(aporteCtaSEREmpresaAmountBase));
                List<MonthProjection> projections = new ArrayList<>();
                // Calcular la aportación a la cuenta SER de la empresa para cada mes de la proyección
                double lastAporteCtaSEREmpresaParam= aporteCtaSEREmpresaBase;
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    // Parse the month from the projection
                    DateTimeFormatter formatterProj = DateTimeFormatter.ofPattern("yyyyMM");
                    LocalDate projectionDate = YearMonth.parse(projection.getMonth(), formatterProj).atEndOfMonth();

                    // Calculate age and seniority for the projection month
                    int ageProj = birthDate != null ? Period.between(birthDate, projectionDate).getYears() : 0;
                    int seniorityProj = hiringDate != null ? Period.between(hiringDate, projectionDate).getYears() : 0;

                    double amount;
                    if (!isCp && (ageProj >= 30 || seniorityProj >= 3)) {
                        double baseSalary = projection.getAmount().doubleValue();
                        ParametersDTO aporteCtaSEREmpresaParam = parametersMap.get(projection.getMonth());
                        double aportacionCtaSEREmpresa;
                        if (aporteCtaSEREmpresaParam != null) {
                            aportacionCtaSEREmpresa = aporteCtaSEREmpresaParam.getValue() / 100.0;
                            lastAporteCtaSEREmpresaParam = aportacionCtaSEREmpresa;
                        } else {
                            aportacionCtaSEREmpresa = lastAporteCtaSEREmpresaParam;
                        }
                        amount = baseSalary * aportacionCtaSEREmpresa;
                    }else {
                        amount = 0.0;
                    }
                    MonthProjection aportacionCtaSEREmpresaProjection = new MonthProjection();
                    aportacionCtaSEREmpresaProjection.setMonth(projection.getMonth());
                    aportacionCtaSEREmpresaProjection.setAmount(BigDecimal.valueOf(amount));
                    projections.add(aportacionCtaSEREmpresaProjection);
                }
                aportacionCtaSEREmpresaComponent.setProjections(projections);
                // Agregar el componente 'APORTACION_CTA_SER_EMPRESA' a la lista de componentes
                component.add(aportacionCtaSEREmpresaComponent);
            }else {
                //ZERO
                PaymentComponentDTO aportacionCtaSEREmpresaComponent = new PaymentComponentDTO();
                aportacionCtaSEREmpresaComponent.setPaymentComponent("APORTACION_CTA_SER_EMPRESA");
                aportacionCtaSEREmpresaComponent.setAmount(BigDecimal.valueOf(0.0));
                aportacionCtaSEREmpresaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
                component.add(aportacionCtaSEREmpresaComponent);
            }
        }else {
            //ZERO
            PaymentComponentDTO aportacionCtaSEREmpresaComponent = new PaymentComponentDTO();
            aportacionCtaSEREmpresaComponent.setPaymentComponent("APORTACION_CTA_SER_EMPRESA");
            aportacionCtaSEREmpresaComponent.setAmount(BigDecimal.valueOf(0.0));
            aportacionCtaSEREmpresaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(aportacionCtaSEREmpresaComponent);
        }
    }
    /* Para el cálculo del concepto Provisión Aguinaldo Cta SER se requiere el monto mensual calculado en el Concepto 'Aportación Cta SER empresa', el salario base mensual de la posición y los días indicados en el parámetro {Días Provisión aguinaldo}.
        Los {Días Provisión aguinaldo} ingresados en parámetros, se mantiene invariable hasta que se actualiza el valor de estos desde el periodo que se indique.

        Cuando el costo mensual del concepto 'Aportación Cta SER empresa' de la posición es > 0:
        El costo para la posición equivale al salario base mensual dividido en 30 para llevarlo a un valor diario, y multiplicarlo por la cantidad de días indicados en {Días Provisión aguinaldo}.
        Si el costo del concepto 'Aportación Cta SER empresa' es = 0, el costo para la posición es 0. */
    public void provisionAguinaldoCtaSER(List<PaymentComponentDTO> component, List<ParametersDTO> diasProvAguinaldo, String period, Integer range) {
        Map<String, ParametersDTO> provisionAguinaldoCtaSERMap = new ConcurrentHashMap<>();
        Map<String, Double> cache = new ConcurrentHashMap<>();
        createCache(diasProvAguinaldo, provisionAguinaldoCtaSERMap, cache, (parameter, mapParameter) -> {});

        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        if (salaryComponent != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO diasProvisionAguinaldoParam = provisionAguinaldoCtaSERMap.get(nextPeriod);
            double diasProvisionAguinaldoBase = diasProvisionAguinaldoParam == null ? 0.0 : diasProvisionAguinaldoParam.getValue();
            //log.debug("diasProvisionAguinaldoParam: {}", diasProvisionAguinaldoParam);
            PaymentComponentDTO aportacionCtaSEREmpresaComponent = componentMap.get("APORTACION_CTA_SER_EMPRESA");
            double provisionAguinaldoCtaSERBase = aportacionCtaSEREmpresaComponent != null ? aportacionCtaSEREmpresaComponent.getAmount().doubleValue() : 0.0;

            double dailyBaseSalary = salaryComponent.getAmount().doubleValue() / 30;
            double provisionAguinaldoCtaSERAmountBase = dailyBaseSalary * diasProvisionAguinaldoBase;
            PaymentComponentDTO provisionAguinaldoCtaSERComponent = new PaymentComponentDTO();
            provisionAguinaldoCtaSERComponent.setPaymentComponent("PROVISION_AGUINALDO_CTA_SER");
            provisionAguinaldoCtaSERComponent.setAmount(BigDecimal.valueOf(aportacionCtaSEREmpresaComponent != null && provisionAguinaldoCtaSERBase > 0 ? provisionAguinaldoCtaSERAmountBase : 0.0));
            List<MonthProjection> projections = new ArrayList<>();
            double lastProvisionAguinaldoCtaSER = provisionAguinaldoCtaSERAmountBase;
            if (aportacionCtaSEREmpresaComponent != null && provisionAguinaldoCtaSERBase > 0) {
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    //APORTACION_CTA_SER_EMPRESA per month
                    ParametersDTO provisionAguinaldoCtaSERParam = provisionAguinaldoCtaSERMap.get(projection.getMonth());
                    //dias de provision aguinaldo
                    double provisionAguinaldoCtaSER;
                    if (provisionAguinaldoCtaSERParam != null) {
                        provisionAguinaldoCtaSER = provisionAguinaldoCtaSERParam.getValue();
                        lastProvisionAguinaldoCtaSER = provisionAguinaldoCtaSER;
                    } else {
                        provisionAguinaldoCtaSER = lastProvisionAguinaldoCtaSER;
                    }
                    double baseSalary = projection.getAmount().doubleValue();
                    double provisionAguinaldoCtaSERAmount = (baseSalary / 30) * (provisionAguinaldoCtaSER / 12);
                    /*double provisionAguinaldoCtaSERAmount;
                    if(provisionAguinaldoCtaSERParam != null){
                        provisionAguinaldoCtaSERAmount = (baseSalary / 30) * (provisionAguinaldoCtaSER / 12);
                        lastProvisionAguinaldoCtaSER = provisionAguinaldoCtaSERAmount;
                    }else {
                        provisionAguinaldoCtaSERAmount = lastProvisionAguinaldoCtaSER;
                    }*/
                    MonthProjection provisionAguinaldoCtaSERProjection = new MonthProjection();
                    provisionAguinaldoCtaSERProjection.setMonth(projection.getMonth());
                    provisionAguinaldoCtaSERProjection.setAmount(BigDecimal.valueOf(provisionAguinaldoCtaSERAmount));
                    projections.add(provisionAguinaldoCtaSERProjection);
                }
            }else {
                projections = Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0));
            }
            provisionAguinaldoCtaSERComponent.setProjections(projections);
            component.add(provisionAguinaldoCtaSERComponent);
        }else {
            //ZERO
            PaymentComponentDTO provisionAguinaldoCtaSERComponent = new PaymentComponentDTO();
            provisionAguinaldoCtaSERComponent.setPaymentComponent("PROVISION_AGUINALDO_CTA_SER");
            provisionAguinaldoCtaSERComponent.setAmount(BigDecimal.valueOf(0.0));
            provisionAguinaldoCtaSERComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(provisionAguinaldoCtaSERComponent);
        }
    }
    public void provisionPrimaVacacionalSER(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, ParametersDTO> provisionPrimaVacacionalSERMap = new ConcurrentHashMap<>();
        Map<String, Double> cache = new ConcurrentHashMap<>();
        createCache(parameters, provisionPrimaVacacionalSERMap, cache, (parameter, mapParameter) -> {});

        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO provisionAguinaldoCtaSERComponent = componentMap.get("PROVISION_AGUINALDO_CTA_SER");
        PaymentComponentDTO provisionVacacionesComponent = componentMap.get("VACACIONES");
        if (provisionAguinaldoCtaSERComponent != null && provisionAguinaldoCtaSERComponent.getAmount().doubleValue() > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO provisionPrimaVacacionalSERParam = provisionPrimaVacacionalSERMap.get(nextPeriod);
            double provisionPrimaVacacionalSERParamValue = provisionPrimaVacacionalSERParam == null ? 0.0 : provisionPrimaVacacionalSERParam.getValue() / 100.0;
            PaymentComponentDTO provisionPrimaVacacionalSERComponent = new PaymentComponentDTO();
            provisionPrimaVacacionalSERComponent.setPaymentComponent("PROVISION_PRIMA_VACACIONAL_SER");
            List<MonthProjection> projections = new ArrayList<>();
            double provisionPrimaVacacionalSERBase;
            if (provisionVacacionesComponent.getAmount().doubleValue() > 0) {
                provisionPrimaVacacionalSERBase = provisionVacacionesComponent.getAmount().doubleValue() * provisionPrimaVacacionalSERParamValue;
                provisionPrimaVacacionalSERComponent.setAmount(BigDecimal.valueOf(provisionPrimaVacacionalSERBase));
                // Calculate the contribution for each month of the projection
                double lastProvisionPrimaVacacionalSER = provisionPrimaVacacionalSERBase;
                for (MonthProjection projection : provisionVacacionesComponent.getProjections()) {
                    ParametersDTO provisionPrimaVacacionalSERParamProj = provisionPrimaVacacionalSERMap.get(projection.getMonth());
                    double provisionPrimaVacacionalSERProj;
                    if (provisionPrimaVacacionalSERParamProj != null) {
                        provisionPrimaVacacionalSERProj = provisionPrimaVacacionalSERParamProj.getValue() / 100.0;
                        lastProvisionPrimaVacacionalSER = provisionPrimaVacacionalSERProj;
                    } else {
                        provisionPrimaVacacionalSERProj = lastProvisionPrimaVacacionalSER;
                    }

                    double provisionVacation = projection.getAmount().doubleValue();
                    double provisionPrimaVacacionalSER = provisionVacation * provisionPrimaVacacionalSERProj;

                    MonthProjection provisionPrimaVacacionalSERProjection = new MonthProjection();
                    provisionPrimaVacacionalSERProjection.setMonth(projection.getMonth());
                    provisionPrimaVacacionalSERProjection.setAmount(BigDecimal.valueOf(provisionPrimaVacacionalSER));
                    projections.add(provisionPrimaVacacionalSERProjection);
                }
                provisionPrimaVacacionalSERComponent.setProjections(projections);
            }else {
               provisionPrimaVacacionalSERComponent.setAmount(BigDecimal.valueOf(0.0));
               provisionPrimaVacacionalSERComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            }
            component.add(provisionPrimaVacacionalSERComponent);
        }else {
            //ZERO
            PaymentComponentDTO provisionPrimaVacacionalSERComponent = new PaymentComponentDTO();
            provisionPrimaVacacionalSERComponent.setPaymentComponent("PROVISION_PRIMA_VACACIONAL_SER");
            provisionPrimaVacacionalSERComponent.setAmount(BigDecimal.valueOf(0.0));
            provisionPrimaVacacionalSERComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(provisionPrimaVacacionalSERComponent);
        }
    }
    public void provisionFondoAhorro(List<PaymentComponentDTO> component, List<ParametersDTO> topeMensualFondoAhorro, List<ParametersDTO> topeSueldoFondoAhorro, List<ParametersDTO> umaMensual, List<ParametersDTO> provisionFondoAhorro, String period, Integer range) {
        Map<String, ParametersDTO> topeMensualFondoAhorroMap = new ConcurrentHashMap<>();
        Map<String, Double> topeMensualFondoAhorroCache = new ConcurrentHashMap<>();
        createCache(topeMensualFondoAhorro, topeMensualFondoAhorroMap, topeMensualFondoAhorroCache, (parameter, mapParameter) -> {});
        Map<String, ParametersDTO> topeSueldoFondoAhorroMap = new ConcurrentHashMap<>();
        Map<String, Double> topeSueldoFondoAhorroCache = new ConcurrentHashMap<>();
        createCache(topeSueldoFondoAhorro, topeSueldoFondoAhorroMap, topeSueldoFondoAhorroCache, (parameter, mapParameter) -> {});
        Map<String, ParametersDTO> umaMensualMap = new ConcurrentHashMap<>();
        Map<String, Double> umaMensualCache = new ConcurrentHashMap<>();
        createCache(umaMensual, umaMensualMap, umaMensualCache, (parameter, mapParameter) -> {});
        Map<String, ParametersDTO> provisionFondoAhorroMap = new ConcurrentHashMap<>();
        Map<String, Double> provisionFondoAhorroCache = new ConcurrentHashMap<>();
        createCache(provisionFondoAhorro, provisionFondoAhorroMap, provisionFondoAhorroCache, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        if (salaryComponent != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
            YearMonth yearMonth = YearMonth.parse(period, formatter);
            yearMonth = yearMonth.plusMonths(1);
            String nextPeriod = yearMonth.format(formatter);
            ParametersDTO umaMensualParam = umaMensualMap.get(nextPeriod);
            ParametersDTO topeMensualFondoParam = topeMensualFondoAhorroMap.get(nextPeriod);
            ParametersDTO topeSueldoMensualFondoParam = topeSueldoFondoAhorroMap.get(nextPeriod);
            ParametersDTO provisionFondoAhorroParam = provisionFondoAhorroMap.get(nextPeriod);
            double salary = salaryComponent.getAmount().doubleValue();
            double umaMensualBase = umaMensualParam == null ? 0.0 : umaMensualParam.getValue();
            double topeMensualFondoBase = topeMensualFondoParam == null ? (umaMensualBase / 30) * 1.3 * 30 : topeMensualFondoParam.getValue();
            double topeSueldoMensualFondoBase = topeSueldoMensualFondoParam == null ? topeMensualFondoBase / 1.3 : topeSueldoMensualFondoParam.getValue();
            double provisionFondoAhorroBase = provisionFondoAhorroParam == null ? 0.0 : provisionFondoAhorroParam.getValue() / 100;
            double provisionFondoAhorroAmountBase;
            PaymentComponentDTO provisionFondoAhorroComponent = new PaymentComponentDTO();
            provisionFondoAhorroComponent.setPaymentComponent("PROVISION_FONDO_AHORRO");
            if (salary > topeMensualFondoBase) {
                provisionFondoAhorroAmountBase = topeSueldoMensualFondoBase;
            } else {
                provisionFondoAhorroAmountBase = salary * provisionFondoAhorroBase;
            }
            provisionFondoAhorroComponent.setAmount(BigDecimal.valueOf(provisionFondoAhorroAmountBase));

            List<MonthProjection> projections = new ArrayList<>();
            double lastUmaMensual = umaMensualBase;
            double lastTopeMensualFondo = topeMensualFondoBase;
            double lastTopeSueldoMensualFondo = topeSueldoMensualFondoBase;
            double lastProvisionFondoAhorro = provisionFondoAhorroAmountBase;
            for (MonthProjection projection : salaryComponent.getProjections()) {
                ParametersDTO umaMensualParamProj = umaMensualMap.get(projection.getMonth());
                double umaMensualProj;
                if (umaMensualParamProj != null) {
                    umaMensualProj = umaMensualParamProj.getValue();
                    lastUmaMensual = umaMensualProj;
                } else {
                    umaMensualProj = lastUmaMensual;
                }

                ParametersDTO topeMensualFondoParamProj = topeMensualFondoAhorroMap.get(projection.getMonth());
                double topeMensualFondoProj;
                double dayliUma;
           /*     El {Tope mensual - fondo} se actualiza en base a la {UMA mensual}, en donde esta última se divide entre 30, se multiplica x 1,3 y se multiplica nuevamente por 30 (mostrar el parámetro con el cálculo hecho por default, con posibilidad de cambio).*/
                if (topeMensualFondoParamProj != null) {
                    dayliUma = (umaMensualProj / 30) * 1.3;
                    topeMensualFondoProj = dayliUma * 30;
                    lastTopeMensualFondo = topeMensualFondoProj;
                } else {
                    topeMensualFondoProj = lastTopeMensualFondo;
                }

                ParametersDTO topeSueldoMensualFondoParamProj = topeSueldoFondoAhorroMap.get(projection.getMonth());
                double topeMensualFondoSueldoProj;
                if (topeSueldoMensualFondoParamProj != null) {
                    topeMensualFondoSueldoProj = topeMensualFondoProj / 1.3;
                    lastTopeSueldoMensualFondo = topeMensualFondoSueldoProj;
                } else {
                    topeMensualFondoSueldoProj = lastTopeSueldoMensualFondo;
                }

                ParametersDTO provisionFondoAhorroParamProj = provisionFondoAhorroMap.get(projection.getMonth());
                double provisionFondoAhorroProj;
                if (provisionFondoAhorroParamProj != null) {
                    provisionFondoAhorroProj = provisionFondoAhorroParamProj.getValue() / 100.0;
                    lastProvisionFondoAhorro = provisionFondoAhorroProj;
                } else {
                    provisionFondoAhorroProj = lastProvisionFondoAhorro;
                }
                double salaryProj = projection.getAmount().doubleValue();

                double provisionFondoAhorroAmount;
                if (salaryProj > topeMensualFondoProj) {
                    provisionFondoAhorroAmount = topeMensualFondoSueldoProj;
                } else {
                    provisionFondoAhorroAmount = salaryProj * provisionFondoAhorroProj;
                }

                MonthProjection provisionFondoAhorroProjection = new MonthProjection();
                provisionFondoAhorroProjection.setMonth(projection.getMonth());
                provisionFondoAhorroProjection.setAmount(BigDecimal.valueOf(provisionFondoAhorroAmount));
                projections.add(provisionFondoAhorroProjection);
            }

            provisionFondoAhorroComponent.setProjections(projections);
            component.add(provisionFondoAhorroComponent);
        } else {
            PaymentComponentDTO provisionFondoAhorroComponent = new PaymentComponentDTO();
            provisionFondoAhorroComponent.setPaymentComponent("PROVISION_FONDO_AHORRO");
            provisionFondoAhorroComponent.setAmount(BigDecimal.valueOf(0.0));
            provisionFondoAhorroComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(provisionFondoAhorroComponent);
        }
    }
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
    //COMPENSACION
    public void compensacion(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("COMPENSACION_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue() / 100;
        PaymentComponentDTO compensacionComponent = new PaymentComponentDTO();
        compensacionComponent.setPaymentComponent("COMPENSACION");
        if (salaryComponent != null && salaryComponent.getAmount().doubleValue() > 0){
            compensacionComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastCompensation = proporciónMensualBase;
            for (MonthProjection compeProjection : salaryComponent.getProjections()) {
                double monthlyCompensation = compeProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(compeProjection.getMonth());
                double monthlyProportionValue;
                if(monthlyProportion != null){
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastCompensation = monthlyProportionValue;
                }else {
                    monthlyProportionValue = lastCompensation;
                }
                double compensation = monthlyCompensation * monthlyProportionValue;
              /*  double compensation;
                if(monthlyProportion != null){
                    compensation = monthlyCompensation * monthlyProportionValue;
                    lastCompensation = compensation;
                }else {
                    compensation = lastCompensation;
                }*/
                MonthProjection compensationProjection = new MonthProjection();
                compensationProjection.setMonth(compeProjection.getMonth());
                compensationProjection.setAmount(BigDecimal.valueOf(compensation));
                projections.add(compensationProjection);
            }
            compensacionComponent.setProjections(projections);
        } else {
            compensacionComponent.setAmount(BigDecimal.ZERO);
            compensacionComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(compensacionComponent);
    }
    //disponibilidadComponent
    public void disponibilidad(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {});
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("DISPONIBILIDAD_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue() / 100;
        PaymentComponentDTO disponibilidadComponent = new PaymentComponentDTO();
        disponibilidadComponent.setPaymentComponent("DISPONIBILIDAD");
        if (salaryComponent != null) {
            disponibilidadComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastDisponibilidad = proporciónMensualBase;
            for (MonthProjection dispoProjection : salaryComponent.getProjections()) {
                double monthlyDisponibilidad = dispoProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(dispoProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastDisponibilidad = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastDisponibilidad;
                }
                double disponibilidad = monthlyDisponibilidad * monthlyProportionValue;
                /*double disponibilidad;
                if (monthlyProportion != null) {
                    disponibilidad = monthlyDisponibilidad * monthlyProportionValue;
                    lastDisponibilidad = disponibilidad;
                } else {
                    disponibilidad = lastDisponibilidad;
                }*/
                MonthProjection disponibilidadProjection = new MonthProjection();
                disponibilidadProjection.setMonth(dispoProjection.getMonth());
                disponibilidadProjection.setAmount(BigDecimal.valueOf(disponibilidad));
                projections.add(disponibilidadProjection);
            }
            disponibilidadComponent.setProjections(projections);
        } else {
            disponibilidadComponent.setAmount(BigDecimal.ZERO);
            disponibilidadComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(disponibilidadComponent);
    }
    //GRATIFICACION
    public void gratificacion(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("GRATIFICACION_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO gratificacionComponent = new PaymentComponentDTO();
        gratificacionComponent.setPaymentComponent("GRATIFICACION");
        if (salaryComponent != null) {
            gratificacionComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastGratificacion = salaryComponent.getAmount().doubleValue();
            for (MonthProjection gratProjection : salaryComponent.getProjections()) {
                double monthlyGratificacion = gratProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(gratProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastGratificacion = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastGratificacion;
                }
                double gratificacion = monthlyGratificacion * monthlyProportionValue;
                /*double gratificacion;
                if (monthlyProportion != null) {
                    gratificacion = monthlyGratificacion * monthlyProportionValue;
                    lastGratificacion = gratificacion;
                } else {
                    gratificacion = lastGratificacion;
                }*/
                MonthProjection gratificacionProjection = new MonthProjection();
                gratificacionProjection.setMonth(gratProjection.getMonth());
                gratificacionProjection.setAmount(BigDecimal.valueOf(gratificacion));
                projections.add(gratificacionProjection);
            }
            gratificacionComponent.setProjections(projections);
        } else {
            gratificacionComponent.setAmount(BigDecimal.ZERO);
            gratificacionComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(gratificacionComponent);
    }
    //GRATIFICACION_EXTRAORDINARIA
    public void gratificacionExtraordinaria(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("GRATIFICACION_EXTRAORDINARIA_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO gratificacionExtraordinariaComponent = new PaymentComponentDTO();
        gratificacionExtraordinariaComponent.setPaymentComponent("GRATIFICACION_EXTRAORDINARIA");
        if (salaryComponent != null) {
            gratificacionExtraordinariaComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastGratificacionExtraordinaria = salaryComponent.getAmount().doubleValue();
            for (MonthProjection gratExtraProjection : salaryComponent.getProjections()) {
                double monthlyGratificacionExtraordinaria = gratExtraProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(gratExtraProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastGratificacionExtraordinaria = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastGratificacionExtraordinaria;
                }
                double gratificacionExtraordinaria = monthlyGratificacionExtraordinaria * monthlyProportionValue;
                /*double gratificacionExtraordinaria;
                if (monthlyProportion != null) {
                    gratificacionExtraordinaria = monthlyGratificacionExtraordinaria * monthlyProportionValue;
                    lastGratificacionExtraordinaria = gratificacionExtraordinaria;
                } else {
                    gratificacionExtraordinaria = lastGratificacionExtraordinaria;
                }*/
                MonthProjection gratificacionExtraordinariaProjection = new MonthProjection();
                gratificacionExtraordinariaProjection.setMonth(gratExtraProjection.getMonth());
                gratificacionExtraordinariaProjection.setAmount(BigDecimal.valueOf(gratificacionExtraordinaria));
                projections.add(gratificacionExtraordinariaProjection);
            }
            gratificacionExtraordinariaComponent.setProjections(projections);
        } else {
            gratificacionExtraordinariaComponent.setAmount(BigDecimal.ZERO);
            gratificacionExtraordinariaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(gratificacionExtraordinariaComponent);
    }
    //TRABAJO_EXTENSO
    public void trabajoExtenso(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("TRABAJO_EXTENSO_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO trabajoExtensoComponent = new PaymentComponentDTO();
        trabajoExtensoComponent.setPaymentComponent("TRABAJO_EXTENSO");
        if (salaryComponent != null) {
            trabajoExtensoComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastTrabajoExtenso = salaryComponent.getAmount().doubleValue();
            for (MonthProjection trabajoExtensoProjection : salaryComponent.getProjections()) {
                double monthlyTrabajoExtenso = trabajoExtensoProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(trabajoExtensoProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastTrabajoExtenso = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastTrabajoExtenso;
                }
                double trabajoExtenso = monthlyTrabajoExtenso * monthlyProportionValue;
               /* double trabajoExtenso;
                if (monthlyProportion != null) {
                    trabajoExtenso = monthlyTrabajoExtenso * monthlyProportionValue;
                    lastTrabajoExtenso = trabajoExtenso;
                } else {
                    trabajoExtenso = lastTrabajoExtenso;
                }*/
                MonthProjection trabajoExtProjection = new MonthProjection();
                trabajoExtProjection.setMonth(trabajoExtensoProjection.getMonth());
                trabajoExtProjection.setAmount(BigDecimal.valueOf(trabajoExtenso));
                projections.add(trabajoExtProjection);
            }
            trabajoExtensoComponent.setProjections(projections);
        } else {
            trabajoExtensoComponent.setAmount(BigDecimal.ZERO);
            trabajoExtensoComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(trabajoExtensoComponent);
    }
    //TRABAJO_GRAVABLE
    public void trabajoGravable(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("TRABAJO_GRAVABLE_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO trabajoGravableComponent = new PaymentComponentDTO();
        trabajoGravableComponent.setPaymentComponent("TRABAJO_GRAVABLE");
        if (salaryComponent != null) {
            trabajoGravableComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastTrabajoGravable = salaryComponent.getAmount().doubleValue();
            for (MonthProjection trabajoGravableProjection : salaryComponent.getProjections()) {
                double monthlyTrabajoGravable = trabajoGravableProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(trabajoGravableProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastTrabajoGravable = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastTrabajoGravable;
                }
                double trabajoGravable = monthlyTrabajoGravable * monthlyProportionValue;
                /*double trabajoGravable;
                if (monthlyProportion != null) {
                    trabajoGravable = monthlyTrabajoGravable * monthlyProportionValue;
                    lastTrabajoGravable = trabajoGravable;
                } else {
                    trabajoGravable = lastTrabajoGravable;
                }*/
                MonthProjection trabajoGravProjection = new MonthProjection();
                trabajoGravProjection.setMonth(trabajoGravableProjection.getMonth());
                trabajoGravProjection.setAmount(BigDecimal.valueOf(trabajoGravable));
                projections.add(trabajoGravProjection);
            }
            trabajoGravableComponent.setProjections(projections);
        } else {
            trabajoGravableComponent.setAmount(BigDecimal.ZERO);
            trabajoGravableComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(trabajoGravableComponent);
    }
    //PARTE_EXENTA_FESTIVO_LABORADO
    public void parteExentaFestivoLaborado(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("PARTE_EXENTA_FESTIVO_LABORADO_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue() / 100;
        PaymentComponentDTO parteExentaFestivoLaboradoComponent = new PaymentComponentDTO();
        parteExentaFestivoLaboradoComponent.setPaymentComponent("PARTE_EXENTA_FESTIVO_LABORADO");
        if (salaryComponent != null) {
            parteExentaFestivoLaboradoComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastParteExentaFestivoLaborado = proporciónMensualBase;
            for (MonthProjection parteExentaFestivoLaboradoProjection : salaryComponent.getProjections()) {
                double monthlyParteExentaFestivoLaborado = parteExentaFestivoLaboradoProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(parteExentaFestivoLaboradoProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastParteExentaFestivoLaborado = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastParteExentaFestivoLaborado;
                }
                double parteExentaFestivoLaborado = monthlyParteExentaFestivoLaborado * monthlyProportionValue;
               /* double parteExentaFestivoLaborado;
                if (monthlyProportion != null) {
                    parteExentaFestivoLaborado = monthlyParteExentaFestivoLaborado * monthlyProportionValue;
                    lastParteExentaFestivoLaborado = parteExentaFestivoLaborado;
                } else {
                    parteExentaFestivoLaborado = lastParteExentaFestivoLaborado;
                }*/
                MonthProjection parteExentaFestLaboradoProjection = new MonthProjection();
                parteExentaFestLaboradoProjection.setMonth(parteExentaFestivoLaboradoProjection.getMonth());
                parteExentaFestLaboradoProjection.setAmount(BigDecimal.valueOf(parteExentaFestivoLaborado));
                projections.add(parteExentaFestLaboradoProjection);
            }
            parteExentaFestivoLaboradoComponent.setProjections(projections);
        } else {
            parteExentaFestivoLaboradoComponent.setAmount(BigDecimal.ZERO);
            parteExentaFestivoLaboradoComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(parteExentaFestivoLaboradoComponent);
    }
    //PARTE_GRAVABLE_FESTIVO_LABORADO
    public void parteGravableFestivoLaborado(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("PARTE_GRAVABLE_FESTIVO_LABORADO_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO parteGravableFestivoLaboradoComponent = new PaymentComponentDTO();
        parteGravableFestivoLaboradoComponent.setPaymentComponent("PARTE_GRAVABLE_FESTIVO_LABORADO");
        if (salaryComponent != null) {
            parteGravableFestivoLaboradoComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastParteGravableFestivoLaborado = proporciónMensualBase;
            for (MonthProjection parteGravableFestivoLaboradoProjection : salaryComponent.getProjections()) {
                double monthlyParteGravableFestivoLaborado = parteGravableFestivoLaboradoProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(parteGravableFestivoLaboradoProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastParteGravableFestivoLaborado = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastParteGravableFestivoLaborado;
                }
                double parteGravableFestivoLaborado = monthlyParteGravableFestivoLaborado * monthlyProportionValue;
                /*double parteGravableFestivoLaborado;
                if (monthlyProportion != null) {
                    parteGravableFestivoLaborado = monthlyParteGravableFestivoLaborado * monthlyProportionValue;
                    lastParteGravableFestivoLaborado = parteGravableFestivoLaborado;
                } else {
                    parteGravableFestivoLaborado = lastParteGravableFestivoLaborado;
                }*/
                MonthProjection parteGravFestLaboradoProjection = new MonthProjection();
                parteGravFestLaboradoProjection.setMonth(parteGravableFestivoLaboradoProjection.getMonth());
                parteGravFestLaboradoProjection.setAmount(BigDecimal.valueOf(parteGravableFestivoLaborado));
                projections.add(parteGravFestLaboradoProjection);
            }
            parteGravableFestivoLaboradoComponent.setProjections(projections);
        } else {
            parteGravableFestivoLaboradoComponent.setAmount(BigDecimal.ZERO);
            parteGravableFestivoLaboradoComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(parteGravableFestivoLaboradoComponent);
    }
    //PRIMA_DOMINICAL_GRAVABLE
    public void primaDominicalGravable(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("PRIMA_DOMINICAL_GRAVABLE_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO primaDominicalGravableComponent = new PaymentComponentDTO();
        primaDominicalGravableComponent.setPaymentComponent("PRIMA_DOMINICAL_GRAVABLE");
        if (salaryComponent != null) {
            primaDominicalGravableComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastPrimaDominicalGravable = salaryComponent.getAmount().doubleValue();
            for (MonthProjection primaDominicalGravableProjection : salaryComponent.getProjections()) {
                double monthlyPrimaDominicalGravable = primaDominicalGravableProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(primaDominicalGravableProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastPrimaDominicalGravable = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastPrimaDominicalGravable;
                }
                double primaDominicalGravable = monthlyPrimaDominicalGravable * monthlyProportionValue;
                /*double primaDominicalGravable;
                if (monthlyProportion != null) {
                    primaDominicalGravable = monthlyPrimaDominicalGravable * monthlyProportionValue;
                    lastPrimaDominicalGravable = primaDominicalGravable;
                } else {
                    primaDominicalGravable = lastPrimaDominicalGravable;
                }*/
                MonthProjection primaDominicalGravProjection = new MonthProjection();
                primaDominicalGravProjection.setMonth(primaDominicalGravableProjection.getMonth());
                primaDominicalGravProjection.setAmount(BigDecimal.valueOf(primaDominicalGravable));
                projections.add(primaDominicalGravProjection);
            }
            primaDominicalGravableComponent.setProjections(projections);
        } else {
            primaDominicalGravableComponent.setAmount(BigDecimal.ZERO);
            primaDominicalGravableComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(primaDominicalGravableComponent);
    }
    //MUDANZA
    public void mudanza(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("MUDANZA_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO mudanzaComponent = new PaymentComponentDTO();
        mudanzaComponent.setPaymentComponent("MUDANZA");
        if (salaryComponent != null) {
            mudanzaComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastMudanza = salaryComponent.getAmount().doubleValue();
            for (MonthProjection mudanzaProjection : salaryComponent.getProjections()) {
                double monthlyMudanza = mudanzaProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(mudanzaProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastMudanza = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastMudanza;
                }
                double mudanza = monthlyMudanza * monthlyProportionValue;
                /*double mudanza;
                if (monthlyProportion != null) {
                    mudanza = monthlyMudanza * monthlyProportionValue;
                    lastMudanza = mudanza;
                } else {
                    mudanza = lastMudanza;
                }*/
                MonthProjection mudanzaProjectionMonth = new MonthProjection();
                mudanzaProjectionMonth.setMonth(mudanzaProjection.getMonth());
                mudanzaProjectionMonth.setAmount(BigDecimal.valueOf(mudanza));
                projections.add(mudanzaProjectionMonth);
            }
            mudanzaComponent.setProjections(projections);
        } else {
            mudanzaComponent.setAmount(BigDecimal.ZERO);
            mudanzaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(mudanzaComponent);
    }
    //VIDA_CARA
    public void vidaCara(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("VIDA_CARA_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO vidaCaraComponent = new PaymentComponentDTO();
        vidaCaraComponent.setPaymentComponent("VIDA_CARA");
        if (salaryComponent != null) {
            vidaCaraComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastVidaCara = salaryComponent.getAmount().doubleValue();
            for (MonthProjection vidaCaraProjection : salaryComponent.getProjections()) {
                double monthlyVidaCara = vidaCaraProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(vidaCaraProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastVidaCara = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastVidaCara;
                }
                //double vidaCara = monthlyVidaCara * monthlyProportionValue;
                double vidaCara = monthlyVidaCara * monthlyProportionValue;
                /*if (monthlyProportion != null) {
                    vidaCara = monthlyVidaCara * monthlyProportionValue;
                    lastVidaCara = vidaCara;
                } else {
                    vidaCara = lastVidaCara;
                }*/
                MonthProjection vidaCaraProjectionMonth = new MonthProjection();
                vidaCaraProjectionMonth.setMonth(vidaCaraProjection.getMonth());
                vidaCaraProjectionMonth.setAmount(BigDecimal.valueOf(vidaCara));
                projections.add(vidaCaraProjectionMonth);
            }
            vidaCaraComponent.setProjections(projections);
        } else {
            vidaCaraComponent.setAmount(BigDecimal.ZERO);
            vidaCaraComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(vidaCaraComponent);
    }
    //PRIMA_DOMINICAL_EXENTA
    public void primaDominicalExenta(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range,  List<ParametersDTO> mothProportionParam) {
        Map<String, ParametersDTO> proporciónMensualMap = new HashMap<>();
        Map<String, Double> proporciónMensualCache = new HashMap<>();
        createCache(mothProportionParam, proporciónMensualMap, proporciónMensualCache, (parameter, mapParameter) -> {
        });
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("PRIMA_DOMINICAL_EXENTA_BASE");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO proporciónMensualParamBase = proporciónMensualMap.get(nextPeriod);
        double proporciónMensualBase = proporciónMensualParamBase == null ? 0.0 : proporciónMensualParamBase.getValue();
        PaymentComponentDTO primaDominicalExentaComponent = new PaymentComponentDTO();
        primaDominicalExentaComponent.setPaymentComponent("PRIMA_DOMINICAL_EXENTA");
        if (salaryComponent != null) {
            primaDominicalExentaComponent.setAmount(BigDecimal.valueOf(salaryComponent.getAmount().doubleValue() * proporciónMensualBase));
            List<MonthProjection> projections = new ArrayList<>();
            double lastPrimaMonthyProportion = proporciónMensualBase;
            for (MonthProjection primaDominicalExentaProjection : salaryComponent.getProjections()) {
                double monthlyPrimaDominicalExenta = primaDominicalExentaProjection.getAmount().doubleValue();
                ParametersDTO monthlyProportion = proporciónMensualMap.get(primaDominicalExentaProjection.getMonth());
                double monthlyProportionValue;
                if (monthlyProportion != null) {
                    monthlyProportionValue = monthlyProportion.getValue() / 100.0;
                    lastPrimaMonthyProportion = monthlyProportionValue;
                } else {
                    monthlyProportionValue = lastPrimaMonthyProportion;
                }
                double primaDominicalExenta = monthlyPrimaDominicalExenta * monthlyProportionValue;
               /* double primaDominicalExenta;
                if (monthlyProportion != null) {
                    primaDominicalExenta = monthlyPrimaDominicalExenta * monthlyProportionValue;
                    lastPrimaDominicalExenta = primaDominicalExenta;
                } else {
                    primaDominicalExenta = lastPrimaDominicalExenta;
                }*/
                MonthProjection primaDominicalExentaProjectionMonth = new MonthProjection();
                primaDominicalExentaProjectionMonth.setMonth(primaDominicalExentaProjection.getMonth());
                primaDominicalExentaProjectionMonth.setAmount(BigDecimal.valueOf(primaDominicalExenta));
                projections.add(primaDominicalExentaProjectionMonth);
            }
            primaDominicalExentaComponent.setProjections(projections);
        } else {
            primaDominicalExentaComponent.setAmount(BigDecimal.ZERO);
            primaDominicalExentaComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.ZERO));
        }
        component.add(primaDominicalExentaComponent);
    }
    public void calculateStateTax(List<PaymentComponentDTO> component, List<ParametersDTO> stateTaxParameter, String period, Integer range) {
        Map<String, ParametersDTO> stateTaxParameterMap = new HashMap<>();
        Map<String, Double> stateTaxParameterCache = new HashMap<>();
        createCache(stateTaxParameter, stateTaxParameterMap, stateTaxParameterCache, (parameter, mapParameter) -> {});
        /*Para el cálculo del concepto Impuesto estatal, se requiere el valor calculado anteriormente para 'Sueldo Base', 'Provisión Aguinaldo', 'Provisión Vacaciones', 'Bono por desempeño', Prima Vacacional', 'Provisión Aguinaldo Cta SER', 'Provision Prima Vacacional SER', 'Bono de Disponibilidad', 'Compensación', 'Gratificación', 'Gratificacion Extraordinaria', 'Descanso Trabajado Exento', 'Descanso Trabajado Gravable', 'Parte Exenta del Festivo Laborado', 'Parte Gravable del Festivo Laborado', 'Prima Dominical Exenta', 'Prima Dominical Gravable', 'Ayuda Mudanza', 'Importe vida cara' y el parámetro ingresado {% Impuesto Estatal}*/
        // Crear una lista de los componentes necesarios para el cálculo
        List<String> taxComponents = Arrays.asList("SALARY", "PROVISION_AGUINALDO", "VACACIONES", "PERFORMANCE_BONUS",
                "PRIMA_VACACIONAL", "PROVISION_AGUINALDO_CTA_SER", "PROVISION_PRIMA_VACACIONAL_SER", "DISPONIBILIDAD",
                "COMPENSACION", "GRATIFICACION", "GRATIFICACION_EXTRAORDINARIA", "TRABAJO_EXTENSO", "TRABAJO_GRAVABLE",
                "PARTE_EXENTA_FESTIVO_LABORADO", "PARTE_GRAVABLE_FESTIVO_LABORADO", "PRIMA_DOMINICAL_EXENTA", "PRIMA_DOMINICAL_GRAVABLE", "MUDANZA", "VIDA_CARA");

        // Recoger los componentes de la lista de componentes pasada al método
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .filter(c -> taxComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> replacement));
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        // Calcular la suma de los montos de cada componente
        BigDecimal totalAmount = taxComponents.stream()
                .map(componentMap::get)
                .filter(Objects::nonNull)
                .map(PaymentComponentDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        YearMonth yearMonth = YearMonth.parse(period, formatter);
        yearMonth = yearMonth.plusMonths(1);
        String nextPeriod = yearMonth.format(formatter);
        ParametersDTO stateTaxParameterBase = stateTaxParameterMap.get(nextPeriod);
        double stateTaxParameterAmountBase = stateTaxParameterBase == null ? 0.0 : stateTaxParameterBase.getValue();
        // Multiplicar la suma total por el parámetro de impuesto estatal
        BigDecimal stateTaxCost = totalAmount.multiply(BigDecimal.valueOf(stateTaxParameterAmountBase));

        // Crear un nuevo PaymentComponentDTO para el Impuesto Estatal
        PaymentComponentDTO stateTaxComponent = new PaymentComponentDTO();
        stateTaxComponent.setPaymentComponent("STATE_TAX");
        stateTaxComponent.setAmount(stateTaxCost);

        List<MonthProjection> projections = new ArrayList<>();
        double lastStateTax = stateTaxCost.doubleValue();
        for (MonthProjection stateTaxProjection : salaryComponent.getProjections()) {
            ParametersDTO monthlyProportion = stateTaxParameterMap.get(stateTaxProjection.getMonth());
            double monthlyProportionValue = monthlyProportion == null ? 0.0 : monthlyProportion.getValue() / 100.0;
            double totalAmountValue = taxComponents.stream()
                    .map(componentMap::get)
                    .filter(Objects::nonNull)
                    .flatMap(c -> c.getProjections().stream())
                    .filter(p -> p.getMonth().equals(stateTaxProjection.getMonth()))
                    .mapToDouble(p -> p.getAmount().doubleValue())
                    .sum();
            double stateTax;
            if (monthlyProportion != null) {
                stateTax = totalAmountValue * monthlyProportionValue;
                lastStateTax = stateTax;
            }else {
                stateTax = lastStateTax;
            }
            MonthProjection stateTaxProjectionMonth = new MonthProjection();
            stateTaxProjectionMonth.setMonth(stateTaxProjection.getMonth());
            stateTaxProjectionMonth.setAmount(BigDecimal.valueOf(stateTax));
            projections.add(stateTaxProjectionMonth);
        }
        stateTaxComponent.setProjections(projections);
        // Añadir el componente de impuesto estatal a la lista de componentes
        component.add(stateTaxComponent);
    }
}

