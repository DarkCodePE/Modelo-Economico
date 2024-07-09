package ms.hispam.budget.rules.configuration;

import ms.hispam.budget.dto.Config;

public interface ConfigStrategy {
    Config getConfig(String bu);
    String getCountryCode();
}
