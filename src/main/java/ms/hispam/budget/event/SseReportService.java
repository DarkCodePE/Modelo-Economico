package ms.hispam.budget.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Service
@Slf4j
public class SseReportService {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Object> emitterLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    public synchronized SseEmitter getOrCreateEmitter(String jobId) {
        log.info("Obteniendo o creando emitter para jobId: {}", jobId);
        emitterLocks.putIfAbsent(jobId, new Object());
        return emitters.computeIfAbsent(jobId, this::createEmitter);
    }

    private SseEmitter createEmitter(String jobId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
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
        sendEventAsync(jobId, "report_update", Map.of("status", status, "message", message, "progress", progress));
    }

    public void sendDetailUpdate(String jobId, String message) {
        sendEventAsync(jobId, "detail_update", Map.of("message", message));
    }

    private void sendEventAsync(String jobId, String eventName, Map<String, Object> data) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(jobId, eventName, data);
            } catch (Exception e) {
                log.error("Error al enviar evento asíncrono para jobId: {}", jobId, e);
                // No se propaga la excepción para no bloquear el proceso principal
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
        Object lock = emitterLocks.get(jobId);
        if (lock != null) {
            synchronized (lock) {
                SseEmitter emitter = emitters.remove(jobId);
                emitterLocks.remove(jobId);
                if (emitter != null) {
                    try {
                        emitter.complete();
                        log.info("Emitter completado para jobId: {}", jobId);
                    } catch (Exception e) {
                        log.error("Error al completar emitter para jobId: {}", jobId, e);
                    }
                }
            }
        }
    }

    private synchronized void removeEmitter(String jobId) {
        Object lock = emitterLocks.remove(jobId);
        if (lock != null) {
            synchronized (lock) {
                SseEmitter emitter = emitters.remove(jobId);
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

    @Scheduled(fixedDelay = 600000) // Ejecuta cada 10 minutos (600000 milisegundos)
    public void cleanupEmittersJob() {
        log.info("Ejecutando limpieza de emitters...");
        cleanupEmitters();
    }
    public void cleanupEmitters() {
        emitters.keySet().forEach(this::removeEmitter);
    }
}
