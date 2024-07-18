package ms.hispam.budget.rules.configuration.country;

import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.dto.countries.DefaultConfig;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Service
public class DefaultConfigStrategy extends BaseConfigStrategy {

    @Autowired
    protected DefaultConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
    }

    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el BU"));
        return DefaultConfig.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vDefault(parameterDefaultRepository.findByBu(vbu.getId()))
                .nominas(codeNominaRepository.findByIdBu(vbu.getId()))
                .validationRules(validationRuleRepository.findByBu_Bu(vbu.getId()))
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
                .build();
    }

    @Override
    public String getCountryCode() {
        return "DEFAULT";
    }
}
