package ms.hispam.budget.service.impl;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.*;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.entity.mysql.ParameterProjection;
import ms.hispam.budget.exception.BadRequestException;
import ms.hispam.budget.exception.FormatAmountException;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.repository.sqlserver.ParametersRepository;
import ms.hispam.budget.rules.*;
import ms.hispam.budget.rules.operations.salary.FoodBenefitsOperation;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.rules.operations.salary.*;
import ms.hispam.budget.service.BuService;
import ms.hispam.budget.service.MexicoService;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.service.ReportGenerationService;
import ms.hispam.budget.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j(topic = "PROJECTION_SERVICE")
public class ProjectionServiceImpl implements ProjectionService {

    private static final int HHEE_ADJUSTMENT_FACTOR_ID = 71;
    @Autowired
    private ParametersRepository repository;
    @Autowired
    private DemoRepository sharedRepo;
    @Autowired
    private ParameterRepository parameterRepository;
    @Autowired
    private HistorialProjectionRepository historialProjectionRepository;
    @Autowired
    private ParameterProjectionRepository parameterProjectionRepository;
    @Autowired
    private DisabledPoHistorialRepository disabledPoHistorialRepository;
    @Autowired
    private BuRepository buRepository;
    @Autowired
    private LegalEntityRepository legalEntityRepository;
    @Autowired
    private CodeNominaRepository codeNominaRepository;
    @Autowired
    private BaseExternRepository baseExternRepository;
    @Autowired
    private FrecuentlyRepository frecuentlyRepository;
    @Autowired
    private PoHistorialExternRepository poHistorialExternRepository;
    @Autowired
    private JsonDatosRepository jsonDatosRepository;
    @Autowired
    private ParameterDefaultRepository parameterDefaultRepository;
    @Autowired
    private BusinessCaseRepository businessCaseRepository;
    @Autowired
    private TypEmployeeRepository typEmployeeRepository;
    @Autowired
    private DaysVacationOfTimeRepository daysVacationOfTimeRepository;
    @Autowired
    private RangeBuPivotRepository rangeBuPivotRepository;
    @Autowired
    private RangeBuRepository rangeBuRepository;
    @Autowired
    private BuService buService;
    @Autowired
    private NominaPaymentComponentLinkRepository nominaPaymentComponentLinkRepository;
    @Autowired
    private ReportGenerationService reportGenerationService;
    @Autowired
    private Executor executor;
    @Autowired
    private XlsReportService xlsReportService;
    @Autowired
    private ConvenioRepository convenioRepository;
    @Autowired
    private ConvenioBonoRepository convenioBonoRepository;
    @Autowired
    private RangoBuPivotHistoricalRepository rangoBuPivotHistoricalRepository;
    @Autowired
    private rangoBuPivotHistoricalDetailRepository rangoBuPivotHistoricalDetailRepository;
    private Map<String, List<NominaPaymentComponentLink>> nominaPaymentComponentLinksCache;
    private final MexicoService mexicoService;
    private List<String> excludedPositionsBC = new ArrayList<>();
    List<HeadcountProjection> headcountTemporal = new ArrayList<>();
    private List<Operation> operations;
    private Peru methodsPeru;
    @Autowired
    public ProjectionServiceImpl(MexicoService mexicoService) {
        this.mexicoService = mexicoService;
    }
    private static final String[] headers = {"po","idssff"};
    private static final String HEADERPO="po";
    private static final String[]  HEADERNAME={"po","typeEmployee","name"};

    private Map<String, Map<String, Object>> dataMapTemporal = new HashMap<>();

    @Cacheable("daysVacationCache")
    public List<DaysVacationOfTime> getAllDaysVacation() {
        return daysVacationOfTimeRepository.findAll();
    }
    private Map<String, ConvenioBono> convenioBonoCache;
    private Map<String, Convenio> convenioCache;
    @PostConstruct
    public void init() {
        operations = new ArrayList<>();
        methodsPeru = new Peru(operations);
        operations.add(new SalaryOperationPeru());
        operations.add(new RevisionSalaryOperationPeru());
        operations.add(new VacationEnjoymentOperation());
        operations.add(new BaseSalaryOperation());
        operations.add(new VacationProvisionOperation());
        operations.add(new GratificationOperation());
        operations.add(new FoodBenefitsOperation());
        operations.add(new CTSOperation());
        operations.add(new LifeInsuranceOperation());
        operations.add(new MovingOperation());
        operations.add(new HousingOperation());
        operations.add(new ExpatriateseOperation());
        operations.add(new AFPOperation());
        /*List<ConvenioBono> convenioBonos = convenioBonoRepository.findAll();
        convenioBonoCache = new HashMap<>();
        for (ConvenioBono convenioBono : convenioBonos) {
            String key = convenioBono.getConvenioNivel();
            convenioBonoCache.put(key, convenioBono);
        }
        List<Convenio> convenios = convenioRepository.findAll();
        convenioCache = new HashMap<>();
        for (Convenio convenio : convenios) {
            String key = convenio.getConvenioName();
            convenioCache.put(key, convenio);
        }*/
    }

    @Override
    public Page<ProjectionDTO> getProjection(ParametersByProjection projection) {
        try {
            /* BECARIO TEST */
            /*List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                    .stream()
                    .filter(projectionDTO ->  projectionDTO.getPo().equals("PO90006575"))
                    .collect(Collectors.toList());*/
            /* EMP TEST */
              /*List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                    .stream()
                    .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10039679"))
                    .collect(Collectors.toList());*/
           List<ProjectionDTO>  headcount =  getHeadcountByAccount(projection);
           //log.info("headcount {}",headcount);
            //log.debug("headcount {}",headcount) ;
            //log.debug("headcount {}",headcount.size());
            List<ComponentProjection> components =sharedRepo.getComponentByBu(projection.getBu());
            //log.debug("components {}",components.size());
            if(headcount.isEmpty()){
               throw new BadRequestException("No existe informacion de la proyección para el periodo "+projection.getPeriod());
            }

            switch (projection.getBu()){
                case "T. ECUADOR":
                    isEcuador(headcount,projection);
                    break;
                case "T. URUGUAY":
                    isUruguay(headcount,projection);
                    break;
                case "T. COLOMBIA":
                    isColombia(headcount,projection);
                    break;
                case "T. MEXICO":
                    isMexico(headcount,projection);
                    break;
                case "T. PERU":
                    isPeru(headcount,projection);
                    break;
                default:
                    break;
            }
            //log.debug("headcount {}",headcount);
            List<ComponentAmount> groupedData = headcount.stream()
                    .flatMap(j -> {
                       // log.debug("j.getComponents() {}",j.getComponents());
                        return  j.getComponents().stream();
                    })
                    .collect(Collectors.groupingBy(
                            PaymentComponentDTO::getPaymentComponent,
                            Collectors.summingDouble(p -> p.getAmount().doubleValue())
                    )).entrySet()
                    .stream()
                    .map(entry -> new ComponentAmount(entry.getKey(), BigDecimal.valueOf(entry.getValue())))
                    .collect(Collectors.toList());
            //log.debug("groupedData {}",groupedData);
            //ocultando los payment que no son mostrados
            Map<String, ComponentProjection> mapaComponentesValidos = components.stream().filter(ComponentProjection::getShow)
                    .collect(Collectors.toMap(ComponentProjection::getComponent, componente -> componente));
            //log.debug("mapaComponentesValidos {}",mapaComponentesValidos);
            // Filtra las proyecciones y agrega el nombre al PaymentComponentDTO si es válido
            List<ProjectionDTO> proyeccionesValidas = headcount.stream()
                    .map(proyeccion -> {
                        List<PaymentComponentDTO> componentesValidosEnProyeccion = proyeccion.getComponents().stream()
                                //.filter(componente -> mapaComponentesValidos.containsKey(componente.getPaymentComponent()))
                                .peek(componente -> {
                                    // Agrega el nombre al PaymentComponentDTO
                                    //log.info("componente.getPaymentComponent() {}",componente.getPaymentComponent());
                                    //ComponentProjection componenteValido = mapaComponentesValidos.get(componente.getPaymentComponent());
                                    componente.setName(componente.getPaymentComponent());
                                })
                                .collect(Collectors.toList());
                       // log.debug("componentesValidosEnProyeccion {}",componentesValidosEnProyeccion);
                        // Crea una nueva proyección con los componentes válidos
                        return new ProjectionDTO(
                                proyeccion.getIdssff(),
                                proyeccion.getPo(),
                                proyeccion.getPoName(),
                                componentesValidosEnProyeccion,
                                proyeccion.getClassEmployee(),
                                proyeccion.getFNac(),
                                proyeccion.getFContra(),
                                proyeccion.getAreaFuncional(),
                                proyeccion.getDivision(),
                                proyeccion.getCCostos(),
                                proyeccion.getConvent(),
                                proyeccion.getLevel()
                        );
                    })
                    .collect(Collectors.toList());
            Double tCambio  = repository.getTypeChange(projection.getPeriod(),
                    projection.getCurrent()).orElse(0.0);
            return new Page<>(0,0, headcount.size(),
                    proyeccionesValidas
                    ,groupedData,
                    tCambio);
        }catch (ConversionFailedException ex) {
            log.debug("El valor proporcionado no es un número decimal válido: ", ex);
            throw new FormatAmountException("El valor proporcionado no es un número decimal válido");
        }catch (NumberFormatException ex){
            throw ex;
        } catch (BadRequestException ex){
            throw ex;
        } catch (Exception ex){
            log.error("Error al generar la proyección",ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar la proyección", ex);
            //return new Page<>();
        }
    }

    @Override
    public ProjectionSecondDTO getNewProjection(ParametersByProjection projection) {
        try {
            Map<String, AccountProjection> componentesMap = new HashMap<>();
            //log.debug("projection {}",projection.getIdBu());
            List<AccountProjection> components = getAccountsByBu(projection.getIdBu());
            //log.debug("components {}",components.size());
            for (AccountProjection concept : components) {
                componentesMap.put(concept.getVcomponent(), concept);
            }
            //.debug("componentesMap {}", componentesMap);
            List<ProjectionDTO>  headcount=  getHeadcount(projection, componentesMap);
            //List<ProjectionDTO> headcount = getHeadcount(projection, componentesMap);
            //log.debug("headcount {}", headcount);
            //Agrupar por componente y agrupar por mes
            List<ResumenComponentDTO> componentMonthAmountMap = getResumenPorMonth(headcount, componentesMap).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());

            //Agrupar por mes
            List<MonthProjection> monthAmountList = groupingByMonth(componentMonthAmountMap).stream()
                    .sorted(Comparator.comparing(MonthProjection::getMonth)).collect(Collectors.toList());

            //agrupar por año
            List<MonthProjection> yearAmountList = groupingByYear(monthAmountList).stream()
                    .sorted(Comparator.comparing(MonthProjection::getMonth)).collect(Collectors.toList());
            // agrupar por componente y agrupar por año
            List<ResumenComponentDTO> resumenPorAnioList = getResumenPorAnio(componentMonthAmountMap).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());

            //Agrupar por cuenta y mes componentMonthAmountMap
            List<ResumenComponentDTO> groupingByAccountMonth = groupingAccountMonth(componentMonthAmountMap, components).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());

