package ms.hispam.budget.service.impl;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.*;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.entity.mysql.ParameterProjection;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.repository.sqlserver.ParametersRepository;
import ms.hispam.budget.rules.Ecuador;
import ms.hispam.budget.rules.Uruguay;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.util.Constant;
import ms.hispam.budget.util.ExcelService;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.stream.Collectors;


@Service
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

    private static final String[] headers = {"po","idssff"};
    private static final String HEADERPO="POXXXXXX";



    @Override
    public Response<Page<ProjectionDTO>> getProjection(ParametersByProjection projection) {
        try {
            List<ProjectionDTO>  headcount=  getHeadcountByAccount(projection);


            if(headcount.isEmpty()){
                Response<Page<ProjectionDTO>> data = new Response<>();
                data.setStatus(404);
                data.setSuccess(false);
                data.setMessage("No existe datos para este periodo");
                return  data;
            }

            switch (projection.getBu()){
                case "T. ECUADOR":
                    isEcuador(headcount,projection);
                    break;
                case "T. URUGUAY":
                    isUruguay(headcount,projection);
                    break;
                default:
                    break;
            }

            List<ComponentAmount> groupedData = headcount.stream()
                    .flatMap(j -> j.getComponents().stream())
                    .collect(Collectors.groupingBy(
                            PaymentComponentDTO::getPaymentComponent,
                            Collectors.summingDouble(PaymentComponentDTO::getAmount)
                    )).entrySet()
                    .stream()
                    .map(entry -> new ComponentAmount(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());


            Page<ProjectionDTO> page = new Page<>(0,0, headcount.size(),
                    headcount,groupedData);

            Response<Page<ProjectionDTO>> data = new Response<>();
            data.setStatus(200);
            data.setSuccess(true);
            data.setData(page);
            return  data;
        }catch (Exception ex){
            Response<Page<ProjectionDTO>> data = new Response<>();
            data.setStatus(500);
            data.setSuccess(false);
            data.setMessage(Arrays.stream(ex.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining(",")));
            return  data;
        }

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
        projection.getParameters().sort((o1, o2) -> Shared.compare(o1.getPeriod(),o2.getPeriod()));
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
                    projection.getPeriod(),projection.getRange());}
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
                       .amount(po!=null && po.get(p)!=null?Double.parseDouble(po.get(p).toString()):0)
                       .projections(Shared.generateMonthProjection(period,range,po!=null&&
                               po.get(p)!=null?Double.parseDouble(po.get(p).toString()):0))
                       .build()
       ).collect(Collectors.toList());
        headcount.getComponents().addAll(bases);

    }

    @Override
    public Config getComponentByBu(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElse(null);
        if(vbu!=null){
            return Config.builder().
                    components(sharedRepo.getComponentByBu(bu))
                    .parameters(parameterRepository.getParameterBu(bu))
                    .icon(vbu.getIcon())
                    .money(vbu.getMoney())
                    .vViewPo(vbu.getVViewPo())
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
    public Response<Boolean> saveProjection(ParameterHistorial projection,String email) {

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

            return Response.<Boolean>builder().success(true).message("Proyección guardada con éxito").status(200).build();


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
    public Response<Boolean> deleteHistorical(Integer id) {
        try {
            parameterProjectionRepository.deleteByIdHistorial(id);
            historialProjectionRepository.deleteById(id);
            return Response.<Boolean>builder().success(true).message("Eliminación con éxito").status(200).build();

        }catch (Exception ex){
            return Response.<Boolean>builder().success(false).status(500).message("Ocurrio un error inesperado")
                    .error("Error").build();

        }

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

        List<HeadcountHistoricalProjection> headcount=  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                String.join(",", entities),data.getPeriod(),
                components.stream().map(ComponentProjection::getComponent).collect(Collectors.joining(",")));

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
                        Collectors.mapping(deuda -> new ComponentAmount(deuda.getComponent(), deuda.getAmount()), Collectors.toList())
                )).entrySet().stream()
                .map(entry -> {
                    HeadcountHistoricalProjection info = headcount.stream().filter(i->i.getPosition().equalsIgnoreCase(entry.getKey())).findFirst().get();
                    return  new DataBaseResponse(entry.getKey(),info.getIdssff(),info.getPoname(),entry.getValue());
                })
                .collect(Collectors.toList());

       deudasAgrupadas.forEach(u-> u.getComponents().addAll(nominal.stream().filter(k->  k.getID_SSFF().equalsIgnoreCase(u.getIdssff()))
               .map(o->ComponentAmount.builder()
                       .component(o.getCodigoNomina())
                       .amount(o.getImporte())
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
    public Response<List<AccountProjection>> getAccountsByBu(Integer idBu) {

        try {
            return Response.<List<AccountProjection>>builder().status(200)
                    .data(sharedRepo.getAccount(idBu)).success(true).message("ok").build();
        }catch (Exception ex ){
            return Response.<List<AccountProjection>>builder().status(500).success(false)
                    .error(Arrays.stream(ex.getStackTrace()).map(StackTraceElement::toString)
                            .collect(Collectors.joining(","))
            ).build();
        }

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
        List<HeadcountProjection> headcount=  repository.getHistoricalBuAndPeriodSp(Constant.KEY_BD,
                        String.join(",", entities),projection.getPeriod(),String.join(",",
                        projection.getPaymentComponent().stream().map(PaymentComponentType::getComponent).collect(Collectors.joining(","))))
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
                        .classEmp(e.getClassemp())
                            .build()).collect(Collectors.toList());

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
                                                                .amount(0.0)
                                                                .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), 0.0))
                                                                .build();
                                                        list.stream().filter(t->t.getComponent().equalsIgnoreCase(p.getComponent())).findFirst().ifPresent(u->{
                                                                    r.setPaymentComponent(p.getComponent());
                                                                    r.setType(p.getType());
                                                                    r.setAmount(u.getAmount());
                                                                    r.setProjections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(), u.getAmount()));
                                                                });
                                                      return r;
                                                    }

                                            ).collect(Collectors.toList());
                                    addNominal(projection,projectionsComponent,nominal,codeNominals,list);
                                    return new ProjectionDTO(
                                            list.get(0).getIdssff(),
                                            list.get(0).getPosition(),
                                            list.get(0).getPoname(),
                                            projectionsComponent
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
                    paymentComponent("TURN").amount(hhee)
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),hhee)).build());
            projectionsComponent.add(PaymentComponentDTO.builder().
                    type(12).
                    paymentComponent("260").amount(guarderia)
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),guarderia)).build());
        }else if(projection.getBu().equalsIgnoreCase("T. URUGUAY")){
            double hhee = 0.0;
            for(NominaProjection h : nominal.stream().filter(g->g.getIdssff()
                    .equalsIgnoreCase(list.get(0).getIdssff())).collect(Collectors.toList()) ){
                if(codeNominas.stream().anyMatch(p->p.getCodeNomina().equalsIgnoreCase(h.getCodeNomina()))) {
                    hhee = hhee +h.getImporte();
                }
            }
            projectionsComponent.add(PaymentComponentDTO.builder().
                    type(16).
                    paymentComponent("HHEE").amount(hhee)
                    .projections(Shared.generateMonthProjection(projection.getPeriod(), projection.getRange(),hhee)).build());
        }
    }
}
