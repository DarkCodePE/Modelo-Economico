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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j(topic = "PROJECTION_SERVICE")
public class ProjectionServiceImpl implements ProjectionService {

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
    private BuService buService;
    @Autowired
    private NominaPaymentComponentLinkRepository nominaPaymentComponentLinkRepository;
    @Autowired
    private ReportGenerationService reportGenerationService;
    @Autowired
    private Executor executor;
    @Autowired
    private XlsReportService xlsReportService;
    private Map<String, List<NominaPaymentComponentLink>> nominaPaymentComponentLinksCache;
    private final MexicoService mexicoService;

    private List<Operation> operations;
    private Peru methodsPeru;
    @Autowired
    public ProjectionServiceImpl(MexicoService mexicoService) {
        this.mexicoService = mexicoService;
    }
    private static final String[] headers = {"po","idssff"};
    private static final String HEADERPO="POXXXXXX";

    @Cacheable("daysVacationCache")
    public List<DaysVacationOfTime> getAllDaysVacation() {
        return daysVacationOfTimeRepository.findAll();
    }

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
            //log.debug("headcount {}",headcount.size()) ;
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
            log.debug("headcount {}",headcount);
            List<ComponentAmount> groupedData = headcount.stream()
                    .flatMap(j -> {
                        log.debug("j.getComponents() {}",j.getComponents());
                        return  j.getComponents().stream();
                    })
                    .collect(Collectors.groupingBy(
                            PaymentComponentDTO::getPaymentComponent,
                            Collectors.summingDouble(p -> p.getAmount().doubleValue())
                    )).entrySet()
                    .stream()
                    .map(entry -> new ComponentAmount(entry.getKey(), BigDecimal.valueOf(entry.getValue())))
                    .collect(Collectors.toList());
            log.debug("groupedData {}",groupedData);
            //ocultando los payment que no son mostrados
            Map<String, ComponentProjection> mapaComponentesValidos = components.stream().filter(ComponentProjection::getShow)
                    .collect(Collectors.toMap(ComponentProjection::getComponent, componente -> componente));
            log.debug("mapaComponentesValidos {}",mapaComponentesValidos);
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
                                proyeccion.getCCostos()
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
        Mexico methodsMexico = new Mexico(mexicoService);
        //Genera las proyecciones del rango
        List<ParametersDTO> salaryList = filterParametersByName(projection.getParameters(), "Salario Mínimo Mexico");
        List<ParametersDTO> incrementList = filterParametersByName(projection.getParameters(), "Increm Salario Mín");
        List<ParametersDTO> revisionList = filterParametersByName(projection.getParameters(), "Revision Salarial");
        List<ParametersDTO>  employeeParticipationList = filterParametersByName(projection.getParameters(), "Participación de los trabajadores");
        List<ParametersDTO> sgmmList = filterParametersByName(projection.getParameters(), "SGMM");
        List<ParametersDTO> dentalInsuranceList = filterParametersByName(projection.getParameters(), "Seguro Dental");
        List<ParametersDTO> lifeInsuranceList = filterParametersByName(projection.getParameters(), "Seguro de Vida");
        //Calcular la suma total de todos los salarios de la plantilla
        headcount.stream()
                //.parallel()
                .forEach(headcountData -> {
                    log.info("getPo {}  -  isCp {}",headcountData.getPo(), headcountData.getPoName().contains("CP"));
                    //log.debug("getPo {} - Salary {}",headcountData.getPo(), headcountData.getComponents().stream().filter(c->c.getPaymentComponent().equals("PC938003") || c.getPaymentComponent().equals("PC938012")).mapToDouble(c->c.getAmount().doubleValue()).max().getAsDouble());
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.salary(component, salaryList, incrementList, revisionList, projection.getPeriod(), projection.getRange(), headcountData.getPoName());
                    methodsMexico.provAguinaldo(component, projection.getPeriod(), projection.getRange());
                    methodsMexico.provVacacionesRefactor(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),  headcountData.getFContra(), headcountData.getFNac(), rangeBuByBU, idBu);
                    //methodsMexico.valesDeDespensa(component, projection.getParameters(), projection.getPeriod(), projection.getRange());
                    if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                        addBaseExtern(headcountData,projection.getBaseExtern(),
                                projection.getPeriod(),projection.getRange());
                    }
                });
                double totalSalarios = calcularTotalSalarios(headcount);
        headcount.stream()
                //.parallel()
                        .forEach(headcountData -> {
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.participacionTrabajadores(component, employeeParticipationList, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.seguroDental(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.seguroVida(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.provisionSistemasComplementariosIAS(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), totalSalarios);
                    methodsMexico.SGMM(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), headcountData.getPoName(), totalSalarios);
                });
    }
    public double calcularTotalSalarios(List<ProjectionDTO> headcount) {
        return headcount.stream()
                .flatMap(h -> h.getComponents().stream())
                .filter(c -> c.getPaymentComponent().equals("SALARY"))
                .map(PaymentComponentDTO::getProjections)
                .map(projections -> projections.get(projections.size() - 1))
                .mapToDouble(p -> p.getAmount().doubleValue())
                .sum();
    }
    private void isColombia( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        Colombia methodsColombia = new Colombia();
        //SUMATORIA LOS COMPONENTES  PC938003 / PC938012
        double sum = headcount.parallelStream()
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
        //log.debug("commissionList {}",commissionList);
        //log.debug("revisionEttList {}",revisionEttList);
        //log.debug("revisionList {}",revisionList);
        //log.debug("salaryList {}",salaryList);
        //Genera las proyecciones del rango
        headcount.stream()
                .parallel()
                .forEach(headcountData -> {
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsColombia.salary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), salaryList, revisionList, revisionEttList, salaryIntegralsList);
                    methodsColombia.temporalSalary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), salaryList, revisionList, revisionEttList, salaryIntegralsList);
                    methodsColombia.salaryPra(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),salaryList, salaryPraList);
                    methodsColombia.revisionSalary(component, projection.getParameters(), projection.getPeriod(), projection.getRange(), headcountData.getClassEmployee());
                    methodsColombia.commission(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), totalSum, commissionList);
                    methodsColombia.prodMonthPrime(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedVacation(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeverance(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceInterest(component, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.contributionBox(component, headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    methodsColombia.transportSubsidy(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.companyHealthContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContribution(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContributionTrainee(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContributionTemporaries(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.icbfContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.senaContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.companyPensionContribution(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.sodexo(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange(), sodexoList);
                    methodsColombia.sena(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.senaTemporales(component, projection.getParameters(),headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
                    methodsColombia.uniqueBonus(component, headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.AuxilioDeTransporteAprendizSena(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.AuxilioConectividadDigital(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange(), headcountData.getPoName());
                    methodsColombia.commissionTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),totalSum, commissionList);
                    methodsColombia.prodMonthPrimeTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedVacationTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.consolidatedSeveranceInterestTemporal(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.transportSubsidyTemporaries(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.companyHealthContributionTemporals(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.companyRiskContributionTemporals(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.contributionBoxTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.icbfContributionTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.senaContributionTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.companyPensionContributionTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.feeTemporaries(component, headcountData.getClassEmployee(), projection.getParameters(), projection.getPeriod(), projection.getRange());
                    if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                        addBaseExtern(headcountData,projection.getBaseExtern(),
                                projection.getPeriod(),projection.getRange());
                    }
                });
    }

    private void isUruguay( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        Uruguay methodsUruguay = new Uruguay();
        //Genera las proyecciones del rango
        for (ProjectionDTO projectionDTO : headcount) {
            //parametros que no tienen periodo que se ejecutan siempre
            if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){addBaseExtern(projectionDTO,projection.getBaseExtern(),
                    projection.getPeriod(),projection.getRange());}
            methodsUruguay.addParameter(projectionDTO, projection);
        }
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
        log.info("vbu {}",vbu);
        return Config.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vTemporal(buService.getAllBuWithRangos(vbu.getId()))
                .vDefault(parameterDefaultRepository.findByBu(vbu.getId()))
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .baseExtern(baseExternRepository.findByBu(vbu.getId()).stream().map(c->OperationResponse
                        .builder().code(c.getCode()).name(c.getName())
                        .build()).collect(Collectors.toList()))
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
            bc.getHeaders().add(HEADERPO);
            List<Map<String,Object>> data = new ArrayList<>();
            businessCaseHistorials.forEach(t-> data.stream().filter(e->e.get(HEADERPO).equals(t.getPo())).findFirst().ifPresentOrElse(r->
                            r.put(t.getComponent(),Double.parseDouble(Shared.desencriptar(t.getNvalue())))
                    ,()->{
                        Map<String, Object> map = new HashMap<>();
                        map.put(HEADERPO,t.getPo());
                        map.put(t.getComponent(),Double.parseDouble(Shared.desencriptar(t.getNvalue())));
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
    public void downloadProjection(ParameterDownload projection, String userContact, ReportJob job) {
            try {
                projection.setPeriod(projection.getPeriod().replace("/", ""));
                projection.setNominaFrom(projection.getNominaFrom().replace("/", ""));
                projection.setNominaTo(projection.getNominaTo().replace("/", ""));

                List<ComponentProjection> componentProjections = sharedRepo.getComponentByBu(projection.getBu());
                //TODO: ADD BASE EXTERN

                DataRequest dataBase = DataRequest.builder()
                        .idBu(projection.getIdBu())
                        .idBu(projection.getIdBu())
                        .bu(projection.getBu())
                        .period(projection.getPeriod())
                        .nominaFrom(projection.getNominaFrom())
                        .nominaTo(projection.getNominaTo())
                        .isComparing(false)
                        .build();
                  xlsReportService.generateAndCompleteReportAsync(projection, componentProjections, getDataBase(dataBase), userContact, job, userContact);
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
                        Collectors.mapping(deuda -> new ComponentAmount(deuda.getComponent(),deuda.getAmount()!=null? BigDecimal.valueOf(deuda.getAmount()):BigDecimal.ZERO), Collectors.toList())
                )).entrySet().stream()
                .map(entry -> {
                    HeadcountHistoricalProjection info = headcount.stream().filter(i->i.getPosition().equalsIgnoreCase(entry.getKey())).findFirst().get();
                    return  new DataBaseResponse(entry.getKey(),info.getIdssff(),info.getPoname(),info.getClassemp(),entry.getValue());
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

    @Override
    public byte[] downloadFileType(List<ProjectionDTO> projection,Integer type,Integer idBu) {
        Bu bu = buRepository.findById(idBu).orElseThrow(()-> new BadRequestException("No se encuentra la Bu"));
        return ExcelService.generateExcelType(projection,type,bu,sharedRepo.getAccount(idBu));
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
                        .build()).collect(Collectors.toList());
        //log.debug("headcount {}",headcount);
        List<CodeNomina> codeNominals = codeNominaRepository.findByIdBu(projection.getIdBu());
        List<NominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,projection.getBu(),projection.getNominaFrom(),projection.getNominaTo(),
                codeNominals.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")))
                .stream().map(e->NominaProjection.builder()
                        .idssff(e.getID_SSFF())
                        .codeNomina(e.getCodigoNomina())
                        .importe(e.getImporte())
                            .build()).collect(Collectors.toList());
        //log.debug("!projection.getBc() {}", projection.getBc());
        if(!projection.getBc().getData().isEmpty()){
            for (int i = 0; i < projection.getBc().getData().size() ; i++) {
                Map<String,Object> resp = projection.getBc().getData().get(i);
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
                      headcount.add(HeadcountProjection.builder()
                                      .position(resp.get(HEADERPO).toString())
                                      .idssff(String.valueOf(finalI))
                                      .poname("")
                                      .classEmp(resp.get("typeEmployee").toString())
                                      .component(t.getComponent())
                                      .amount(Double.parseDouble(resp.get(t.getName()).toString()))
                              .build()),()->
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
            }

        }

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
                                                        list.stream().filter(t-> t.getComponent()!=null && t.getComponent().equalsIgnoreCase(p.getComponent())).findFirst().ifPresent(u->{

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
                                    if (nominaPaymentComponentLinksCache == null) {
                                        List<NominaPaymentComponentLink> allLinks = nominaPaymentComponentLinkRepository.findAll();
                                        nominaPaymentComponentLinksCache = allLinks.stream()
                                                .collect(Collectors.groupingBy(n -> n.getNominaConcept().getCodeNomina()));
                                    }
                                    //log.debug("nominaPaymentComponentLinksCache: {}", nominaPaymentComponentLinksCache);
                                    Set<String> existingNominaCodes = nominaPaymentComponentLinksCache.keySet();
                                    List<NominaProjection> filteredNominal = nominal.stream()
                                            .filter(g -> g.getIdssff().equalsIgnoreCase(list.get(0).getIdssff()))
                                            .filter(h -> existingNominaCodes.contains(h.getCodeNomina()))
                                            .collect(Collectors.toList());
                                    addNominal(projection,projectionsComponent,filteredNominal,codeNominals,list);
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
                                            list.get(0).getCCostos()

                                    );
                                }
                        )
                ))
                .values());

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
    private void addNominal(ParametersByProjection projection, List<PaymentComponentDTO> projectionsComponent,
                            List<NominaProjection> nominal , List<CodeNomina> codeNominas ,
                            List<HeadcountProjection> list ){
        if (projection.getBu().equalsIgnoreCase("T. ECUADOR"))
        {
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
        }else if(projection.getBu().equalsIgnoreCase("T. URUGUAY") || projection.getBu().equalsIgnoreCase("T. COLOMBIA")){
            double totalHHEE = 0.0;
            double totalRecargo = 0.0;
            double translation = 0.0;
            double housing = 0.0;
            double bearing = 0.0;
            //   for(NominaProjection h : nominal.stream().filter(g->g.getIdssff()
            //                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList()) ){
            for(NominaProjection h : nominal) {
                List<NominaPaymentComponentLink> nominaPaymentComponentLinks = nominaPaymentComponentLinksCache.get(h.getCodeNomina());
                if (nominaPaymentComponentLinks != null) {
                    totalHHEE += nominaPaymentComponentLinks.stream()
                            .filter(n -> n.getPaymentComponent().getPaymentComponent().equalsIgnoreCase("HHEE"))
                            .mapToDouble(n -> h.getImporte()).sum();
                    totalRecargo += nominaPaymentComponentLinks.stream()
                            .filter(n -> n.getPaymentComponent().getPaymentComponent().equalsIgnoreCase("SURCHARGES"))
                            .mapToDouble(n -> h.getImporte()).sum();
                    translation = nominaPaymentComponentLinks.stream()
                            .filter(n -> n.getPaymentComponent().getPaymentComponent().equalsIgnoreCase("AUXILIO_TRASLADO"))
                            .mapToDouble(n -> h.getImporte()).sum();
                    housing = nominaPaymentComponentLinks.stream()
                            .filter(n -> n.getPaymentComponent().getPaymentComponent().equalsIgnoreCase("AUXILIO_VIVIENDA"))
                            .mapToDouble(n -> h.getImporte()).sum();
                    bearing = nominaPaymentComponentLinks.stream()
                            .filter(n -> n.getPaymentComponent().getPaymentComponent().equalsIgnoreCase("AUXILIO_RODAMIENTO"))
                            .mapToDouble(n -> h.getImporte()).sum();
                }
            }
            PaymentComponentDTO transferAssistanceComponent = PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent("AUXILIO_TRASLADO")
                    .amount(BigDecimal.valueOf(translation))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(translation)))
                    .build();
            PaymentComponentDTO hheeComponent = PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent("HHEE")
                    .amount(BigDecimal.valueOf(totalHHEE))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(totalHHEE)))
                    .build();
            PaymentComponentDTO recargoComponent = PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent("SURCHARGES")
                    .amount(BigDecimal.valueOf(totalRecargo))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(totalRecargo)))
                    .build();
            PaymentComponentDTO housingComponent = PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent("AUXILIO_VIVIENDA")
                    .amount(BigDecimal.valueOf(housing))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(housing)))
                    .build();
            PaymentComponentDTO bearingComponent = PaymentComponentDTO.builder()
                    .type(16)
                    .paymentComponent("AUXILIO_RODAMIENTO")
                    .amount(BigDecimal.valueOf(bearing))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(bearing)))
                    .build();
            projectionsComponent.add(hheeComponent);
            projectionsComponent.add(recargoComponent);
            projectionsComponent.add(transferAssistanceComponent);
            projectionsComponent.add(housingComponent);
            projectionsComponent.add(bearingComponent);

        }else if (projection.getBu().equalsIgnoreCase("T. PERU")){
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
}
