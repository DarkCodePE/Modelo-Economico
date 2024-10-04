package ms.hispam.budget.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.ParametersByProjection;
import ms.hispam.budget.dto.ProjectionSecondDTO;
import ms.hispam.budget.entity.mysql.ProjectionHistory;
import ms.hispam.budget.entity.mysql.UserSession;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.exception.HistorySaveException;
import ms.hispam.budget.exception.SerialHistoryException;
import ms.hispam.budget.repository.mysql.HistorialProjectionRepository;
import ms.hispam.budget.repository.mysql.ProjectionHistoryRepository;
import ms.hispam.budget.repository.mysql.UserSessionRepository;
import ms.hispam.budget.util.ExternalService;
import ms.hispam.budget.util.ProjectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.rowset.serial.SerialException;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j(topic = "ProjectionHistoryService")
public class ProjectionHistoryService {
    private final ProjectionHistoryRepository historyRepository;
    private final ExternalService externalService;
    private final SseReportService sseReportService;
    private final ProjectionUtils projectionUtils;
    private final UserSessionRepository userSessionRepository;
    @Autowired
    public ProjectionHistoryService(ProjectionHistoryRepository historyRepository,
                                    ExternalService externalService,
                                    SseReportService sseReportService,
                                    ProjectionUtils projectionUtils, UserSessionRepository userSessionRepository) {
        this.historyRepository = historyRepository;
        this.externalService = externalService;
        this.sseReportService = sseReportService;
        this.projectionUtils = projectionUtils;
        this.userSessionRepository = userSessionRepository;
    }

    @Async("asyncTaskExecutor")
    public CompletableFuture<Void> saveProjectionAsync(ParametersByProjection parameters, ProjectionSecondDTO projectionResult, String userContact, String sessionId, String reportName) {
        sseReportService.sendHistoryUpdate(sessionId, "procesando", "Guardando la proyección en el historial", 50);
        try {
            saveProjectionToHistory(parameters, projectionResult, sessionId);
            sseReportService.sendHistoryUpdate(sessionId, "completado", "Proyección guardada en el historial", 100);
        } catch (Exception e) {
            sseReportService.sendHistoryUpdate(sessionId, "error", "Error al guardar la proyección en el historial", 100);
            log.error("Error al guardar la proyección en el historial: ", e);
            throw new HistorySaveException("Error al guardar la proyección en el historial", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void saveProjectionToHistory(ParametersByProjection parameters, ProjectionSecondDTO projectionResult, String sessionId) {
        try {
            String cacheKey = ProjectionUtils.generateHash(parameters);
            byte[] serializedData = serializeAndCompress(projectionResult);
            UserSession userSession = userSessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("La sesión no es válida"));
            String fileUrl = externalService.uploadProjectionFile(userSession.getId().intValue(), serializedData, cacheKey);

            ProjectionHistory history = ProjectionHistory.builder()
                    .userId(userSession.getId())
                    .parameters(serializeParameters(parameters))
                    .fileUrl(fileUrl)
                    .createdAt(LocalDateTime.now())
                    .hash(cacheKey)
                    .build();

            historyRepository.save(history);
        } catch (Exception e) {
            log.error("Error al guardar la proyección en el historial: ", e);
            throw new HistorySaveException("Error al guardar la proyección en el historial", e);
        }
    }
    private byte[] serializeAndCompress(ProjectionSecondDTO projectionResult) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
             ObjectOutputStream oos = new ObjectOutputStream(gzipOut)) {
            oos.writeObject(projectionResult);
            oos.flush();
            gzipOut.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerialHistoryException("Error al serializar y comprimir la proyección", e);
        }
    }

    private String serializeParameters(ParametersByProjection parameters) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error al serializar los parámetros de la proyección", e);
        }
    }
    public List<ProjectionHistory> getUserProjections(Long userId) {
        return historyRepository.findByUserId(userId);
    }

    public ProjectionSecondDTO getProjectionFromHistory(Long historyId, Long userId) {
        ProjectionHistory history = historyRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyección no encontrada"));

        // Descargar y deserializar la proyección
        byte[] data = externalService.downloadProjectionFile(history.getFileUrl());
        return decompressAndDeserialize(data);
    }

    private ProjectionSecondDTO decompressAndDeserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ObjectInputStream ois = new ObjectInputStream(gzipIn)) {
            return (ProjectionSecondDTO) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error al deserializar la proyección", e);
        }
    }
}

