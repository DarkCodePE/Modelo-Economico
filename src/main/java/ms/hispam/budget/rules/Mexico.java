package ms.hispam.budget.rules;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.DateCache;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ParamFilterDTO;
import ms.hispam.budget.service.MexicoService;
import ms.hispam.budget.util.DaysVacationInfo;
import ms.hispam.budget.util.Shared;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
    public Mexico(MexicoService mexicoService) {
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
       double baseSalary = getValueByComponentName(component, PC320001);
       double baseSalaryIntegral = getValueByComponentName(component, PC320002);
       for (ParametersDTO salaryRevision : revisionList) {
           salaryRevisionMap.put(salaryRevision.getPeriod(), salaryRevision);
       }
       PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
       Map<String, Pair<Double, Double>> cache = createCache(salaryList, incrementList);
       double lastDifferPercent = 0;
       double highestAmountSoFar = baseSalary;
       for (MonthProjection projection : paymentComponentDTO.getProjections()) {
           String month = projection.getMonth();
           Pair<Double, Double> salaryAndIncrement = cache.get(month);
           double incrementPercent ;
           double lastSalary = 0.0;
           boolean isCp = poName != null && poName.contains("CP");
           //log.info("isCp: {}", isCp);
           if (salaryAndIncrement != null) {
               incrementPercent = isCp ? salaryAndIncrement.getValue() / 100.0  :  0.0 ;
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
               log.info("cacheWithRevision: {}", lastSalary);
               Pair<ParametersDTO, Double> closestSalaryRevisionAndDifferPercent = findClosestSalaryRevision(paymentComponentDTO, month, cacheWithRevision, isCp, lastSalary);
               log.info("closestSalaryRevisionAndDifferPercent: {}", closestSalaryRevisionAndDifferPercent);
               if (closestSalaryRevisionAndDifferPercent != null && month.equals(closestSalaryRevisionAndDifferPercent.getKey().getPeriod())) {
                   lastDifferPercent = closestSalaryRevisionAndDifferPercent.getValue();
               } else {
                   lastDifferPercent = 0.0;
               }
           }else{
               lastDifferPercent = 0.0;
           }
           double revisedAmount = highestAmountSoFar * (1 + lastDifferPercent);
           highestAmountSoFar = Math.max(lastSalary, revisedAmount);
           projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
       }
       component.add(paymentComponentDTO);
   }
    public void provAguinaldo(List<PaymentComponentDTO> component, String period, Integer range) {
        // Convierte la lista de componentes a un mapa
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
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
    public void provVacacionesRefactor(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, LocalDate dateContract, LocalDate dateBirth, RangeBuDTO rangeBuByBU, Integer idBu) {
        Objects.requireNonNull(dateContract, "La fecha de contrato no puede ser nula");
        LocalDate dateActual = LocalDate.now();
        long seniority = Math.max(ChronoUnit.YEARS.between(dateContract, dateActual), 0);
        List<RangeBuDetailDTO> daysVacations;
        if (rangeBuByBU != null) {
            daysVacations = rangeBuByBU.getRangeBuDetails();
        }else {
            daysVacations = getDaysVacationList();
        }
        if (daysVacations.isEmpty()) daysVacations = getDaysVacationList();
        Map<String, RangeBuDetailDTO> daysVacationsMap = daysVacations.stream()
                .collect(Collectors.toMap(RangeBuDetailDTO::getRange, Function.identity()));
        int vacationsDays = getCachedVacationsDays(seniority, daysVacationsMap);
        if (vacationsDays == 0) {
            int daysVacationPerMonth = daysVacationList.get(0).getVacationDays() / 12;
            long monthsSeniority = ChronoUnit.MONTHS.between(dateContract, dateActual);
            vacationsDays = (int) (daysVacationPerMonth * monthsSeniority);
        }
        // Convierte la lista de componentes a un mapa
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        // Busca el componente de pago "SALARY" en el mapa
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (salaryComponent != null) {
            // Crea un nuevo objeto PaymentComponentDTO para las vacaciones
            PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO();
            paymentComponentProvVacations.setPaymentComponent(VACACIONES);
            BigDecimal vacationAmount = BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / 30) * vacationsDays / 12);
            paymentComponentProvVacations.setAmount(vacationAmount);
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double amountProj = projection.getAmount().doubleValue() / 30;
                double vacationsDaysPerMonth =  (double) vacationsDays / 12;
                //log.info("amountProj: {} , vacationsDaysPerMonth {}", amountProj, vacationsDaysPerMonth);
                BigDecimal newAmount = BigDecimal.valueOf(amountProj *  vacationsDaysPerMonth);
                MonthProjection vacationProvisionProjection = new MonthProjection();
                vacationProvisionProjection.setMonth(projection.getMonth());
                vacationProvisionProjection.setAmount(newAmount);
                projections.add(vacationProvisionProjection);
            }
            paymentComponentProvVacations.setProjections(projections);
            // Agrega el componente de vacaciones a la lista de componentes
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
        String seniorityKey = String.valueOf(seniority);
        return vacationsDaysCache.computeIfAbsent(seniorityKey, key -> {
            RangeBuDetailDTO daysVacation = daysVacationsMap.get(key);
            if (daysVacation != null) {
                return daysVacation.getValue().intValue();
            }
            return 0;
        });
    }

    private boolean isValidRange(long seniority, DaysVacationInfo daysVacation) {
        return seniority >= daysVacation.getLowerLimit() && seniority <= daysVacation.getUpperLimit();
    }
}
