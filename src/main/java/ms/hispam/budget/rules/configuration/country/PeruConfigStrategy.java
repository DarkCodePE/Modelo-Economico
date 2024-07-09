package ms.hispam.budget.rules.configuration.country;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "PERU")
public class PeruConfigStrategy extends BaseConfigStrategy {

    private final EmployeeClassificationRepository employeeClassificationRepository;
    private final SeniorityAndQuinquenniumRepository seniorityAndQuinquenniumRepository;
    private final ConceptoPresupuestalRepository conceptoPresupuestalRepository;

    @Autowired
    protected PeruConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, EmployeeClassificationRepository employeeClassificationRepository, SeniorityAndQuinquenniumRepository seniorityAndQuinquenniumRepository, ConceptoPresupuestalRepository conceptoPresupuestalRepository) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.employeeClassificationRepository = employeeClassificationRepository;
        this.seniorityAndQuinquenniumRepository = seniorityAndQuinquenniumRepository;
        this.conceptoPresupuestalRepository = conceptoPresupuestalRepository;
    }


    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
        //log.debug("combinedComponents {}",combinedComponents);
        List<EmployeeClassification> employeeClassifications = employeeClassificationRepository.findAll();
        //seniorityAndQuinquenniumRepository
        List<SeniorityAndQuinquennium> seniorityAndQuinquennium = seniorityAndQuinquenniumRepository.findAll();
        //conceptoPresupuestalRepository
        List<ConceptoPresupuestal> conceptoPresupuestal = conceptoPresupuestalRepository.findAll();
        return Config.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .employeeClassifications(employeeClassifications)
                .seniorityAndQuinquenniums(seniorityAndQuinquennium)
                .conceptoPresupuestals(conceptoPresupuestal)
                .vDefault(parameterDefaultRepository.findByBu(vbu.getId()))
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .baseExtern(baseExternRepository.findByBu(vbu.getId())
                        .stream()
                        .map(c-> OperationResponse
                                .builder()
                                .code(c.getCode())
                                .name(c.getName())
                                .bu(c.getBu())
                                .isInput(c.getIsInput())
                                .build()
                        )
                        .collect(Collectors.toList()))
                .validationRules(validationRuleRepository.findByBu_Bu(vbu.getId()))
                .current(vbu.getCurrent())
                .build();
    }

    @Override
    public String getCountryCode() {
        return "T. PERU";
    }
}