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

    private void updateProjections(PaymentComponentDTO paymentComponentDTO, Map<String, Pair<Double, Double>> cache) {
        Pair<Double, Double> lastSalaryAndIncrement = null;
        double currentAmount ;
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
            String month = projection.getMonth();
            Pair<Double, Double> salaryAndIncrement = cache.get(month);
            if (salaryAndIncrement != null) {
                lastSalaryAndIncrement = salaryAndIncrement;
            }
            if (lastSalaryAndIncrement != null) {
                double minSalary = lastSalaryAndIncrement.getKey();
                double salaryIncrement = lastSalaryAndIncrement.getValue() / 100.0;
                double amount = projection.getAmount().doubleValue();
                if (amount < minSalary && minSalary != 0.0) {
                    double incrementSalary = amount * salaryIncrement;
                    currentAmount = amount + incrementSalary;
                } else {
                    currentAmount = amount;
                }
                projection.setAmount(BigDecimal.valueOf(currentAmount));
            }
        }
    }



    public void revisionSalary(List<PaymentComponentDTO> component,List<ParametersDTO> parameters){
        List<ParametersDTO> revisionSalaryParameters = parameters.stream()
                .filter(p -> p.getParameter().getName().equalsIgnoreCase(SALARY_REVISION))
                .collect(Collectors.toList());
        for (ParametersDTO salaryRevision : revisionSalaryParameters) {
            salaryRevisionMap.put(salaryRevision.getPeriod(), salaryRevision);
        }
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        if (salaryComponent != null) {
            double lastDifferPercent = 0;
            boolean applyRevision = false;
            for (MonthProjection projection : salaryComponent.getProjections()) {
                Pair<ParametersDTO, Double> closestSalaryRevisionAndDifferPercent = findClosestSalaryRevision(salaryComponent, projection.getMonth());
                if (closestSalaryRevisionAndDifferPercent != null) {
                    lastDifferPercent = closestSalaryRevisionAndDifferPercent.getValue();
                    if (projection.getMonth().equals(closestSalaryRevisionAndDifferPercent.getKey().getPeriod())) {
                        applyRevision = true;
                    }
                }
                if (applyRevision) {
                    double amount = projection.getAmount().doubleValue();
                    double v = amount * (1 + lastDifferPercent);
                    projection.setAmount(BigDecimal.valueOf(v));
                }
            }
        }
    }
    public Pair<ParametersDTO, Double> findClosestSalaryRevision(PaymentComponentDTO salaryComponent, String projectionMonth) {
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
            String[] periodRevisionSalary = closestSalaryRevision.getPeriodRetroactive().split("-");
            int idxStart = Shared.getIndex(salaryComponent.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[0]);
            int idxEnd = Shared.getIndex(salaryComponent.getProjections().stream()
                    .map(MonthProjection::getMonth).collect(Collectors.toList()), periodRevisionSalary[1]);
            double salaryFirst = salaryComponent.getProjections().get(idxStart).getAmount().doubleValue();
            double salaryEnd = salaryComponent.getProjections().get(idxEnd).getAmount().doubleValue();
            double differPercent = (salaryFirst / salaryEnd) - 1;
            double percent = closestSalaryRevision.getValue() / 100;
            if (differPercent >= 0 && differPercent <= percent) {
                differPercent = percent - differPercent;
            } else {
                differPercent = percent;
            }
            return Pair.of(closestSalaryRevision, differPercent);
        }
        return null;
    }
    public void salary(List<PaymentComponentDTO> component, List<ParametersDTO> salaryList, List<ParametersDTO> incrementList, List<ParametersDTO>revisionList, String period, Integer range) {
        double baseSalary = getValueByComponentName(component, PC320001);
        double baseSalaryIntegral = getValueByComponentName(component, PC320002);
        for (ParametersDTO salaryRevision : revisionList) {
            salaryRevisionMap.put(salaryRevision.getPeriod(), salaryRevision);
        }
        PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
        Map<String, Pair<Double, Double>> cache = createCache(salaryList, incrementList);
        //log.info("cache: {}", cache);
        double lastDifferPercent = 0;
        double highestAmountSoFar = baseSalary;
        for (MonthProjection projection : paymentComponentDTO.getProjections()) {
            String month = projection.getMonth();
            Pair<ParametersDTO, Double> closestSalaryRevisionAndDifferPercent = findClosestSalaryRevision(paymentComponentDTO, month);
            log.info("closestSalaryRevisionAndDifferPercent: {}", closestSalaryRevisionAndDifferPercent);
            if (closestSalaryRevisionAndDifferPercent != null && month.equals(closestSalaryRevisionAndDifferPercent.getKey().getPeriod())) {
                lastDifferPercent = closestSalaryRevisionAndDifferPercent.getValue();
            }else{
                lastDifferPercent = 0.0;
            }
            double amount = projection.getAmount().doubleValue();
            highestAmountSoFar = Math.max(highestAmountSoFar, amount);
            //log.info("highestAmountSoFar: {}", highestAmountSoFar);
            log.info("lastDifferPercent: {} , moth {}", lastDifferPercent, month);
            double revisedAmount = highestAmountSoFar * (1 + lastDifferPercent);
            Pair<Double, Double> salaryAndIncrement = cache.get(month);
            if (salaryAndIncrement != null) {
                double minSalary = salaryAndIncrement.getKey();
                double incrementPercent = salaryAndIncrement.getValue() / 100.0;
                if (revisedAmount < minSalary && minSalary != 0.0) {
                    //double incrementSalary = revisedAmount * incrementPercent;
                    revisedAmount = revisedAmount * ( 1 + incrementPercent);
                }
            }
            highestAmountSoFar = Math.max(highestAmountSoFar, revisedAmount);
            //log.info("highestAmountSoFar: {}", highestAmountSoFar);
            projection.setAmount(BigDecimal.valueOf(highestAmountSoFar));
        }
        component.add(paymentComponentDTO);
    }

    public ParamFilterDTO getValueSalaryByPeriod(List<ParametersDTO> params, String periodProjection){
        //ordenar lista
        sortParameters(params);
        AtomicReference<Double> value = new AtomicReference<>(0.0);
        AtomicReference<String> periodSalaryMin = new AtomicReference<>("");
        Boolean status = params.stream()
                .anyMatch(p -> {
                    DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                            .appendPattern(TYPEMONTH)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter();
                    LocalDate dateParam = LocalDate.parse(p.getPeriod(),dateFormat);
                    LocalDate dateProjection = LocalDate.parse(periodProjection, dateFormat);
                    value.set(p.getValue());
                    periodSalaryMin.set(p.getPeriod());
                    return dateParam.equals(dateProjection);
                });
        //TODO ELEMINAR EL ELEMENTO SELECCIONADO DE LISTA
        return ParamFilterDTO.builder()
                .status(status)
                .value(value.get())
                .period(periodSalaryMin.get())
                .build();
    }
    private void sortParameters(List<ParametersDTO> params) {
        params.sort((o1, o2) -> {
            DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()
                    .appendPattern(TYPEMONTH)
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter();
            LocalDate dateParam = LocalDate.parse(o1.getPeriod(),dateFormat);
            LocalDate dateProjection = LocalDate.parse(o2.getPeriod(), dateFormat);
            return dateParam.compareTo(dateProjection);
        });
    }
    public void provAguinaldo(List<PaymentComponentDTO> component, String period, Integer range) {
        // get salary
        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);
        // create payment component prov aguinaldo
        if (paymentComponentDTO != null){
            //PaymentComponentDTO paymentComponentDTOClone = paymentComponentDTO.clone();
            PaymentComponentDTO paymentComponentProvAguin = new PaymentComponentDTO(
                    paymentComponentDTO.getPaymentComponent(),
                    paymentComponentDTO.getAmount(),
                    paymentComponentDTO.getProjections()
            ).createWithProjections();
            paymentComponentProvAguin.setPaymentComponent("AGUINALDO");
            BigDecimal aguinaldoAmount = BigDecimal.valueOf((paymentComponentDTO.getAmount().doubleValue() / 30) * 1.25);
            paymentComponentProvAguin.setAmount(aguinaldoAmount);
            //paymentComponentProvAguin.setProjections(paymentComponentDTOClone.getProjections());
            // prov aguinaldo
           /* for (int i = 0; i < paymentComponentProvAguin.getProjections().size(); i++) {
                double amountProj = paymentComponentProvAguin.getProjections().get(i).getAmount().doubleValue();
                paymentComponentProvAguin.getProjections().get(i).setAmount(BigDecimal.valueOf((amountProj/30) * 1.25));
            }*/
            for (MonthProjection projection : paymentComponentProvAguin.getProjections()) {
                double amountProj = projection.getAmount().doubleValue();
                BigDecimal newAmount = BigDecimal.valueOf((amountProj / 30) * 1.25);
                projection.setAmount(newAmount);
            }
            component.add(paymentComponentProvAguin);
        }
    }
    private boolean isValidRange(long seniority, String range) {
        if (range == null || range.isEmpty()) {
            return false;
        }
        String[] parts = range.split("a");
        if (parts.length == 1) {
            // Si solo hay un valor, verificar si coincide con la antigüedad
            try {
                long singleValue = Long.parseLong(parts[0].trim());
                return seniority == singleValue;
            } catch (NumberFormatException e) {
                // Manejar la excepción si no se puede convertir a número
                return false;
            }
        } else if (parts.length == 2) {
            // Si hay dos valores, verificar si la antigüedad está en el rango
            try {
                long startRange = Long.parseLong(parts[0].trim());
                long endRange = Long.parseLong(parts[1].trim());
                return seniority >= startRange && seniority <= endRange;
            } catch (NumberFormatException e) {
                // Manejar la excepción si no se pueden convertir a números
                return false;
            }
        }
        // Si no hay uno o dos valores, el formato no es válido
        return false;
    }
    public void provVacacionesRefactor(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String classEmployee, String period, Integer range, LocalDate dateContract, LocalDate dateBirth, List<RangeBuDTO> rangeBu, Integer idBu) {
        Objects.requireNonNull(dateContract, "La fecha de contrato no puede ser nula");

        LocalDate dateActual = LocalDate.now();
        long seniority = Math.max(ChronoUnit.YEARS.between(dateContract, dateActual), 0);
        //log.info("seniority: {}",seniority);
        RangeBuDTO rangeBuByBU = rangeBu.stream()
                .filter(r -> r.getIdBu().equals(idBu))
                .findFirst()
                .orElse(null);
        //log.info("rangeBuByBU: {}",rangeBuByBU);
        List<RangeBuDetailDTO> daysVacations;

        if (rangeBuByBU != null) {
             daysVacations = getAllDaysVacation(rangeBuByBU.getRangeBuDetails(), idBu);
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

        PaymentComponentDTO paymentComponentDTO = component.stream()
                .filter(p -> p.getPaymentComponent().equalsIgnoreCase("SALARY"))
                .findFirst()
                .orElse(null);

        if (paymentComponentDTO != null) {
            PaymentComponentDTO paymentComponentProvVacations = new PaymentComponentDTO(
                    paymentComponentDTO.getPaymentComponent(),
                    paymentComponentDTO.getAmount(),
                    paymentComponentDTO.getProjections()
            ).createWithProjections();
            paymentComponentProvVacations.setPaymentComponent("VACACIONES");
            BigDecimal vacationAmount = BigDecimal.valueOf((paymentComponentDTO.getAmount().doubleValue() / 30) * vacationsDays / 12);
            paymentComponentProvVacations.setAmount(vacationAmount);

            for (MonthProjection projection : paymentComponentProvVacations.getProjections()) {
                double amountProj = projection.getAmount().doubleValue() / 30;
                BigDecimal newAmount = BigDecimal.valueOf((amountProj * vacationsDays) / 12);
                projection.setAmount(newAmount);
            }

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
