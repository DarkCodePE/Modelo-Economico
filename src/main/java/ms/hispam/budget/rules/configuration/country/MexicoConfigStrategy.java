package ms.hispam.budget.rules.configuration.country;

import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.dto.countries.DefaultConfig;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.Convenio;
import ms.hispam.budget.entity.mysql.ConvenioBono;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import ms.hispam.budget.service.BuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MexicoConfigStrategy extends BaseConfigStrategy {

    private final ConvenioRepository convenioRepository;
    private final ConvenioBonoRepository convenioBonoRepository;
    private final BuService buService;

    @Autowired
    protected MexicoConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository, ConvenioRepository convenioRepository, ConvenioBonoRepository convenioBonoRepository, BuService buService) {
        super(sharedRepo, parameterRepository, buRepository, codeNominaRepository, baseExternRepository, parameterDefaultRepository, validationRuleRepository);
        this.convenioRepository = convenioRepository;
        this.convenioBonoRepository = convenioBonoRepository;
        this.buService = buService;
    }


    @Override
    public Config getConfig(String bu) {
        Bu vbu = buRepository.findByBu(bu).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontro el BU"));
        List<Convenio> convenio = convenioRepository.findAll();
        List<ConvenioBono> convenioBono = convenioBonoRepository.findAll();
        return DefaultConfig.builder()
                .components(sharedRepo.getComponentByBu(bu))
                .parameters(parameterRepository.getParameterBu(bu))
                .icon(vbu.getIcon())
                .money(vbu.getMoney())
                .vViewPo(vbu.getVViewPo())
                .vTemporal(buService.getAllBuWithRangos(vbu.getId()))
                .convenios(convenio)
                .convenioBonos(convenioBono)
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
        return "T. MEXICO";
    }
}
