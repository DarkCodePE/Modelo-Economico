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
import java.util.concurrent.ScheduledFuture;
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
        //log.debug("baseSalary: {} , baseSalaryIntegral: {}", baseSalary, baseSalaryIntegral);
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
       //double baseSalary = getValueByComponentName(component, PC320001);
       //double baseSalaryIntegral = getValueByComponentName(component, PC320002);
       Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
       PaymentComponentDTO pc320001Component = componentMap.get(PC320001);
       PaymentComponentDTO pc320002Component = componentMap.get(PC320002);
       double baseSalary = pc320001Component == null ? 0.0 : pc320001Component.getAmount().doubleValue();
       double baseSalaryIntegral = pc320002Component == null ? 0.0 : pc320002Component.getAmount().doubleValue();
       for (ParametersDTO salaryRevision : revisionList) {
           salaryRevisionMap.put(salaryRevision.getPeriod(), salaryRevision);
       }
       PaymentComponentDTO paymentComponentDTO = createPaymentComponent(baseSalary, baseSalaryIntegral, period, range);
       Map<String, Pair<Double, Double>> cache = createCache(salaryList, incrementList);
       double lastDifferPercent = 0;
       double highestAmountSoFar = Math.max(baseSalary, baseSalaryIntegral);
       for (MonthProjection projection : paymentComponentDTO.getProjections()) {
           String month = projection.getMonth();
           Pair<Double, Double> salaryAndIncrement = cache.get(month);
           double incrementPercent ;
           double lastSalary = 0.0;
           boolean isCp = poName != null && poName.contains("CP");
           //log.debug("isCp: {}", isCp);
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
                //log.debug("amountProj: {} , vacationsDaysPerMonth {}", amountProj, vacationsDaysPerMonth);
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

    public void participacionTrabajadores(List<PaymentComponentDTO> component,  List<ParametersDTO>  employeeParticipationList, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        // Convierte la lista de componentes a un mapa
        Map<String, PaymentComponentDTO> componentMap = component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));
        // Busca el componente de pago "SALARY" en el mapa
        PaymentComponentDTO salaryComponent = componentMap.get(SALARY);
        // Obtener el monto de "Participación de los Trabajadores" ingresado en parámetros
        ParametersDTO participacionTrabajadoresParam = parameters.stream()
            .filter(p -> p.getParameter().getDescription().equals("Participacion de los trabajadores"))
            .findFirst().orElse(null);

        double participacionTrabajadores = participacionTrabajadoresParam==null ? 0.0 : participacionTrabajadoresParam.getValue();

       // log.info("SALARY COMPONENT: {}", salaryComponent);
        // Crear un nuevo PaymentComponentDTO para "Participación de los Trabajadores"
        PaymentComponentDTO participacionComponent = new PaymentComponentDTO();
        participacionComponent.setPaymentComponent("PARTICIPACION");
        participacionComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * participacionTrabajadores));
        // Aplicar la fórmula para cada mes de proyección
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            log.info("PROJECTION - AMOUNT: {}", projection.getAmount());
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double participacion = proportion * participacionTrabajadores;
            log.info("PROJECTION- PROPORTION: {}", proportion);
            log.info("PROJECTION- PARTICIPATION: {}", participacion);
            MonthProjection participacionProjection = new MonthProjection();
            participacionProjection.setMonth(projection.getMonth());
            participacionProjection.setAmount(BigDecimal.valueOf(participacion));
            log.info("PROJECTION- PARTICIPATION: {}", participacionProjection.getAmount());
            projections.add(participacionProjection);
        }
        participacionComponent.setProjections(projections);
        // Agregar el componente de "Participación de los Trabajadores" a la lista de componentes
        component.add(participacionComponent);
    }

    public void seguroDental(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        // Get the "Seguro Dental" amount from parameters
        ParametersDTO seguroDentalParam = parameters.stream()
            .filter(p -> p.getParameter().getDescription().equals("Seguro Dental"))
            .findFirst()
                .orElse(null);

        double seguroDental = seguroDentalParam == null ? 0.0 : seguroDentalParam.getValue();

        // Create a new PaymentComponentDTO for "Seguro Dental"
        PaymentComponentDTO seguroDentalComponent = new PaymentComponentDTO();
        seguroDentalComponent.setPaymentComponent("SEGURO_DENTAL");
        seguroDentalComponent.setAmount(BigDecimal.valueOf((component.get(0).getAmount().doubleValue() / totalSalaries) * seguroDental));
        // Apply the formula for each month of projection
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double seguroDentalAmount = proportion * seguroDental;
            MonthProjection seguroDentalProjection = new MonthProjection();
            seguroDentalProjection.setMonth(projection.getMonth());
            seguroDentalProjection.setAmount(BigDecimal.valueOf(seguroDentalAmount));
            projections.add(seguroDentalProjection);
        }
        seguroDentalComponent.setProjections(projections);

        // Add the "Seguro Dental" component to the component list
        component.add(seguroDentalComponent);
    }
    public void seguroVida(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, double totalSalaries) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        // Get the "Seguro Vida" amount from parameters
        ParametersDTO seguroVidaParam = parameters.stream()
                .filter(p -> p.getParameter().getDescription().equals("Seguro Vida"))
                .findFirst()
                .orElse(null);

        double seguroVida = seguroVidaParam == null ? 0.0 : seguroVidaParam.getValue();

        // Create a new PaymentComponentDTO for "Seguro Vida"
        PaymentComponentDTO seguroVidaComponent = new PaymentComponentDTO();
        seguroVidaComponent.setPaymentComponent("SEGURO_VIDA");
        seguroVidaComponent.setAmount(BigDecimal.valueOf((component.get(0).getAmount().doubleValue() / totalSalaries) * seguroVida));
        // Apply the formula for each month of projection
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double seguroVidaAmount = proportion * seguroVida;
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
      Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
      PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        // Get the "Prov Retiro (IAS)" amount from parameters
        ParametersDTO provRetiroIASParam = parameters.stream()
            .filter(p -> p.getParameter().getName().equals("Prov Retiro (IAS)"))
            .findFirst()
            .orElse(null);

        double provRetiroIAS = provRetiroIASParam == null ? 0.0 : provRetiroIASParam.getValue();

        // Create a new PaymentComponentDTO for "Prov Retiro (IAS)"
        PaymentComponentDTO provRetiroIASComponent = new PaymentComponentDTO();
        provRetiroIASComponent.setPaymentComponent("PROV_RETIRO_IAS");
        provRetiroIASComponent.setAmount(BigDecimal.valueOf((componentMap.get("SALARY").getAmount().doubleValue() / totalSalaries) * provRetiroIAS));
        // Apply the formula for each month of projection
        List<MonthProjection> projections = new ArrayList<>();
        for (MonthProjection projection : salaryComponent.getProjections()) {
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double provRetiroIASAmount = proportion * provRetiroIAS;
            MonthProjection provRetiroIASProjection = new MonthProjection();
            provRetiroIASProjection.setMonth(projection.getMonth());
            provRetiroIASProjection.setAmount(BigDecimal.valueOf(provRetiroIASAmount));
            projections.add(provRetiroIASProjection);
        }
        provRetiroIASComponent.setProjections(projections);

        // Add the "Prov Retiro (IAS)" component to the component list
        component.add(provRetiroIASComponent);
    }
    public void SGMM (List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range, String poName, double totalSalaries) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
        // Get the "SGMM" amount from parameters
        ParametersDTO SGMMParam = parameters.stream()
                .filter(p -> p.getParameter().getName().equals("SGMM"))
                .findFirst()
                .orElse(null);

        double SGMM = SGMMParam == null ? 0.0 : SGMMParam.getValue();

        boolean isCp = poName != null && poName.contains("CP");
        if (isCp) {
            // Create a new PaymentComponentDTO for "SGMM"
            PaymentComponentDTO SGMMComponent = new PaymentComponentDTO();
            SGMMComponent.setPaymentComponent("SGMM");
            SGMMComponent.setAmount(BigDecimal.valueOf((component.get(0).getAmount().doubleValue() / totalSalaries) * SGMM));
            // Apply the formula for each month of projection
            List<MonthProjection> projections = new ArrayList<>();
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double proportion = projection.getAmount().doubleValue() / totalSalaries;
                double SGMMAmount = proportion * SGMM;
                MonthProjection SGMMProjection = new MonthProjection();
                SGMMProjection.setMonth(projection.getMonth());
                SGMMProjection.setAmount(BigDecimal.valueOf(SGMMAmount));
                projections.add(SGMMProjection);
            }
            SGMMComponent.setProjections(projections);

            // Add the "SGMM" component to the component list
            component.add(SGMMComponent);
        }else {
            //ZERO
            PaymentComponentDTO SGMMComponent = new PaymentComponentDTO();
            SGMMComponent.setPaymentComponent("SGMM");
            SGMMComponent.setAmount(BigDecimal.valueOf(0.0));
            SGMMComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
            component.add(SGMMComponent);
        }
    }

    public void valesDeDespensa(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de salario
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        // Verificar si el componente de salario existe
        if (salaryComponent != null) {
            // Obtener los parámetros
            ParametersDTO topeValesDespensaParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Tope Vales Despensa"))
                    .findFirst()
                    .orElse(null);
            ParametersDTO umaMensualParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("UMA mensual"))
                    .findFirst()
                    .orElse(null);

            // Calcular el vales de despensa para cada mes de la proyección
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double baseSalary = projection.getAmount().doubleValue();

                // Calcular el vales de despensa
                double topeValesDespensa = topeValesDespensaParam == null ? 0.0 : topeValesDespensaParam.getValue();
                double umaMensual = umaMensualParam == null ? 0.0 : umaMensualParam.getValue();
                double valesDeDespensa = baseSalary * topeValesDespensa;

                // Comparar con UMA mensual
                if (valesDeDespensa > umaMensual) {
                    valesDeDespensa = umaMensual;
                }

                // Crear un nuevo PaymentComponentDTO para "VALES_DESPENSA"
                PaymentComponentDTO valesDeDespensaComponent = new PaymentComponentDTO();
                valesDeDespensaComponent.setPaymentComponent("VALES_DESPENSA");
                valesDeDespensaComponent.setAmount(BigDecimal.valueOf(valesDeDespensa));

                // Agregar el componente "VALES_DESPENSA" a la lista de componentes
                component.add(valesDeDespensaComponent);
            }
        }
    }
    private Map<String, PaymentComponentDTO> createComponentMap(List<PaymentComponentDTO> component) {
        return component.stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (existing, replacement) -> {
                    existing.getProjections().addAll(replacement.getProjections());
                    return existing;
                }));
    }
}
