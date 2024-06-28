package ms.hispam.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

@Configuration
public class LockConfig {
    @Bean
    public ConcurrentHashMap<String, Lock> sheetLocks() {
        return new ConcurrentHashMap<>();
    }
    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
}
