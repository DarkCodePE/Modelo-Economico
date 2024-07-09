package ms.hispam.budget.rules.configuration;

import ms.hispam.budget.rules.configuration.country.ArgentinaConfigStrategy;
import ms.hispam.budget.rules.configuration.country.DefaultConfigStrategy;
import ms.hispam.budget.rules.configuration.country.PeruConfigStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConfigStrategyFactory {
    private final Map<String, ConfigStrategy> strategyMap;
    @Autowired
    public ConfigStrategyFactory(List<ConfigStrategy> strategies) {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(ConfigStrategy::getCountryCode, Function.identity()));
    }

    public ConfigStrategy getStrategy(String countryCode) {
        return strategyMap.getOrDefault(countryCode, strategyMap.get("DEFAULT"));
    }
}