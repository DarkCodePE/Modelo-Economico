package ms.hispam.budget.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class SseReportService {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Object> emitterLocks = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivityTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService asyncExecutor;

    private static final long EMITTER_TIMEOUT = Duration.ofMinutes(30).toMillis();

    @Autowired
    public SseReportService(
            @Qualifier("sseScheduler") ScheduledExecutorService scheduler,
            @Qualifier("sseAsyncExecutor") ExecutorService asyncExecutor
    ) {
        this.scheduler = scheduler;
        this.asyncExecutor = asyncExecutor;
    }

    public synchronized SseEmitter getOrCreateEmitter(String jobId) {
        log.info("Obteniendo o creando emitter para jobId: {}", jobId);
        emitterLocks.putIfAbsent(jobId, new Object());
        lastActivityTime.put(jobId, Instant.now());
        return emitters.computeIfAbsent(jobId, this::createEmitter);
    }

    private SseEmitter createEmitter(String jobId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitter.onCompletion(() -> removeEmitter(jobId));
        emitter.onTimeout(() -> removeEmitter(jobId));
        emitter.onError(e -> {
            log.error("Error en el emitter para jobId: {}", jobId, e);
            removeEmitter(jobId);
        });

        scheduler.scheduleAtFixedRate(() -> sendHeartbeat(jobId), 0, 15, TimeUnit.SECONDS);

        log.info("Emitter creado para jobId: {}", jobId);
        return emitter;
    }

    private void sendHeartbeat(String jobId) {
        sendEventAsync(jobId, "heartbeat", Map.of("data", "ping"));
    }

    public void sendUpdate(String jobId, String status, String message, int progress) {
        log.info("Enviando actualización para jobId: {} - status: {} - message: {} - progress: {}", jobId, status, message, progress);
        sendEventAsync(jobId, "report_update", Map.of("status", status, "message", message, "progress", progress));
    }

    public void sendDetailUpdate(String jobId, String message) {
        log.info("Enviando actualización de detalle para jobId: {} - message: {}", jobId, message);
        sendEventAsync(jobId, "detail_update", Map.of("message", message));
    }

    public void sendHistoryUpdate(String jobId, String status, String message, int progress) {
        log.info("Enviando actualización de historial para jobId: {} - status: {} - message: {} - progress: {}", jobId, status, message, progress);
        sendEventAsync(jobId, "history", Map.of("status", status, "message", message, "progress", progress));
    }

    private void sendEventAsync(String jobId, String eventName, Map<String, Object> data) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(jobId, eventName, data);
                lastActivityTime.put(jobId, Instant.now());
            } catch (Exception e) {
                log.error("Error al enviar evento asíncrono para jobId: {}", jobId, e);
            }
        }, asyncExecutor);
    }

    private void sendEvent(String jobId, String eventName, Map<String, Object> data) {
        Object lock = emitterLocks.get(jobId);
        if (lock != null) {
            synchronized (lock) {
                SseEmitter emitter = emitters.get(jobId);
                if (emitter != null) {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(data)
                                .id(String.valueOf(System.currentTimeMillis()))
                                .name(eventName));
                    } catch (IOException e) {
                        log.error("Error al enviar actualización para jobId: {}", jobId, e);
                        removeEmitter(jobId);
                    }
                }
            }
        }
    }

    public void completeEmitter(String jobId) {
        removeEmitter(jobId);
    }

    private synchronized void removeEmitter(String jobId) {
        Object lock = emitterLocks.remove(jobId);
        if (lock != null) {
            synchronized (lock) {
                SseEmitter emitter = emitters.remove(jobId);
                lastActivityTime.remove(jobId);
                if (emitter != null) {
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error al completar emitter para jobId: {} durante la eliminación", jobId, e);
                    }
                }
            }
        }
        log.info("Emitter eliminado para jobId: {}", jobId);
    }

    @Scheduled(fixedRate = 600000) // Ejecutar cada 5 minutos
    public void cleanupEmitters() {
        log.info("Iniciando limpieza de emitters inactivos");
        Instant now = Instant.now();
        emitters.keySet().forEach(jobId -> {
            Instant lastActivity = lastActivityTime.get(jobId);
            if (lastActivity != null && Duration.between(lastActivity, now).toMillis() > EMITTER_TIMEOUT) {
                log.info("Eliminando emitter inactivo para jobId: {}", jobId);
                removeEmitter(jobId);
            }
        });
        log.info("Limpieza de emitters inactivos completada");
    }
}
