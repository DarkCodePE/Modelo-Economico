package ms.hispam.budget.rules.configuration.country;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.dto.ParameterGroup;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;
import ms.hispam.budget.entity.mysql.ConventArg;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import ms.hispam.budget.service.BuService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "ARG")
public class ArgentinaConfigStrategy extends BaseConfigStrategy{
    private final ConventArgRepository conventArgRepository;

    protected ArgentinaConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, ConventArgRepository conventArgRepository) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.conventArgRepository = conventArgRepository;
    }

    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));

        List<ConventArg> convenios = conventArgRepository.findAll();
        List<ms.hispam.budget.dto.projections.ParameterProjection> allParameters = parameterRepository.getParameterBu(bu);
        // Agrupar parámetros por convenio
        Map<String, List<ParameterProjection>> groupedParameters = allParameters.stream()
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
        return Config.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
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
                .validationRules(validationRuleRepository.findByBu_Bu(vbu.getId()))
                .parameterGroups(parameterGroups)
                .current(vbu.getCurrent())
                .build();
    }

    @Override
    public String getCountryCode() {
        return "T. ARGENTINA";
    }
}