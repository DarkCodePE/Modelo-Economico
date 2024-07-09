package ms.hispam.budget.rules.configuration.country;

import ms.hispam.budget.dto.Config;
import ms.hispam.budget.dto.OperationResponse;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.repository.mysql.*;
import ms.hispam.budget.rules.configuration.ConfigStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Component
public abstract class BaseConfigStrategy implements ConfigStrategy {

    protected final DemoRepository sharedRepo;
    protected final ParameterRepository parameterRepository;
    protected final BuRepository buRepository;
    protected final CodeNominaRepository codeNominaRepository;
    protected final BaseExternRepository baseExternRepository;
    protected final ParameterDefaultRepository parameterDefaultRepository;
    protected final ValidationRuleRepository validationRuleRepository;

    @Autowired
    protected BaseConfigStrategy(DemoRepository sharedRepo, ParameterRepository parameterRepository, BuRepository buRepository, CodeNominaRepository codeNominaRepository, BaseExternRepository baseExternRepository, ParameterDefaultRepository parameterDefaultRepository, ValidationRuleRepository validationRuleRepository) {
        this.sharedRepo = sharedRepo;
        this.parameterRepository = parameterRepository;
        this.buRepository = buRepository;
        this.codeNominaRepository = codeNominaRepository;
        this.baseExternRepository = baseExternRepository;
        this.parameterDefaultRepository = parameterDefaultRepository;
        this.validationRuleRepository = validationRuleRepository;
    }
}