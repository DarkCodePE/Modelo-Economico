package ms.hispam.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ReportExecutorConfig {
    @Bean(name = "reportExecutorService")
    public ExecutorService reportExecutorService() {
        // Define un tamaño fijo de hilos, por ejemplo, 10
        return Executors.newFixedThreadPool(10);
    }
}