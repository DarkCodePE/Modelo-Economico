package ms.hispam.budget.rules.configuration.country;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.countries.ConfigArg;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.projections.ParameterProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;
import ms.hispam.budget.entity.mysql.ConventArg;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import ms.hispam.budget.service.BuService;
import ms.hispam.budget.util.ParameterMapperUtil;
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
    private final ParameterMapperUtil parameterMapperUtil;

    protected ArgentinaConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, ConventArgRepository conventArgRepository, ParameterMapperUtil parameterMapperUtil) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.conventArgRepository = conventArgRepository;
        this.parameterMapperUtil = parameterMapperUtil;
    }

    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));

        List<ConventArg> convenios = conventArgRepository.findAll();
        List<ms.hispam.budget.dto.projections.ParameterProjection> allParameters = parameterRepository.getParameterBu(bu);

        // Mapear ParameterProjection a ParametersDTO
        List<ParametersDTO> parametersDTOList = parameterMapperUtil.toDTOList(allParameters);

        /*// Agrupar parámetros por convenio
        Map<String, List<ParameterProjection>> groupedParameters = allParameters.stream()
                .collect(Collectors.groupingBy(pa -> convenios.stream()
                        .filter(conv -> conv.getConvenio().equals(pa.getConvenio()))
                        .map(ConventArg::getConvenio)
                        .findFirst()
                        .orElse("No Convenio")));
        //log.info("groupedParameters {}",groupedParameters);
        // Crear lista de grupos de parámetros
        List<ParameterGroup> parameterGroups = groupedParameters.entrySet().stream()
                .map(entry -> ParameterGroup.builder()
                        .convenio(entry.getKey())
                        .parameters(entry.getValue())
                        .build())
                .collect(Collectors.toList());*/
        //log.info("parameterGroups {}",parameterGroups);
        List<ConventArgDTO> conveniosList = convenios.stream().map(c-> ConventArgDTO.builder()
                .id(c.getId())
                .convenio(c.getConvenio())
                .areaPersonal(c.getAreaPersonal())
                .build()).collect(Collectors.toList());
        List<ParameterDefaultDTO> parameterDefaultDTO = parameterDefaultRepository.findByBu(vbu.getId()).stream().map(p -> {
            ConventArgDTO conventArg = allParameters.stream()
                    .filter(pa -> pa.getId().equals(p.getVParameter()))
                    .map(pa -> ConventArgDTO.builder()
                            .id(pa.getId())
                            .convenio(pa.getConvenio())
                            .areaPersonal(pa.getArea())
                            .build())
                    .findFirst()
                    .orElse(null);
            return ParameterDefaultDTO.builder()
                    .id(p.getId())
                    .bu(p.getBu())
                    .vParameter(p.getVParameter())
                    .value(p.getValue())
                    .conventArg(conventArg)
                    .build();
        }).collect(Collectors.toList());
        log.info("parameterDefaultDTO {}",parameterDefaultDTO);
        return ConfigArg.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parametersDTOList)
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vDefault(parameterDefaultDTO)
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .conventArgs(conveniosList)
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
                .parameterGroups(null)
                .current(vbu.getCurrent())
                .build();
    }

    @Override
    public String getCountryCode() {
        return "T. ARGENTINA";
    }
}