package ms.hispam.budget.rules.configuration.country;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.service.BuService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Service
@Slf4j(topic = "COLOMBIA")
public class ColombiaConfigStrategy extends BaseConfigStrategy {
    private final BuService buService;

    protected ColombiaConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, BuService buService) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.buService = buService;
    }

    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));

        return Config.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vTemporal(buService.getAllBuWithRangos(vbu.getId()))
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
        return "T. COLOMBIA";
    }
}
