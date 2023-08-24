package ms.hispam.budget.service.impl;

import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.ComponentCashProjection;
import ms.hispam.budget.dto.projections.ComponentNominaProjection;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.dto.projections.ParameterProjectionBD;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.HistorialProjection;
import ms.hispam.budget.entity.mysql.LegalEntity;
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
    private BuRepository buRepository;

    @Autowired
    private LegalEntityRepository legalEntityRepository;


    @Override
    public Response<Page<ProjectionDTO>> getProjection(ParametersByProjection projection) {
        try {
            int skipCount = (projection.getPage()) * projection.getSize();
            DataProjection  headcount=  getHeadcountByAccount(projection.getBu(),
                    projection.getPeriod(),projection.getPaymentComponent(),projection.getRange(),skipCount,
                    projection.getSize(),projection.getNominaFrom(),projection.getNominaTo());

            switch (projection.getBu()){
                case "T. ECUADOR":
                    isEcuador(headcount.getGroupData(),projection);
                    break;
                case "T. URUGUAY":
                    isUruguay(headcount.getGroupData(),projection);
                    break;
            }

            List<ComponentAmount> groupedData = headcount.getComponents().stream()
                    .collect(Collectors.groupingBy(
                            ComponentCashProjection::getID_de_componente_de_pago,
                            Collectors.summingDouble(ComponentCashProjection::getImporte)
                    )).entrySet()
                    .stream()
                    .map(entry -> new ComponentAmount(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());;

            int totalPages= (int) Math.ceil((double) headcount.getTotalData().size() / projection.getSize());

            Page<ProjectionDTO> page = new Page<>(projection.getPage(),totalPages, headcount.getTotalData().size(), headcount.getGroupData(),groupedData);

            Response<Page<ProjectionDTO>> data = new Response<>();
            data.setStatus(200);
            data.setSuccess(true);
            data.setData(page);
            return  data;
        }catch (Exception ex){
            ex.printStackTrace();
            Response<Page<ProjectionDTO>> data = new Response<>();
            data.setStatus(500);
            data.setSuccess(false);
            data.setMessage(Arrays.stream(ex.getStackTrace()).map(c->c.toString()).collect(Collectors.joining(",")));
            return  data;
        }

    }

    private void isUruguay( List<ProjectionDTO>  headcount , ParametersByProjection projection){
        Uruguay methodsUruguay = new Uruguay();
        //Genera las proyecciones del rango
        for (ProjectionDTO projectionDTO : headcount) {
            //parametros que no tienen periodo que se ejecutan siempre

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
        }
    }

    @Override
    public Config getComponentByBu(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElse(null);
        return Config.builder().components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu!=null?vbu.getIcon():"")
                .money(vbu!=null?vbu.getMoney():"")
                .build();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Response<Boolean> saveProjection(ParameterHistorial projection,String email) {
        try {
            HistorialProjection historial = new HistorialProjection();
            historial.setBu(projection.getBu());
            historial.setName(projection.getName());
            historial.setVDate(new Date());
            historial.setVRange(projection.getRange());
            historial.setVPeriod(projection.getPeriod());
            historial.setCreatedAt(new Date());
            historial.setCreatedBy(email);
            historial.setNominaFrom(projection.getNominaFrom().replaceAll("/",""));
            historial.setNominaTo(projection.getNominaTo().replaceAll("/",""));
            historial = historialProjectionRepository.save(historial);
            HistorialProjection finalHistorial = historial;
            List<ParameterProjection> parameters = projection.getParameters().stream().map(p-> new ParameterProjection(
              null, finalHistorial.getId(),
                    p.getParameter().getId(),
                    p.getValue(),
                    p.getPeriod()==""?null: p.getPeriod(),
                    p.getRange()==""?null:p.getRange(),
                    p.getIsRetroactive(),
                    p.getPeriodRetroactive()==""?null:p.getPeriodRetroactive()
            )).collect(Collectors.toList());
            parameterProjectionRepository.saveAll(parameters);

            return Response.<Boolean>builder().success(true).message("Proyección guardada con éxito").status(200).build();
        }catch (Exception e){
            return Response.<Boolean>builder().success(false).status(500).message("Ocurrio un error inesperado").error(e.getStackTrace().toString()).build();
        }

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
                .build()).collect(Collectors.toList());
    }

    @Override
    public Response<Page<ProjectionDTO>> getHistorialProjection(Integer  id,Integer page, Integer size) {
        List<ParameterProjectionBD> parameters = sharedRepo.getParameter_historical(id);
        List<ComponentProjection> components = sharedRepo.getComponentByBu(parameters.get(0).getVbu());
        ParametersByProjection projection = ParametersByProjection
                .builder()
                .bu(parameters.get(0).getVbu())
                .period(parameters.get(0).getHperiod())
                .range(parameters.get(0).getHrange())
                .nominaFrom(parameters.get(0).getNfrom())
                .nominaTo(parameters.get(0).getNto())
                .page(page)
                .size(size)
                .paymentComponent(components.stream().filter(ComponentProjection::getIscomponent).map(u->PaymentComponentType.builder()
                        .component(u.getComponent())
                        .type(u.getType())
                        .build()).collect(Collectors.toList()))
                .parameters(parameters.stream().filter(p->p.getId()!=null).map(k->ParametersDTO.builder()
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

        replaceSlash(projection);
        Response<Page<ProjectionDTO>> response = getProjection(projection);
        response.getData().setComponents(components);
        response.getData().setParameters(projection.getParameters());
        return response;
    }

    private void replaceSlash(ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("/",""));
        projection.getParameters().forEach(k-> {
            k.setPeriod(k.getPeriod()==null?"":  k.getPeriod().replaceAll("/",""));
            k.setRange(k.getRange()==null?"": k.getRange().replaceAll("/",""));
            k.setPeriodRetroactive(k.getPeriodRetroactive()==null?"": k.getPeriodRetroactive().replaceAll("/",""));
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
            return Response.<Boolean>builder().success(false).status(500).message("Ocurrio un error inesperado").error(ex.getStackTrace().toString()).build();

        }

    }

    @Override
    public byte[] downloadProjection(  ParametersByProjection projection) {
        DataProjection headcount=  getHeadcountByAccount(projection.getBu(),
                projection.getPeriod(),projection.getPaymentComponent(),projection.getRange(),0,999999,
                projection.getNominaFrom(),projection.getNominaTo());
        List<ComponentProjection>  componentProjections=  sharedRepo.getComponentByBu(projection.getBu());
        switch (projection.getBu()){
            case "T. ECUADOR":
                isEcuador(headcount.getTotalData(),projection);
                break;
            case "T. URUGUAY":
                isUruguay(headcount.getTotalData(),projection);
                break;
        }

       return  ExcelService.generateExcelProjection(headcount.getTotalData(),componentProjections,projection);
    }

    @Override
    public byte[] downloadProjectionHistorical(Integer id) {
        List<ParameterProjectionBD> parameters = sharedRepo.getParameter_historical(id);
        List<ComponentProjection> components = sharedRepo.getComponentByBu(parameters.get(0).getVbu());
        ParametersByProjection projection = ParametersByProjection
                .builder()
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
        replaceSlash(projection);

        return downloadProjection(projection);
    }

    private List<PaymentComponentDTO> validate(Ecuador methods, List<PaymentComponentDTO> componentDTO,ParametersDTO dto ,
                                               List<ParametersDTO> parameters ){
        // PARAMETROS PARA EL SUELDO BASE
        //Revision salarial
        if(dto.getParameter().getId()==1){
           return methods.revisionSalary(componentDTO,dto,parameters);
        }
        // Ajuste salario minimo
        if(dto.getParameter().getId()==2){
          return methods.adjustSalaryAdjustment(componentDTO,dto);
        }
        return componentDTO;
    }


    private DataProjection getHeadcountByAccount(String bu , String period,List<PaymentComponentType> paymentComponent ,
                                                      int range,int page,int size,String start,String end){
        Integer[] nominalHheeEcuador= { 150,160,170,180,185};
        Integer[] nominas= { 150,160,170,180,185,260};
        List<String> entities = legalEntityRepository.findByBu(bu).stream().map(LegalEntity::getLegalEntity).collect(Collectors.toList());
        List<ProjectionDTO> headcount=  repository.getHistoricalBuAndPeriod(period,entities)
                .stream().map(c->ProjectionDTO.builder().
                        po(c.getPosition()).
                        poName(c.getPoname()).
                        idssff(c.getIdssff()).
                        build()).collect(Collectors.toList());
        List<ComponentCashProjection> components = repository.getComponentCash(Constant.KEY_BD, String.join(",", entities), period);


        List<ComponentNominaProjection> nominal =  repository.getcomponentNomina(Constant.KEY_BD,bu,start,end);
        List<ProjectionDTO> resume = headcount.stream()
                .skip(page)
                .limit(size)
                .collect(Collectors.toList());



        resume.forEach(c-> {
            List<PaymentComponentDTO> projectionsComponent =
                    paymentComponent.stream().map(p -> {
                        ComponentCashProjection am = components.stream()
                                .filter(o -> o.getID_de_componente_de_pago().equalsIgnoreCase(p.getComponent()) &&
                                        o.getID_posicion().equalsIgnoreCase(c.getPo()))
                                .findFirst().orElse(null);
                        Double amount = am != null ? am.getImporte() : 0.0;
                        return PaymentComponentDTO.builder().
                                type(p.getType()).
                                paymentComponent(p.getComponent()).amount(amount)
                                .projections(Shared.generateMonthProjection(period, range, amount)).build();
                    }).collect(Collectors.toList());
            if (bu.equalsIgnoreCase("T. ECUADOR"))
            {
                //Nomina
                double hhee = 0.0;
                double guarderia =0.0;
                for(ComponentNominaProjection h : nominal.stream().filter(g->g.getID_SSFF()
                        .equalsIgnoreCase(c.getIdssff())).collect(Collectors.toList()) ){
                    if(Arrays.asList(nominalHheeEcuador).contains(h.getCodigoNomina())){
                        hhee=+h.getImporte();
                    }
                    if(h.getCodigoNomina()==260){
                        guarderia=h.getImporte();
                    }
                }
                projectionsComponent.add(PaymentComponentDTO.builder().
                        type(7).
                        paymentComponent("TURN").amount(hhee)
                        .projections(Shared.generateMonthProjection(period,range,hhee)).build());
                projectionsComponent.add(PaymentComponentDTO.builder().
                        type(12).
                        paymentComponent("260").amount(guarderia)
                        .projections(Shared.generateMonthProjection(period,range,guarderia)).build());
            }else if(bu.equalsIgnoreCase("T. URUGUAY")){
                double hhee = 0.0;
                for(ComponentNominaProjection h : nominal.stream().filter(g->g.getID_SSFF()
                        .equalsIgnoreCase(c.getIdssff())).collect(Collectors.toList()) ){
                    if(Arrays.asList(nominalHheeEcuador).contains(h.getCodigoNomina())) {
                        hhee = +h.getImporte();
                    }
                }
                projectionsComponent.add(PaymentComponentDTO.builder().
                        type(16).
                        paymentComponent("HHEE").amount(hhee)
                        .projections(Shared.generateMonthProjection(period,range,hhee)).build());
            }
            c.setComponents(projectionsComponent);


        });
        System.out.println("Pidiendo corrido " + new Date());



        return DataProjection.builder().totalData(headcount).groupData(resume).components(components).build();
    }








}
