package ms.hispam.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class SseExecutor {
    @Bean(name = "sseScheduler")
    public ScheduledExecutorService sseScheduler() {
        return Executors.newScheduledThreadPool(1);
    }

    @Bean(name = "sseAsyncExecutor")
    public ExecutorService sseAsyncExecutor() {
        return new ThreadPoolExecutor(
                5, 20,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
