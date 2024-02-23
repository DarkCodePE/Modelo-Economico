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
    private final ConvenioRepository convenioRepository;
    private final  ConvenioBonoRepository convenioBonoRepository;
    private Map<String, Convenio> convenioCache;
    private Map<String, ConvenioBono> convenioBonoCache;
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
        this.convenioRepository = convenioRepository;
        this.convenioBonoRepository = convenioBonoRepository;
    }

    @PostConstruct
    public void init() {
        List<Convenio> convenios = convenioRepository.findAll();
        convenioCache = new HashMap<>();
        for (Convenio convenio : convenios) {
            String key = convenio.getConvenioName();
            convenioCache.put(key, convenio);
        }
        List<ConvenioBono> convenioBonos = convenioBonoRepository.findAll();
        convenioBonoCache = new HashMap<>();
        for (ConvenioBono convenioBono : convenioBonos) {
            String key = convenioBono.getConvenioNivel();
            convenioBonoCache.put(key, convenioBono);
        }
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
       double lastDifferPercent = 0;
       double highestAmountSoFar = Math.max(baseSalary, baseSalaryIntegral);
       for (MonthProjection projection : paymentComponentDTO.getProjections()) {
           String month = projection.getMonth();
           Pair<Double, Double> salaryAndIncrement = cache.get(month);
           double incrementPercent ;
           double lastSalary = 0.0;
           boolean isCp = poName != null && poName.contains("CP");
           ////log.debug("isCp: {}", isCp);
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
        if (dateContract != null) {
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
                    ////log.debug("amountProj: {} , vacationsDaysPerMonth {}", amountProj, vacationsDaysPerMonth);
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
/*<<<<<<< HEAD
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double participacion = proportion * participacionTrabajadores;
            MonthProjection participacionProjection = new MonthProjection();
            participacionProjection.setMonth(projection.getMonth());
            participacionProjection.setAmount(BigDecimal.valueOf(participacion));
=======*/
            //log.info("PROJECTION - AMOUNT: {}", projection.getAmount());
            double proportion = projection.getAmount().doubleValue() / totalSalaries;
            double participacion = proportion * participacionTrabajadores;
            //log.info("PROJECTION- PROPORTION: {}", proportion);
            //log.info("PROJECTION- PARTICIPATION: {}", participacion);
            MonthProjection participacionProjection = new MonthProjection();
            participacionProjection.setMonth(projection.getMonth());
            participacionProjection.setAmount(BigDecimal.valueOf(participacion));
            //log.info("PROJECTION- PARTICIPATION: {}", participacionProjection.getAmount());
//>>>>>>> old_change_peru_v3
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
        seguroVidaComponent.setAmount(BigDecimal.valueOf((salaryComponent.getAmount().doubleValue() / totalSalaries) * seguroVida));
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
            .filter(p -> p.getParameter().getDescription().equals("Prov Retiro (IAS)"))
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
                .filter(p -> p.getParameter().getDescription().equals("SGMM"))
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
                    .filter(p -> p.getParameter().getDescription().equals("Tope - Vales Despensa"))
                    .findFirst()
                    .orElse(null);
            ParametersDTO umaMensualParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("UMA mensual"))
                    .findFirst()
                    .orElse(null);
            double topeValesDespensaBase = topeValesDespensaParam == null ? 0.0 : topeValesDespensaParam.getValue()/100;
            double umaMensualBase = umaMensualParam == null ? 0.0 : umaMensualParam.getValue();
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
            for (MonthProjection projection : salaryComponent.getProjections()) {
                double baseSalary = projection.getAmount().doubleValue();

                // Calcular el vales de despensa
                double topeValesDespensa = topeValesDespensaParam == null ? 0.0 : topeValesDespensaParam.getValue() / 100.0;
                double umaMensual = umaMensualParam == null ? 0.0 : umaMensualParam.getValue();
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
    public void performanceBonus(List<PaymentComponentDTO> component, String poName, String convenioBono, String period, Integer range) {

        PaymentComponentDTO bonusComponent = new PaymentComponentDTO();
        bonusComponent.setPaymentComponent("PERFORMANCE_BONUS");
        bonusComponent.setAmount(BigDecimal.valueOf(0.0));
        bonusComponent.setProjections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(0.0)));
        component.add(bonusComponent);
       /* ConvenioBono convenioBonoData = convenioBonoCache.get("ANA8");
        if (convenioBonoData != null) {
            // Convert the component list to a map
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

            // Find the "SALARY" payment component in the map
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");
            boolean isV = poName != null && !poName.contains("V");
            // If the level is 'V', no bonus is applied
            if (isV) {
                // Get the bonus percentage from the ConvenioBono object
                double bonusPercent = convenioBonoData.getBonoPercentage();

                double monthlyBonus = (salaryComponent.getAmount().doubleValue() * bonusPercent / 100) / 12;

                // Create a new PaymentComponentDTO for the bonus
                PaymentComponentDTO bonusComponent = new PaymentComponentDTO();
                bonusComponent.setPaymentComponent("PERFORMANCE_BONUS");
                bonusComponent.setAmount(BigDecimal.valueOf(monthlyBonus));

                // Apply the formula for each month of projection
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    MonthProjection bonusProjection = new MonthProjection();
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
        }*/
    }
    public void seguroSocial(List<PaymentComponentDTO> component, String convenioName, String period, Integer range) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social
        if (convenio != null) {
            double seguroSocial = convenio.getImssPercentage();

            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social
                PaymentComponentDTO seguroSocialComponent = new PaymentComponentDTO();
                seguroSocialComponent.setPaymentComponent("IMSS");

                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialCost = projection.getAmount().doubleValue() * seguroSocial / 100;
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
    public void seguroSocialRetiro(List<PaymentComponentDTO> component, String convenioName, String period, Integer range) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social (Retiro)
        if (convenio != null) {
            double seguroSocialRetiro = convenio.getRetiroPercentage();

            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social (Retiro)
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social (Retiro)
                PaymentComponentDTO seguroSocialRetiroComponent = new PaymentComponentDTO();
                seguroSocialRetiroComponent.setPaymentComponent("IMSS_RETIRO");

                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialRetiroCost = projection.getAmount().doubleValue() * seguroSocialRetiro / 100;
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
    public void seguroSocialInfonavit(List<PaymentComponentDTO> component, String convenioName, String period, Integer range) {
        // Obtén el convenio de la posición
        Convenio convenio = convenioCache.get(convenioName);
        // Si el convenio no se encuentra en el caché, no se puede calcular el Seguro Social (Infonavit)
        if (convenio != null) {
            double seguroSocialInfonavit = convenio.getInfonavitPercentage();

            // Convertir la lista de componentes en un mapa para facilitar la búsqueda
            Map<String, PaymentComponentDTO> componentMap = component.stream()
                    .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity()));

            // Encuentra el componente de salario en el mapa
            PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

            // Si el componente de salario no existe, no se puede calcular el Seguro Social (Infonavit)
            if (salaryComponent != null) {
                // Crea un nuevo PaymentComponentDTO para el Seguro Social (Infonavit)
                PaymentComponentDTO seguroSocialInfonavitComponent = new PaymentComponentDTO();
                seguroSocialInfonavitComponent.setPaymentComponent("IMSS_INFONAVIT");

                // Aplica la fórmula para cada mes de proyección
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double seguroSocialInfonavitCost = projection.getAmount().doubleValue() * seguroSocialInfonavit / 100;
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
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de 'Provisión Vacaciones'
        PaymentComponentDTO provisionVacacionesComponent = componentMap.get("VACACIONES");

        // Verificar si el componente de 'Provisión Vacaciones' existe
        if (provisionVacacionesComponent != null) {
            // Obtener el parámetro '{%Provision Prima Vacacional}'
            ParametersDTO provisionPrimaVacacionalParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Prima Vacacional"))
                    .findFirst()
                    .orElse(null);

            double provisionPrimaVacacionalBase = provisionPrimaVacacionalParam == null ? 0.0 : provisionPrimaVacacionalParam.getValue() / 100.0;
            double primaVacacionalBase = provisionVacacionesComponent.getAmount().doubleValue() * provisionPrimaVacacionalBase;

            // Crear un nuevo PaymentComponentDTO para 'PRIMA_VACACIONAL'
            PaymentComponentDTO primaVacacionalComponent = new PaymentComponentDTO();
            primaVacacionalComponent.setPaymentComponent("PRIMA_VACACIONAL");
            primaVacacionalComponent.setAmount(BigDecimal.valueOf(primaVacacionalBase));

            List<MonthProjection> projections = new ArrayList<>();
            // Calcular la prima vacacional para cada mes de la proyección
            for (MonthProjection projection : provisionVacacionesComponent.getProjections()) {
                double provisionVacaciones = projection.getAmount().doubleValue();
                double primaVacacional = provisionVacaciones * provisionPrimaVacacionalBase;

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
       // //log.debug("birthDate: {}", birthDate);
        // Crear el mapa de componentes
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);

        // Obtener el componente de 'SALARY'
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        // Verificar si el componente de 'SALARY' existe
        if (salaryComponent != null) {
            // Obtener el parámetro '{%Aporte Cta SER empresa}'
            ParametersDTO aporteCtaSEREmpresaParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Aporte Cta SER empresa"))
                    .findFirst()
                    .orElse(null);

            double aporteCtaSEREmpresaBase = 0.0;
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
                    aporteCtaSEREmpresaBase = salaryComponent.getAmount().doubleValue() * (aporteCtaSEREmpresaParam == null ? 0.0 : aporteCtaSEREmpresaParam.getValue() / 100.0);
                }

                // Crear un nuevo PaymentComponentDTO para 'APORTACION_CTA_SER_EMPRESA'
                PaymentComponentDTO aportacionCtaSEREmpresaComponent = new PaymentComponentDTO();
                aportacionCtaSEREmpresaComponent.setPaymentComponent("APORTACION_CTA_SER_EMPRESA");
                aportacionCtaSEREmpresaComponent.setAmount(BigDecimal.valueOf(aporteCtaSEREmpresaBase));
                List<MonthProjection> projections = new ArrayList<>();
                // Calcular la aportación a la cuenta SER de la empresa para cada mes de la proyección
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double baseSalary = projection.getAmount().doubleValue();
                    double aportacionCtaSEREmpresa = baseSalary * (aporteCtaSEREmpresaParam == null ? 0.0 : aporteCtaSEREmpresaParam.getValue() / 100.0);
                    MonthProjection aportacionCtaSEREmpresaProjection = new MonthProjection();
                    aportacionCtaSEREmpresaProjection.setMonth(projection.getMonth());
                    aportacionCtaSEREmpresaProjection.setAmount(BigDecimal.valueOf(aportacionCtaSEREmpresa));
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

    public void provisionAguinaldoCtaSER(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {

        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        if (salaryComponent != null) {
            ParametersDTO diasProvisionAguinaldoParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Dias prov aguinaldo"))
                    .findFirst()
                    .orElse(null);
            ////log.debug("diasProvisionAguinaldoParam: {}", diasProvisionAguinaldoParam);
            PaymentComponentDTO aportacionCtaSEREmpresaComponent = componentMap.get("APORTACION_CTA_SER_EMPRESA");

            double provisionAguinaldoCtaSERBase = 0.0;
            if (aportacionCtaSEREmpresaComponent != null && aportacionCtaSEREmpresaComponent.getAmount().doubleValue() > 0) {
                double dailyBaseSalary = salaryComponent.getAmount().doubleValue() / 30;
                provisionAguinaldoCtaSERBase = dailyBaseSalary * (diasProvisionAguinaldoParam == null ? 0.0 : diasProvisionAguinaldoParam.getValue());
                PaymentComponentDTO provisionAguinaldoCtaSERComponent = new PaymentComponentDTO();
                provisionAguinaldoCtaSERComponent.setPaymentComponent("PROVISION_AGUINALDO_CTA_SER");
                provisionAguinaldoCtaSERComponent.setAmount(BigDecimal.valueOf(provisionAguinaldoCtaSERBase));
                List<MonthProjection> projections = new ArrayList<>();
                for (MonthProjection projection : salaryComponent.getProjections()) {
                    double baseSalary = projection.getAmount().doubleValue();
                    double provisionAguinaldoCtaSER = baseSalary / 30 * (diasProvisionAguinaldoParam == null ? 0.0 : diasProvisionAguinaldoParam.getValue());
                    MonthProjection provisionAguinaldoCtaSERProjection = new MonthProjection();
                    provisionAguinaldoCtaSERProjection.setMonth(projection.getMonth());
                    provisionAguinaldoCtaSERProjection.setAmount(BigDecimal.valueOf(provisionAguinaldoCtaSER));
                    projections.add(provisionAguinaldoCtaSERProjection);
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
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO provisionAguinaldoCtaSERComponent = componentMap.get("PROVISION_AGUINALDO_CTA_SER");
        PaymentComponentDTO provisionVacacionesComponent = componentMap.get("VACACIONES");

        if (provisionAguinaldoCtaSERComponent != null) {
            ParametersDTO provisionPrimaVacacionalSERParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Provision Prima Vacacional SER"))
                    .findFirst()
                    .orElse(null);

            double provisionPrimaVacacionalSERBase = 0.0;
            if (provisionAguinaldoCtaSERComponent.getAmount().doubleValue() > 0) {
                provisionPrimaVacacionalSERBase = provisionVacacionesComponent.getAmount().doubleValue() * (provisionPrimaVacacionalSERParam == null ? 0.0 : provisionPrimaVacacionalSERParam.getValue() / 100.0);
            }

            PaymentComponentDTO provisionPrimaVacacionalSERComponent = new PaymentComponentDTO();
            provisionPrimaVacacionalSERComponent.setPaymentComponent("PROVISION_PRIMA_VACACIONAL_SER");
            provisionPrimaVacacionalSERComponent.setAmount(BigDecimal.valueOf(provisionPrimaVacacionalSERBase));
            List<MonthProjection> projections = new ArrayList<>();
            // Calculate the contribution for each month of the projection
            for (MonthProjection projection : provisionAguinaldoCtaSERComponent.getProjections()) {
                double provisionAguinaldoCtaSER = projection.getAmount().doubleValue();
                double provisionPrimaVacacionalSER = provisionAguinaldoCtaSER * (provisionPrimaVacacionalSERParam == null ? 0.0 : provisionPrimaVacacionalSERParam.getValue() / 100.0);
                MonthProjection provisionPrimaVacacionalSERProjection = new MonthProjection();
                provisionPrimaVacacionalSERProjection.setMonth(projection.getMonth());
                provisionPrimaVacacionalSERProjection.setAmount(BigDecimal.valueOf(provisionPrimaVacacionalSER));
                projections.add(provisionPrimaVacacionalSERProjection);
            }
            provisionPrimaVacacionalSERComponent.setProjections(projections);
            component.add(provisionPrimaVacacionalSERComponent);
        }
    }
    public void provisionFondoAhorro(List<PaymentComponentDTO> component, List<ParametersDTO> parameters, String period, Integer range) {
        Map<String, PaymentComponentDTO> componentMap = createComponentMap(component);
        PaymentComponentDTO salaryComponent = componentMap.get("SALARY");

        if (salaryComponent != null) {
            ParametersDTO umaMensualParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("UMA mensual"))
                    .findFirst()
                    .orElse(null);
            ParametersDTO topeMensualFondoParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Tope mensual fondo"))
                    .findFirst()
                    .orElse(null);
            ParametersDTO topeSueldoMensualFondoParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("Tope sueldo mensual - fondo"))
                    .findFirst()
                    .orElse(null);
            ParametersDTO provisionFondoAhorroParam = parameters.stream()
                    .filter(p -> p.getParameter().getDescription().equals("% Provisión Fondo de Ahorro"))
                    .findFirst()
                    .orElse(null);

            double umaMensual = umaMensualParam == null ? 0.0 : umaMensualParam.getValue();
            double topeMensualFondo = topeMensualFondoParam == null ? (umaMensual / 30) * 1.3 * 30 : topeMensualFondoParam.getValue();
            double topeSueldoMensualFondo = topeSueldoMensualFondoParam == null ? topeMensualFondo / 1.3 : topeSueldoMensualFondoParam.getValue();
            double provisionFondoAhorro = provisionFondoAhorroParam == null ? 0.0 : provisionFondoAhorroParam.getValue() / 100;

            PaymentComponentDTO provisionFondoAhorroComponent = new PaymentComponentDTO();
            provisionFondoAhorroComponent.setPaymentComponent("PROVISION_FONDO_AHORRO");
            List<MonthProjection> projections = new ArrayList<>();

            for (MonthProjection projection : salaryComponent.getProjections()) {
                double baseSalary = projection.getAmount().doubleValue();
                double provisionFondoAhorroAmount;

                if (baseSalary > topeSueldoMensualFondo) {
                    provisionFondoAhorroAmount = topeMensualFondo;
                } else {
                    provisionFondoAhorroAmount = baseSalary * provisionFondoAhorro;
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

}
