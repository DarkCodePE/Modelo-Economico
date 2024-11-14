package ms.hispam.budget.service.impl;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.cache.ProjectionCache;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.countries.DefaultConfig;
import ms.hispam.budget.dto.projections.*;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.entity.mysql.ParameterProjection;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.exception.BadRequestException;
import ms.hispam.budget.exception.FormatAmountException;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.repository.sqlserver.ParametersRepository;
import ms.hispam.budget.rules.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import ms.hispam.budget.rules.configuration.ConfigStrategyFactory;
import ms.hispam.budget.rules.operations.salary.FoodBenefitsOperation;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.rules.operations.salary.*;
import ms.hispam.budget.service.*;
import ms.hispam.budget.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ms.hispam.budget.util.Shared.generateMonthProjectionV3;


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
    /*@Autowired
    private Executor executor;*/
    @Autowired
    @Lazy
    private XlsReportService xlsReportService;
    @Autowired
    private ConvenioRepository convenioRepository;
    @Autowired
    private ConvenioBonoRepository convenioBonoRepository;
    @Autowired
    private RangoBuPivotHistoricalRepository rangoBuPivotHistoricalRepository;
    @Autowired
    private RangoBuPivotHistoricalDetailRepository rangoBuPivotHistoricalDetailRepository;
    @Autowired
    private TypePaymentComponentRepository typePaymentComponentRepository;
    @Autowired
    private ConvenioHistorialRepository convenioHistorialRepository;
    @Autowired
    private ConvenioBonoHistorialRepository convenioBonoHistorialRepository;
    @Autowired
    private EmployeeClassificationRepository employeeClassificationRepository;
    @Autowired
    private SeniorityAndQuinquenniumRepository seniorityAndQuinquenniumRepository;
    @Autowired
    private ConceptoPresupuestalRepository conceptoPresupuestalRepository;
    @Autowired
    private ValidationRuleRepository validationRuleRepository;
    @Autowired
    private ConventArgRepository conventArgRepository;
    @Autowired
    private EdadSVRepository edadSVRepository;
    @Autowired
    private ConfigStrategyFactory configStrategyFactory;
    @Autowired
    private SseReportService sseReportService;
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private ProjectionCache projectionCache;
    @Autowired
    private ProjectionUtils projectionUtils;
    @Autowired
    private ProjectionHistoryService projectionHistoryService;

    private final ConcurrentMap<String, ConcurrentHashMap<String, List<NominaPaymentComponentLink>>> nominaPaymentComponentLinksByBuCache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<NominaPaymentComponentLink>> nominaPaymentComponentLinksCache;
    private Map<String, Map<String, List<NominaPaymentComponentLink>>> nominaPaymentComponentLinksByYearCache;
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
    private static final String[] EXCLUDED_HEADERS = {"po", "AF", "idssff"}; // Add other excluded headers
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Map<String, List<MonthProjection>> PROJECTION_CACHE = new ConcurrentHashMap<>();
    private Map<String, Map<String, Object>> dataMapTemporal = new HashMap<>();
    private static final ForkJoinPool BASE_EXTERN_THREAD_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

    @Cacheable("daysVacationCache")
    public List<DaysVacationOfTime> getAllDaysVacation() {
        return daysVacationOfTimeRepository.findAll();
    }
    private Map<String, ConvenioBono> convenioBonoCache;
    private Map<String, Convenio> convenioCache;
    private Map<String, EmployeeClassification> classificationMap = new HashMap<>();
    private Map<Integer, BigDecimal> quinquenniumMap = new HashMap<>();
    private Map<Tuple<Integer, Integer>, Double> ageSVMap = new HashMap<>();
    private Map<String, ConceptoPresupuestal> conceptoPresupuestalMap = new HashMap<>();
    //ConventArg
    private Map<String, ConventArg> conventArgMap = new HashMap<>();
    private BigDecimal totalCommissions;
    private BigDecimal totalOvertime;
    private BigDecimal totalIncentives;
    private Set<String> annualizedPositions;
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
        List<EmployeeClassification> classifications = employeeClassificationRepository.findAll();
        for (EmployeeClassification classification : classifications) {
            //log.info("classification -> {}", classification.getTypeEmp());
            classificationMap.put(classification.getCategory(), classification);
        }
        // Inicializar quinquenniumMap
        List<SeniorityAndQuinquennium> seniorities = seniorityAndQuinquenniumRepository.findAll();
        for (SeniorityAndQuinquennium seniority : seniorities) {
            quinquenniumMap.put(seniority.getSeniority(), seniority.getQuinquennium());
        }
        // Inicializar conceptoPresupuestalMap
        List<ConceptoPresupuestal> conceptos = conceptoPresupuestalRepository.findAll();
        for (ConceptoPresupuestal concepto : conceptos) {
            ConceptoPresupuestal adjustedConcepto = new ConceptoPresupuestal();
            adjustedConcepto.setConceptoPresupuestal(concepto.getConceptoPresupuestal());

            // Pre-calculate all percentages by dividing by 100
            if (concepto.getGratificacion() != null) {
                adjustedConcepto.setGratificacion(concepto.getGratificacion().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            }
            if (concepto.getCts() != null) {
                adjustedConcepto.setCts(concepto.getCts().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            }
            if (concepto.getEssalud() != null) {
                adjustedConcepto.setEssalud(concepto.getEssalud().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            }
            if (concepto.getBonifExtTemp() != null) {
                adjustedConcepto.setBonifExtTemp(concepto.getBonifExtTemp().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            }
            conceptoPresupuestalMap.put(concepto.getConceptoPresupuestal(), adjustedConcepto);
        }
        // Inicializar ConventArg
        List<ConventArg> conventArgs = conventArgRepository.findAll();
        for (ConventArg conventArg : conventArgs) {
            conventArgMap.put(conventArg.getConvenio(), conventArg);
        }
        // Inicializar ageSVMap
        List<EdadSV> edadSVList = edadSVRepository.findAll();
        for (EdadSV edadSV : edadSVList) {
            ageSVMap.put(new Tuple<>(edadSV.getEdadInicio(), edadSV.getEdadFin()), edadSV.getPorcentajeSvLey());
        }
        this.annualizedPositions = new HashSet<>(Arrays.asList(
                "PO99010522", "PO99017955", "PO99010442", "PO99010163", "PO99013467",
                "PO99010467", "PO99009804", "PO90001143", "PO99010354", "PO99010099",
                "PO99010334", "PO99009886", "PO99010825", "PO99010209", "PO99010480",
                "PO99010421", "PO99010185", "PO99010714", "PO99012353", "PO99010755",
                "PO10002749", "PO99010740", "PO99010524", "PO99012348", "PO10035702",
                "PO99010444", "PO99012808", "PO99010176", "PO99010634", "PO99010299",
                "PO10002367", "PO99011575", "PO99010199", "PO99009912", "PO99009836",
                "PO99011485", "PO99009841", "PO10001847", "PO99010747", "PO99009811",
                "PO99010071", "PO99017938", "PO10036786", "PO99010671", "PO99010124",
                "PO99010668", "PO99015506", "PO10040433", "PO99009829", "PO99010295",
                "PO10011800", "PO10003371", "PO99010265", "PO10018533"
        ));

    }
    // Este método se llamaría al inicio del procesamiento, posiblemente en getHeadcountByAccount
    private void initializePeruCache(ParametersByProjection projection) {
        if (nominaPaymentComponentLinksByYearCache == null) {
            nominaPaymentComponentLinksByYearCache = new HashMap<>();
            Map<Integer, List<NominaPaymentComponentLink>> linksByYear = projection.getNominaPaymentLinks();
            log.debug("linksByYear {}", linksByYear);
            linksByYear.forEach((year, links) -> {
                String yearString = String.valueOf(year);
                Map<String, List<NominaPaymentComponentLink>> yearCache = links.stream()
                        .filter(n -> n.getPaymentComponent().getBu().equals(projection.getIdBu()))
                        .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));

                nominaPaymentComponentLinksByYearCache.put(yearString, yearCache);
            });
        }
    }
    public BigDecimal getQuinquenniumValue(int seniority) {
        return quinquenniumMap.getOrDefault(seniority, BigDecimal.ZERO);
    }
    public Map<String, EmployeeClassification> getClassificationMap() {
        return classificationMap;
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
             /* List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                    .stream()
                    .stream()
                    .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10038188"))
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
                    isEcuadorv2(headcount,projection);
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
                                proyeccion.getLevel(),
                                proyeccion.getCategoryLocal(),
                                proyeccion.getEstadoVacante()
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
    public ProjectionSecondDTO getProjectionFromHistoryId(Long historyId){
        return projectionHistoryService.getProjectionFromHistory(historyId).getProjectionResult();
    }

    @Override
    public ProjectionSecondDTO getNewProjection(ParametersByProjection projection, String sessionId, String reportName) {
        String cacheKey = ProjectionUtils.generateHash(projection);
        try {
            ProjectionSecondDTO projectionResult = calculateProjection(projection);
            projectionHistoryService.saveProjectionAsync(
                    projection,
                    projectionResult,
                    sessionId,
                    reportName,
                    cacheKey
            );
            return projectionResult;
        } catch (Exception ex) {
            log.error("Error al generar la proyección: ", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar la proyección", ex);
        }
    }

    public ProjectionSecondDTO calculateProjection(ParametersByProjection projection) {
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
            //filter by pos
            // Obtener las cuentas SAP como una cadena separada por comas
            String accountSap = components.stream()
                    .map(AccountProjection::getAccount)
                    .collect(Collectors.joining(","));
            /*List<ProjectionDTO>  headcount=  getHeadcount(projection, componentesMap)
                    .stream()
                    .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10038188"))
                    .collect(Collectors.toList());*/
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

            List<RealesProjection> getReales = repository.getReales(
                    "%" + projection.getBu().replace("T. ", "") + "%",
                    projection.getPeriod(),
                    accountSap
            );

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
            ViewPosition viewPosition = ViewPosition.builder()
                    .positions(headcount)
                    .count(headcount.size())
                    .build();

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
    public static void addSLash( ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("","/"));
        projection.setNominaFrom(projection.getNominaFrom().replace("","/"));
        projection.setNominaTo(projection.getNominaTo().replace("","/"));
        projection.getParameters().forEach(k-> {
            k.setPeriod(k.getPeriod().replace("","/"));
            k.setRange(k.getRange().replace("","/"));
            k.setPeriodRetroactive(k.getPeriodRetroactive().replace("","/"));
        });
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
       /* List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                .stream()
                .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10038188"))
                .collect(Collectors.toList());*/
        //filter projection by pos
        List<ProjectionDTO>  headcount =  getHeadcountByAccount(projection);
        //log.info("headcount {}",headcount);
        //PO10000756
       /* List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection)
                .stream()
                .filter(projectionDTO ->  projectionDTO.getPo().equals("PO10000756"))
                .collect(Collectors.toList());*/
        //log.debug("headcount {}",headcount);
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
                isEcuadorv2(headcount,projection);
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
            case "T. ARGENTINA":
                isArgentina(headcount,projection);
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
                .findFirst()
                .ifPresent(p->
                        p.getComponents()
                                .forEach(t->t.getProjections()
                                .stream()
                                        .filter(h->Shared.estaEnRango(r.getFrom(),r.getTo(),h.getMonth()))
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
        //log.debug("headcount {}",headcount);
        return headcount;
    }

    private void isArgentina(List<ProjectionDTO> headcount, ParametersByProjection projection) {
        Argentina argentina = new Argentina();
        // Parámetros
        List<ParametersDTO> salaryIncreaseList = filterParametersByRootName(projection.getParameters(), "%Aumento Fuera Conv");
        List<ParametersDTO> executiveSalaryIncreaseList = filterParametersByRootName(projection.getParameters(), "%Aumento Salarial");
        List<ParametersDTO> directorSalaryIncreaseList = filterParametersByRootName(projection.getParameters(), "%Aumento Remunerativo");
        List<ParametersDTO> vacationDaysList = filterParametersByRootName(projection.getParameters(), "%Aumento Viático");
        log.debug("salaryIncreaseList {}",projection);
        headcount
                .stream()
                .parallel()
                .forEach(headcountData -> {
                    //Grupo_de_convenio_LABEL
                    //Area_de_personal_LABEL
                    //TODO: reemplazar Grupo_de_convenio_LABEL por headcountData.getGrupo_de_convenio()
                    String Grupo_de_convenio_LABEL = "FC MVL";
                    // Obtén el convenio asociado a la posición

                    if (projection.getBaseExtern() != null && !projection.getBaseExtern().getData().isEmpty()) {
                        addBaseExtern(headcountData, projection.getBaseExtern(),
                                projection.getPeriod(), projection.getRange());
                    }
                    argentina.basicSalary(headcountData.getComponents(), projection.getParameters(),
                            projection.getPeriod(), projection.getRange(), conventArgMap, Grupo_de_convenio_LABEL);
                    argentina.additionalRemuneration(headcountData.getComponents(), projection.getParameters(),
                            projection.getPeriod(), projection.getRange(), conventArgMap, Grupo_de_convenio_LABEL);
                    argentina.discontinuousDay(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                    argentina.telephoneRate(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                    argentina.nonResidentAllowance(headcountData.getComponents(), projection.getParameters(),
                            projection.getPeriod(), projection.getRange(), conventArgMap, Grupo_de_convenio_LABEL);
                    argentina.snr(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                    argentina.sumConformedSalary(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                    argentina.percentageIncreaseConformedSalary(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                    argentina.additional(headcountData.getComponents(), projection.getPeriod(), projection.getRange());
                });
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
    public void calculateTotalCommissions(List<ProjectionDTO> headcount) {
        this.totalCommissions = headcount.stream()
                .map(h -> {
                    PaymentComponentDTO commissionsBase = h.getComponents().stream()
                            .filter(c -> "COMMISSIONS_BASE".equals(c.getPaymentComponent()))
                            .findFirst()
                            .orElse(null);
                    return commissionsBase != null ? commissionsBase.getAmount() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    //calculate total overtime
    public void calculateTotalOvertime(List<ProjectionDTO> headcount) {
        this.totalOvertime = headcount.stream()
                .map(h -> {
                    PaymentComponentDTO overtime = h.getComponents().stream()
                            .filter(c -> "OVERTIME_BASE".equals(c.getPaymentComponent()))
                            .findFirst()
                            .orElse(null);
                    return overtime != null ? overtime.getAmount() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    //calculate total incentives
    public void calculateTotalIncentives(List<ProjectionDTO> headcount) {
        this.totalIncentives = headcount.stream()
                .map(h -> {
                    PaymentComponentDTO incentives = h.getComponents().stream()
                            .filter(c -> "INCENTIVES_BASE".equals(c.getPaymentComponent()))
                            .findFirst()
                            .orElse(null);
                    return incentives != null ? incentives.getAmount() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void isPeru(List<ProjectionDTO> headcount, ParametersByProjection projection) {
        PeruRefactor methodsPeru = new PeruRefactor();
        calculateTotalCommissions(headcount);
        calculateTotalOvertime(headcount);
        calculateTotalIncentives(headcount);
        // Parámetros
        List<ParametersDTO> salaryIncreaseList = filterParametersByName(projection.getParameters(), "Rev Salarial EMP %");
        List<ParametersDTO> executiveSalaryIncreaseList = filterParametersByName(projection.getParameters(), "Rev Salarial EJC/GER %");
        List<ParametersDTO> directorSalaryIncreaseList = filterParametersByName(projection.getParameters(), "Rev Salarial DIR/DPZ %");
        List<ParametersDTO> vacationDaysList = filterParametersByName(projection.getParameters(), "Días vacaciones disp anual");
        List<ParametersDTO> vacationSeasonalityList = filterParametersByName(projection.getParameters(), "Estacionalidad vacaciones");
        List<ParametersDTO> groupSVList = filterParametersByName(projection.getParameters(), "SV grupo");
        List<ParametersDTO> youngExecutiveSalaryList = filterParametersByName(projection.getParameters(), "Salario Joven ejecutivo");
        List<ParametersDTO> internSalaryList = filterParametersByName(projection.getParameters(), "Salario Practicante");
        List<ParametersDTO> minimumSalaryList = filterParametersByName(projection.getParameters(), "Salario mínimo");
        List<ParametersDTO> familyAllowanceList = filterParametersByName(projection.getParameters(), "% Asignación Familiar");
        List<ParametersDTO> mobilityRefrigerioTopList = filterParametersByName(projection.getParameters(), "Monto tope Movilidad y Refrigerio");
        List<ParametersDTO> clothingAllowanceTopList = filterParametersByName(projection.getParameters(), "Monto tope Bonificación Vestuario");
        List<ParametersDTO> transportationAllowanceTopList = filterParametersByName(projection.getParameters(), "Monto tope Bonificación Traslado");
        List<ParametersDTO> teleworkLawTopList = filterParametersByName(projection.getParameters(), "Monto tope Ley Teletrabajo");
        List<ParametersDTO> ejcPeopleBTPList = filterParametersByName(projection.getParameters(), "BTP: % de Personas (EJC)");
        List<ParametersDTO> ejcBonusBTPList = filterParametersByName(projection.getParameters(), "BTP: % de Bono (EJC)");
        List<ParametersDTO> dirPeopleBTPList = filterParametersByName(projection.getParameters(), "BTP: % de Personas (DIR)");
        List<ParametersDTO> dirBonusBTPList = filterParametersByName(projection.getParameters(), "BTP: % de Bono (DIR)");
        List<ParametersDTO> schoolAllowanceSeasonalityList = filterParametersByName(projection.getParameters(), "Estacionalidad Asignación escolar");
        List<ParametersDTO> preSchoolAllowanceSeasonalityList = filterParametersByName(projection.getParameters(), "Estacionalidad Bon. Pre Escolar y Superior");
        List<ParametersDTO> distinctionAllowanceSeasonalityList = filterParametersByName(projection.getParameters(), "Estacionalidad Bonificación por destaque");
        List<ParametersDTO> overtimeSeasonalityList = filterParametersByName(projection.getParameters(), "Estacionalidad Horas Extras");
        List<ParametersDTO> annualOvertimeValueList = filterParametersByName(projection.getParameters(), "Valor HHEE (anual)");
        List<ParametersDTO> annualCommissionValueList = filterParametersByName(projection.getParameters(), "Valor Comisiones (anual)");
        List<ParametersDTO> annualIncentiveValueList = filterParametersByName(projection.getParameters(), "Valor Incentivos (anual)");
        List<ParametersDTO> laborClosureBonusList = filterParametersByName(projection.getParameters(), "Bono Cierre Pliego Sindical");
        List<ParametersDTO> lumpSumBonusList = filterParametersByName(projection.getParameters(), "Monto Bono Lump Sum");
        List<ParametersDTO> signingBonusList = filterParametersByName(projection.getParameters(), "Monto Bono Signing Bonus");
        List<ParametersDTO> extraordinaryGratificationList = filterParametersByName(projection.getParameters(), "Monto Gratificación Extraordinaria Convenio");
        List<ParametersDTO> extraordinaryBonusList = filterParametersByName(projection.getParameters(), "Monto Bonificación Extraordinaria");
        List<ParametersDTO> inflationList = filterParametersByName(projection.getParameters(), "Inflación");
        //calcular cantidad de EMP
     /*   long countEMP = headcount.parallelStream()
                .filter(h -> {
                    log.info("h.getCategoryLocal() {}", h.getCategoryLocal());
                    log.info("get PO", h.getPo());
                    //log.info("optionalEmployeeClassification.isPresent() {}", optionalEmployeeClassification.isPresent());"
                    Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(h.getCategoryLocal().toUpperCase()));
                    return optionalEmployeeClassification.map(empClass -> "EMP".equals(empClass.getTypeEmp())).orElse(false);
                })
                .count();*/
        long countEMP = headcount.parallelStream()
                .filter(h -> {
                    String categoryLocal = h.getCategoryLocal();
                    String po = h.getPo(); // Asumiendo que getPo() retorna un String

                    if (categoryLocal == null) {
                        //log.warn("ProjectionDTO tiene categoryLocal null para headcountData: PO={}", po);
                        //log.info("catgoria loocal", categoryLocal);
                        return false; // Excluir este elemento de la cuenta
                    }

                    String categoryLocalUpper = categoryLocal.toUpperCase();
                    Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(categoryLocalUpper));
                    return optionalEmployeeClassification.map(empClass -> "EMP".equals(empClass.getTypeEmp())).orElse(false);
                })
                .count();
        //log.info("countEMP {}", countEMP);
        //calcular cantidad de EJC
        long countEJC = headcount.parallelStream()
                .filter(h -> {
                    String categoryLocal = h.getCategoryLocal();
                    String po = h.getPo(); // Asumiendo que getPo() retorna un String
                    //log.info("h.getCategoryLocal() {}", h.getCategoryLocal());
                    if (categoryLocal == null) {
                        //log.warn("ProjectionDTO tiene categoryLocal null para headcountData: PO={}", po);
                        //log.info("catgoria loocal", categoryLocal);
                        return false; // Excluir este elemento de la cuenta
                    }
                    Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(h.getCategoryLocal().toUpperCase()));
                    return optionalEmployeeClassification.map(empClass -> "EJC".equals(empClass.getTypeEmp())).orElse(false);
                })
                .count();
        //log.info("countEJC {}", countEJC);
        //calcular cantidad de GER
        long countGER = headcount.parallelStream()
                .filter(h -> {
                    String categoryLocal = h.getCategoryLocal();
                    String po = h.getPo(); // Asumiendo que getPo() retorna un String
                    //log.info("h.getCategoryLocal() {}", h.getCategoryLocal());
                    if (categoryLocal == null) {
                        //log.warn("ProjectionDTO tiene categoryLocal null para headcountData: PO={}", po);
                        //log.info("catgoria loocal", categoryLocal);
                        return false; // Excluir este elemento de la cuenta
                    }
                    Optional<EmployeeClassification> optionalEmployeeClassification = Optional.ofNullable(classificationMap.get(h.getCategoryLocal().toUpperCase()));
                    return optionalEmployeeClassification.map(empClass -> "GER".equals(empClass.getTypeEmp())).orElse(false);
                })
                .count();
        // Calcular el total de posiciones
        long totalPositions = headcount
                .parallelStream()
                .filter(h -> h.getPoName() != null)
                .count();
        //log.info("totalPositions {}", countGER);
        // Actualizar todas las cachés relevantes
        updateCaches(projection);
        // Preprocesar baseExtern y construir un Map para acceso rápido
        if (projection.getBaseExtern() != null && !projection
                .getBaseExtern()
                .getData()
                .isEmpty()) {
            Map<String, Map<String, Object>> baseExternMap = projection.getBaseExtern().getData().stream()
                    .collect(Collectors.toMap(
                            po -> (String) po.get("po"),
                            po -> po
                    ));

            // Lista de encabezados relevantes
            List<String> relevantHeaders = projection
                    .getBaseExtern()
                    .getHeaders()
                    .stream()
                    .filter(t -> Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(t)))
                    .collect(Collectors.toList());
            //log.info("relevantHeaders -> {}", relevantHeaders);
            headcount
                    .stream()
                    .parallel()
                    .forEach(headcountData -> {
                        try {
                            //log.info("getPoName {}",headcountData.getPo());
                           /* if (projection.getBaseExtern() != null && !projection.getBaseExtern().getData().isEmpty()) {
                                addBaseExtern(headcountData, projection.getBaseExtern(),
                                        projection.getPeriod(), projection.getRange());
                            }*/
                            Map<String, Object> poData = baseExternMap.get(headcountData.getPo());
                            if (poData != null) {
                                updateProjection2(headcountData, poData, relevantHeaders, projection.getPeriod(), projection.getRange());
                            }

                            //log.info("headcountData.getPoName() {}", headcountData);
                            List<PaymentComponentDTO> component = headcountData.getComponents();
                            methodsPeru.calculateTheoreticalSalary(component, salaryIncreaseList, headcountData.getCategoryLocal(), projection.getPeriod(), projection.getRange(), executiveSalaryIncreaseList, directorSalaryIncreaseList, classificationMap, annualizedPositions, headcountData.getPo());
                            methodsPeru.relocation(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housing(component, projection.getPeriod(), projection.getRange(), headcountData.getPo());
                            methodsPeru.increaseSNP(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseAFP(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseSNPAndIncrease(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseAFP1023(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housingExpatriates(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.vacationEnjoyment(component, projection.getPeriod(), projection.getRange(), vacationDaysList, vacationSeasonalityList);
                            methodsPeru.overtime(component, projection.getPeriod(), projection.getRange(), this.totalOvertime, annualOvertimeValueList, overtimeSeasonalityList);
                            //log.debug("totalComisiones {}", totalComisiones);
                            methodsPeru.commissions(component, projection.getPeriod(), projection.getRange(), this.totalCommissions.doubleValue(), annualCommissionValueList);
                            methodsPeru.incentives(component, projection.getPeriod(), projection.getRange(), this.totalIncentives, annualIncentiveValueList);
                            methodsPeru.nightBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.availabilityPlus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.unionClosingBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), laborClosureBonusList, countEMP, classificationMap);
                            methodsPeru.vacationIncreaseBonus(component, projection.getPeriod(), projection.getRange(), vacationSeasonalityList);
                            methodsPeru.vacationBonus(component, projection.getPeriod(), projection.getRange(), vacationSeasonalityList);
                            methodsPeru.travelExpenses(component, projection.getPeriod(), projection.getRange());
                            /*  methodsPeru.coinv(component, projection.getPeriod(), projection.getRange());*/
                        /*methodsPeru.psp(component, projection.getPeriod(), projection.getRange());
                        methodsPeru.rsp(component, projection.getPeriod(), projection.getRange());
                        methodsPeru.tfsp(component, projection.getPeriod(), projection.getRange());*/
                            methodsPeru.specialDaysBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housingAssignment(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.complementaryBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.groupResponsibleBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.basicSalaryComplement(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.judicialMandateConcepts(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.storeDay(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.transferBonus(component, projection.getPeriod(), projection.getRange(),transportationAllowanceTopList);
                            methodsPeru.clothingBonus(component, projection.getPeriod(), projection.getRange(),clothingAllowanceTopList);
                            methodsPeru.teleworkLaw(component, projection.getPeriod(), projection.getRange(),teleworkLawTopList);
                            methodsPeru.mobilityAndRefreshment(component, projection.getPeriod(), projection.getRange(),mobilityRefrigerioTopList);
                            methodsPeru.familyAssignment(component, projection.getPeriod(), projection.getRange(),familyAllowanceList, minimumSalaryList);
                            methodsPeru.lumpSumBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), lumpSumBonusList, countEJC, countGER, classificationMap);
                            methodsPeru.signingBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), signingBonusList, countEJC, countGER, classificationMap);
                            methodsPeru.extraordinaryConventionBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), extraordinaryGratificationList, countEMP, classificationMap);
                            methodsPeru.extraordinaryBonus(component, projection.getPeriod(), projection.getRange(), extraordinaryBonusList, totalPositions);
                            methodsPeru.epsContribution(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.schoolAssignment(component, projection.getPeriod(), projection.getRange(), schoolAllowanceSeasonalityList);
                            methodsPeru.studiesBonus(component, projection.getPeriod(), projection.getRange(), preSchoolAllowanceSeasonalityList);
                            methodsPeru.foodBenefits(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), classificationMap);
                            methodsPeru.interns(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), youngExecutiveSalaryList, internSalaryList, classificationMap);
                            methodsPeru.medicalInsurance(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.vacationProvision(component, projection.getPeriod(), projection.getRange(), vacationDaysList);
                            methodsPeru.seniority(component, projection.getPeriod(), projection.getRange(), headcountData.getFContra(), quinquenniumMap, classificationMap,  headcountData.getCategoryLocal());
                            methodsPeru.srdBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.topPerformerBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), ejcPeopleBTPList, ejcBonusBTPList, dirPeopleBTPList, dirBonusBTPList, classificationMap);
                            //methodsPeru.epsCredit(component, projection.getPeriod(), projection.getRange());
                            // public void extraordinaryTransferBonus(List<PaymentComponentDTO> component, String period, Integer range)
                            methodsPeru.extraordinaryTransferBonus(component, projection.getPeriod(), projection.getRange());
                            /*methodsPeru.voluntaryContribution(component, projection.getPeriod(), projection.getRange());*/
                            //public void theoreticalSalaryGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
                            methodsPeru.theoreticalSalaryGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void vacationProvisionGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingCompensationGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void afpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncrementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void snpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.snpIncrementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAllowanceGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAllowanceGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void topPerformerBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void groupResponsibleBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);// public void housingAssignmentGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void judicialMandateConceptsGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void specialDaysBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void nightWorkBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //public void detachmentBonus(List<PaymentComponentDTO> components, String period, Integer range, List<ParametersDTO> detachmentBonusValueList)
                            methodsPeru.detachmentBonus(component, projection.getPeriod(), projection.getRange(), distinctionAllowanceSeasonalityList);
                            methodsPeru.detachmentBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void theoreticalSalaryTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void vacationProvisionTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void housingCompensationTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void afpIncrementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncrementTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void increaseSNPAndIncreaseTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increaseSNPAndIncreaseTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAssignmentTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void topPerformerBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void judicialMandateConceptsTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void complementaryBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);//public void specialDaysBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void nightWorkBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void groupResponsibleBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void theoreticalSalaryCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void vacationProvisionCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void topPerformerBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void groupResponsibleBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void storeDayCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void housingAssignmentCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void judicialMandateConceptsCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void specialDaysBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void availabilityBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void nightWorkBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void detachmentBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void tfspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.tfspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void pspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.pspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void rspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.rspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void coinvCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.coinvCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void theoreticalSalaryEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);//public void vacationProvisionEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void housingCompensationEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void afpIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncreaseEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void increaseSNPAndIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increaseSNPAndIncreaseEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void srdBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.srdBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAllowanceEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAllowanceEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void topPerformerBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void groupResponsibleBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingAssignmentEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void judicialMandateConceptsEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void specialDaysBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void nightWorkBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void COINVEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.COINVEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void PSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.PSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void RSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.RSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void TFSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.TFSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void calculatePlanPrevDirAportVolEmp(List<PaymentComponentDTO> components, String period, Integer range)
                            methodsPeru.calculatePlanPrevDirAportVolEmp(component, projection.getPeriod(), projection.getRange());
                            // public void calculateLifeInsurance(List<PaymentComponentDTO> components, String period, Integer range, Map<Tuple<Integer, Integer>, Double> ageSVMap, List<ParametersDTO> groupSVList, LocalDate birthDate)
                            methodsPeru.calculateLifeInsurance(component, projection.getPeriod(), projection.getRange(), ageSVMap, groupSVList, headcountData.getFNac());
                            //public void housingCompensationCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void afpIncreaseCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncreaseCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void increase33CTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increase33CTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void provisionBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //methodsPeru.provisionBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void srdBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.srdBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void basicSalaryComplementCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void familyAssignmentCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAssignmentCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void tDayCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //methodsPeru.tDayCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                        } catch (Exception e) {
                            log.error("Exception occurred in method for headcountData: " + headcountData, e);
                            log.error("Exception message: " + e.getMessage());
                            log.error("Exception cause: " + e.getCause());
                            log.error("Exception stack trace: " + Arrays.toString(e.getStackTrace()));
                        }
                    });
        }else {
            headcount
                    .stream()
                    .parallel()
                    .forEach(headcountData -> {
                        try {
                            //log.info("headcountData.getPoName() {}", headcountData);
                            List<PaymentComponentDTO> component = headcountData.getComponents();
                            methodsPeru.calculateTheoreticalSalary(component, salaryIncreaseList, headcountData.getCategoryLocal(), projection.getPeriod(), projection.getRange(), executiveSalaryIncreaseList, directorSalaryIncreaseList, classificationMap, annualizedPositions, headcountData.getPo());
                            methodsPeru.relocation(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housing(component, projection.getPeriod(), projection.getRange(), headcountData.getPo());
                            methodsPeru.increaseSNP(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseAFP(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseSNPAndIncrease(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.increaseAFP1023(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housingExpatriates(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.vacationEnjoyment(component, projection.getPeriod(), projection.getRange(), vacationDaysList, vacationSeasonalityList);
                            methodsPeru.overtime(component, projection.getPeriod(), projection.getRange(), this.totalOvertime, annualOvertimeValueList, overtimeSeasonalityList);
                            //log.debug("totalComisiones {}", totalComisiones);
                            methodsPeru.commissions(component, projection.getPeriod(), projection.getRange(), this.totalCommissions.doubleValue(), annualCommissionValueList);
                            methodsPeru.incentives(component, projection.getPeriod(), projection.getRange(), this.totalIncentives, annualIncentiveValueList);
                            methodsPeru.nightBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.availabilityPlus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.unionClosingBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), laborClosureBonusList, countEMP, classificationMap);
                            methodsPeru.vacationIncreaseBonus(component, projection.getPeriod(), projection.getRange(), vacationSeasonalityList);
                            methodsPeru.vacationBonus(component, projection.getPeriod(), projection.getRange(), vacationSeasonalityList);
                            methodsPeru.travelExpenses(component, projection.getPeriod(), projection.getRange());
                            /*  methodsPeru.coinv(component, projection.getPeriod(), projection.getRange());*/
                        /*methodsPeru.psp(component, projection.getPeriod(), projection.getRange());
                        methodsPeru.rsp(component, projection.getPeriod(), projection.getRange());
                        methodsPeru.tfsp(component, projection.getPeriod(), projection.getRange());*/
                            methodsPeru.specialDaysBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.housingAssignment(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.complementaryBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.groupResponsibleBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.basicSalaryComplement(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.judicialMandateConcepts(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.storeDay(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.transferBonus(component, projection.getPeriod(), projection.getRange(),transportationAllowanceTopList);
                            methodsPeru.clothingBonus(component, projection.getPeriod(), projection.getRange(),clothingAllowanceTopList);
                            methodsPeru.teleworkLaw(component, projection.getPeriod(), projection.getRange(),teleworkLawTopList);
                            methodsPeru.mobilityAndRefreshment(component, projection.getPeriod(), projection.getRange(),mobilityRefrigerioTopList);
                            methodsPeru.familyAssignment(component, projection.getPeriod(), projection.getRange(),familyAllowanceList, minimumSalaryList);
                            methodsPeru.lumpSumBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), lumpSumBonusList, countEJC, countGER, classificationMap);
                            methodsPeru.signingBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), signingBonusList, countEJC, countGER, classificationMap);
                            methodsPeru.extraordinaryConventionBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), extraordinaryGratificationList, countEMP, classificationMap);
                            methodsPeru.extraordinaryBonus(component, projection.getPeriod(), projection.getRange(), extraordinaryBonusList, totalPositions);
                            methodsPeru.epsContribution(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.schoolAssignment(component, projection.getPeriod(), projection.getRange(), schoolAllowanceSeasonalityList);
                            methodsPeru.studiesBonus(component, projection.getPeriod(), projection.getRange(), preSchoolAllowanceSeasonalityList);
                            methodsPeru.foodBenefits(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), classificationMap);
                            methodsPeru.interns(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), youngExecutiveSalaryList, internSalaryList, classificationMap);
                            methodsPeru.medicalInsurance(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.vacationProvision(component, projection.getPeriod(), projection.getRange(), vacationDaysList);
                            methodsPeru.seniority(component, projection.getPeriod(), projection.getRange(), headcountData.getFContra(), quinquenniumMap, classificationMap,  headcountData.getCategoryLocal());
                            methodsPeru.srdBonus(component, projection.getPeriod(), projection.getRange());
                            methodsPeru.topPerformerBonus(component, projection.getPeriod(), projection.getRange(), headcountData.getCategoryLocal(), ejcPeopleBTPList, ejcBonusBTPList, dirPeopleBTPList, dirBonusBTPList, classificationMap);
                            //methodsPeru.epsCredit(component, projection.getPeriod(), projection.getRange());
                            // public void extraordinaryTransferBonus(List<PaymentComponentDTO> component, String period, Integer range)
                            methodsPeru.extraordinaryTransferBonus(component, projection.getPeriod(), projection.getRange());
                            /*methodsPeru.voluntaryContribution(component, projection.getPeriod(), projection.getRange());*/
                            //public void theoreticalSalaryGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap) {
                            methodsPeru.theoreticalSalaryGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void vacationProvisionGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingCompensationGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void afpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncrementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void snpIncrementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.snpIncrementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAllowanceGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAllowanceGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void topPerformerBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void groupResponsibleBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);// public void housingAssignmentGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void judicialMandateConceptsGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void specialDaysBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void nightWorkBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusGratification(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //public void detachmentBonus(List<PaymentComponentDTO> components, String period, Integer range, List<ParametersDTO> detachmentBonusValueList)
                            methodsPeru.detachmentBonus(component, projection.getPeriod(), projection.getRange(), distinctionAllowanceSeasonalityList);
                            methodsPeru.detachmentBonusGratification(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void theoreticalSalaryTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void vacationProvisionTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void housingCompensationTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void afpIncrementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncrementTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void increaseSNPAndIncreaseTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increaseSNPAndIncreaseTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAssignmentTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void topPerformerBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingAssignmentTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void judicialMandateConceptsTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void complementaryBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);//public void specialDaysBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void nightWorkBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void groupResponsibleBonusTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void theoreticalSalaryCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void vacationProvisionCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void topPerformerBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void groupResponsibleBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void storeDayCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void housingAssignmentCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void judicialMandateConceptsCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void specialDaysBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void availabilityBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void nightWorkBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void detachmentBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void tfspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.tfspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void pspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.pspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //ublic void rspCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.rspCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void coinvCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.coinvCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void theoreticalSalaryEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.theoreticalSalaryEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);//public void vacationProvisionEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.vacationProvisionEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void housingCompensationEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void afpIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncreaseEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void increaseSNPAndIncreaseEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increaseSNPAndIncreaseEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void srdBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.srdBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void basicSalaryComplementEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void familyAllowanceEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAllowanceEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void teleworkLawEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.teleworkLawEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void topPerformerBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.topPerformerBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void groupResponsibleBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.groupResponsibleBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void storeDayEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.storeDayEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void housingAssignmentEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingAssignmentEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void judicialMandateConceptsEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.judicialMandateConceptsEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void complementaryBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.complementaryBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void specialDaysBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.specialDaysBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void detachmentBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.detachmentBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void availabilityBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.availabilityBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void nightWorkBonusEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.nightWorkBonusEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void COINVEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.COINVEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void PSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.PSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void RSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.RSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void TFSPEssaludTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.TFSPEssaludTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //  public void calculatePlanPrevDirAportVolEmp(List<PaymentComponentDTO> components, String period, Integer range)
                            methodsPeru.calculatePlanPrevDirAportVolEmp(component, projection.getPeriod(), projection.getRange());
                            // public void calculateLifeInsurance(List<PaymentComponentDTO> components, String period, Integer range, Map<Tuple<Integer, Integer>, Double> ageSVMap, List<ParametersDTO> groupSVList, LocalDate birthDate)
                            methodsPeru.calculateLifeInsurance(component, projection.getPeriod(), projection.getRange(), ageSVMap, groupSVList, headcountData.getFNac());
                            //public void housingCompensationCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.housingCompensationCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void afpIncreaseCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.afpIncreaseCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void increase33CTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.increase33CTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void provisionBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //methodsPeru.provisionBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void srdBonusCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.srdBonusCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            // public void basicSalaryComplementCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.basicSalaryComplementCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void familyAssignmentCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            methodsPeru.familyAssignmentCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                            //public void tDayCTSTemporaryBonus(List<PaymentComponentDTO> components, String period, Integer range, Map<String, ConceptoPresupuestal> conceptoPresupuestalMap)
                            //methodsPeru.tDayCTSTemporaryBonus(component, projection.getPeriod(), projection.getRange(), conceptoPresupuestalMap);
                        } catch (Exception e) {
                            log.error("Exception occurred in method for headcountData: " + headcountData, e);
                            log.error("Exception message: " + e.getMessage());
                            log.error("Exception cause: " + e.getCause());
                            log.error("Exception stack trace: " + Arrays.toString(e.getStackTrace()));
                        }
                    });
        }
    }

    private void updateCaches(ParametersByProjection projection) {
        // Actualizar quinquenniumMap
        if (projection.getSeniorityAndQuinquenniums() != null && !projection.getSeniorityAndQuinquenniums().isEmpty()) {
            Map<Integer, BigDecimal> tempQuinquenniumMap = new HashMap<>();
            for (SeniorityAndQuinquennium item : projection.getSeniorityAndQuinquenniums()) {
                tempQuinquenniumMap.put(item.getSeniority(), item.getQuinquennium());
            }
            synchronized (quinquenniumMap) {
                quinquenniumMap.clear();
                quinquenniumMap.putAll(tempQuinquenniumMap);
            }
            //log.info("Caché de quinquenios actualizada con {} entradas", quinquenniumMap.size());
        }

        // Actualizar classificationMap
        if (projection.getEmployeeClassifications() != null && !projection.getEmployeeClassifications().isEmpty()) {
            Map<String, EmployeeClassification> tempClassificationMap = new HashMap<>();
            for (EmployeeClassification classification : projection.getEmployeeClassifications()) {
                String key = classification.getCategory().toUpperCase();
                EmployeeClassification existingClassification = tempClassificationMap.get(key);

                if (existingClassification == null ||
                        !existingClassification.getTypeEmp().equals(classification.getTypeEmp()) ||
                        !Objects.equals(existingClassification.getValueAllowance(), classification.getValueAllowance())) {

                    tempClassificationMap.put(key, classification);
                }
            }
            synchronized (classificationMap) {
                classificationMap.clear();
                classificationMap.putAll(tempClassificationMap);
            }
            //log.info("Caché de clasificaciones de empleados actualizada con {} entradas", classificationMap.size());
        }
        // Actualizar conceptoPresupuestalMap
        if (projection.getConceptoPresupuestals() != null && !projection.getConceptoPresupuestals().isEmpty()) {
            Map<String, ConceptoPresupuestal> tempConceptoPresupuestalMap = new HashMap<>();
            for (ConceptoPresupuestal concepto : projection.getConceptoPresupuestals()) {
                ConceptoPresupuestal adjustedConcepto = new ConceptoPresupuestal();
                adjustedConcepto.setConceptoPresupuestal(concepto.getConceptoPresupuestal());

                // Pre-calculate all percentages by dividing by 100
                if (concepto.getGratificacion() != null) {
                    adjustedConcepto.setGratificacion(concepto.getGratificacion().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                }
                if (concepto.getCts() != null) {
                    adjustedConcepto.setCts(concepto.getCts().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                }
                if (concepto.getEssalud() != null) {
                    adjustedConcepto.setEssalud(concepto.getEssalud().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                }
                if (concepto.getBonifExtTemp() != null) {
                    adjustedConcepto.setBonifExtTemp(concepto.getBonifExtTemp().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                }

                tempConceptoPresupuestalMap.put(concepto.getConceptoPresupuestal(), adjustedConcepto);
            }
            synchronized (conceptoPresupuestalMap) {
                conceptoPresupuestalMap.clear();
                conceptoPresupuestalMap.putAll(tempConceptoPresupuestalMap);
            }
            //log.info("Caché de conceptos presupuestales actualizada con {} entradas", conceptoPresupuestalMap.size());
        }
    }

    private List<ParametersDTO> filterParametersByName(List<ParametersDTO> parameters, String name) {
        return parameters.stream()
                .filter(p -> Objects.equals(p.getParameter().getDescription(), name))
                .collect(Collectors.toList());
    }
    public List<ParametersDTO> filterParametersByRootName(List<ParametersDTO> parameters, String rootName) {
        return parameters.stream()
                .filter(p -> p.getParameter().getDescription().startsWith(rootName))
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
        List<ParametersDTO> revisionList = filterParametersByName(projection.getParameters(), "Revision Salarial Mexico");
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
                //.filter(h -> h.getPo().equals("PO10002098") || h.getPo().equalsIgnoreCase("PO10038706") || h.getPo().equals("PO9981236"))
                .parallel()
                .forEach(headcountData -> {
                    // log.info("getPo {}  -  isCp {}",headcountData.getPo(), headcountData.getPoName().contains("CP"));
                    //log.debug("getPo {} - Salary {}",headcountData.getPo(), headcountData.getComponents().stream().filter(c->c.getPaymentComponent().equals("PC938003") || c.getPaymentComponent().equals("PC938012")).mapToDouble(c->c.getAmount().doubleValue()).max().getAsDouble());
                    //log.debug("getPo {} - Salary {}",  headcountData.getConvent());
                    //log.debug("getPo {} - Salary {}",  headcountData.getLevel());
                    String convenioNivel = headcountData.getConvent() + headcountData.getLevel();
                    String convenio = headcountData.getConvent();
                    //log.debug("convenioNivel {}",convenioNivel);
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.salary2(component, salaryList, incrementList, revisionList, projection.getPeriod(), projection.getRange(), headcountData.getPoName());
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
                //.filter(h -> h.getPo().equals("PO10002098"))
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
        List<RangeBuDTO> temporalParameters = projection.getTemporalParameters();
        RangeBuDTO rangeBuByBU = temporalParameters != null ? temporalParameters.stream()
                .filter(r -> r.getIdBu().equals(projection.getIdBu()))
                .findFirst()
                .orElse(null) : null;
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
        //Dias licencia anual
        List<ParametersDTO> diasLicenciaAnual = filterParametersByName(projection.getParameters(), "Dias licencia anual");
        headcount
                .stream()
                //filter por po PO10042856 || PO99006564
                //.filter(h -> h.getPo().equals("PO10042856") || h.getPo().equals("PO99006564"))
                .parallel()
                .forEach(headcountData -> {
                    try {

                        //log.info("getPoName {}",headcountData.getPo());

                        if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                            addBaseExtern(headcountData,projection.getBaseExtern(),
                                    projection.getPeriod(),projection.getRange());
                        }
                        List<PaymentComponentDTO> component = headcountData.getComponents();
                      /*  log.info("getClassEmployee -> {}", headcountData.getPoName());*/
                        methodsUruguay.salary(component, salaryIncreaseList, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), inflationList);
                        methodsUruguay.overtime(component,headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteHHEE);
                        methodsUruguay.activeGuard(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), factorAjusteGuardias);
                        methodsUruguay.specialGuard(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.legalGuard(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.quarterlyBonus(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.quarterlyBonus8(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.monthlyBonus(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        methodsUruguay.monthlyBonus15(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //annualBonus
                        methodsUruguay.annualBonus(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //salesBonus
                        methodsUruguay.salesBonus(component, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //salesCommissions
                        methodsUruguay.salesCommissions(component, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //collectionCommissions
                        methodsUruguay.collectionCommissions(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //foodTicket
                        methodsUruguay.foodTicket(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), aumentoValorTicketAlimentacion);
                        //SUAT
                        methodsUruguay.suat(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), aumentoValorSUAT);
                        //SUAT GRav Dinero
                        methodsUruguay.suatGravDinero(component, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //BSE
                        methodsUruguay.bcBs(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), inflationList);
                        //metlife
                        methodsUruguay.metlife(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), salaryIncreaseList);
                        //metlife grav dinero
                        methodsUruguay.metlifeGravDinero(component, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //bse
                        methodsUruguay.bse(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), impuestoBSE);
                        //carAllowance
                        methodsUruguay.carAllowance(component, headcountData.getPoName(), projection.getPeriod(), projection.getRange(), salaryIncreaseList, inflationList);
                        //aguinaldo
                        methodsUruguay.aguinaldo(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                        //Salario Vacacional
                        methodsUruguay.vacationSalary(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange(), diasLicenciaAnual, headcountData.getFContra());
                        //montepioPatronal
                        methodsUruguay.montepioPatronal(component, projection.getPeriod(), projection.getRange());
                        //Montepío Patronal
                        methodsUruguay.montepioPatronalEspecie(component, projection.getPeriod(), projection.getRange(), montepio);
                        //frlPatronal
                        methodsUruguay.frlPatronal(component, projection.getPeriod(), projection.getRange());
                        //fonasaPatronal
                        methodsUruguay.fonasaPatronal(component, projection.getPeriod(), projection.getRange(), fonasa);
                        //fgclPatronal
                        methodsUruguay.fgclPatronal(component, projection.getPeriod(), projection.getRange(), fgcl);
                        //employerContributions
                        methodsUruguay.aportesPatronales(component, projection.getPeriod(), projection.getRange(), fonasa, montepio, fgcl);
                        //Uniforme
                        methodsUruguay.uniforme(component, projection.getPeriod(), projection.getRange());
                        //TFSP
                        methodsUruguay.tfsp(component, projection.getPeriod(), projection.getRange());
                        //psp
                        methodsUruguay.psp(component, projection.getPeriod(), projection.getRange());
                        //GESP
                        methodsUruguay.gesp(component, projection.getPeriod(), projection.getRange());
                        //IPD
                        methodsUruguay.ipd(component, projection.getPeriod(), projection.getRange());
                        //Fiesta
                        methodsUruguay.fiesta(component, projection.getPeriod(), projection.getRange());
                    } catch (Exception e) {
                        log.error("Exception occurred in method for headcountData: " + headcountData, e);
                        log.error("Exception message: " + e.getMessage());
                        log.error("Exception cause: " + e.getCause());
                        log.error("Exception stack trace: " + Arrays.toString(e.getStackTrace()));
                    }
                });
    }

    private void isEcuadorv2(List<ProjectionDTO> headcount, ParametersByProjection projection) {
        Ecuador methodsEcuador = new Ecuador(sseReportService, userSessionService);
        projection.getParameters()
                .sort((o1, o2) -> Shared.compare(o1.getPeriod(), o2.getPeriod()));

        // Procesar BaseExtern
        if (projection.getBaseExtern() != null && !projection.getBaseExtern().getData().isEmpty()) {
            List<ProjectionDTO> baseExternPositions = addBaseExtern(headcount.get(0), projection.getBaseExtern(),
                    projection.getPeriod(), projection.getRange());

            // Crear un mapa de las posiciones existentes
            Map<String, ProjectionDTO> existingPositions = headcount.stream()
                    .collect(Collectors.toMap(ProjectionDTO::getPo, Function.identity(), (existing, replacement) -> existing));

            // Actualizar o añadir posiciones
            for (ProjectionDTO baseExternProjection : baseExternPositions) {
                String po = baseExternProjection.getPo();
                if (existingPositions.containsKey(po)) {
                    // Actualizar posición existente
                    ProjectionDTO existingProjection = existingPositions.get(po);
                    mergeComponents(existingProjection, baseExternProjection);
                } else {
                    // Añadir nueva posición
                    headcount.add(baseExternProjection);
                }
            }
        }

        // Procesar todas las posiciones
        for (ProjectionDTO projectionDTO : headcount) {
            processProjectionDTO(projectionDTO, projection, methodsEcuador);
        }

        // Log para verificar
        //log.info("Número total de posiciones después de procesar: " + headcount.size());
        //log.info("Número de BC123: " + headcount.stream().filter(p -> "BC123".equals(p.getPo())).count());
    }

    private void mergeComponents(ProjectionDTO existing, ProjectionDTO newProjection) {
        Map<String, PaymentComponentDTO> existingComponents = existing.getComponents().stream()
                .collect(Collectors.toMap(PaymentComponentDTO::getPaymentComponent, Function.identity(), (e1, e2) -> e1));

        for (PaymentComponentDTO newComponent : newProjection.getComponents()) {
            if (existingComponents.containsKey(newComponent.getPaymentComponent())) {
                // Actualizar componente existente
                PaymentComponentDTO existingComponent = existingComponents.get(newComponent.getPaymentComponent());
                existingComponent.setAmount(newComponent.getAmount());
                if (newComponent.getProjections() != null) {
                    existingComponent.setProjections(new ArrayList<>(newComponent.getProjections()));
                }
            } else {
                // Añadir nuevo componente
                existing.getComponents().add(newComponent);
            }
        }
    }

    private void processProjectionDTO(ProjectionDTO projectionDTO, ParametersByProjection projection, Ecuador methodsEcuador) {
        List<PaymentComponentDTO> components = projectionDTO.getComponents();
        if (components == null) {
            components = new ArrayList<>();
            projectionDTO.setComponents(components);
        }

        if (!projection.getParameters().isEmpty()) {
            for (int j = 0; j < projection.getParameters().stream().filter(q -> q.getPeriod() != null).count(); j++) {
                components = validate(methodsEcuador, components, projection.getParameters().get(j), projection.getParameters());
            }
        }

        // Parámetros que no tienen periodo que se ejecutan siempre
        methodsEcuador.srv(components, projection.getParameters());
        methodsEcuador.addDecimoCuarto(components, projection.getPeriod(), projection.getParameters(), projection.getRange());
        methodsEcuador.iess(components, projection.getPeriod(), projection.getParameters(), projection.getRange());
        methodsEcuador.decimoTercero(components, projection.getPeriod(), projection.getParameters(), projection.getRange());
        methodsEcuador.fondoReserva(components, projection.getPeriod(), projection.getParameters(), projection.getRange());
        methodsEcuador.vacations(components, projection.getPeriod(), projection.getParameters(), projection.getRange());

        projectionDTO.setComponents(components);
    }

    private PaymentComponentDTO createPaymentComponent(String header, Object value, String period, Integer range) {
        if ("mes_promo".equalsIgnoreCase(header)) {
            return PaymentComponentDTO.builder()
                    .paymentComponent(header)
                    .amountString(value != null ? value.toString() : null)
                    .build();
        } else {
            BigDecimal amount = value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
            return PaymentComponentDTO.builder()
                    .paymentComponent(header)
                    .amount(amount)
                    .projections(generateMonthProjectionV3(period, range, amount))
                    .build();
        }
    }
   /* public static List<MonthProjection> generateMonthProjection(String monthBase, int range, BigDecimal amount) {
        String cacheKey = monthBase + "_" + range + "_" + amount;
        return PROJECTION_CACHE.computeIfAbsent(cacheKey, k -> {
            YearMonth startDate = YearMonth.parse(monthBase, MONTH_FORMATTER);
            return IntStream.range(0, range)
                    .mapToObj(i -> MonthProjection.builder()
                            .month(startDate.plusMonths(i).format(MONTH_FORMATTER))
                            .amount(amount)
                            .build())
                    .collect(Collectors.toList());
        });
    }*/
   /*public List<ProjectionDTO> addBaseExtern(ProjectionDTO originalHeadcount, BaseExternResponse baseExtern, String period, Integer range) {
       List<String> relevantHeaders = baseExtern
               .getHeaders()
               .stream()
               .filter(t -> Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(t)))
               .collect(Collectors.toList());

       ConcurrentMap<String, ProjectionDTO> positionsMap = new ConcurrentHashMap<>();
       positionsMap.put(originalHeadcount.getPo(), originalHeadcount);

       baseExtern
               .getData()
               //.stream() // Usa stream() en lugar de parallelStream() para evitar anidación
               .forEach(po -> {
                   String currentPo = (String) po.get("po");
                   positionsMap.compute(currentPo, (key, existingProjection) -> {
                       ProjectionDTO projection = (existingProjection != null) ? existingProjection :
                               ProjectionDTO.builder()
                                       .po(currentPo)
                                       .components(new ArrayList<>())
                                       .build();
                       updateProjection2(projection, po, relevantHeaders, period, range);
                       return projection;
                   });
               });

       return new ArrayList<>(positionsMap.values());
    }*/
   public List<ProjectionDTO> addBaseExtern(ProjectionDTO originalHeadcount, BaseExternResponse baseExtern, String period, Integer range) {
       List<String> relevantHeaders = baseExtern.getHeaders().stream()
               .filter(t -> Arrays.stream(headers).noneMatch(c -> c.equalsIgnoreCase(t)))
               .collect(Collectors.toList());

       ConcurrentMap<String, ProjectionDTO> positionsMap = new ConcurrentHashMap<>();
       positionsMap.put(originalHeadcount.getPo(), originalHeadcount);

       // Utilizar un ForkJoinPool personalizado para un mejor control sobre la concurrencia
       //int parallelism = Runtime.getRuntime().availableProcessors();
       //ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);

       try {
           BASE_EXTERN_THREAD_POOL.submit(() ->
                   baseExtern
                           .getData()
                           //.parallelStream()
                           .forEach(po -> {
                               String currentPo = (String) po.get("po");
                               positionsMap.compute(currentPo, (key, existingProjection) -> {
                                   ProjectionDTO projection = (existingProjection != null) ? existingProjection :
                                           ProjectionDTO.builder()
                                                   .po(currentPo)
                                                   .components(new ArrayList<>())
                                                   .build();
                                   updateProjection2(projection, po, relevantHeaders, period, range);
                                   return projection;
                               });
                           })
           ).get(60, TimeUnit.SECONDS);
       } catch (InterruptedException e) {
           log.error("Thread was interrupted", e);
           Thread.currentThread().interrupt();
           throw new RuntimeException("Thread was interrupted", e);
       } catch (ExecutionException e) {
           log.error("Execution exception", e);
           throw new RuntimeException("Execution exception", e);
       } catch (TimeoutException e) {
           log.error("Task timed out", e);
           throw new RuntimeException("Task timed out", e);
       }

       return new ArrayList<>(positionsMap.values());
   }

    private void updateProjection2(ProjectionDTO projection, Map<String, Object> po, List<String> relevantHeaders, String period, Integer range) {
        relevantHeaders.forEach(header -> {
            //log.info("po.get(header) {}",po.get(header));
            PaymentComponentDTO component = createPaymentComponent(header, po.get(header), period, range);
            projection.getComponents().add(component);
        });
        projection.setAreaFuncional(po.get("AF") != null ? po.get("AF").toString() : null);
        projection.setIdssff((String) po.get("idssff"));
    }

    @Override
    public Config getComponentByBuV2(String bu){
        ConfigStrategy strategy = configStrategyFactory.getStrategy(bu);
        return strategy.getConfig(bu);
    }

    @Override
    public Config getComponentByBu(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
        //mexico
        List<Convenio> convenio = convenioRepository.findAll();
        List<ConvenioBono> convenioBono = convenioBonoRepository.findAll();
        //uruguay
        // Obtener todos los componentes
        List<ComponentProjection> allComponents = sharedRepo.getComponentByBu(bu);
        //log.info("allComponents {}",allComponents);
        // Filtrar solo los componentes base
        List<ComponentProjection> baseComponents = allComponents.stream()
                .filter(ComponentProjection::getIsBase)
                .collect(Collectors.toList());
        //log.info("baseComponents {}",baseComponents);
        // Filtrar los componentes que tienen un typePaymentComponentId único
        List<ComponentProjection> uniqueTypeComponents = allComponents.stream()
                .filter(component -> isUniqueTypePaymentComponentId(component, allComponents))
                .filter(c -> !c.getIsAdditional())
                .collect(Collectors.toList());
        //log.info("uniqueTypeComponents {}",uniqueTypeComponents);
        //log.debug("uniqueTypeComponents {}",uniqueTypeComponents);
        // Combinar las dos listas y eliminar duplicados
        List<ComponentProjection> combinedComponents =
                Stream.concat(baseComponents.stream(), uniqueTypeComponents.stream())
                .distinct()
                .collect(Collectors.toList());
        //peru
        //list nominaPaymentComponentLink
        List<NominaPaymentComponentLink> nominaPaymentComponentLink = nominaPaymentComponentLinkRepository.findByBu(vbu.getId());
        //log.debug("combinedComponents {}",combinedComponents);
        List<EmployeeClassification> employeeClassifications = employeeClassificationRepository.findAll();
        //seniorityAndQuinquenniumRepository
        List<SeniorityAndQuinquennium> seniorityAndQuinquennium = seniorityAndQuinquenniumRepository.findAll();
        //conceptoPresupuestalRepository
        List<ConceptoPresupuestal> conceptoPresupuestal = conceptoPresupuestalRepository.findAll();
        List<ValidationRule> validationRules = validationRuleRepository.findByBu_Bu(vbu.getId());

        //argentina
        // Obtener todos los convenios y sus parámetros
        List<ConventArg> convenios = conventArgRepository.findAll();
        List<ms.hispam.budget.dto.projections.ParameterProjection> allParameters = parameterRepository.getParameterBu(bu);

        // Agrupar parámetros por convenio
        Map<String, List<ms.hispam.budget.dto.projections.ParameterProjection>> groupedParameters = allParameters.stream()
                .collect(Collectors.groupingBy(pa -> convenios.stream()
                        .filter(conv -> conv.getConvenio().equals(pa.getVparameter()))
                        .map(ConventArg::getConvenio)
                        .findFirst()
                        .orElse("No Convenio")));

        // Crear lista de grupos de parámetros
        List<ParameterGroup> parameterGroups = groupedParameters.entrySet().stream()
                .map(entry -> ParameterGroup.builder()
                        .convenio(entry.getKey())
                        .parameters(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        return DefaultConfig.builder()
                .components(combinedComponents) // usar los componentes combinados
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vTemporal(buService.getAllBuWithRangos(vbu.getId()))
                .convenios(convenio)
                .convenioBonos(convenioBono)
                .employeeClassifications(employeeClassifications)
                .seniorityAndQuinquenniums(seniorityAndQuinquennium)
                .conceptoPresupuestals(conceptoPresupuestal)
                .vDefault(parameterDefaultRepository.findByBu(vbu.getId()))
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .nominaPaymentComponentRelations(nominaPaymentComponentLink)
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
                .validationRules(validationRules)
                .current(vbu.getCurrent())
                .build();
    }
    private boolean isUniqueTypePaymentComponentId(ComponentProjection component, List<ComponentProjection> components) {
        //log.info("component {}",component);
        long count = components.stream()
                .filter(c -> c.getType().equals(component.getType()))
                .count();
        return count == 1;
    }
    private boolean isUniqueTypePaymentComponentId2(ComponentProjection component) {
        //log.info("component {}",component);
        List<TypePaymentComponent> allTypePaymentComponents = typePaymentComponentRepository.findAll();
        long count = allTypePaymentComponents.stream()
                .filter(c -> c.getId().equals(component.getType()))
                .count();
        return count == 1;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Boolean saveProjection(ParameterHistorial projection,String email) {
            //log.info("projection {}",projection);
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
            if(projection.getTemporalParameters()!=null && !projection.getTemporalParameters().isEmpty()){
                buService.saveRangeBuDTO(projection.getTemporalParameters(), historial);
            }
            //SAVE CONVENIO AND CONVENIO BONO
            if(projection.getConvenio()!=null && !projection.getConvenio().isEmpty()){
                HistorialProjection finalHistorial1 = historial;
                List<ConvenioHistorial> convenioHistorials = projection.getConvenio().stream().map(c-> {
                    ConvenioHistorial conven = new ConvenioHistorial();
                    conven.setHistorialProjection(finalHistorial1);
                    conven.setId(c.getId());
                    conven.setName(c.getConvenioName());
                    conven.setImssPercentage(c.getImssPercentage());
                    conven.setRetiroPercentage(c.getRetiroPercentage());
                    conven.setInfonavitPercentage(c.getInfonavitPercentage());
                    return conven;
                }).collect(Collectors.toList());
                convenioHistorialRepository.saveAll(convenioHistorials);
            }
            if(projection.getConvenioBono()!=null && !projection.getConvenioBono().isEmpty()){
                HistorialProjection finalHistorial1 = historial;
                List<ConvenioBonoHistorial> convenioBonoHistorials = projection.getConvenioBono().stream().map(c-> {
                    ConvenioBonoHistorial conven = new ConvenioBonoHistorial();
                    conven.setHistorialProjection(finalHistorial1);
                    conven.setId(c.getId());
                    conven.setNivel(c.getConvenioNivel());
                    conven.setPorcentaje(c.getBonoPercentage());
                    return conven;
                }).collect(Collectors.toList());
                convenioBonoHistorialRepository.saveAll(convenioBonoHistorials);
            }

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
        //Recuperar lo parametros temporales del historial
        //List<RangeBuDTO> temporalParametersHistorical = buService.getTemporalParameterHistoricalProjections(idHistorial);
        //Recuperar convenios bono del historial
        //List<ConvenioBonoHistorial> convenioBonoHistorial = convenioBonoHistorialRepository.findByHistorialProjection_Id(idHistorial);
        //Recuperar convenios del historial
        //List<ConvenioHistorial> convenioHistorial = convenioHistorialRepository.findByHistorialProjection_Id(idHistorial);

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
                        .idBu(p.getIdBu())
                       /* .temporalParameters(temporalParametersHistorical)
                        .convenio(convenioHistorial.stream().map(c->ConvenioDTO.builder()
                                .id(c.getId())
                                .convenioName(c.getName())
                                .imssPercentage(c.getImssPercentage())
                                .retiroPercentage(c.getRetiroPercentage())
                                .infonavitPercentage(c.getInfonavitPercentage())
                                .build()).collect(Collectors.toList()))
                        .convenioBono(convenioBonoHistorial.stream().map(c->ConvenioBonoDTO.builder()
                                .id(c.getId())
                                .convenioNivel(c.getNivel())
                                .bonoPercentage(c.getPorcentaje())
                                .build()).collect(Collectors.toList()))*/
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
    @Async("reportTaskExecutor")
    @Override
    public void downloadProjection(ParametersByProjection projection, String userContact, ReportJob job, Integer idBu, String sessionId, String reportName, Long historyId) {
        try {
            Shared.replaceSLash(projection);
            //sseReportService.sendUpdate(sessionId, "iniciado", "Iniciando generación del reporte", 0);
            List<ComponentProjection> componentProjections = sharedRepo.getComponentByBu(projection.getBu());

            DataRequest dataBase = DataRequest.builder()
                    .idBu(projection.getIdBu())
                    .bu(projection.getBu())
                    .period(projection.getPeriod())
                    .nominaFrom(projection.getNominaFrom())
                    .nominaTo(projection.getNominaTo())
                    .isComparing(false)
                    .build();
            xlsReportService.generateAndCompleteReportAsync(projection,
                    componentProjections, getDataBase(dataBase), userContact, job, userContact, idBu, sessionId, reportName, historyId);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }

    @Async("reportTaskExecutor")
    @Override
    public void downloadPlannerAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job, String sessionId) {
        try {
            Bu bu = buRepository.findById(idBu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
            Shared.replaceSLash(projection);
            Map<String, AccountProjection> componentesMap = new HashMap<>();
            List<AccountProjection> components = getAccountsByBu(idBu) ;
            for (AccountProjection concept : components) {
                componentesMap.put(concept.getVcomponent(), concept);
            }
            //AÑADIR AL HEADCOUNT PO DE BASE EXTERN
            List<ProjectionDTO> headcount =  getHeadcount(projection,componentesMap);
            //log.info("headcount {}",headcount);
            xlsReportService.generateAndCompleteReportAsyncPlanner(projection, headcount, bu, sharedRepo.getAccount(idBu), job, userContact, sessionId);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }

    @Async("reportTaskExecutor")
    @Override
    //downloadCdgAsync
    public void downloadCdgAsync(ParametersByProjection projection, Integer type, Integer idBu, String userContact, ReportJob job, String sessionId) {
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
            xlsReportService.generateAndCompleteReportAsyncCdg(projection, headcount, bu, sharedRepo.getAccount(idBu),job,userContact, sessionId);
        } catch (Exception e) {
            log.error("Error al procesar la proyección", e);
            throw new CompletionException(e);
        }
    }

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
        // Limpiar las fechas de periodos y nóminas
        data.setPeriod(data.getPeriod().replace("/", ""));
        data.setNominaTo(data.getNominaTo().replace("/", ""));
        data.setNominaFrom(data.getNominaFrom().replace("/", ""));

        // Obtener las entidades relacionadas con el BU
        List<String> entities = legalEntityRepository.findByBu(data.getBu()).stream()
                .map(LegalEntity::getLegalEntity)
                .collect(Collectors.toList());

        // Obtener los componentes para el BU y filtrar por isComponent
        List<ComponentProjection> components = sharedRepo.getComponentByBu(data.getBu()).stream()
                .filter(ComponentProjection::getIscomponent)
                .collect(Collectors.toList());

        log.debug("components {}", components);

        // Obtener los tipos de empleados relacionados con el BU
        List<String> typeEmployee = typEmployeeRepository.findByBu(data.getIdBu()).stream()
                .map(TypeEmployeeProjection::getTypeEmployee)
                .collect(Collectors.toList());

        // Obtener datos de headcount
        List<HeadcountHistoricalProjection> headcount = repository.getHistoricalBuAndPeriodSp(
                Constant.KEY_BD,
                String.join(",", entities),
                data.getPeriod(),
                components.stream().map(ComponentProjection::getComponent).collect(Collectors.joining(",")),
                String.join(",", typeEmployee));

        if (headcount.isEmpty()) {
            return DataBaseMainReponse.builder()
                    .data(new ArrayList<>())
                    .components(components)
                    .nominas(new ArrayList<>())
                    .comparing(new ArrayList<>())
                    .build();
        }

        // Obtener códigos de nómina
        List<CodeNomina> codeNominas = codeNominaRepository.findByIdBu(data.getIdBu());

        // Obtener componentes nominales
        List<ComponentNominaProjection> nominal = repository.getcomponentNomina(
                Constant.KEY_BD,
                data.getBu(),
                data.getNominaFrom(),
                data.getNominaTo(),
                codeNominas.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")));

        // Crear un mapa de posición a HeadcountHistoricalProjection (tomando el primero en caso de duplicados)
        Map<String, HeadcountHistoricalProjection> headcountMap = headcount.stream()
                .collect(Collectors.toMap(
                        HeadcountHistoricalProjection::getPosition,
                        Function.identity(),
                        (existing, replacement) -> existing // Mantener el primero
                ));

        // Agrupar los ComponentAmount por posición
        Map<String, List<ComponentAmount>> headcountComponentsMap = headcount.stream()
                .collect(Collectors.groupingBy(
                        HeadcountHistoricalProjection::getPosition,
                        Collectors.mapping(
                                deuda -> new ComponentAmount(deuda.getComponent(), BigDecimal.valueOf(deuda.getAmount())),
                                Collectors.toList())
                ));

        // Crear un mapa de ID_SSFF a lista de ComponentAmount desde nominal
        Map<String, List<ComponentAmount>> nominalMap = nominal.stream()
                .collect(Collectors.groupingBy(
                        ComponentNominaProjection::getID_SSFF,
                        Collectors.mapping(
                                o -> ComponentAmount.builder()
                                        .component(o.getCodigoNomina())
                                        .amount(BigDecimal.valueOf(o.getImporte()))
                                        .build(),
                                Collectors.toList())
                ));

        // Construir la lista de deudas agrupadas
        List<DataBaseResponse> deudasAgrupadas = headcountComponentsMap.entrySet().stream()
                .map(entry -> {
                    String position = entry.getKey();
                    HeadcountHistoricalProjection info = headcountMap.get(position);
                    if (info == null) {
                        // Manejar el caso donde no se encuentra la información
                        log.warn("No se encontró HeadcountHistoricalProjection para la posición {}", position);
                        return null; // O puedes omitir esta entrada
                    }
                    String fechaNac = info.getFnac() != null ? info.getFnac().toString() : "";
                    String fechaContra = info.getFcontra() != null ? info.getFcontra().toString() : "";

                    // Obtener componentes desde headcount y nominal
                    List<ComponentAmount> componentsList = new ArrayList<>(entry.getValue());
                    List<ComponentAmount> nominalComponents = nominalMap.getOrDefault(info.getIdssff(), Collections.emptyList());
                    componentsList.addAll(nominalComponents);

                    return new DataBaseResponse(
                            position,
                            info.getIdssff(),
                            info.getPoname(),
                            info.getClassemp(),
                            fechaNac,
                            fechaContra,
                            info.getConvent(),
                            info.getLevel(),
                            componentsList,
                            info.getCategoria_Local(),
                            info.getEstado_Vacante()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Liberar memoria
        headcount = null;
        nominal = null;

        List<DataBaseResponse> comparing = new ArrayList<>();

        if (Boolean.TRUE.equals(data.getIsComparing())) {
            data.setPeriod(data.getPeriodComparing());
            data.setNominaFrom(data.getNominaFromComparing());
            data.setNominaTo(data.getNominaToComparing());
            data.setIsComparing(false);
            comparing = getDataBase(data).getData();
        }

        return DataBaseMainReponse.builder()
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

    //getConventArg
    @Override
    public List<ConventArgDTO> getConventArg(){
        List<ConventArg> listConventArg = conventArgRepository.findAll();
        return listConventArg.stream()
                .map(c->ConventArgDTO.builder()
                        .id(c.getId())
                        .convenio(c.getConvenio())
                        .areaPersonal(c.getAreaPersonal())
                        .build())
                .collect(Collectors.toList());
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
    private LocalDate parseDate(String dateString) {
        try {
            // Intenta parsear con el formato "yyyy/MM"
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy/MM"));
        } catch (DateTimeParseException e) {
            // Si falla, intenta con el formato "yyyyMM"
            return LocalDate.parse(dateString + "01", DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
    }
    private int convertDateToInt(LocalDate date) {
        return Integer.parseInt(date.format(DateTimeFormatter.ofPattern("yyyyMM")));
    }
    private List<ProjectionDTO> getHeadcountByAccount(ParametersByProjection projection){
        List<String> filterPositions = Arrays.asList(
                "PO10051081",
                "PO10029240"
        );
        //initializePeruCache(projection);
        //TODO: ADD MONTH BASE
        List<String> entities = legalEntityRepository.findByBu(projection.getBu()).stream().map(LegalEntity::getLegalEntity).collect(Collectors.toList());
        List<String> typeEmployee = typEmployeeRepository.findByBu(projection.getIdBu()).stream().map(TypeEmployeeProjection::getTypeEmployee).collect(Collectors.toList());
        List<HeadcountProjection> headcount =  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                        String.join(",", entities),projection.getPeriod(),String.join(",",
                                projection.getPaymentComponent().stream().map(PaymentComponentType::getComponent)
                                        .collect(Collectors.joining(","))),String.join(",", typeEmployee))
                .parallelStream() // Use parallel stream here
                //.filter(e -> filterPositions.contains(e.getPosition())) // user for debug
                //.filter(e->e.getIdssff().equalsIgnoreCase("1004103") || e.getIdssff().equalsIgnoreCase("1004392") || e.getIdssff().equalsIgnoreCase("1004929"))
                //.filter(e->e.getPosition().equals("PO10009248"))
                .map(e->HeadcountProjection.builder()
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
                        .categoryLocal(e.getCategoria_Local()) // Asegúrate de mapear correctamente
                        .estadoVacante(e.getEstado_Vacante()) // Asegúrate de mapear correctamente
                        .build()).collect(Collectors.toList());
        //log.info("headcount {}",headcount);
        List<CodeNomina> codeNominals = codeNominaRepository.findByIdBu(projection.getIdBu());
        LocalDate projectionFrom = parseDate(projection.getNominaFrom());
        LocalDate projectionTo = parseDate(projection.getNominaTo());
        //log.debug("projectionFrom {}",projectionFrom);
        //log.debug("projectionTo {}",projectionTo);
        Map<NominaRangeType, Pair<LocalDate, LocalDate>> intersectedRanges = intersectRanges(projectionFrom, projectionTo, codeNominals);
     /*   List<NominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,projection.getBu(),projection.getNominaFrom(),projection.getNominaTo(),
                        codeNominals.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")))
                .parallelStream() // Use parallel stream here
                .map(e->NominaProjection.builder()
                        .idssff(e.getID_SSFF())
                        .codeNomina(e.getCodigoNomina())
                        .importe(e.getImporte())
                        .qDiasHoras(e.getQ_Dias_Horas())
                        .build())
                .collect(Collectors.toList());*/
        List<NominaProjection> nominal = new ArrayList<>();

        for (Map.Entry<NominaRangeType, Pair<LocalDate, LocalDate>> entry : intersectedRanges.entrySet()) {
            NominaRangeType rangeType = entry.getKey();
            LocalDate from = entry.getValue().getLeft();
            LocalDate to = entry.getValue().getRight();

            List<String> relevantCodeNominas = new ArrayList<>();
            for (CodeNomina cn : codeNominals) {
                if (cn.getRangeType() == rangeType) {
                    String codeNomina = cn.getCodeNomina();
                   /* if (codeNomina.equals("5103925")) {
                        log.info("codeNomina {}",codeNomina);
                        //from
                        log.info("from {}",from);
                        //to
                        log.info("to {}",to);
                        //frecuencia
                        log.info("frecuencia {}", cn.getRangeType());
                    }*/
                    /*//code 6130
                    if (codeNomina.equals("6130")) {
                        log.info("codeNomina {}",codeNomina);
                        //from
                        log.info("from {}",from);
                        //to
                        log.info("to {}",to);
                        //frecuencia
                        log.info("frecuencia {}", cn.getRangeType());
                    }
                    if (codeNomina.equals("6166")) {
                        log.info("codeNomina {}",codeNomina);
                        //from
                        log.info("from {}",from);
                        //to
                        log.info("to {}",to);
                        //frecuencia
                        log.info("frecuencia {}", cn.getRangeType());
                    }*/
                    relevantCodeNominas.add(codeNomina);
                }
            }
            //log.info("relevantCodeNominas {}",relevantCodeNominas);
            String initDate = from.format(DateTimeFormatter.ofPattern("yyyyMM"));
            String endDate = to.format(DateTimeFormatter.ofPattern("yyyyMM"));

            if (!relevantCodeNominas.isEmpty()) {
                List<NominaProjection> rangeNominal = repository.getcomponentNomina(
                                Constant.KEY_BD,
                                projection.getBu(),
                                initDate,
                                endDate,
                                String.join(",", relevantCodeNominas)
                        )
                        .parallelStream()
                        .map(e -> NominaProjection.builder()
                                .idssff(e.getID_SSFF())
                                .codeNomina(e.getCodigoNomina())
                                .importe(e.getImporte())
                                .qDiasHoras(e.getQ_Dias_Horas())
                                .build())
                        .collect(Collectors.toList());

                nominal.addAll(rangeNominal);
            }
        }
        //log.debug("nominal {}",nominal);
        //log.debug("!projection.getBc() {}", projection.getBc());
        if(!projection.getBc().getData().isEmpty()){
            for (int i = 0; i < projection.getBc().getData().size(); i++) {
                Map<String, Object> resp = projection.getBc().getData().get(i);
                String pos = resp.get(HEADERPO).toString();

                // Remover cualquier HeadcountProjection existente con la misma posición
                headcount.removeIf(e -> e.getPosition().equals(pos));

                LocalDate fNac = resp.get("FNAC") != null ? convertToJavaDate(resp.get("FNAC").toString()) : LocalDate.now();
                LocalDate fContra = resp.get("FCON") != null ? convertToJavaDate(resp.get("FCON").toString()) : LocalDate.now();
                String typeEmpl = resp.get("typeEmployee") != null ? resp.get("typeEmployee").toString() : "";
                String conv = resp.get("CONV") != null ? resp.get("CONV").toString() : "";
                String level = resp.get("NIV") != null ? resp.get("NIV").toString() : "";
                String categoryLocal = resp.get("categoryLocal") != null ? resp.get("categoryLocal").toString() : "";
                String estadoVacante = resp.get("estadoVacante") != null ? resp.get("estadoVacante").toString() : "";
                String name = resp.get("name") != null ? resp.get("name").toString() : "";
                final int finalI = i;  // Hacemos final esta variable para usarla en la lambda

                for (Map.Entry<String, Object> entry : resp.entrySet()) {
                    String entryKey = entry.getKey();
                    Object entryValue = entry.getValue();

                    projection.getPaymentComponent()
                            .stream()
                            .filter(t -> !entryKey.equals(HEADERPO) && t.getName().equalsIgnoreCase(entryKey))
                            .findFirst()
                            .ifPresent(t -> {
                                HeadcountProjection headcountProjection = HeadcountProjection.builder()
                                        .position(pos)
                                        .idssff("")  // Se actualizará más tarde si es necesario
                                        .poname(name)
                                        .classEmp(typeEmpl)
                                        .fContra(fContra)
                                        .fNac(fNac)
                                        .convent(conv)
                                        .level(level)
                                        .categoryLocal(categoryLocal)
                                        .estadoVacante(estadoVacante)
                                        .component(t.getComponent())
                                        .amount(Double.parseDouble(entryValue.toString()))
                                        .build();
                                headcount.add(headcountProjection);
                            });

                    codeNominals.stream()
                            .filter(r -> !entryKey.equals(HEADERPO) && r.getName().equalsIgnoreCase(entryKey))
                            .findFirst()
                            .ifPresent(q -> {
                                NominaProjection nominaProjection = NominaProjection.builder()
                                        .idssff(String.valueOf(finalI))
                                        .codeNomina(q.getCodeNomina())
                                        .importe(Double.parseDouble(entryValue.toString()))
                                        .build();
                                nominal.add(nominaProjection);

                                // Actualizar idssff en los HeadcountProjection correspondientes
                                headcount.stream()
                                        .filter(h -> h.getPosition().equals(pos))
                                        .forEach(h -> h.setIdssff(String.valueOf(finalI)));
                            });
                }
            }
        }
        //log.debug("headcount -> {}", headcount);
        //log.debug("headcountTemporal -> {}",headcountTemporal);
        return new ArrayList<>(headcount
                .parallelStream()
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
                                                                .projections(getProjectionMethodByBu(projection.getBu(), projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                                                                .build();
                                                        list.stream()
                                                                .filter(t-> t.getComponent() !=null && t.getComponent().equalsIgnoreCase(p.getComponent()))
                                                                .findFirst()
                                                                .ifPresent(u->{
                                                                    if (u.getAmount() != null) {
                                                                        r.setPaymentComponent(p.getComponent());
                                                                        r.setType(p.getType());
                                                                        r.setAmount(BigDecimal.valueOf(u.getAmount()));
                                                                        r.setProjections(getProjectionMethodByBu(projection.getBu(), projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(u.getAmount())));
                                                                    } else {
                                                                        throw new BadRequestException("No se encuentra información para el mes base " + projection.getPeriod());
                                                                    }
                                                        });
                                                        return r;
                                                    }

                                            ).collect(Collectors.toList());
                                    List<NominaProjection> filteredNominal;
                                    String bu = projection.getBu().toUpperCase();
                                    String idssff = list.get(0).getIdssff();
                                    /*if ("T. CHILE".equalsIgnoreCase(projection.getBu())) {
                                        //TODO: Revisar si es necesario filtrar por BU, por que ya filtramos en initializePeruCache
                                        if (nominaPaymentComponentLinksByYearCache == null) {
                                            nominaPaymentComponentLinksByYearCache = new HashMap<>();
                                            log.debug("nominaPaymentComponentLinksByYearCache {}",projection.getNominaPaymentLinks());
                                            Map<Integer, List<NominaPaymentComponentLink>> linksByYear = projection.getNominaPaymentLinks();

                                            for (Map.Entry<Integer, List<NominaPaymentComponentLink>> entry : linksByYear.entrySet()) {
                                                String year = entry.getKey().toString();
                                                List<NominaPaymentComponentLink> links = entry.getValue();

                                                Map<String, List<NominaPaymentComponentLink>> yearCache = links.stream()
                                                        .filter(n -> n.getPaymentComponent().getBu().equals(projection.getIdBu()))
                                                        .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));

                                                nominaPaymentComponentLinksByYearCache.put(year, yearCache);
                                            }
                                        }
                                        // Usar todos los años en el rango para filtrar nominal
                                        Set<String> existingNominaCodes = nominaPaymentComponentLinksByYearCache.values().stream()
                                                .flatMap(yearMap -> yearMap.keySet().stream())
                                                .collect(Collectors.toSet());

                                        filteredNominal = nominal.parallelStream()
                                                .filter(h -> existingNominaCodes.contains(h.getCodeNomina()))
                                                .collect(Collectors.toList());
                                    }else {
                                        //log.debug("projectionsComponent {}",projectionsComponent);
                                        if (nominaPaymentComponentLinksCache == null) {
                                          *//*  List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll()
                                                    .stream()
                                                    .filter(n -> n.getPaymentComponent()
                                                            .getBu()
                                                            .equals(projection.getIdBu()))
                                                    .toList();
                                            nominaPaymentComponentLinksCache = allLinks.stream()
                                                    .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));*//*
                                            // Inicialización segura de la caché por BU
                                            ConcurrentMap<String, List<NominaPaymentComponentLink>> cacheByBu = nominaPaymentComponentLinksCache.computeIfAbsent(bu, k -> {
                                                List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll().stream()
                                                        .filter(n -> n.getPaymentComponent().getBu().equals(projection.getIdBu()))
                                                        .toList();
                                                return new ConcurrentHashMap<>(allLinks.stream()
                                                        .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina())));
                                            });

                                        }

                                        Set<String> existingNominaCodes = nominaPaymentComponentLinksCache.keySet();
                                        filteredNominal = nominal
                                                .parallelStream()
                                                //.filter(g -> g.getIdssff().equalsIgnoreCase(list.get(0).getIdssff()))
                                                .filter(h -> existingNominaCodes.contains(h.getCodeNomina()))
                                                .toList();
                                        //log.debug("filteredNominal: {}", filteredNominal);
                                    }*/
                                    //log.debug("filteredNominal: {}", filteredNominal);
                                    //addNominalV2(projection, projectionsComponent, filteredNominal, codeNominals, list);
                                    // Inicialización segura de la caché por BU
                                 /*   List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll()
                                            .stream()
                                            .filter(n -> n.getPaymentComponent()
                                                    .getBu()
                                                    .equals(projection.getIdBu()))
                                            .toList();
                                    nominaPaymentComponentLinksCache = allLinks.stream()
                                            .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));*/

                                    ConcurrentMap<String, List<NominaPaymentComponentLink>> cacheByBu = nominaPaymentComponentLinksByBuCache.computeIfAbsent(bu, k -> {
                                        List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll().stream()
                                                .filter(n -> n.getPaymentComponent().getBu().equals(projection.getIdBu()))
                                                .toList();
                                        return new ConcurrentHashMap<>(allLinks.stream()
                                                .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina())));
                                    });
                                    Set<String> existingNominaCodes = cacheByBu.keySet();
                                    filteredNominal = nominal.parallelStream()
                                            .filter(h -> existingNominaCodes.contains(h.getCodeNomina()))
                                            .toList();
                                    addNominalV2(projection, projectionsComponent, filteredNominal, codeNominals, list);
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
                                            list.get(0).getLevel(),
                                            list.get(0).getCategoryLocal(),
                                            list.get(0).getEstadoVacante()
                                    );
                                }
                        )
                ))
                .values());

    }
    private Map<NominaRangeType, Pair<LocalDate, LocalDate>> intersectRanges(LocalDate projectionFrom, LocalDate projectionTo, List<CodeNomina> codeNominals) {
        Map<NominaRangeType, Pair<LocalDate, LocalDate>> intersectedRanges = new EnumMap<>(NominaRangeType.class);

        for (NominaRangeType rangeType : NominaRangeType.values()) {
            if (codeNominals.stream().anyMatch(cn -> cn.getRangeType() == rangeType)) {
                LocalDate adjustedFrom = adjustDateRange(projectionTo, rangeType);
                LocalDate intersectedFrom = adjustedFrom.isBefore(projectionFrom) ? projectionFrom : adjustedFrom;
                intersectedRanges.put(rangeType, Pair.of(intersectedFrom, projectionTo));
            }
        }

        return intersectedRanges;
    }

    private LocalDate adjustDateRange(LocalDate nominaTo, NominaRangeType rangeType) {
        return switch (rangeType) {
            case ONE_MONTH -> nominaTo.withDayOfMonth(1); // Primer día del mes actual
            case SIX_MONTHS -> nominaTo.minusMonths(5).withDayOfMonth(1); // Primer día de 6 meses atrás
            case TWELVE_MONTHS -> nominaTo.minusMonths(11).withDayOfMonth(1); // Primer día de 12 meses atrás
            case ALL -> nominaTo.minusYears(100); // Un valor muy anterior para incluir todo
        };
    }
    private String findMostSimilarPosition(String targetPosition, Set<String> knownPositions) {
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

    private static final List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    public static LocalDate convertStringToLocalDate(String dateString) {
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateString, formatter);
            } catch (DateTimeParseException e) {
                // Intenta con el siguiente formato
            }
        }
        throw new IllegalArgumentException("El valor proporcionado no es una fecha válida: " + dateString);
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

    private ParametersDTO findLatestSalaryForPeriod(List<ParametersDTO> salaryList, String period) {
        for (int i = salaryList.size() - 1; i >= 0; i--) {
            if (salaryList.get(i).getPeriod().compareTo(period) <= 0) {
                return salaryList.get(i);
            }
        }
        return null;
    }
    private double totalHorasExtrasPorBU = 0.0;
    private  double totalComisionesPorBU = 0.0;
    private double totalIncentivosPorBU = 0.0;
    private void addNominalV2(ParametersByProjection projection, List<PaymentComponentDTO> projectionsComponent,
                            List<NominaProjection> nominal, List<CodeNomina> codeNominas,
                            List<HeadcountProjection> list) {
        String bu = projection.getBu().toUpperCase();
        String idssff = list.get(0).getIdssff();
        //String idssff = "2015717";
        //log.info("Bu: {}", bu);
        //log.info("IDSSFF from list: {}", idssff);
        //log.info("Posiciones en list: {}", list.stream().map(HeadcountProjection::getIdssff).collect(Collectors.toList()));
        //log.info("Posiciones en nominal: {}", nominal.stream().map(NominaProjection::getIdssff).distinct().collect(Collectors.toList()));
        switch (bu) {
            case "T. ECUADOR" -> processEcuador(nominal, projectionsComponent, projection, idssff);
            case "T. COLOMBIA", "T. MEXICO", "T. PERU", "T. URUGUAY", "T. ARGENTINA" -> processOtherCountriesNominal(nominal, projectionsComponent, projection, bu, idssff);
        }
        /*if (bu.equals("T. ECUADOR")) {
            processEcuador(nominal, projectionsComponent, projection, idssff);
            //log.info("projectionsComponent {}",projectionsComponent);
        } else if (bu.equals("T. COLOMBIA") || bu.equals("T. MEXICO") || bu.equals("T. PERU") || bu.equals("T. URUGUAY") || bu.equals("T. ARGENTINA")) {
            *//*if (bu.equals("T. PERU")) {
                processPeruNominal(nominal, projectionsComponent, projection);
            } else {
                processOtherCountriesNominal(nominal, projectionsComponent, projection, bu, idssff);
            }*//*
            //log.info("nominal {}",nominal);
            //log.info("codeNominas {}",codeNominas);
            processOtherCountriesNominal(nominal, projectionsComponent, projection, bu, idssff);
        }*/
    }

    private void processEcuador(List<NominaProjection> nominal, List<PaymentComponentDTO> projectionsComponent,
                                ParametersByProjection projection, String idssff) {
        List<NominaProjection> nominalBySSFF = nominal.stream()
                .filter(n -> n.getIdssff().equals(idssff))
                .collect(Collectors.toList());

        double hhee = nominalBySSFF.stream()
                .filter(h -> !"0260".equalsIgnoreCase(h.getCodeNomina()))
                .mapToDouble(h -> h.getImporte() != null ? h.getImporte() : 0.0)
                .sum();
        //log.info("hhee: {}", hhee);
        double guarderia = nominalBySSFF.stream()
                .filter(h -> "0260".equalsIgnoreCase(h.getCodeNomina()))
                .mapToDouble(h -> h.getImporte() != null ? h.getImporte() : 0.0)
                .sum();
        //log.info("guarderia: {}", guarderia);
        projectionsComponent.add(createPaymentComponentDTO("TURN", 7, hhee, projection));
        projectionsComponent.add(createPaymentComponentDTO("260", 12, guarderia, projection));
    }

    private void processPeruNominal(List<NominaProjection> nominal,
                                    List<PaymentComponentDTO> projectionsComponent,
                                    ParametersByProjection projection) {
        String bu = "T. PERU";
        String idssff = nominal.isEmpty() ? "" : nominal.get(0).getIdssff();

        List<NominaProjection> nominalBySSFF = nominal.stream()
                .filter(n -> n.getIdssff().equals(idssff))
                .collect(Collectors.toList());

        if (!nominalBySSFF.isEmpty()) {
            Map<String, Double> componentTotals = new ConcurrentHashMap<>();

            int startYear = Integer.parseInt(projection.getNominaFrom().substring(0, 4));
            int endYear = Integer.parseInt(projection.getNominaTo().substring(0, 4));

            for (int year = startYear; year <= endYear; year++) {
                String yearKey = String.valueOf(year);
                Map<String, List<NominaPaymentComponentLink>> linksForYear = nominaPaymentComponentLinksByYearCache.get(yearKey);

                if (linksForYear != null) {
                    nominalBySSFF.parallelStream().forEach(h -> {
                        List<NominaPaymentComponentLink> links = linksForYear.get(h.getCodeNomina());
                        if (links != null) {
                            processLinks(links, h, componentTotals, bu);
                        }
                    });
                }
            }

            processComponentTotals(componentTotals, projectionsComponent, projection, bu);
        } else {
            addEmptyPaymentComponents(projectionsComponent, bu, projection);
        }
    }

    private void processOtherCountriesNominal(List<NominaProjection> nominal, List<PaymentComponentDTO> projectionsComponent, ParametersByProjection projection, String bu, String idssff) {
        List<NominaProjection> nominalBySSFF = nominal
                .stream()
                .filter(n -> n.getIdssff().equals(idssff))
                .collect(Collectors.toList());

        if (!nominalBySSFF.isEmpty()) {
            Map<String, Double> componentTotals = new ConcurrentHashMap<>();

            ConcurrentMap<String, List<NominaPaymentComponentLink>> cacheByBu = nominaPaymentComponentLinksByBuCache.get(bu);
            if (cacheByBu == null) {
                log.warn("La caché para la BU {} no está inicializada.", bu);
                return;
            }

            nominalBySSFF.forEach(h -> {
                String codeNominaUpper = h.getCodeNomina().toUpperCase();
                //List<NominaPaymentComponentLink> links = nominaPaymentComponentLinksByBuCache.get(bu).get(h.getCodeNomina());
                // Obtener la caché específica para la BU actual
                List<NominaPaymentComponentLink> links = cacheByBu.get(codeNominaUpper);

                if (links != null) {
                    processLinks(links, h, componentTotals, bu);
                }
            });

            //log.info("Componentes totalizados: {}", componentTotals);

            processComponentTotals(componentTotals, projectionsComponent, projection, bu);
        } else {
            //log.warn("No se encontraron registros para IDSSFF: {}", idssff);
            addEmptyPaymentComponents(projectionsComponent, bu, projection);
        }
    }


    private void processLinks(List<NominaPaymentComponentLink> links, NominaProjection nomina,
                              Map<String, Double> componentTotals, String bu) {
        links.forEach(link -> {
            String component = link.getPaymentComponent().getPaymentComponent();
            double importe = nomina.getImporte() != null ? nomina.getImporte() : 0.0;
           /* if (bu.equals("T. URUGUAY") && ("0010".equalsIgnoreCase(component) || "0020".equalsIgnoreCase(component))){
                log.info("Procesando link: componente={}, importe={}, getQDiasHoras={}", component, importe, nomina.getQDiasHoras());
            }*/
            if (bu.equals("T. URUGUAY") && ("0010".equalsIgnoreCase(component) || "0020".equalsIgnoreCase(component)) && nomina.getQDiasHoras() != null) {
                importe = nomina.getQDiasHoras() == 0 ? 0 : ( importe / nomina.getQDiasHoras() ) * 30;
                ///log.info("Procesando link: componente={}, importe={}, getQDiasHoras={}", component, importe, nomina.getQDiasHoras());
            }
            componentTotals.merge(component, importe, Double::sum);
        });
        //log.info("ComponentTotals después de procesar links: {}", componentTotals);
    }


    private void processComponentTotals(Map<String, Double> componentTotals,
                                        List<PaymentComponentDTO> projectionsComponent,
                                        ParametersByProjection projection, String bu) {
        componentTotals.forEach((component, total) -> {
            //log.info("Procesando total para componente: {}, total: {}", component, total);
            if (total > 0) {
                PaymentComponentDTO paymentComponent = buildPaymentComponentDTO(bu, component, total, projection.getPeriod(), projection.getRange());
                projectionsComponent.add(paymentComponent);
                //log.info("Añadido componente: {}", paymentComponent);
            } else {
                //log.info("Componente con total cero: {}", component);
                projectionsComponent.add(buildPaymentComponentDTO(bu, component, 0, projection.getPeriod(), projection.getRange()));
            }
        });
    }

    private void updatePeruTotals(String component, double total) {
        switch (component) {
            case "OVERTIME_BASE":
                totalHorasExtrasPorBU += total;
                break;
            case "COMMISSIONS_BASE":
                totalComisionesPorBU += total;
                break;
            case "INCENTIVES_BASE":
                totalIncentivosPorBU += total;
                break;
        }
    }

    private PaymentComponentDTO createPaymentComponentDTO(String paymentComponent, int type, double amount, ParametersByProjection projection) {
        //log.info("Creando PaymentComponentDTO para componente: {}, tipo: {}, monto: {}", paymentComponent, type, amount);
        return PaymentComponentDTO.builder()
                .type(type)
                .paymentComponent(paymentComponent)
                .amount(BigDecimal.valueOf(amount))
                .projections(getProjectionMethodByBu(projection.getBu(), projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(amount)))
                .build();
    }

    private void addEmptyPaymentComponents(List<PaymentComponentDTO> projectionsComponent, String bu, ParametersByProjection projection) {
        String[] components = getEmptyComponentsForBu(bu);
        for (String component : components) {
            projectionsComponent.add(PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent(component)
                    .amount(BigDecimal.ZERO)
                    .projections(getProjectionMethodByBu(bu, projection.getPeriod(), projection.getRange(), BigDecimal.ZERO))
                    .show(true)
                    .build());
        }
    }

    private String[] getEmptyComponentsForBu(String bu) {
        switch (bu) {
            case "T. COLOMBIA":
                return new String[]{"AUXILIO_TRASLADO", "HHEE_BASE", "SURCHARGES_BASE", "AUXILIO_VIVIENDA", "AUXILIO_RODAMIENTO"};
            case "T. MEXICO":
                return new String[]{"DISPONIBILIDAD_BASE", "COMPENSACION_BASE", "GRATIFICACION_BASE", "GRATIFICACION_EXTRAORDINARIA_BASE",
                        "TRABAJO_EXTENSO_BASE", "TRABAJO_GRAVABLE_BASE", "PARTE_EXENTA_FESTIVO_LABORADO_BASE", "PARTE_GRAVABLE_FESTIVO_LABORADO_BASE",
                        "PRIMA_DOMINICAL_GRAVABLE_BASE", "MUDANZA_BASE", "VIDA_CARA_BASE", "PRIMA_DOMINICAL_EXENTA_BASE"};
            case "T. URUGUAY":
                return new String[]{"0010", "0020", "HHEE_BASE_UR", "GUARDIA_BASE_UR", "PREMIO_MENSUAL_20_BASE_UR", "PREMIO_MENSUAL_15_BASE_UR",
                        "PREMIO_CUATRIMESTRAL_8_BASE_UR", "PREMIO_CUATRIMESTRAL_BASE_UR", "BONO_ANUAL", "BONO_VENTAS", "COMISIONES_VENTAS",
                        "COMISIONES_COBRANZAS", "TICKET_ALIMENTACION", "SUAT_BASE", "BC_BS_BASE", "VIATICO_AUTO"};
            default:
                return new String[0];
        }
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
    private PaymentComponentDTO buildPaymentComponentDTO(String bu,String component, double amount, String period, Integer range) {
        return PaymentComponentDTO.builder()
                .type(16)
                .paymentComponent(component)
                .amount(BigDecimal.valueOf(amount))
                .projections(getProjectionMethodByBu(bu, period, range, BigDecimal.valueOf(amount)))
                .build();
    }
    public List<MonthProjection> getProjectionMethodByBu(String bu, String period, Integer range, BigDecimal amount) {
        /*return bu.equals("T. PERU") ? Shared.generateMonthProjectionV2(period, range, amount) : Shared.generateMonthProjectionV3(period, range, amount);*/
        return generateMonthProjectionV3(period, range, amount);
    }

}
