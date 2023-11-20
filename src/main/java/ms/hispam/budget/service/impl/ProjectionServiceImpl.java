package ms.hispam.budget.service.impl;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.*;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.entity.mysql.ParameterProjection;
import ms.hispam.budget.exception.BadRequestException;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.repository.sqlserver.ParametersRepository;
import ms.hispam.budget.rules.*;
import ms.hispam.budget.rules.operations.Operation;
import ms.hispam.budget.rules.operations.salary.*;
import ms.hispam.budget.service.BuService;
import ms.hispam.budget.service.MexicoService;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.util.Constant;
import ms.hispam.budget.util.ExcelService;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    }

    @Override
    public Page<ProjectionDTO> getProjection(ParametersByProjection projection) {
        try {
            List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection);
            //log.info("headcount {}",headcount);
            //List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection).stream().limit(10).collect(Collectors.toList());
          /*  List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection).stream().filter(projectionDTO -> projectionDTO.getPo().equals("PO10007788")).collect(Collectors.toList());*/

            //.info("headcount {}",headcount);

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

            List<ComponentAmount> groupedData = headcount.stream()
                    .flatMap(j -> j.getComponents().stream())
                    .collect(Collectors.groupingBy(
                            PaymentComponentDTO::getPaymentComponent,
                            Collectors.summingDouble(p -> p.getAmount().doubleValue())
                    )).entrySet()
                    .stream()
                    .map(entry -> new ComponentAmount(entry.getKey(), BigDecimal.valueOf(entry.getValue())))
                    .collect(Collectors.toList());


            Page<ProjectionDTO> page = new Page<>(0,0, headcount.size(),
                    headcount,groupedData);
            return  page;
        }catch (Exception ex){
            log.error("Error al generar la proyección",ex);
            ex.printStackTrace();
            return new Page<>();
        }
    }
    private void isPeru(List<ProjectionDTO> headcount, ParametersByProjection projection) {
        BaseExternResponse baseExtern = projection.getBaseExtern();
        boolean hasBaseExtern = baseExtern != null && !baseExtern.getData().isEmpty();
        headcount
                .parallelStream()
                .forEach(headcountData -> {
            if (hasBaseExtern) {
                addBaseExternV2(headcountData, baseExtern, projection.getPeriod(), projection.getRange());
            }
            //log.info("headcountData {}",headcountData.getPo());
            methodsPeru.salary(headcountData.getComponents(), projection.getParameters(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
            //methodsPeru.revisionSalary(headcountData.getComponents(), projection.getParameters(), headcountData.getClassEmployee(), projection.getPeriod(), projection.getRange());
        });
    }
    private void isMexico(List<ProjectionDTO>  headcount, ParametersByProjection projection){
        Mexico methodsMexico = new Mexico(mexicoService);
        //Genera las proyecciones del rango
        headcount.stream()
                .parallel()
                .forEach(headcountData -> {
                    //log.info("headcountData {}",headcountData.getPo());
                    //log.info("headcountData {}",  i.getAndIncrement());
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsMexico.salary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsMexico.revisionSalary(component, projection.getParameters());
                    methodsMexico.provAguinaldo(component, projection.getPeriod(), projection.getRange());
                    methodsMexico.provVacacionesRefactor(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(),  headcountData.getFContra(), headcountData.getFNac(), projection.getTemporalParameters(), projection.getIdBu());

                    if(projection.getBaseExtern()!=null &&!projection.getBaseExtern().getData().isEmpty()){
                        addBaseExtern(headcountData,projection.getBaseExtern(),
                                projection.getPeriod(),projection.getRange());
                    }
                });
    }
    private void isColombia( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        Colombia methodsColombia = new Colombia();
        //SUMATORIA LOS COMPONENTES  PC938003 / PC938012
        AtomicReference<BigDecimal> sum = new AtomicReference<>(BigDecimal.ZERO);
        headcount.stream()
                .parallel()
                .forEach(headcountData -> {
                    //Max
                    double totalPO = headcountData.getComponents().stream()
                            .filter(c-> Objects.equals(c.getPaymentComponent(), "PC938012") || Objects.equals(c.getPaymentComponent(), "PC938003"))
                            .mapToDouble(c->c.getAmount().doubleValue()).max().getAsDouble();
                    sum.updateAndGet(v -> v.add(totalPO>0?BigDecimal.valueOf(totalPO):BigDecimal.ZERO));
                });

        //Genera las proyecciones del rango
        headcount.stream()
                .parallel()
                .forEach(headcountData -> {
                    List<PaymentComponentDTO> component = headcountData.getComponents();
                    methodsColombia.salary(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
                    methodsColombia.revisionSalary(component, projection.getParameters(), projection.getPeriod(), projection.getRange());
                    methodsColombia.commission(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange(), sum.get());
                    methodsColombia.prodMonthPrime(component, projection.getParameters(), headcountData.getClassEmployee(),  projection.getPeriod(), projection.getRange());
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
        headcount.getComponents().addAll(bases);

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
        Bu vbu = buRepository.findByBu(bu).orElse(null);
        if(vbu!=null){
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
                    .build();
        }
        return null;

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

    @Override
    public byte[] downloadProjection(  ParameterDownload projection) {
        projection.setPeriod(projection.getPeriod().replace("/",""));
        projection.setNominaFrom(projection.getNominaFrom().replace("/",""));
        projection.setNominaTo(projection.getNominaTo().replace("/",""));

        List<ComponentProjection>  componentProjections=  sharedRepo.getComponentByBu(projection.getBu());
        DataRequest dataBase = DataRequest.builder()
                .idBu(projection.getIdBu())
                .bu(projection.getBu())
                .period(projection.getPeriod())
                .nominaFrom(projection.getNominaFrom())
                .nominaTo(projection.getNominaTo())
                .isComparing(false)
                .build();

       return  ExcelService.generateExcelProjection(projection,componentProjections,getDataBase(dataBase));
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


        List<DataBaseResponse> deudasAgrupadas = headcount.stream()
                .collect(Collectors.groupingBy(
                        HeadcountHistoricalProjection::getPosition,
                        Collectors.mapping(deuda -> new ComponentAmount(deuda.getComponent(), BigDecimal.valueOf(deuda.getAmount())), Collectors.toList())
                )).entrySet().stream()
                .map(entry -> {
                    HeadcountHistoricalProjection info = headcount.stream().filter(i->i.getPosition().equalsIgnoreCase(entry.getKey())).findFirst().get();
                    return  new DataBaseResponse(entry.getKey(),info.getIdssff(),info.getPoname(),entry.getValue());
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
 return  DataBaseMainReponse.builder().data(deudasAgrupadas).components(components).nominas(codeNominas).comparing(comparing).build();
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

        List<HeadcountProjection> headcount=  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                        String.join(",", entities),projection.getPeriod(),String.join(",",
                        projection.getPaymentComponent().stream().map(PaymentComponentType::getComponent).collect(Collectors.joining(","))),String.join(",", typeEmployee))
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
                            .build()).collect(Collectors.toList());
        //log.info("headcount {}",headcount);
        List<CodeNomina> codeNominals = codeNominaRepository.findByIdBu(projection.getIdBu());
        List<NominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,projection.getBu(),projection.getNominaFrom(),projection.getNominaTo(),
                codeNominals.stream().map(CodeNomina::getCodeNomina).collect(Collectors.joining(",")))
                .stream().map(e->NominaProjection.builder()
                        .idssff(e.getID_SSFF())
                        .codeNomina(e.getCodigoNomina())
                        .importe(e.getImporte())
                            .build()).collect(Collectors.toList());
        if(!projection.getBc().getData().isEmpty()){
            for (int i = 0; i < projection.getBc().getData().size() ; i++) {
                Map<String,Object> resp = projection.getBc().getData().get(i);
                for (Map.Entry<String, Object> entry : resp.entrySet()) {
                    int finalI = i;
                    projection.getPaymentComponent().stream().filter(t-> !entry.getKey().equals(HEADERPO) &&
                          t.getName().equalsIgnoreCase(entry.getKey())).findFirst().ifPresentOrElse(t->
                      headcount.add(HeadcountProjection.builder()
                                      .position(resp.get(HEADERPO).toString())
                                      .idssff(String.valueOf(finalI))
                                      .poname("")
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

        return new ArrayList<>(headcount.stream()
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
                                                        list.stream().filter(t->t.getComponent().equalsIgnoreCase(p.getComponent())).findFirst().ifPresent(u->{
                                                                    r.setPaymentComponent(p.getComponent());
                                                                    r.setType(p.getType());
                                                                    r.setAmount(BigDecimal.valueOf(u.getAmount()));
                                                                    r.setProjections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), BigDecimal.valueOf(u.getAmount())));
                                                                });
                                                      return r;
                                                    }

                                            ).collect(Collectors.toList());
                                    addNominal(projection,projectionsComponent,nominal,codeNominals,list);
                                    return new ProjectionDTO(
                                            list.get(0).getIdssff(),
                                            list.get(0).getPosition(),
                                            list.get(0).getPoname(),
                                            projectionsComponent,
                                            list.get(0).getClassEmp(),
                                            list.get(0).getFNac(),
                                            list.get(0).getFContra()
                                    );
                                }
                        )
                )).values());
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
                    hhee+=h.getImporte();
                }else if(h.getCodeNomina().equalsIgnoreCase("0260")){
                    guarderia=h.getImporte();
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
            double hhee = 0.0;
            for(NominaProjection h : nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList()) ){
                if(codeNominas.stream().anyMatch(p->p.getCodeNomina().equalsIgnoreCase(h.getCodeNomina()))) {
                    hhee = hhee +h.getImporte();
                }
            }
            projectionsComponent.add(PaymentComponentDTO.builder().
                    type(16).
                    paymentComponent("HHEE").amount(BigDecimal.valueOf(hhee))
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),BigDecimal.valueOf(hhee))).build());
        }
    }
}
