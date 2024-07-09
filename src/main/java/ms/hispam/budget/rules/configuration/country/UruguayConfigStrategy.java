package ms.hispam.budget.rules.configuration.country;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j(topic = "URY")
public class UruguayConfigStrategy extends BaseConfigStrategy{
    private final NominaPaymentComponentLinkRepository nominaPaymentComponentLinkRepository;

    @Autowired
    protected UruguayConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, NominaPaymentComponentLinkRepository nominaPaymentComponentLinkRepository) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.nominaPaymentComponentLinkRepository = nominaPaymentComponentLinkRepository;
    }


    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
        // Obtener todos los componentes
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
        //list nominaPaymentComponentLink
        List<NominaPaymentComponentLink> nominaPaymentComponentLink = nominaPaymentComponentLinkRepository.findByBu(vbu.getId());
        //log.debug("combinedComponents {}",combinedComponents);
        return Config.builder()
                .components(combinedComponents) // usar los componentes combinados
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
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
                .validationRules(validationRuleRepository.findByBu_Bu(vbu.getId()))
                .current(vbu.getCurrent())
                .build();
    }

    @Override
    public String getCountryCode() {
        return "T. URUGUAY";
    }
    private boolean isUniqueTypePaymentComponentId(ComponentProjection component, List<ComponentProjection> components) {
        //log.info("component {}",component);
        long count = components.stream()
                .filter(c -> c.getType().equals(component.getType()))
                .count();
        return count == 1;
    }
}
