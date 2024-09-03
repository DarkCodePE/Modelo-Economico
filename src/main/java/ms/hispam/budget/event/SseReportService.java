package ms.hispam.budget.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SseReportService {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SseEmitter createEmitter(String jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));

        // Programa un heartbeat cada 30 segundos
        scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (Exception e) {
                emitters.remove(jobId);
                emitter.completeWithError(e);
            }
        }, 0, 30, TimeUnit.SECONDS);

        return emitter;
    }

    public void sendUpdate(String jobId, String status, String message) {
        log.info("Intentando enviar actualización para jobId: {}", jobId);
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            log.info("Emitter encontrado para jobId: {}", jobId);
            try {
                emitter.send(SseEmitter.event()
                        .data(Map.of("status", status, "message", message))
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name("report_update"));
                emitter.send(System.currentTimeMillis());
                log.info("Actualización enviada con éxito para jobId: {}", jobId);
            } catch (IOException e) {
                log.error("Error al enviar actualización para jobId: {}", jobId, e);
                emitters.remove(jobId);
                emitter.completeWithError(e);
            }
        }else {
            log.info("Emitter no encontrado para jobId: {}", jobId);
        }
    }

    public void completeEmitter(String jobId) {
        SseEmitter emitter = emitters.remove(jobId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