            List<ResumenComponentDTO> groupingByAccountYear = getResumenPorAnio(groupingByAccountMonth).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());

            List<RealesProjection> getReales = repository.getReales("%" + projection.getBu()
                    .replace("T. ", "") + "%", projection.getPeriod());

            //agrupar getreales por cuenta y mes y monto
            List<ResumenComponentDTO> groupedRealesMonth = groupedReales(getReales).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());

            List<ResumenComponentDTO> groupedRealesYear = getResumenPorAnio(groupedRealesMonth).stream()
                    .peek(component -> component.getProjections().sort(Comparator.comparing(MonthProjection::getMonth)))
                    .sorted(Comparator.comparing(ResumenComponentDTO::getAccount))
                    .collect(Collectors.toList());
            //Agrupar reales por mes
            List<MonthProjection> monthReales = groupingByMonth(groupedRealesMonth).stream()
                    .sorted(Comparator.comparing(MonthProjection::getMonth)).collect(Collectors.toList());

            //agrupar por año
            List<MonthProjection> yearReales = groupingByYear(monthReales).stream()
                    .sorted(Comparator.comparing(MonthProjection::getMonth)).collect(Collectors.toList());
            ViewPosition viewPosition = Boolean.TRUE.equals(projection.getViewPo()) ? ViewPosition.builder()
                    .positions(headcount)
                    .count(headcount.size())
                    .build() : null;

            //get rosseta
            List<RosetaDTO> rosseta = getRoseta(projection.getIdBu());
            //recorrer rosseta por childs y ver si en payment esta incluido componentMonthAmountMap para acumular el mes y monto
            for (RosetaDTO rosetaDTO : rosseta) {
                acumularMontosPorComponente(rosetaDTO, componentMonthAmountMap);
            }
            Double tCambio = repository.getTypeChange(projection.getPeriod(),
                    projection.getCurrent()).orElse(0.0);


            return ProjectionSecondDTO.builder()
                    .resumeComponent(ResumeSpecDTO.builder()
                            .resumeComponentMonth(componentMonthAmountMap)
                            .resumeComponentYear(resumenPorAnioList)
                            .build())
                    .monthProjections(monthAmountList)
                    .yearProjections(yearAmountList)
                    .resumeAccount(ResumeSpecDTO.builder()
                            .resumeComponentMonth(groupingByAccountMonth)
                            .resumeComponentYear(groupingByAccountYear)
                            .build())
                    .realesMonth(groupedRealesMonth)
                    .realesYear(groupedRealesYear)
                    .resumeRealesMonth(monthReales)
                    .resumeRealesYear(yearReales)
                    .viewPosition(viewPosition)
                    .rosseta(rosseta)
                    .tCambio(new BigDecimal(tCambio))
                    .build();
       }catch (ConversionFailedException ex) {
            log.debug("El valor proporcionado no es un número decimal válido: ", ex);
            throw new FormatAmountException("El valor proporcionado no es un número decimal válido");
        }catch (NumberFormatException ex){
            throw ex;
        } catch (BadRequestException ex){
            throw ex;
        } catch (Exception ex){
            log.error("Error al generar la proyección",ex);

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar la proyección", ex);
        }
    }

    private  void acumularMontosPorComponente(RosetaDTO rosetaDTO,List<ResumenComponentDTO> componentMonthAmountMap) {
        Map<String, BigDecimal> montosPorMes = new HashMap<>();
        for (String componente : rosetaDTO.getPayment()) {
            for (ResumenComponentDTO component : componentMonthAmountMap) {
                if (component.getCode().equals(componente)) {
                    for (MonthProjection projection : component.getProjections()) {
                        String month = projection.getMonth();
                        BigDecimal amount = projection.getAmount();

                        if (montosPorMes.containsKey(month)) {
                            montosPorMes.put(month, montosPorMes.get(month).add(amount));
                        } else {
                            montosPorMes.put(month, amount);
                        }
                    }
                }
            }
        }

        for (RosetaDTO child : rosetaDTO.getChilds()) {
            acumularMontosPorComponente(child,componentMonthAmountMap);
        }

        for (Map.Entry<String, BigDecimal> entry : montosPorMes.entrySet()) {
            rosetaDTO.getProjections().add(new MonthProjection(entry.getKey(), entry.getValue()));
        }
    }



    private List<ProjectionDTO> getHeadcount(ParametersByProjection projection, Map<String, AccountProjection> componentesMap){
        //Calll nomina
        List<ProjectionDTO>  headcount =  getHeadcountByAccount(projection);
        //log.info("headcount {}",headcount);
        /*List<ProjectionDTO>  headcount =  getHeadcountByAccount(projection)
                .stream()
                .filter(projectionDTO ->  projectionDTO.getIdssff().equals("26"))
                .collect(Collectors.toList());*/
        if(headcount.isEmpty()){
            throw new BadRequestException("No existe informacion de la proyección para el periodo "+projection.getPeriod());
        }
        //filer by pos PO10016310
        /*List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                .stream()
                .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10016310"))
                .collect(Collectors.toList());*/
        //filter headcount temporal
      /*  List<ProjectionDTO> headcountT = headcount.stream()
                .filter(h -> h.getClassEmployee().equals("T"))
                .collect(Collectors.toList());
        log.debug("headcount {}",headcountT);*/

        switch (projection.getBu()){
            case "T. ECUADOR":
                isEcuador(headcount,projection);
                break;
            case "T. URUGUAY":
                isUruguay(headcount,projection);
                break;
            case "T. COLOMBIA":
                isColombia(headcount, projection);
                break;
            case "T. MEXICO":
                isMexico(headcount,projection);
                break;
            case "T. PERU":
                isPeru(headcount,projection);
                break;
            default:
                break;
        }
        //FILTER COLOMBIA - HHEE Y RECARGOS -> MULTIPLICAR POR 12 AMBOS
        if(projection.getBu().equals("T. COLOMBIA")){
            headcount.forEach(p -> p.getComponents().forEach(t -> t.getProjections().forEach(m -> {
                if (t.getPaymentComponent().equals("HHEE") || t.getPaymentComponent().equals("SURCHARGES")) {
                    m.setAmount(m.getAmount().multiply(BigDecimal.valueOf(12)));
                }
            })));
        }
        //log.debug("headcount {}",headcount);
    // Posiciones deshabilitadas
        projection.getDisabledPo().forEach(r-> headcount.stream().filter(p->p.getPo().equals(r.getPosition()))
                .findFirst().ifPresent(p->
                        p.getComponents().forEach(t->t.getProjections()
                                .stream().filter(h->Shared.estaEnRango(r.getFrom(),r.getTo(),h.getMonth()))
                                .forEach(m->m.setAmount(BigDecimal.ZERO)))));

        Set<String> validComponents = componentesMap.entrySet().stream()
                .filter(entry -> entry.getValue().getVshow() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        headcount.forEach(p -> p.setComponents(p.getComponents().stream()
                .filter(c -> validComponents.contains(c.getPaymentComponent()))
                .collect(Collectors.toList())));

        //Filter parameters by show
        /*headcount.forEach(p -> p.setComponents(p.getComponents().stream()
                .filter(c -> componentesMap.get(c.getPaymentComponent()) != null
                        && componentesMap.get(c.getPaymentComponent()).getVshow() == 1)
                .collect(Collectors.toList())));*/
        return headcount;
    }

    private List<ResumenComponentDTO> groupedReales( List<RealesProjection> getReales) {
        return getReales.stream()
                .collect(Collectors.groupingBy(
                        RealesProjection::getCuentaSAP, // Agrupar por cuenta
                        Collectors.groupingBy(
                                RealesProjection::getPeriodoMensual, // Agrupar por mes
                                Collectors.mapping(RealesProjection::getAmount, Collectors.toList()) // Mapear la cantidad por mes
                        )
                ))
                .entrySet().stream()
                .map(entry -> {
                    ResumenComponentDTO realesProjection = new ResumenComponentDTO();
                    realesProjection.setAccount(entry.getKey());
                    realesProjection.setProjections(entry.getValue().entrySet().stream()
                            .map(monthEntry -> new MonthProjection(monthEntry.getKey(), monthEntry.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add)))
                            .collect(Collectors.toList()));
                    return realesProjection;
                })
                .collect(Collectors.toList());
    }

    private  List<MonthProjection> groupingByMonth( List<ResumenComponentDTO> componentMonthAmountMap){
        return componentMonthAmountMap.stream()
                .flatMap(component -> component.getProjections().stream())
                .collect(Collectors.groupingBy(MonthProjection::getMonth,
                        Collectors.reducing(BigDecimal.ZERO, MonthProjection::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .map(e -> new MonthProjection(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
    private  List<MonthProjection> groupingByYear( List<MonthProjection> monthAmountList){
        return monthAmountList.stream()
                .collect(Collectors.groupingBy(ma -> ma.getMonth().substring(0, 4),
                        Collectors.reducing(BigDecimal.ZERO, MonthProjection::getAmount, BigDecimal::add)))
                .entrySet().stream()
                .map(e -> new MonthProjection(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private List<ResumenComponentDTO> groupingAccountMonth(List<ResumenComponentDTO> componentMonthAmountMap, List<AccountProjection> accounts){
        return componentMonthAmountMap.stream()
                .collect(Collectors.groupingBy(
                        ResumenComponentDTO::getAccount, // Agrupar por componente
                        Collectors.flatMapping(
                                paymentComponent -> paymentComponent.getProjections() .stream(), // Obtener un stream de todas las proyecciones de cada componente
                                Collectors.groupingBy(
                                        MonthProjection::getMonth, // Agrupar por mes
                                        Collectors.reducing(BigDecimal.ZERO, MonthProjection::getAmount, BigDecimal::add) // Sumar las cantidades por mes
                                )
                        )
                )).entrySet().stream().map(entry -> {
                    ResumenComponentDTO componentAmount = new ResumenComponentDTO();
                    componentAmount.setAccount(entry.getKey());
                    componentAmount.setComponent(accounts.stream().filter(r->r.getAccount().equals(entry.getKey())).findFirst().get().getNameaccount());
                    List<MonthProjection> projections = entry.getValue().entrySet().stream()
                            .map(monthEntry -> new MonthProjection(monthEntry.getKey(), monthEntry.getValue()))
                            .collect(Collectors.toList());
                    componentAmount.setProjections(projections);
                    return componentAmount;
                })
                .collect(Collectors.toList());
    }

    private   List<ResumenComponentDTO> getResumenPorMonth(List<ProjectionDTO> headcount,Map<String, AccountProjection> componentesMap){
        return  headcount.stream()
                .flatMap(headcoun -> headcoun.getComponents().stream()) // Obtener un stream de todos los componentes de cada headcount
                .collect(Collectors.groupingBy(
                        PaymentComponentDTO::getPaymentComponent, // Agrupar por componente
                        Collectors.flatMapping(
                                paymentComponent -> paymentComponent.getProjections().stream(), // Obtener un stream de todas las proyecciones de cada componente
                                Collectors.groupingBy(
                                        MonthProjection::getMonth, // Agrupar por mes
                                        Collectors.reducing(BigDecimal.ZERO, MonthProjection::getAmount, BigDecimal::add) // Sumar las cantidades por mes
                                )
                        )
                )).entrySet().stream().map(entry -> {
                    ResumenComponentDTO componentAmount = new ResumenComponentDTO();
                    componentAmount.setComponent(componentesMap.get(entry.getKey()).getVname());
                    componentAmount.setAccount(componentesMap.get(entry.getKey()).getAccount());
                    componentAmount.setCode(entry.getKey());
                    List<MonthProjection> projections = entry.getValue().entrySet().stream()
                            .map(monthEntry -> new MonthProjection(monthEntry.getKey(), monthEntry.getValue()))
                            .collect(Collectors.toList());
                    componentAmount.setProjections(projections);
                    return componentAmount;
                })
                .collect(Collectors.toList());

    }

    private List<ResumenComponentDTO> getResumenPorAnio(List<ResumenComponentDTO> resumenMonth){
        return resumenMonth.stream()
                .map(resumenComponentDTO -> {
                    Map<String, BigDecimal> amountByYear = resumenComponentDTO.getProjections().stream()
                            .collect(Collectors.groupingBy(
                                    monthProjection -> monthProjection.getMonth().substring(0, 4), // Agrupar por año
                                    Collectors.reducing(BigDecimal.ZERO, MonthProjection::getAmount, BigDecimal::add) // Sumar las cantidades por año
                            ));
                    return new ResumenComponentDTO(resumenComponentDTO.getComponent(), resumenComponentDTO.getAccount(),
                            amountByYear.entrySet().stream()
                                    .map(entry -> new MonthProjection(entry.getKey(), entry.getValue()))
                                    .collect(Collectors.toList()));
                })
                .collect(Collectors.toList());
    }


    private List<ParametersDTO> getListParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .collect(Collectors.toList());
    }

public Map<String, List<Double>> storeAndSortVacationSeasonality(List<ParametersDTO> vacationSeasonalityList, String period, Integer range) {
    // Ordenar la lista por el período
    vacationSeasonalityList.sort(Comparator.comparing(ParametersDTO::getPeriod));

    // Crear un nuevo HashMap para almacenar los valores
    Map<String, List<Double>> vacationSeasonality = new HashMap<>();

    // Calcular el año y mes de inicio
    int startYear = Integer.parseInt(period.substring(0, 4));
    int startMonth = Integer.parseInt(period.substring(4, 6));

    // Calcular el año y mes de finalización
    int endYear = startYear + (startMonth + range - 1) / 12;
    int endMonth = (startMonth + range - 1) % 12 + 1;

    // Iterar sobre el rango de años
    for (int year = startYear; year <= endYear; year++) {
        // Crear la clave del mapa en formato "yyyy"
        String key = String.format("%04d", year);

        // Crear una lista de 12 elementos con valor predeterminado 8.33
        List<Double> values = new ArrayList<>(Collections.nCopies(12, 8.33));

        // Buscar en la lista los elementos que coincidan con el año actual
        vacationSeasonalityList.stream()
                .filter(p -> p.getPeriod().startsWith(key))
                .forEach(p -> {
                    // Obtener el mes del período
                    int month = Integer.parseInt(p.getPeriod().substring(4, 6));
                    // Actualizar el valor para el mes correspondiente
                    values.set(month - 1, p.getValue());
                });

        // Agregar los valores encontrados al mapa
        vacationSeasonality.put(key, values);
    }

    return vacationSeasonality;
}

    private void isPeru(List<ProjectionDTO> headcount, ParametersByProjection projection) {
        BaseExternResponse baseExtern = projection.getBaseExtern();
        boolean hasBaseExtern = baseExtern != null && !baseExtern.getData().isEmpty();
        List<ParametersDTO> vacationSeasonalityList = getListParametersById(projection.getParameters(), 40);
        Map<String, List<Double>> vacationSeasonalityValues = vacationSeasonalityList.isEmpty() ? null : storeAndSortVacationSeasonality(vacationSeasonalityList, projection.getPeriod(), projection.getRange());
        //log.debug("vacationSeasonalityList {}", vacationSeasonalityValues);
        //Map<String, List<Double>> vacationSeasonalityValues = null;
                //Map<String, List<Double>> vacationSeasonalityValues = vacationSeasonalityList.isEmpty() ? null : calculateVacationSeasonality(vacationSeasonalityList, projection.getPeriod(), projection.getRange());
        //log.debug("vacationSeasonalityValues {}", vacationSeasonalityValues);
        headcount
                .parallelStream()
                .forEach(headcountData -> {
            if (hasBaseExtern) {
                addBaseExternV2(headcountData, baseExtern, projection.getPeriod(), projection.getRange());
            }
            //log.debug("headcountData {}",headcountData.getPo());
            methodsPeru.salary(headcountData.getComponents(), projection.getParameters(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), projection.getTemporalParameters(), headcountData.getFNac(), vacationSeasonalityValues);
            //methodsPeru.revisionSalary(headcountData.getComponents(), projection.getParameters(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
        });
    }

    private List<ParametersDTO> filterParametersByName(List<ParametersDTO> parameters, String name) {
        return parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getDescription(), name))
                .collect(Collectors.toList());
    }
    private void isMexico(List<ProjectionDTO>  headcount, ParametersByProjection projection){
        RangeBuDTO rangeBuByBU = projection.getTemporalParameters().stream()
                .filter(r -> r.getIdBu().equals(projection.getIdBu()))
                .findFirst()
                .orElse(null);
        Integer idBu = projection.getIdBu();
        //convenio by ParamwtersProjection
        List<Convenio> convenioList = projection.getConvenios() != null ? projection.getConvenios() : new ArrayList<>();
        //convenio bono by ParamwtersProjection
        List<ConvenioBono> convenioBonoList = projection.getConvenioBonos() != null ? projection.getConvenioBonos() : new ArrayList<>();

        convenioCache = new HashMap<>();
        for (Convenio convenio : convenioList) {
            if (convenio != null) {
                String key = convenio.getConvenioName();
                if (key != null) {
                    convenioCache.put(key, convenio);
                }
            }
        }
        //log.debug("convenioCache {}", convenioCache);
        convenioBonoCache = new HashMap<>();
        for (ConvenioBono convenioBono : convenioBonoList) {
            if (convenioBono != null) {
                String key = convenioBono.getConvenioNivel();
                if (key != null) {
                    convenioBonoCache.put(key, convenioBono);
                }
            }
        }
        //log.debug("convenioCache {}", convenioCache);


        Mexico methodsMexico = new Mexico(mexicoService, convenioRepository, convenioBonoRepository);
        //Genera las proyecciones del rango
        List<ParametersDTO> salaryList = filterParametersByName(projection.getParameters(), "Salario Mínimo Mexico");
        List<ParametersDTO> incrementList = filterParametersByName(projection.getParameters(), "Increm Salario Mín");
        List<ParametersDTO> revisionList = filterParametersByName(projection.getParameters(), "Revision Salarial");
        List<ParametersDTO> employeeParticipationList = filterParametersByName(projection.getParameters(), "Participación de los trabajadores");
        List<ParametersDTO> sgmmList = filterParametersByName(projection.getParameters(), "SGMM");
        List<ParametersDTO> dentalInsuranceList = filterParametersByName(projection.getParameters(), "Seguro Dental");
        List<ParametersDTO> lifeInsuranceList = filterParametersByName(projection.getParameters(), "Seguro de Vida");
        List<ParametersDTO> mothProportionParam = filterParametersByName(projection.getParameters(), "Proporción Mensual");
        //Prima Vacacional
        List<ParametersDTO> primaVacacionalParam = filterParametersByName(projection.getParameters(), "Prima Vacacional");
        //%Aporte Cta SER empresa
        List<ParametersDTO> aportacionCtaSEREmpresa = filterParametersByName(projection.getParameters(), "Aportación Cta SER Empresa");
        //Dias prov aguinaldo
        List<ParametersDTO> diasProvAguinaldo = filterParametersByName(projection.getParameters(), "Dias Prov Aguinaldo");
        //log.info("diasProvAguinaldo {}",diasProvAguinaldo);
        //Provision Prima Vacacional SER
        List<ParametersDTO> provisionPrimaVacacionalSER = filterParametersByName(projection.getParameters(), "Provision Prima Vacacional SER");
        //Tope mensual - Fondo Ahorro
        List<ParametersDTO> topeMensualFondoAhorro = filterParametersByName(projection.getParameters(), "Tope Mensual - Fondo Ahorro");
        //Tope sueldo - Fondo Ahorro
        List<ParametersDTO> topeSueldoFondoAhorro = filterParametersByName(projection.getParameters(), "Tope Sueldo - Fondo Ahorro");
        //UMA mensual
        List<ParametersDTO> umaMensual = filterParametersByName(projection.getParameters(), "UMA");
        //% Provisión Fondo de Ahorro
        List<ParametersDTO> provisionFondoAhorro = filterParametersByName(projection.getParameters(), "Provisión Fondo de Ahorro");
        //%Impuesto Estatal
        List<ParametersDTO> stateTax = filterParametersByName(projection.getParameters(), "Impuesto Estatal");
        //Calcular la suma total de todos los salarios de la plantilla
        //Prov Retiro (IAS)
        List<ParametersDTO> provisionRetiroIAS = filterParametersByName(projection.getParameters(), "Prov Retiro (IAS)");
        //vales de despensa
        List<ParametersDTO> valesDespensa = filterParametersByName(projection.getParameters(), "vales");
        headcount.stream()
                //.filter(h -> h.getPo().equals("PO10039174"))
                .parallel()
                .forEach(headcountData -> {
                    //log.info("getPo {}  -  isCp {}",headcountData.getPo(), headcountData.getPoName().contains("CP"));
                    //log.debug("getPo {} - Salary {}",headcountData.getPo(), headcountData.getComponents().stream().filter(c->c.getPaymentComponent().equals("PC938003") || c.getPaymentComponent().equals("PC938012")).mapToDouble(c->c.getAmount().doubleValue()).max().getAsDouble());
                    //log.debug("getPo {} - Salary {}",  headcountData.getConvent());
                    //log.debug("getPo {} - Salary {}",  headcountData.getLevel());
                    String convenioNivel = headcountData.getConvent() + headcountData.getLevel();
                    String convenio = headcountData.getConvent();
                    //log.debug("convenioNivel {}",convenioNivel);
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.salary(component, salaryList, incrementList, revisionList, projection.getPeriod(), projection.getRange(), headcountData.getPoName());
                    methodsMexico.provAguinaldo(component, projection.getPeriod(), projection.getRange());
                    methodsMexico.provVacacionesRefactor(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),  headcountData.getFContra(), headcountData.getFNac(), rangeBuByBU, idBu, headcountData.getPo());
                    methodsMexico.valesDeDespensa(component, valesDespensa, umaMensual, projection.getPeriod(), projection.getRange());
                    methodsMexico.performanceBonus(component, headcountData.getPoName(), convenioNivel, projection.getPeriod(), projection.getRange(), convenioBonoCache);
                    methodsMexico.seguroSocial(component, convenio, projection.getPeriod(), projection.getRange(),convenioCache);
                    methodsMexico.seguroSocialRetiro(component, convenio, projection.getPeriod(), projection.getRange(), convenioCache);
                    methodsMexico.seguroSocialInfonavit(component, convenio, projection.getPeriod(), projection.getRange(), convenioCache);
                    methodsMexico.primaVacacional(component, primaVacacionalParam, projection.getPeriod(), projection.getRange());
                    methodsMexico.aportacionCtaSEREmpresa(component, aportacionCtaSEREmpresa, projection.getPeriod(), projection.getRange(), headcountData.getPoName(), headcountData.getFNac(), headcountData.getFContra());
                    methodsMexico.provisionAguinaldoCtaSER(component, diasProvAguinaldo, projection.getPeriod(), projection.getRange());
                    methodsMexico.provisionPrimaVacacionalSER(component, provisionPrimaVacacionalSER, projection.getPeriod(), projection.getRange());
                    methodsMexico.provisionFondoAhorro(component, topeMensualFondoAhorro, topeSueldoFondoAhorro, umaMensual, provisionFondoAhorro, projection.getPeriod(), projection.getRange());
                    methodsMexico.compensacion(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), mothProportionParam);
                    methodsMexico.disponibilidad(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), mothProportionParam);
                    /*methodsMexico.disponibilidad(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), mothProportionParam);*/
                    methodsMexico.gratificacion(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.gratificacionExtraordinaria(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.trabajoExtenso(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.trabajoGravable(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.parteExentaFestivoLaborado(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.parteGravableFestivoLaborado(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    //methodsMexico.parteGravableFestivoLaborado(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.primaDominicalGravable(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.mudanza(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.vidaCara(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.primaDominicalExenta(component, projection.getParameters(), projection.getPeriod(), projection.getRange(),mothProportionParam);
                    methodsMexico.calculateStateTax(component, stateTax, projection.getPeriod(), projection.getRange());
                    if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                        addBaseExtern(headcountData,projection.getBaseExtern(),
                                projection.getPeriod(),projection.getRange());
                    }
                });
                double totalSalarios = calcularTotalSalarios(headcount, "CP", true);
                double totalSalariosNoCP = calcularTotalSalariosNoCP(headcount, "CP", false);
        headcount.stream()
                        .parallel()
                        .forEach(headcountData -> {
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.participacionTrabajadores(component, employeeParticipationList, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.seguroDental(component,dentalInsuranceList, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.seguroVida(component, lifeInsuranceList, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.provisionSistemasComplementariosIAS(component, provisionRetiroIAS, projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.SGMM(component, sgmmList, projection.getPeriod(), projection.getRange(), headcountData.getPoName(), totalSalariosNoCP);
                });
        //log.debug("headcount {}",headcount);
    }
    public double calcularTotalSalarios(List<ProjectionDTO> headcount, String type, boolean includeType) {
        return headcount.stream()
                .flatMap(h -> h.getComponents().stream())
                .filter(c -> c.getPaymentComponent().equals("SALARY"))
                .map(PaymentComponentDTO::getProjections)
                .map(projections -> projections.get(projections.size() - 1))
                .mapToDouble(p -> p.getAmount().doubleValue())
                .sum();
    }
    //calcularTotalSalarios NO CP
    public double calcularTotalSalariosNoCP(List<ProjectionDTO> headcount, String type, boolean includeType) {
        return headcount.stream()
                .filter(h -> !h.getPoName().contains("CP"))
                .flatMap(h -> h.getComponents().stream())
                .filter(c -> c.getPaymentComponent().equals("SALARY"))
                .map(PaymentComponentDTO::getProjections)
                .map(projections -> projections.get(projections.size() - 1))
                .mapToDouble(p -> p.getAmount().doubleValue())
                .sum();
    }
    private void isColombia( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        //getRangeBuDetails value is equal to 1
        RangeBuDTO rangeBuByBU = projection.getTemporalParameters().stream()
                .filter(r -> r.getIdBu().equals(projection.getIdBu()))
                .findFirst()
                .orElse(null);
        //log.debug("rangeBuByBU {}",rangeBuByBU);
        List<RangeBuDetailDTO> rangeBuDetail = rangeBuByBU != null ? rangeBuByBU.getRangeBuDetails() : new ArrayList<>();
        //log.debug("rangeBuDetail {}",rangeBuDetail);
        //log.debug("excludedPositions {}",excludedPositions);
        Colombia methodsColombia = new Colombia();
        //SUMATORIA LOS COMPONENTES  PC938003 / PC938012
        double sum = headcount.parallelStream()
                .filter(h -> !h.getClassEmployee().equals("T"))
                .mapToDouble(headcountData -> headcountData.getComponents().stream()
                        .filter(c -> Objects.equals(c.getPaymentComponent(), "PC938012") || Objects.equals(c.getPaymentComponent(), "PC938003"))
                        .mapToDouble(c -> c.getAmount().doubleValue())
                        .max().orElse(0.0))
                .sum();

        BigDecimal totalSum = BigDecimal.valueOf(sum);

        //log.debug("Sunm -> {}",sum.get());
        List<ParametersDTO> salaryList = filterParametersByName(projection.getParameters(), "Salario mínimo legal");
        List<ParametersDTO> salaryIntegralsList = filterParametersByName(projection.getParameters(), "Salario mínimo Integral");
        List<ParametersDTO> revisionList = filterParametersByName(projection.getParameters(), "%Inc Rev Salarial");
        List<ParametersDTO> revisionEttList = filterParametersByName(projection.getParameters(), "%Inc Plantilla ETT");
        List<ParametersDTO> salaryPraList = filterParametersByName(projection.getParameters(), "Salario Estudiante PRA");
        //comisiones
        List<ParametersDTO> commissionList = filterParametersByName(projection.getParameters(), "Comisiones (anual)");
        List<ParametersDTO> sodexoList = filterParametersByName(projection.getParameters(), "Sodexo");
        List<ParametersDTO> transportSubsidyList = filterParametersByName(projection.getParameters(), "Subsidio de Transporte");
        //ParametersDTO digitalConnectivityAid = getParametersById(parameters, 51);
        List<ParametersDTO> digitalConnectivityList = filterParametersByName(projection.getParameters(), "Auxilio Conectividad Digital");
        //SSBono
        List<ParametersDTO> ssBonoList = filterParametersByName(projection.getParameters(), "SSBono");
        //Días vacaciones provisionados
        List<ParametersDTO> vacationDaysProvisioned = filterParametersByName(projection.getParameters(), "Dias vacaciones provisionados");
        //Factor ajuste HHEE
        List<ParametersDTO> factorAjusteHHEE = filterParametersByName(projection.getParameters(), "Factor ajuste HHEE/Recargos");
        //log.debug("commissionList {}",commissionList);
        //log.debug("revisionEttList {}",revisionEttList);
        //log.debug("revisionList {}",revisionList);
        //log.debug("salaryList {}",salaryList);
        //Genera las proyecciones del rango
        // filter po PO90005153
        headcount.stream()
                //.filter(h -> h.getPo().equals("PO10039174"))
                .parallel()
                .forEach(headcountData -> {
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    // LOG PON NAME
                    //log.info("getPoName {}",headcountData.getPo());
                    methodsColombia.salary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), salaryList, revisionList, revisionEttList, salaryIntegralsList);
                    methodsColombia.temporalSalary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), salaryList, revisionList, revisionEttList, salaryIntegralsList,  dataMapTemporal, headcountData.getPo());
                    methodsColombia.salaryPra(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),salaryList, salaryPraList);
                    methodsColombia.revisionSalary(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), headcountData.getClassEmployee());
                    //methodsColombia.commission(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), totalSum, commissionList);
                    methodsColombia.commission(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), totalSum, commissionList, excludedPositionsBC, headcountData.getPoName());
                    methodsColombia.prodMonthPrime(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedVacation(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), vacationDaysProvisioned);
                    methodsColombia.consolidatedSeverance(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceInterest(component, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.contributionBox(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    methodsColombia.transportSubsidy(component, salaryList, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), transportSubsidyList, headcountData.getPo());
                    methodsColombia.companyHealthContribution(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContribution(component, salaryList, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContributionTrainee2(component, salaryPraList, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.icbfContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.senaContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.companyPensionContribution(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.sodexo(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange(), sodexoList, headcountData.getPoName(), rangeBuDetail );
                    methodsColombia.sena(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    //methodsColombia.senaTemporales(component, projection.getParameters(),headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    methodsColombia.uniqueBonus(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    //subsidy list
                    methodsColombia.AuxilioDeTransporteAprendizSena(component, headcountData.getClassEmployee(), salaryPraList, projection.getPeriod(), projection.getRange(), transportSubsidyList);
                    methodsColombia.AuxilioConectividadDigital(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange(), headcountData.getPoName(), rangeBuDetail , digitalConnectivityList);
                    methodsColombia.commissionTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),totalSum, commissionList, dataMapTemporal, headcountData.getPo());
                    methodsColombia.prodMonthPrimeTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedVacationTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceInterestTemporal(component, projection.getParameters(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    methodsColombia.transportSubsidyTemporaries(component, salaryList, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), transportSubsidyList);
                    methodsColombia.companyHealthContributionTemporals(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContributionTemporals(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.contributionBoxTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.icbfContributionTemporaries(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.senaContributionTemporaries(component, headcountData.getClassEmployee(), salaryList, projection.getPeriod(), projection.getRange());
                    methodsColombia.companyPensionContributionTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.feeTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.socialSecurityUniqueBonus(component, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), ssBonoList);
                    methodsColombia.overtime(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), factorAjusteHHEE);
                    methodsColombia.surcharges(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), factorAjusteHHEE);
                    if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                        addBaseExtern(headcountData,projection.getBaseExtern(),
                                projection.getPeriod(),projection.getRange());
                    }
                });
    }

    private void isUruguay( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        UruguayRefactor methodsUruguay = new UruguayRefactor();
        //Genera las proyecciones del rango
        //%Aumento por Consejo de Salario
        List<ParametersDTO> salaryIncreaseList = filterParametersByName(projection.getParameters(), "Aumento por Consejo de Salario");
        //%Rev x Inflación
        List<ParametersDTO> inflationList = filterParametersByName(projection.getParameters(), "Rev x Inflación");
        //Factor ajuste HHEE
        List<ParametersDTO> factorAjusteHHEE = filterParametersByName(projection.getParameters(), "Factor ajuste HHEE");
        //Factor ajuste Guardias
        List<ParametersDTO> factorAjusteGuardias = filterParametersByName(projection.getParameters(), "Factor ajuste Guardias");
       //Aumento valor Ticket alimentación
        List<ParametersDTO> aumentoValorTicketAlimentacion = filterParametersByName(projection.getParameters(), "Aumento valor Ticket alimentación");
        //Aumento valor SUAT
        List<ParametersDTO> aumentoValorSUAT = filterParametersByName(projection.getParameters(), "Aumento valor SUAT");
        //Impuesto BSE
        List<ParametersDTO> impuestoBSE = filterParametersByName(projection.getParameters(), "Impuesto BSE");
        //Días provisionales de vacaciones (al año)
        List<ParametersDTO> diasProvisionalesVacaciones = filterParametersByName(projection.getParameters(), "Días provisionales de vacaciones (al año)");
        //Montepío
        List<ParametersDTO> montepio = filterParametersByName(projection.getParameters(), "Montepío");
        //Fonasa
        List<ParametersDTO> fonasa = filterParametersByName(projection.getParameters(), "Fonasa");
        //FRL
        List<ParametersDTO> frl = filterParametersByName(projection.getParameters(), "FRL");
        //FGCL
        List<ParametersDTO> fgcl = filterParametersByName(projection.getParameters(), "FGCL");
        headcount
                .stream()
                .parallel()
                .forEach(headcountData -> {
                    try {
                        //log.info("getPoName {}",headcountData.getPo());
                        methodsUruguay.salary(headcountData.getComponents(), salaryIncreaseList, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), inflationList);
                        methodsUruguay.overtime(headcountData.getComponents(),headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteHHEE);
                        methodsUruguay.activeGuard(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteGuardias);
                        methodsUruguay.specialGuard(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteGuardias);
                        methodsUruguay.legalGuard(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteGuardias);
                        methodsUruguay.quarterlyBonus(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.quarterlyBonus8(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.monthlyBonus(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.monthlyBonus15(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //annualBonus
                        methodsUruguay.annualBonus(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //salesBonus
                        methodsUruguay.salesBonus(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //salesCommissions
                        methodsUruguay.salesCommissions(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //collectionCommissions
                        methodsUruguay.collectionCommissions(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //foodTicket
                        methodsUruguay.foodTicket(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), aumentoValorTicketAlimentacion);
                        //SUAT
                        //methodsUruguay.suat(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), aumentoValorSUAT);
                        //BSE
                        //methodsUruguay.bcBs(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), impuestoBSE);
                        //metlife
                        //methodsUruguay.metlife(headcountData.getComponents(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), salaryIncreaseList);
                    } catch (Exception e) {
                        log.error("Exception occurred in method for headcountData: " + headcountData, e);
                    }
                });
    }
    private void isEcuador( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        Ecuador methodsEcuador = new Ecuador();
        projection.getParameters()
                .sort((o1, o2) -> Shared.compare(o1.getPeriod(),o2.getPeriod()));
        //Genera las proyecciones del rango
        for (ProjectionDTO projectionDTO : headcount) {
            List<PaymentComponentDTO> component = projectionDTO.getComponents();
            if (!projection.getParameters().isEmpty()) {
                for (int j = 0; j < projection.getParameters().stream().filter(q -> q.getPeriod() != null).count(); j++) {
                    component = validate(methodsEcuador, component, projection.getParameters().get(j), projection.getParameters());
                }
            }
            //parametros que no tienen periodo que se ejecutan siempre
            methodsEcuador.srv(component, projection.getParameters());
            methodsEcuador.addDecimoCuarto(component, projection.getPeriod(), projection.getParameters(), projection.getRange());
            methodsEcuador.iess(component, projection.getPeriod(), projection.getParameters(), projection.getRange());
            methodsEcuador.decimoTercero(component, projection.getPeriod(), projection.getParameters(), projection.getRange());
            methodsEcuador.fondoReserva(component, projection.getPeriod(), projection.getParameters(), projection.getRange());
            methodsEcuador.vacations(component, projection.getPeriod(), projection.getParameters(), projection.getRange());
            if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                addBaseExtern(projectionDTO,projection.getBaseExtern(),
                    projection.getPeriod(),projection.getRange());
            }
        }
    }
    private void  addBaseExtern(ProjectionDTO headcount , BaseExternResponse baseExtern,String period, Integer range){
        Map<String, Object>  po = baseExtern.getData().stream().filter(u->u.get("po")
                .equals(headcount.getPo())).findFirst().orElse(null);
       List<PaymentComponentDTO> bases= baseExtern.getHeaders().stream().
               filter(t-> Arrays.stream(headers).noneMatch(c->c.equalsIgnoreCase(t))).map(
               p->
                       PaymentComponentDTO.builder()
                       .paymentComponent(p)
                       .amount(BigDecimal.valueOf(po!=null && po.get(p)!=null?Double.parseDouble(po.get(p).toString()):0))
                       .projections(Shared.generateMonthProjection(period,range, BigDecimal.valueOf(po!=null&&
                               po.get(p)!=null?Double.parseDouble(po.get(p).toString()):0)))
                       .build()
       ).collect(Collectors.toList());
        List<PaymentComponentDTO> combined = new ArrayList<>(headcount.getComponents());
        combined.addAll(bases);
        headcount.setComponents(combined);
        log.debug("headcount.getComponents() {}",headcount.getComponents());
    }
    private void addBaseExternV2(ProjectionDTO headcount, BaseExternResponse baseExtern, String period, Integer range) {
        Map<String, Map<String, Object>> baseExternMap = baseExtern.getData().stream()
                .collect(Collectors.toMap(u -> (String) u.get("po"), Function.identity()));
        Map<String, Object> po = baseExternMap.get(headcount.getPo());
        List<PaymentComponentDTO> bases = baseExtern.getHeaders().stream()
                .filter(t -> Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(t)))
                .map(p -> {
                    if (p.equalsIgnoreCase("promo")) {
                        return PaymentComponentDTO.builder()
                                .paymentComponent(p)
                                .amountString(po != null && po.get(p) != null ? po.get(p).toString() : null)
                                .build();
                    } else {
                        return PaymentComponentDTO.builder()
                                .paymentComponent(p)
                                .amount(BigDecimal.valueOf(po != null && po.get(p) != null ? Double.parseDouble(po.get(p).toString()) : 0))
                                .projections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(po != null && po.get(p) != null ? Double.parseDouble(po.get(p).toString()) : 0)))
                                .build();
                    }
                })
                .collect(Collectors.toList());
        headcount.getComponents().addAll(bases);
    }
    @Override
    public Config getComponentByBu(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
        List<Convenio> convenio = convenioRepository.findAll();
        List<ConvenioBono> convenioBono = convenioBonoRepository.findAll();
        return Config.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vTemporal(buService.getAllBuWithRangos(vbu.getId()))
                .convenios(convenio)
                .convenioBonos(convenioBono)
                .vDefault(parameterDefaultRepository.findByBu(vbu.getId()))
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .baseExtern(baseExternRepository.findByBu(vbu.getId())
                        .stream()
                        .map(c->OperationResponse
                            .builder()
                                .code(c.getCode())
                                .name(c.getName())
                                .bu(c.getBu())
                                .isInput(c.getIsInput())
                            .build()
                        )
                        .collect(Collectors.toList()))
                .current(vbu.getCurrent())
                .build();
    }


    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Boolean saveProjection(ParameterHistorial projection,String email) {

            HistorialProjection historial = new HistorialProjection();
            historial.setBu(projection.getBu());
            historial.setName(projection.getName());
            historial.setVDate(new Date());
            historial.setVRange(projection.getRange());
            historial.setIdBu(projection.getIdBu());
            historial.setVPeriod(projection.getPeriod());
            historial.setIsTop(projection.getIsTop());
            historial.setCreatedAt(new Date());
            historial.setCreatedBy(email);
            historial.setNominaFrom(projection.getNominaFrom().replace("/",""));
            historial.setNominaTo(projection.getNominaTo().replace("/",""));
            historial = historialProjectionRepository.save(historial);
            HistorialProjection finalHistorial = historial;
            List<ParameterProjection> parameters = projection.getParameters().stream().map(p-> new ParameterProjection(
              null, finalHistorial.getId(),
                    p.getParameter().getId(),
                    p.getValue(),
                    Objects.equals(p.getPeriod(), "") ?null: p.getPeriod(),
                    Objects.equals(p.getRange(), "") ?null:p.getRange(),
                    p.getIsRetroactive(),
                    Objects.equals(p.getPeriodRetroactive(), "") ?null:p.getPeriodRetroactive()
            )).collect(Collectors.toList());
            parameterProjectionRepository.saveAll(parameters);
            List<DisabledPoHistorical> disabledPoHistoricals = projection.getDisabledPo().stream().map(c->
                    DisabledPoHistorical.builder().
                            idssff(c.getIdssff()).
                            idProjectionHistorial(finalHistorial.getId()).
                            po(c.getPo()).
                            periodFrom(c.getFrom()).periodTo(c.getTo()).
                            build()).collect(Collectors.toList());
            disabledPoHistorialRepository.saveAll(disabledPoHistoricals);
        //TODO: AGREGAR IDENTIFICADOR PARA USUARIO -> urgente
        // Obtiene los parámetros atemporales y los transforma en rangos históricos
        List<RangeBuDTO> parametrosAtemporales = buService.getAllBuWithRangos(projection.getIdBu());
        List<RangoBuPivotHistorical> rangosHistoricos = parametrosAtemporales.stream().map(r -> {
            RangoBuPivotHistorical rango = new RangoBuPivotHistorical();
            rango.setIdHistorial(finalHistorial.getId());
            // Aquí solo debemos configurar el rango, no los detalles, ya que todavía no hemos persistido 'rango'
            return rango;
        }).collect(Collectors.toList());

        // Guardamos los rangos históricos en la base de datos para que se genere su ID
        rangosHistoricos = rangoBuPivotHistoricalRepository.saveAll(rangosHistoricos);

        // Ahora, ya con los IDs generados, podemos asignar los detalles
        rangosHistoricos.forEach(rango -> {
            // Encuentra el DTO correspondiente con este rango
            RangeBuDTO rDto = parametrosAtemporales.stream()
                    .filter(rangoDto -> rangoDto.getIdBu().equals(rango.getIdHistorial()))
                    .findFirst()
                    .orElse(null);

            if (rDto != null) {
                List<RangoBuPivotHistoricalDetail> detalles = rDto.getRangeBuDetails().stream().map(d -> {
                    RangoBuPivotHistoricalDetail detail = new RangoBuPivotHistoricalDetail();
                    detail.setRangoBuPivotHistorical(rango); // Usamos la entidad, no el ID
                    detail.setRange(d.getRange());
                    detail.setValue(d.getValue());
                    detail.setIdPivot(d.getIdPivot());
                    return detail;
                }).collect(Collectors.toList());

                // Guardamos los detalles del rango histórico en la base de datos
                rangoBuPivotHistoricalDetailRepository.saveAll(detalles);
            }
        });

        //ADD IF EXTERN IS NOT NULL
            if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
               List<PoHistorialExtern> extern= new ArrayList<>();

                for (Map<String, Object> data: projection.getBaseExtern().getData()) {
                    for (String header: projection.getBaseExtern().getHeaders().stream()
                            .filter(f->Arrays.stream(headers).noneMatch(c->c.equalsIgnoreCase(f))
                                ).collect(Collectors.toList())) {
                        if(data.get(header)!=null){
                            extern.add(PoHistorialExtern.builder()
                                    .po(data.get("po").toString())
                                    .baseExtern(header)
                                    .idHistorial(historial.getId())
                                    .nvalue(Shared.encriptar(data.get(header).toString()))
                                    .build());
                        }

                    }
                }
                JsonTemp json = jsonDatosRepository.save(JsonTemp.builder().datosJson(Shared.convertArrayListToJson(extern)).build());
                sharedRepo.insertHistorialExtern(json.getId(),1);
            }

        if(projection.getBc()!=null &&!projection.getBc().getData().isEmpty()){
            List<BusinessCaseHistorial> extern= new ArrayList<>();
            for (Map<String, Object> data: projection.getBc().getData()) {
                for (String header: projection.getBc().getHeaders().stream()
                        .filter(f->!f.equalsIgnoreCase(HEADERPO)).collect(Collectors.toList())) {
                    if(data.get(header)!=null){
                        extern.add(BusinessCaseHistorial.builder()
                                .po(data.get(HEADERPO).toString())
                                .component(header)
                                .idHistorial(historial.getId())
                                .nvalue(Shared.encriptar(data.get(header).toString()))
                                .build());
                    }

                }
            }
            JsonTemp json = jsonDatosRepository.save(JsonTemp.builder().datosJson(Shared.convertArrayListToJson(extern)).build());
            sharedRepo.insertHistorialExtern(json.getId(),2);
        }

            return true;


    }

    @Override
    public List<HistorialProjectionDTO> getHistorial(String email) {
        return historialProjectionRepository.findByCreatedByOrderByCreatedAtDesc(email).stream().map(
                p->HistorialProjectionDTO.builder()
                        .bu(p.getBu())
                        .id(p.getId())
                        .name(p.getName())
                        .vDate(p.getVDate())
                        .vRange(p.getVRange())
                        .vPeriod(p.getVPeriod())
                        .createdAt(p.getCreatedAt())
                        .isTop(p.getIsTop())
                .build()).collect(Collectors.toList());
    }

    @Override
    public ProjectionInformation getHistorialProjection(Integer  id) {
        List<ParameterProjectionBD> parameters = sharedRepo.getParameter_historical(id);

        List<DisabledPoDTO> disabledPoDTOS = disabledPoHistorialRepository.findByIdProjectionHistorial(id).stream().map(c->
                new DisabledPoDTO(c.getPo(),c.getIdssff(),c.getPeriodFrom(),c.getPeriodTo())).collect(Collectors.toList());
        BaseExternResponse response = BaseExternResponse.builder()
                                .data(new ArrayList<>()).headers(new ArrayList<>()).build();
        BaseExternResponse bc = BaseExternResponse.builder()
                .data(new ArrayList<>()).headers(new ArrayList<>()).build();

        List<PoHistorialExtern> baseExtern= poHistorialExternRepository.findByIdHistorial(id);
        if(!baseExtern.isEmpty()){
            response.setHeaders(baseExternRepository.findByBu(parameters.get(0).getIdbu())
                    .stream().map(BaseExtern::getCode).collect(Collectors.toList()));
            response.getHeaders().addAll(Arrays.asList(headers));
            List<Map<String,Object>> data = new ArrayList<>();
            baseExtern.forEach(t-> data.stream().filter(e->e.get("po").equals(t.getPo())).findFirst().ifPresentOrElse(r->
                r.put(t.getBaseExtern(),Double.parseDouble(Shared.desencriptar(t.getNvalue())))
            ,()->{
                Map<String, Object> map = new HashMap<>();
                map.put("po",t.getPo());
                map.put(t.getBaseExtern(),Double.parseDouble(Shared.desencriptar(t.getNvalue())));
                data.add(map);
            }));
            response.setData(data);
        }
        List<BusinessCaseHistorial> businessCaseHistorials= businessCaseRepository.findByIdHistorial(id);
        if(!businessCaseHistorials.isEmpty()){
            List<ComponentProjection> componentProjections = sharedRepo.getComponentByBu(parameters.get(0).getVbu());
            List<CodeNomina> nomina =codeNominaRepository.findByIdBu(parameters.get(0).getIdbu());

            bc.setHeaders(componentProjections.stream().filter(ComponentProjection::getIscomponent)
                    .map(ComponentProjection::getName).collect(Collectors.toList()));
            bc.getHeaders().addAll(nomina.stream().map(CodeNomina::getName).collect(Collectors.toList()));
            bc.getHeaders().addAll(List.of(HEADERNAME));
            List<Map<String,Object>> data = new ArrayList<>();
            businessCaseHistorials.forEach(t-> data.stream().filter(e->e.get(HEADERPO).equals(t.getPo())).findFirst().ifPresentOrElse(r->
                            r.put(t.getComponent(),t.getComponent().equals("typeEmployee") ||t.getComponent().equals("name")
                                    ?Shared.desencriptar(t.getNvalue()):
                                    Double.parseDouble(Shared.desencriptar(t.getNvalue())))
                    ,()->{
                        Map<String, Object> map = new HashMap<>();
                        map.put(HEADERPO,t.getPo());
                        map.put(t.getComponent(),t.getComponent().equals("typeEmployee")||t.getComponent().equals("name")?Shared.desencriptar(t.getNvalue()):
                                Double.parseDouble(Shared.desencriptar(t.getNvalue())));
                        data.add(map);
                    }));
            bc.setData(data);
        }


        return ProjectionInformation.builder()
                .parameters(parameters)
                .poDisableds(disabledPoDTOS)
                .baseExtern(response)
                .bc(bc)
                .build();
    }

    private void replaceSlash(ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("/",""));
        projection.getParameters().forEach(k-> {
            k.setPeriod(k.getPeriod()==null?"":  k.getPeriod().replace("/",""));
            k.setRange(k.getRange()==null?"": k.getRange().replace("/",""));
            k.setPeriodRetroactive(k.getPeriodRetroactive()==null?"": k.getPeriodRetroactive().replace("/",""));
        });
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Boolean deleteHistorical(Integer id) {

            parameterProjectionRepository.deleteByIdHistorial(id);
            historialProjectionRepository.deleteById(id);
            return true;
    }
    @Async
    @Override
    public void downloadProjection(ParametersByProjection projection, String userContact, ReportJob job) {
        try {
            Shared.replaceSLash(projection);

            List<ComponentProjection> componentProjections = sharedRepo.getComponentByBu(projection.getBu());

            DataRequest dataBase = DataRequest.builder()
                    .idBu(projection.getIdBu())
                    .idBu(projection.getIdBu())
                    .bu(projection.getBu())
                    .period(projection.getPeriod())
                    .nominaFrom(projection.getNominaFrom())
                    .nominaTo(projection.getNominaTo())
                    .isComparing(false)
                    .build();
            xlsReportService.generateAndCompleteReportAsync(projection,
                    componentProjections, getDataBase(dataBase), userContact, job, userContact);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }
    @Async
    @Override
    public void downloadPlannerAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job) {
        try {
            Shared.replaceSLash(projection);
            Map<String, AccountProjection> componentesMap = new HashMap<>();
            List<AccountProjection> components = getAccountsByBu(idBu) ;
            for (AccountProjection concept : components) {
                componentesMap.put(concept.getVcomponent(), concept);
            }
            List<ProjectionDTO> headcount =  getHeadcount(projection,componentesMap);
            xlsReportService.generateAndCompleteReportAsyncPlanner(headcount, sharedRepo.getAccount(idBu),job,userContact);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }

    @Async
    @Override
    //downloadCdgAsync
    public void downloadCdgAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job) {
        try {
            //buscar bu by id
            Bu bu = buRepository.findById(idBu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
            Shared.replaceSLash(projection);
            Map<String, AccountProjection> componentesMap = new HashMap<>();
            List<AccountProjection> components = getAccountsByBu(idBu) ;
            for (AccountProjection concept : components) {
                componentesMap.put(concept.getVcomponent(), concept);
            }
            List<ProjectionDTO> headcount =  getHeadcount(projection,componentesMap);
            xlsReportService.generateAndCompleteReportAsyncCdg(projection, headcount, bu, sharedRepo.getAccount(idBu),job,userContact);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }
    /*@Override
    public byte[] downloadProjection(ParameterDownload projection) {
        // Reemplaza los "/" en los períodos con ""
        projection.setPeriod(projection.getPeriod().replace("/", ""));
        projection.setNominaFrom(projection.getNominaFrom().replace("/", ""));
        projection.setNominaTo(projection.getNominaTo().replace("/", ""));

        // Obtiene la lista de componentes de proyección
        List<ComponentProjection> components = sharedRepo.getComponentByBu(projection.getBu());

        DataRequest dataInit = DataRequest.builder()
                .idBu(projection.getIdBu())
                .bu(projection.getBu())
                .period(projection.getPeriod())
                .nominaFrom(projection.getNominaFrom())
                .nominaTo(projection.getNominaTo())
                .isComparing(false)
                .build();
        // Obtiene la base de datos principal
        DataBaseMainReponse dataBase = getDataBase(dataInit);

        // Crea una lista para almacenar los futuros de las proyecciones de Excel
        List<CompletableFuture<byte[]>> futures = new ArrayList<>();

        // Para cada componente de proyección, genera una proyección de Excel de manera asíncrona
        for (ComponentProjection component : components) {
            futures.add(ReportService.generateExcelProjectionAsync(projection, Arrays.asList(component), dataBase));
        }

        // Espera a que todas las tareas futuras se completen y fusiona los resultados
        byte[] mergedData;
        try {
            mergedData = ReportService.mergeExcelProjections(futures);
        } catch (RuntimeException e) {
            log.error("Error al generar la proyección de Excel", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar la proyección de Excel", e);
        }

        // Devuelve los datos fusionados
        return mergedData;
    } */

    @Override
    public byte[] downloadProjectionHistorical(Integer id) {
      /*  List<ParameterProjectionBD> parameters = sharedRepo.getParameter_historical(id);
        List<ComponentProjection> components = sharedRepo.getComponentByBu(parameters.get(0).getVbu());
        ParametersByProjection projection = ParametersByProjection
                .builder()
                .idBu(parameters.get(0).getIdbu())
                .bu(parameters.get(0).getVbu())
                .period(parameters.get(0).getHperiod())
                .range(parameters.get(0).getHrange())
                .paymentComponent(components.stream().filter(ComponentProjection::getIscomponent).map(u->PaymentComponentType.builder()
                        .component(u.getComponent())
                        .type(u.getType())
                        .build()).collect(Collectors.toList()))
                .parameters(parameters.stream().map(k->ParametersDTO.builder()
                        .period(k.getVperiod())
                        .type(k.getVtype())
                        .parameter(ParameterDTO.builder().id(k.getId()).typeValor(k.getTvalor())
                                .name(k.getName()).description(k.getDescription()) .build())
                        .value(k.getValue())
                        .isRetroactive(k.getVisretroactive())
                        .periodRetroactive(k.getVperiodretroactive())
                        .range(k.getVrange())
                        .build()).collect(Collectors.toList()))
                .build();
        replaceSlash(projection);*/

        return null;
    }

    @Override
    public DataBaseMainReponse getDataBase(DataRequest data) {
        data.setPeriod(data.getPeriod().replace("/",""));
        data.setNominaTo(data.getNominaTo().replace("/",""));
        data.setNominaFrom(data.getNominaFrom().replace("/",""));

        List<String> entities = legalEntityRepository.findByBu(data.getBu()).stream()
                .map(LegalEntity::getLegalEntity).collect(Collectors.toList());

        List<ComponentProjection> components =sharedRepo.getComponentByBu(data.getBu()).stream().filter(ComponentProjection::getIscomponent)
               .collect(Collectors.toList());
        List<String> typeEmployee = typEmployeeRepository.findByBu(data.getIdBu()).stream().map(TypeEmployeeProjection::getTypeEmployee).collect(Collectors.toList());


        List<HeadcountHistoricalProjection> headcount=  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                String.join(",", entities),data.getPeriod(),
                components.stream().map(ComponentProjection::getComponent).collect(Collectors.joining(",")), String.join(",", typeEmployee));

        if(headcount.isEmpty()){
         return   DataBaseMainReponse.builder().data(new ArrayList<>()).components(components).nominas(new ArrayList<>()).comparing(new ArrayList<>()).build();
        }

        List<CodeNomina> codeNominas = codeNominaRepository.findByIdBu(data.getIdBu());

       List<ComponentNominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,data.getBu(),
               data.getNominaFrom(),data.getNominaTo(),
               codeNominas.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")));
        //log.debug("nominal {}",nominal);

        List<DataBaseResponse> deudasAgrupadas = headcount.stream()
                .collect(Collectors.groupingBy(
                        HeadcountHistoricalProjection::getPosition,
                        Collectors.mapping(deuda -> new ComponentAmount(deuda.getComponent(), BigDecimal.valueOf(deuda.getAmount())), Collectors.toList())
                )).entrySet().stream()
                .map(entry -> {
                    HeadcountHistoricalProjection info = headcount.stream().filter(i->i.getPosition().equalsIgnoreCase(entry.getKey())).findFirst().get();
                    String fechaNac = info.getFnac()!=null?info.getFnac().toString():"";
                    String fechaContra = info.getFcontra()!=null?info.getFcontra().toString():"";
                    return  new DataBaseResponse(entry.getKey(),info.getIdssff(),info.getPoname(),info.getClassemp(), fechaNac, fechaContra, info.getConvent(), info.getLevel(), entry.getValue());
                })
                .collect(Collectors.toList());

       deudasAgrupadas.forEach(u-> u.getComponents().addAll(nominal.stream().filter(k->  k.getID_SSFF().equalsIgnoreCase(u.getIdssff()))
               .map(o->ComponentAmount.builder()
                       .component(o.getCodigoNomina())
                       .amount(BigDecimal.valueOf(o.getImporte()))
                       .build()).collect(Collectors.toList())));

        List<DataBaseResponse> comparing = new ArrayList<>();

       if(Boolean.TRUE.equals(data.getIsComparing())){
           data.setPeriod(data.getPeriodComparing());
           data.setNominaFrom(data.getNominaFromComparing());
           data.setNominaTo(data.getNominaToComparing());
           data.setIsComparing(false);
           comparing= getDataBase(data).getData();
       }
 return  DataBaseMainReponse.builder()
         .data(deudasAgrupadas)
         .components(components)
         .nominas(codeNominas)
         .comparing(comparing)
         .build();
    }

    @Override
    public List<Bu> findByBuAccess(String email) {
        return buRepository.findByBuAccess(email);
    }

    @Override
    public List<AccountProjection> getAccountsByBu(Integer idBu) {
            return sharedRepo.getAccount(idBu);
    }

    @Override
    public List<RosetaDTO> getRoseta(Integer bu) {
        List<PaymentRoseta> data = sharedRepo.getPaymentRosseta(bu);
        Map<String, RosetaDTO> mapNodes = new HashMap<>();

        RosetaDTO raiz = null;
        for (PaymentRoseta object : data) {
            RosetaDTO node = new RosetaDTO(object.getCode(), object.getname());
           if(object.getpayment()!=null) {
               node.getPayment().add(object.getpayment());
               validationRosetta(mapNodes, object, node);
           } else {
               validationRosetta(mapNodes, object, node);
           }

            if (object.getchild() == null) {
                raiz = node;
            } else {
                RosetaDTO padre = mapNodes.get(object.getchild());
                if (padre != null && (padre.getChilds().stream().noneMatch(e->e.getCode().equalsIgnoreCase(node.getCode())))){
                        padre.getChilds().add(node);
                }
            }
        }

        List<RosetaDTO> listaNodos = new ArrayList<>();
        listaNodos.add(raiz);
        return listaNodos;
    }

    private void validationRosetta(Map<String, RosetaDTO> mapaNodos, PaymentRoseta objeto, RosetaDTO nodo) {
        if( objeto.getpayment()!=null && mapaNodos.get(objeto.getCode())!=null &&
                !mapaNodos.get(objeto.getCode()).getPayment().contains(objeto.getpayment())){
            mapaNodos.get(objeto.getCode()).getPayment().add(objeto.getpayment());
        }else{
            mapaNodos.put(objeto.getCode(), nodo);
        }
    }

    @Override
    public Boolean saveMoneyOdin(String po,Integer requirement) {
        final boolean[] resp = {false};
        repository.getCostPo(Constant.KEY_BD,po).ifPresent(e->{

                Frecuently fre=    frecuentlyRepository.findById(e.getFrecuencia()).get();
                double amount = e.getImporte()* fre.getFactor();
            try {
                sharedRepo.saveCostPo(requirement,Shared.encriptar(Double.toString(amount)),e.getMoneda());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            resp[0] =true;

        });
        return resp[0];

    }

    /*@Override
    public byte[] downloadFileType(ParametersByProjection projection,Integer type,Integer idBu) {
        Bu bu = buRepository.findById(idBu).orElseThrow(()-> new BadRequestException("No se encuentra la Bu"));
        Shared.replaceSLash(projection);
        Map<String, AccountProjection> componentesMap = new HashMap<>();
        List<AccountProjection> components =   getAccountsByBu(idBu) ;
        for (AccountProjection concept : components) {
            componentesMap.put(concept.getVcomponent(), concept);
        }
        log.debug("componentesMap {}",componentesMap);
        List<ProjectionDTO> headcount =  getHeadcount(projection,componentesMap);
        return xlsReportService.generateExcelType(headcount,type,bu,sharedRepo.getAccount(idBu));
    }*/


    @Override
    public List<PositionBaseline> getPositionBaseline(String period, String filter,String bu,Integer idBu) {
        List<String> entities = legalEntityRepository.findByBu(bu)
                .stream().map(LegalEntity::getLegalEntity).collect(Collectors.toList());
        List<String> typeEmployee = typEmployeeRepository.findByBu
                (idBu).stream().map(TypeEmployeeProjection::getTypeEmployee).collect(Collectors.toList());

        return repository.findPoBaseline(period, String.join(",", entities),
                String.join(",", typeEmployee),"%"+filter+"%");
    }

    private List<PaymentComponentDTO> validate(Ecuador methods, List<PaymentComponentDTO> componentDTO,ParametersDTO dto ,
                                               List<ParametersDTO> parameters ){
        // PARAMETROS PARA EL SUELDO BASE
        //Revision salarial
        if(dto.getParameter().getId()==1){
           return methods.revisionSalary(componentDTO,dto);
        }
        // Ajuste salario minimo
        if(dto.getParameter().getId()==2){
          return methods.adjustSalaryAdjustment(componentDTO,dto);
        }
        return componentDTO;
    }

    private List<ProjectionDTO> getHeadcountByAccount(ParametersByProjection projection){
        //TODO: ADD MONTH BASE
        List<String> entities = legalEntityRepository.findByBu(projection.getBu()).stream().map(LegalEntity::getLegalEntity).collect(Collectors.toList());
        List<String> typeEmployee = typEmployeeRepository.findByBu(projection.getIdBu()).stream().map(TypeEmployeeProjection::getTypeEmployee).collect(Collectors.toList());
        List<HeadcountProjection> headcount =  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                        String.join(",", entities),projection.getPeriod(),String.join(",",
                                projection.getPaymentComponent().stream().map(PaymentComponentType::getComponent)
                                        .collect(Collectors.joining(","))),String.join(",", typeEmployee))
                .stream().map(e->HeadcountProjection.builder()
                        .position(e.getPosition())
                        .poname(e.getPoname())
                        .idssff(e.getIdssff())
                        .entitylegal(e.getEntitylegal())
                        .gender(e.getGender())
                        .bu(e.getBu())
                        .wk(e.getWk())
                        .division(e.getDivision())
                        .department(e.getDepartment())
                        .component(e.getComponent())
                        .amount(e.getAmount())
                        .fContra(e.getFcontraAsLocalDate().isPresent()?e.getFcontraAsLocalDate().get():null)
                        .fNac(e.getFnacAsLocalDate().isPresent()?e.getFnacAsLocalDate().get():null)
                        .classEmp(e.getClassemp())
                        .divisionName(e.getDivisionname())
                        .areaFuncional(e.getAf())
                        .cCostos(e.getCc())
                        .convent(e.getConvent())
                        .level(e.getLevel())
                        .build()).collect(Collectors.toList());

        List<CodeNomina> codeNominals = codeNominaRepository.findByIdBu(projection.getIdBu());
        List<NominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,projection.getBu(),projection.getNominaFrom(),projection.getNominaTo(),
                codeNominals.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")))
                .stream().map(e->NominaProjection.builder()
                        .idssff(e.getID_SSFF())
                        .codeNomina(e.getCodigoNomina())
                        .importe(e.getImporte())
                        .build())
                .collect(Collectors.toList());
        //log.debug("nominal {}",nominal);
        //log.debug("!projection.getBc() {}", projection.getBc());
        if(!projection.getBc().getData().isEmpty()){
            for (int i = 0; i < projection.getBc().getData().size() ; i++) {
                Map<String,Object> resp = projection.getBc().getData().get(i);
                String pos = resp.get(HEADERPO).toString();
                //dataMapTemporal.put(pos, new HashMap<>(resp));
                headcount
                        .stream()
                        .filter(e->e.getPosition()
                                .equals(pos))
                        .findFirst()
                        .ifPresent(headcount::remove);
                    //List<String> excludedPositionsBC = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : resp.entrySet()) {
                        int finalI = i;
                        projection.getPaymentComponent()
                                .stream()
                                .filter(
                                        t-> {
                                            //log.debug("t.getName() {}",t.getName());
                                            //log.debug("entry.getKey() {}",entry.getKey());
                                            ///log.debug("t.getName().equalsIgnoreCase(entry.getKey()) {}",t.getName().equalsIgnoreCase(entry.getKey()));
                                            return  !entry.getKey().equals(HEADERPO) &&
                                                    t.getName().equalsIgnoreCase(entry.getKey());
                                        }
                                )
                                .findFirst()
                                .ifPresentOrElse(t->
                                        {
                                            String position = resp.get(HEADERPO).toString();
                                            LocalDate fNac = convertToJavaDate(resp.get("FNAC").toString());
                                            LocalDate fContra = convertToJavaDate(resp.get("FCON").toString());
                                           /* log.debug("fNac {}",fNac);
                                            log.debug("fContra {}",fContra);*/
                                          /*  LocalDate fNac = LocalDate.parse(resp.get("FNAC").toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                            LocalDate fContra = LocalDate.parse(resp.get("FCON").toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));*/
                                            headcount.add(HeadcountProjection.builder()
                                                    .position(position)
                                                    .idssff("")
                                                    .poname(resp.get("name").toString())
                                                    .classEmp(resp.get("typeEmployee").toString())
                                                    .fContra(fNac)
                                                    .fNac(fContra)
                                                    .convent(resp.get("CONV").toString())
                                                    .level(resp.get("NIV").toString())
                                                    .component(t.getComponent())
                                                    .amount(Double.parseDouble(resp.get(t.getName()).toString()))
                                                    .build());
                                            excludedPositionsBC.add(position);
                                        },()->
                                                codeNominals.stream().filter(r->!entry.getKey().equals(HEADERPO) &&
                                                        r.getName().equalsIgnoreCase(entry.getKey())).findFirst().ifPresent(q->
                                                        nominal.add(NominaProjection.builder()
                                                                .idssff(String.valueOf(finalI))
                                                                .codeNomina(q.getCodeNomina())
                                                                .importe(Double.parseDouble(resp.get(q.getName()).toString()))
                                                                .build())
                                                )
                                );
                    }


               /* else{
                    List<HeadcountProjection> headcountVacantes =
                            headcount.stream().filter(r->r.getPosition().equals(pos)).collect(Collectors.toList());
                    for (Map.Entry<String, Object> entry : resp.entrySet()) {
                        projection.getPaymentComponent()
                                .stream()
                                .filter(
                                        t-> {
                                            //log.debug("t.getName() {}",t.getName());
                                            //log.debug("entry.getKey() {}",entry.getKey());
                                            ///log.debug("t.getName().equalsIgnoreCase(entry.getKey()) {}",t.getName().equalsIgnoreCase(entry.getKey()));
                                            return  !entry.getKey().equals(HEADERPO) &&
                                                    t.getName().equalsIgnoreCase(entry.getKey());
                                        }
                                )
                                .findFirst()
                                .ifPresentOrElse(t->
                                        headcountVacantes.stream().filter(r->r.getComponent().equals(t.getComponent())).findFirst().ifPresent(
                                                q-> q.setAmount(Double.parseDouble(resp.get(t.getName()).toString()))
                                        ),()->
                                        codeNominals.stream().filter(r->!entry.getKey().equals(HEADERPO) &&
                                                r.getName().equalsIgnoreCase(entry.getKey())).findFirst().ifPresent(y->
                                                nominal.stream().filter(r->r.getCodeNomina().equals(y.getCodeNomina())).findFirst().ifPresent(
                                                        q-> q.setImporte(Double.parseDouble(resp.get(y.getName()).toString()))
                                                )
                                ));
                    }


                }*/
            }
        }
        //log.debug("headcount -> {}", headcount);
        //log.debug("headcountTemporal -> {}",headcountTemporal);
        return new ArrayList<>(headcount
                .stream()
                //ordenar por typeEmployee equals T
                .sorted((p1, p2) -> {
                    if ("T".equals(p1.getClassEmp()) && !"T".equals(p2.getClassEmp())) {
                        return -1;
                    } else if (!"T".equals(p1.getClassEmp()) && "T".equals(p2.getClassEmp())) {
                        return 1;
                    } else {
                        return 0;
                    }
                })
                .collect(Collectors.groupingBy(
                        object -> object.getPosition() + object.getIdssff(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    List<PaymentComponentDTO> projectionsComponent =
                                            projection.getPaymentComponent().stream().map(p ->
                                                    {
                                                        PaymentComponentDTO r = PaymentComponentDTO.builder()
                                                                .paymentComponent(p.getComponent())
                                                                .type(p.getType())
                                                                .amount(BigDecimal.ZERO)
                                                                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                                                                .build();
                                                        list.stream().filter(t-> t.getComponent() !=null && t.getComponent().equalsIgnoreCase(p.getComponent())).findFirst().ifPresent(u->{
                                                                if (u.getAmount() != null) {
                                                                    r.setPaymentComponent(p.getComponent());
                                                                    r.setType(p.getType());
                                                                    r.setAmount(BigDecimal.valueOf(u.getAmount()));
                                                                    r.setProjections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(u.getAmount())));
                                                                } else {
                                                                    throw new BadRequestException("No se encuentra información para el mes base " + projection.getPeriod());
                                                                }
                                                                });
                                                      return r;
                                                    }

                                            ).collect(Collectors.toList());
                                    //log.debug("projectionsComponent {}",projectionsComponent);
                                    if (nominaPaymentComponentLinksCache == null) {
                                        List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll();
                                        nominaPaymentComponentLinksCache = allLinks.stream()
                                                .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));
                                    }
                                    //log.debug("nominaPaymentComponentLinksCache: {}", nominaPaymentComponentLinksCache);
                                    Set<String> existingNominaCodes = nominaPaymentComponentLinksCache.keySet();
                                    List<NominaProjection> filteredNominal = nominal.stream()
                                            //.filter(g -> g.getIdssff().equalsIgnoreCase(list.get(0).getIdssff()))
                                            .filter(h -> existingNominaCodes.contains(h.getCodeNomina()))
                                            .collect(Collectors.toList());
                                    //log.debug("filteredNominal: {}", filteredNominal);
                                    addNominal(projection,projectionsComponent,filteredNominal,codeNominals,list);
                                    //log.info("projectionsComponent {}",projectionsComponent);
                                    return new ProjectionDTO(
                                            list.get(0).getIdssff(),
                                            list.get(0).getPosition(),
                                            list.get(0).getPoname(),
                                            projectionsComponent,
                                            list.get(0).getClassEmp(),
                                            list.get(0).getFNac(),
                                            list.get(0).getFContra(),
                                            list.get(0).getAreaFuncional(),
                                            list.get(0).getDivisionName(),
                                            list.get(0).getCCostos(),
                                            list.get(0).getConvent(),
                                            list.get(0).getLevel()
                                    );
                                }
                        )
                ))
                .values());





    }
    public static LocalDate convertToJavaDate(String value) {
        try {
            // Intenta convertir el valor a Double
            double excelDate = Double.parseDouble(value);
            // Si no se lanza una excepción, entonces el valor es numérico
            return convertExcelDateToJavaDate(excelDate);
        } catch (NumberFormatException e) {
            // Si se lanza una excepción, entonces el valor no es numérico
            return convertStringToLocalDate(value);
        }
    }
    public static LocalDate convertExcelDateToJavaDate(double excelDate) {
        return LocalDate.of(1900, 1, 1).plusDays((long) excelDate - 2);
    }
    public static LocalDate convertStringToLocalDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            return LocalDate.parse(dateString, formatter);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El valor proporcionado no es una fecha válida: " + dateString);
        }
    }
    private HeadcountProjection convertToHeadcountProjection(HeadcountHistoricalProjection e) {
        try {
            return HeadcountProjection.builder()
                    .position(e.getPosition())
                    .poname(e.getPoname())
                    .idssff(e.getIdssff())
                    .entitylegal(e.getEntitylegal())
                    .gender(e.getGender())
                    .bu(e.getBu())
                    .wk(e.getWk())
                    .division(e.getDivision())
                    .department(e.getDepartment())
                    .component(e.getComponent())
                    .amount(e.getAmount())
                    .fContra(e.getFcontraAsLocalDate().isPresent()?e.getFcontraAsLocalDate().get():null)
                    .fNac(e.getFnacAsLocalDate().isPresent()?e.getFnacAsLocalDate().get():null)
                    .classEmp(e.getClassemp())
                    .divisionName(e.getDivisionname())
                    .areaFuncional(e.getAf())
                    .cCostos(e.getCc())
                    .build();
        } catch (NumberFormatException ex) {
            log.debug("Error al convertir la proyección para la posición: {}", e.getPosition());
            //throw new BadRequestException(String.format("Error al convertir la proyección para la posición, por que el valor no es valido: %s, %s", e.getPosition(), e.getAmount()));
            throw ex;
        }
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
    private void addNominal(ParametersByProjection projection, List<PaymentComponentDTO> projectionsComponent,
                            List<NominaProjection> nominal , List<CodeNomina> codeNominas ,
                            List<HeadcountProjection> list ){
        if (projection.getBu().equalsIgnoreCase("T. ECUADOR")) {
            //Nomina
            double hhee = 0.0;
            double guarderia =0.0;

            for(NominaProjection h : nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList()) ){
                if(!"0260".equalsIgnoreCase(h.getCodeNomina())){
                    hhee+=h.getImporte() != null ? h.getImporte() : 0.0;
                }else if(h.getCodeNomina().equalsIgnoreCase("0260")){
                    guarderia=h.getImporte() != null ? h.getImporte() : 0.0;
                }
            }
            projectionsComponent.add(PaymentComponentDTO.builder().
                    type(7).
                    paymentComponent("TURN").amount(BigDecimal.valueOf(hhee))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),BigDecimal.valueOf(hhee))).build());
            projectionsComponent.add(PaymentComponentDTO.builder().
                    type(12).
                    paymentComponent("260").amount(BigDecimal.valueOf(guarderia))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),BigDecimal.valueOf(guarderia))).build());
        }else if(projection.getBu().equalsIgnoreCase("T. COLOMBIA")){

            //validate exist ssff
            //param Factor ajuste de HHEE/Recargo
            List<NominaProjection> nominalBySSFF = nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList());
            if (!nominalBySSFF.isEmpty()) {
                Map<String, Double> componentTotals = new HashMap<>();
                for (NominaProjection h : nominalBySSFF) {
                    List<NominaPaymentComponentLink> nominaPaymentComponentLinks = nominaPaymentComponentLinksCache.get(h.getCodeNomina());
                    if (nominaPaymentComponentLinks != null) {
                        for (NominaPaymentComponentLink link : nominaPaymentComponentLinks) {
                            String component = link.getPaymentComponent().getPaymentComponent();
                            double importe = h.getImporte();
                            componentTotals.put(component, componentTotals.getOrDefault(component, 0.0) + importe);
                        }
                    }
                }
                for (Map.Entry<String, Double> entry : componentTotals.entrySet()) {
                    String component = entry.getKey();
                    double total = entry.getValue();
                    /*if(component.equalsIgnoreCase("HHEE") || component.equalsIgnoreCase("SURCHARGES")){
                        total = total * hheeAdjustmentFactor;
                    }*/
                    if (total > 0) {
                        projectionsComponent.add(buildPaymentComponentDTO(component, total, projection.getPeriod(), projection.getRange()));
                    }
                }
                //log.info("projectionsComponent {}",projectionsComponent);
            }else {
                PaymentComponentDTO transferAssistanceComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("AUXILIO_TRASLADO")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),BigDecimal.ZERO))
                        .build();
                PaymentComponentDTO hheeComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("HHEE_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .build();
                PaymentComponentDTO recargoComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("SURCHARGES_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .build();
                PaymentComponentDTO housingComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("AUXILIO_VIVIENDA")
                        .amount((BigDecimal.ZERO))
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .build();
                PaymentComponentDTO bearingComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("AUXILIO_RODAMIENTO")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .build();
                projectionsComponent.add(hheeComponent);
                projectionsComponent.add(recargoComponent);
                projectionsComponent.add(transferAssistanceComponent);
                projectionsComponent.add(housingComponent);
                projectionsComponent.add(bearingComponent);
            }
            //log.info("projectionsComponent {}",projectionsComponent);
        } else if (projection.getBu().equalsIgnoreCase("T. MEXICO") ) {
          /*  List<NominaProjection> nominalBySSFF = nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase("1001906")).collect(Collectors.toList());*/
            List<NominaProjection> nominalBySSFF = nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList());
            //log.info("ssff {}",list.get(0).getIdssff());
            if (!nominalBySSFF.isEmpty()) {
                Map<String, Double> componentTotals = new HashMap<>();
                for (NominaProjection h : nominalBySSFF) {
                    List<NominaPaymentComponentLink> nominaPaymentComponentLinks = nominaPaymentComponentLinksCache.get(h.getCodeNomina());
                    if (nominaPaymentComponentLinks != null) {
                        for (NominaPaymentComponentLink link : nominaPaymentComponentLinks) {
                            String component = link.getPaymentComponent().getPaymentComponent();
                            double importe = h.getImporte();
                            componentTotals.put(component, componentTotals.getOrDefault(component, 0.0) + importe);
                        }
                    }
                }
                for (Map.Entry<String, Double> entry : componentTotals.entrySet()) {
                    String component = entry.getKey();
                    double total = entry.getValue();
                    if (total > 0) {
                        projectionsComponent.add(buildPaymentComponentDTO(component, total, projection.getPeriod(), projection.getRange()));
                    }
                }
            }else {
                PaymentComponentDTO disponibilidadComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("DISPONIBILIDAD_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO compensationComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("COMPENSACION_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO gratificationComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("GRATIFICACION_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO gratificacionExtraordinariaComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("GRATIFICACION_EXTRAORDINARIA_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO trabajoExtensoComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("TRABAJO_EXTENSO_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO trabajoGravableComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("TRABAJO_GRAVABLE_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO parteExentaFestivoLaboradoComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PARTE_EXENTA_FESTIVO_LABORADO_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO parteGravableFestivoLaboradoComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PARTE_GRAVABLE_FESTIVO_LABORADO_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO primaDominicalGravableComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PRIMA_DOMINICAL_GRAVABLE_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO ayudaMudanzaComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("MUDANZA_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO importeVidaCaraComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("VIDA_CARA_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO primaDominicalExentaComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PRIMA_DOMINICAL_EXENTA_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                projectionsComponent.add(disponibilidadComponent);
                projectionsComponent.add(compensationComponent);
                projectionsComponent.add(gratificationComponent);
                projectionsComponent.add(gratificacionExtraordinariaComponent);
                projectionsComponent.add(trabajoExtensoComponent);
                projectionsComponent.add(trabajoGravableComponent);
                projectionsComponent.add(parteExentaFestivoLaboradoComponent);
                projectionsComponent.add(parteGravableFestivoLaboradoComponent);
                projectionsComponent.add(primaDominicalGravableComponent);
                projectionsComponent.add(ayudaMudanzaComponent);
                projectionsComponent.add(importeVidaCaraComponent);
                projectionsComponent.add(primaDominicalExentaComponent);
            }
        } else if (projection.getBu().equalsIgnoreCase("T. PERU")){
            String nominalCodeMoving="1247";
            String nominalCodeHousing="1212";
            String nominalCodeExpatriates="1213";
            String [] nominaAFP= {"1503", "1513", "1523"};
            //log.debug("nominal {}",nominal);
            //log.debug("list {}",list.get(0).getIdssff());
            for(NominaProjection h : nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList()) ) {

                Double importe = h.getImporte();
                if(importe == null) {
                    importe = 0.0;
                }

                if (h.getCodeNomina().equalsIgnoreCase(nominalCodeMoving)) {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("MOVING").amount(BigDecimal.valueOf(importe))
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(importe))).build());
                } else {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("MOVING").amount(BigDecimal.ZERO)
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO)).build());
                }

                if (h.getCodeNomina().equalsIgnoreCase(nominalCodeHousing)) {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("HOUSING").amount(BigDecimal.valueOf(importe))
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(importe))).build());
                } else {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("HOUSING").amount(BigDecimal.ZERO)
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO)).build());
                }

                if (h.getCodeNomina().equalsIgnoreCase(nominalCodeExpatriates)) {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("EXPATRIATES").amount(BigDecimal.valueOf(importe))
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(importe))).build());
                } else {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("EXPATRIATES").amount(BigDecimal.ZERO)
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO)).build());
                }

                if (Arrays.stream(nominaAFP).anyMatch(p -> p.equalsIgnoreCase(h.getCodeNomina()))) {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("AFP").amount(BigDecimal.valueOf(importe))
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(importe))).build());
                } else {
                    projectionsComponent.add(PaymentComponentDTO.builder().
                            type(16).
                            paymentComponent("AFP").amount(BigDecimal.ZERO)
                            .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO)).build());
                }
            }
        }else if(projection.getBu().equalsIgnoreCase("T. Uruguay")) {
            //log.debug("ssff {}",list.get(0).getIdssff());
            List<NominaProjection> nominalBySSFF = nominal.stream().filter(g -> g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList());
            //log.debug("nominalBySSFF {}",nominalBySSFF);
            if (!nominalBySSFF.isEmpty()) {
                Map<String, Double> componentTotals = new HashMap<>();
                for (NominaProjection h : nominalBySSFF) {
                    List<NominaPaymentComponentLink> nominaPaymentComponentLinks = nominaPaymentComponentLinksCache.get(h.getCodeNomina());
                    if (nominaPaymentComponentLinks != null) {
                        for (NominaPaymentComponentLink link : nominaPaymentComponentLinks) {
                            String component = link.getPaymentComponent().getPaymentComponent();
                            double importe = h.getImporte();
                            componentTotals.put(component, componentTotals.getOrDefault(component, 0.0) + importe);
                        }
                    }
                }
                for (Map.Entry<String, Double> entry : componentTotals.entrySet()) {
                    String component = entry.getKey();
                    double total = entry.getValue();
                    if (total > 0) {
                        projectionsComponent.add(buildPaymentComponentDTO(component, total, projection.getPeriod(), projection.getRange()));
                    }
                }
            } else {
                PaymentComponentDTO sueldo010Component = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("0010")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                PaymentComponentDTO sueldo020Component = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("0020")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //HHEE
                PaymentComponentDTO hheeComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("HHEE_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //Guardia
                PaymentComponentDTO guardiaComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("GUARDIA_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //PREMIO_MENSUAL_20_BASE_UR
                PaymentComponentDTO premioComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PREMIO_MENSUAL_20_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //PREMIO_MENSUAL_15_BASE_UR
                PaymentComponentDTO premio15Component = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PREMIO_MENSUAL_15_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //PREMIO_CUATRIMESTRAL_8_BASE_UR
                PaymentComponentDTO premioCuatriComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PREMIO_CUATRIMESTRAL_8_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //PREMIO_CUATRIMESTRAL_BASE_UR
                PaymentComponentDTO premioCuatriBaseComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("PREMIO_CUATRIMESTRAL_BASE_UR")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //BONO_ANUAL
                PaymentComponentDTO bonoAnualComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("BONO_ANUAL")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //BONO_VENTAS
                PaymentComponentDTO bonoVentasComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("BONO_VENTAS")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //COMISIONES_VENTAS
                PaymentComponentDTO comisionesVentasComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("COMISIONES_VENTAS")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //COMISIONES_COBRANZAS
                PaymentComponentDTO comisionesCobranzasComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("COMISIONES_COBRANZAS")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //TICKET_ALIMENTACION
                PaymentComponentDTO ticketAlimentacionComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("TICKET_ALIMENTACION")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //SUAT_BASE
                PaymentComponentDTO suatBaseComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("SUAT_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                //BC_BS_BASE
                PaymentComponentDTO bcBsBaseComponent = PaymentComponentDTO.builder()
                        .type(16)
                        .paymentComponent("BC_BS_BASE")
                        .amount(BigDecimal.ZERO)
                        .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                        .show(true)
                        .build();
                projectionsComponent.add(hheeComponent);
                projectionsComponent.add(sueldo010Component);
                projectionsComponent.add(sueldo020Component);
                projectionsComponent.add(guardiaComponent);
                projectionsComponent.add(premioComponent);
                projectionsComponent.add(premio15Component);
                projectionsComponent.add(premioCuatriComponent);
                projectionsComponent.add(premioCuatriBaseComponent);
                projectionsComponent.add(bonoAnualComponent);
                projectionsComponent.add(bonoVentasComponent);
                projectionsComponent.add(comisionesVentasComponent);
                projectionsComponent.add(comisionesCobranzasComponent);
                projectionsComponent.add(ticketAlimentacionComponent);
                projectionsComponent.add(suatBaseComponent);
                projectionsComponent.add(bcBsBaseComponent);
            }
        }
        //log.debug("projectionsComponent {}",projectionsComponent);
    }
    public HeadcountHistoricalProjectionDTO convertToDTO(HeadcountHistoricalProjection projection) {
        HeadcountHistoricalProjectionDTO dto = new HeadcountHistoricalProjectionDTO();
        dto.setPosition(projection.getPosition());
        dto.setPoname(projection.getPoname());
        dto.setIdssff(projection.getIdssff());
        dto.setEntitylegal(projection.getEntitylegal());
        dto.setGender(projection.getGender());
        dto.setBu(projection.getBu());
        dto.setWk(projection.getWk());
        dto.setDivision(projection.getDivision());
        dto.setDepartment(projection.getDepartment());
        dto.setComponent(projection.getComponent());
        dto.setAmount(projection.getAmount());
        dto.setClassemp(projection.getClassemp());
        dto.setFnac(projection.getFnac());
        dto.setFcontra(projection.getFcontra());
        dto.setAf(projection.getAf());
        dto.setDivisionname(projection.getDivisionname());
        dto.setCc(projection.getCc());
        return dto;
    }
    private ParametersDTO getParametersById(List<ParametersDTO> parameters, int id) {
        return parameters.stream()
                .filter(p -> p.getParameter().getId() == id)
                .findFirst()
                .orElse(null);
    }
    private PaymentComponentDTO buildPaymentComponentDTO(String component, double amount, String period, Integer range) {
        return PaymentComponentDTO.builder()
                .type(16)
                .paymentComponent(component)
                .amount(BigDecimal.valueOf(amount))
                .projections(Shared.generateMonthProjection(period, range, BigDecimal.valueOf(amount)))
                .build();
    }
    private PaymentComponentDTO createPaymentComponentDTO(NominaPaymentComponentLink link, NominaProjection h, ParametersByProjection projection) {
        PaymentComponentDTO paymentComponentDTO = new PaymentComponentDTO();
        paymentComponentDTO.setPaymentComponent(link.getPaymentComponent().getPaymentComponent()+"_BASE");
        paymentComponentDTO.setAmount(BigDecimal.valueOf(h.getImporte()));
        paymentComponentDTO.setProjections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(h.getImporte())));
        paymentComponentDTO.setType(16);
        paymentComponentDTO.setShow(true);
        return paymentComponentDTO;
    }
}
