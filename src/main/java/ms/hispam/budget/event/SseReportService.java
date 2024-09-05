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
    private final Map<String, Object> emitterLocks = new ConcurrentHashMap<>();
    //TODO : Aplicar sharding para distribuir la carga entre diferentes grupos de hilos (shards). En ese caso, cada shard tendría su propio ScheduledExecutorService con varios hilos
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5); // 10 hilos

    public synchronized SseEmitter getOrCreateEmitter(String jobId) {
        log.info("Obteniendo o creando emitter para jobId: {}", jobId);
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
        Object lock = emitterLocks.computeIfAbsent(jobId, k -> new Object());
        synchronized (lock) {
            SseEmitter emitter = emitters.get(jobId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                    log.debug("Heartbeat enviado para jobId: {}", jobId);
                } catch (IOException e) {
                    log.error("Error al enviar heartbeat para jobId: {}", jobId, e);
                    removeEmitter(jobId);
                }
            }
        }
    }

    public void sendUpdate(String jobId, String status, String message) {
        Object lock = emitterLocks.computeIfAbsent(jobId, k -> new Object());
        synchronized (lock) {
            SseEmitter emitter = emitters.get(jobId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event()
                            .data(Map.of("status", status, "message", message))
                            .id(String.valueOf(System.currentTimeMillis()))
                            .name("report_update"));
                    log.info("Actualización enviada con éxito para jobId: {}", jobId);
                } catch (IOException e) {
                    log.error("Error al enviar actualización para jobId: {}", jobId, e);
                    removeEmitter(jobId);
                }
            }
        }
    }

    public void completeEmitter(String jobId) {
        Object lock = emitterLocks.get(jobId); // Obtener el bloqueo para el jobId
        if (lock != null) {
            synchronized (lock) {
                SseEmitter emitter = emitters.remove(jobId); // Eliminar el emitter asociado
                emitterLocks.remove(jobId); // También eliminar el bloqueo asociado
                if (emitter != null) {
                    try {
                        emitter.complete(); // Completar el emitter
                        log.info("Emitter completado para jobId: {}", jobId);
                    } catch (Exception e) {
                        log.error("Error al completar emitter para jobId: {}", jobId, e);
                    }
                }
            }
        }
    }

    private synchronized void removeEmitter(String jobId) {
        SseEmitter emitter = emitters.remove(jobId);
        emitterLocks.remove(jobId); // Eliminar también el bloqueo asociado
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("Error al completar emitter para jobId: {} durante la eliminación", jobId, e);
            }
        }
        log.info("Emitter eliminado para jobId: {}", jobId);
    }

    // Método para limpiar emitters inactivos
    public void cleanupEmitters() {
        emitters.keySet().forEach(this::removeEmitter);
    }
}
