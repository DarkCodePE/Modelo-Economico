package ms.hispam.budget.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.ParametersByProjection;
import ms.hispam.budget.dto.ProjectionSaveRequestDTO;
import ms.hispam.budget.dto.ProjectionSecondDTO;
import ms.hispam.budget.entity.mysql.ProjectionHistory;
import ms.hispam.budget.entity.mysql.UserSession;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.exception.HistorySaveException;
import ms.hispam.budget.exception.SerialHistoryException;
import ms.hispam.budget.exception.SerialParameterException;
import ms.hispam.budget.repository.mysql.HistorialProjectionRepository;
import ms.hispam.budget.repository.mysql.ProjectionHistoryRepository;
import ms.hispam.budget.repository.mysql.UserSessionRepository;
import ms.hispam.budget.util.ExternalService;
import ms.hispam.budget.util.ProjectionUtils;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.rowset.serial.SerialException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper; // Inyectado
    @Autowired
    public ProjectionHistoryService(ProjectionHistoryRepository historyRepository,
                                    ExternalService externalService,
                                    SseReportService sseReportService,
                                    ProjectionUtils projectionUtils, UserSessionRepository userSessionRepository, ObjectMapper objectMapper) {
        this.historyRepository = historyRepository;
        this.externalService = externalService;
        this.sseReportService = sseReportService;
        this.projectionUtils = projectionUtils;
        this.userSessionRepository = userSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Async("asyncTaskExecutor")
    public CompletableFuture<Void> saveProjectionAsync(ParametersByProjection parameters, ProjectionSecondDTO projectionResult, String sessionId, String reportName, String cacheKey) {
        log.info("CacheKey Desde Historial, {}", cacheKey);
        sseReportService.sendHistoryUpdate(sessionId, "procesando", "Guardando la proyección en el historial", 50);
        try {
            saveProjectionToHistory(parameters, projectionResult, sessionId, reportName, cacheKey);
            sseReportService.sendHistoryUpdate(sessionId, "completado", "Proyección guardada en el historial", 100);
        } catch (Exception e) {
            sseReportService.sendHistoryUpdate(sessionId, "error", "Error al guardar la proyección en el historial", 100);
            log.error("Error al guardar la proyección en el historial: ", e);
            throw new HistorySaveException("Error al guardar la proyección en el historial", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void saveProjectionToHistory(ParametersByProjection parameters, ProjectionSecondDTO projectionResult, String sessionId, String reportName, String cacheKey) {
        try {
            //String cacheKey = ProjectionUtils.generateHash(parameters);

            UserSession userSession = userSessionRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("La sesión no es válida"));

            // Obtener la versión máxima y asignar la nueva versión
            Integer maxVersion = historyRepository.findMaxVersionByHash(cacheKey);
            int version = (maxVersion != null) ? maxVersion + 1 : 1;

            // Añadir barras a las fechas solo para el historial
           addSlashesToDates(parameters);

            byte[] serializedData = serializeJSONAndCompress(projectionResult);

            String fileUrl = externalService.uploadProjectionFile(userSession.getId().intValue(), serializedData, cacheKey, version);

            ProjectionHistory history = ProjectionHistory.builder()
                    .userId(userSession.getId())
                    .parameters(serializeParameters(parameters))
                    .fileUrl(fileUrl)
                    .createdAt(LocalDateTime.now())
                    .hash(cacheKey)
                    .reportName(reportName)
                    .version(version)
                    .build();

            historyRepository.save(history);

            // Volver a quitar las barras después de guardar
            removeSLashes(parameters);
        } catch (Exception e) {
            log.error("Error al guardar la proyección en el historial: ", e);
            throw new HistorySaveException("Error al guardar la proyección en el historial", e);
        }
    }

    private byte[] serializeJSONAndCompress(ProjectionSecondDTO projectionResult) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {

            objectMapper.writeValue(gzipOut, projectionResult);
            gzipOut.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerialHistoryException("Error al serializar y comprimir la proyección", e);
        }
    }

    private ProjectionSecondDTO decompressJSONAndDeserialize(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais)) {

            return objectMapper.readValue(gzipIn, ProjectionSecondDTO.class);
        } catch (IOException e) {
            throw new SerialHistoryException("Error al deserializar la proyección", e);
        }
    }

    private String serializeParameters(ParametersByProjection parameters) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new SerialParameterException("Error al serializar los parámetros de la proyección", e);
        }
    }
    // Método auxiliar para deserializar los parámetros
    private ParametersByProjection deserializeParameters(String parametersJson) {
        try {
            return objectMapper.readValue(parametersJson, ParametersByProjection.class);
        } catch (JsonProcessingException e) {
            throw new SerialParameterException("Error al deserializar los parámetros de la proyección", e);
        }
    }

    public List<ProjectionHistory> getUserProjections(String userContact) {
        UserSession userSession = userSessionRepository
                .findByUserId(userContact)
                .orElseThrow(() -> new RuntimeException("No se encontraron proyecciones"));
        return historyRepository.findByUserId(userSession.getId());
    }

    public List<ProjectionHistory> getAllProjections() {
        return historyRepository.findAllByOrderByCreatedAtDesc();
    }


    public ProjectionSaveRequestDTO getProjectionFromHistory(Long historyId) {
        ProjectionHistory history = historyRepository.findById(historyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyección no encontrada"));

        // Deserializar los parámetros
        ParametersByProjection parameters = deserializeParameters(history.getParameters());

        // Descargar y deserializar el resultado de la proyección
        byte[] data = externalService.downloadProjectionFile(history.getFileUrl());
        ProjectionSecondDTO projectionResult = decompressJSONAndDeserialize(data);

        // Construir ProjectionSaveRequestDTO
        ProjectionSaveRequestDTO saveRequest = new ProjectionSaveRequestDTO();
        saveRequest.setProjection(parameters);
        saveRequest.setProjectionResult(projectionResult);
        saveRequest.setReportName(history.getReportName());
        // Manejar sessionId según corresponda. Si no está disponible, puede ser null o manejarse de otra manera.
        saveRequest.setSessionId(null); // Ajustar según necesidad

        return saveRequest;
    }
    public boolean existsProjection(String cacheKey) {
        return historyRepository.existsByHash(cacheKey);
    }

    /**
     * Busca una proyección por su hash y versión.
     * @param hash El hash de la proyección a buscar
     * @param version La versión de la proyección a buscar
     * @return La proyección encontrada o null si no se encuentra
     */
    public ProjectionHistory findProjectionHistoryByHashAndVersion(String hash, int version) {
        return historyRepository.findByHashAndVersion(hash, version).orElse(null);
    }
    /**
     * Verifica si ya existe una proyección con los mismos parámetros.
     *
     * @param parameters Los parámetros de la proyección a verificar
     * @return Una lista de proyecciones existentes con el mismo hash
     */
    public List<ProjectionHistory> checkExistProjection(ParametersByProjection parameters) {
        Shared.replaceSLash(parameters);
        String cacheKey = ProjectionUtils.generateHash(parameters);
        log.info("Verificando existencia de proyección con hash: {}", cacheKey);
        return historyRepository.findByHashOrderByVersionDesc(cacheKey);
    }

    public ProjectionSaveRequestDTO getProjectionFromHistoryHash(String hash, int version) {
        ProjectionHistory history = historyRepository.findByHashAndVersion(hash, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyección no encontrada"));

        // Deserializar los parámetros
        ParametersByProjection parameters = deserializeParameters(history.getParameters());

        // Descargar y deserializar el resultado de la proyección
        byte[] data = externalService.downloadProjectionFile(history.getFileUrl());
        ProjectionSecondDTO projectionResult = decompressJSONAndDeserialize(data);

        // Construir ProjectionSaveRequestDTO
        ProjectionSaveRequestDTO saveRequest = new ProjectionSaveRequestDTO();
        saveRequest.setProjection(parameters);
        saveRequest.setProjectionResult(projectionResult);
        saveRequest.setReportName(history.getReportName());
        // Manejar sessionId según corresponda. Si no está disponible, puede ser null o manejarse de otra manera.
        saveRequest.setSessionId(null); // Ajustar según necesidad

        return saveRequest;
    }
    private ParametersByProjection addSlashesToDates(ParametersByProjection parameters) {
        // Añadir barras a las fechas principales
        parameters.setPeriod(addSlashes(parameters.getPeriod()));
        parameters.setNominaFrom(addSlashes(parameters.getNominaFrom()));
        parameters.setNominaTo(addSlashes(parameters.getNominaTo()));

        // Procesar fechas en parámetros internos si es necesario
        if (parameters.getParameters() != null) {
            parameters.getParameters().forEach(param -> {
                param.setPeriod(addSlashes(param.getPeriod()));
                param.setRange(addSlashes(param.getRange()));
                param.setPeriodRetroactive(addSlashes(param.getPeriodRetroactive()));
            });
        }

        return parameters;
    }

    private String addSlashes(String date) {
        if (date == null || date.isEmpty() || date.contains("/")) {
            return date; // Retorna la fecha original si ya tiene barras o es nula/vacía
        }
        // Asume formato YYYYMM
        return date.substring(0, 4) + "/" + date.substring(4);
    }
    private void removeSLashes(ParametersByProjection projection) {
        projection.setPeriod(projection.getPeriod().replace("/", ""));
        projection.setNominaFrom(projection.getNominaFrom().replace("/", ""));
        projection.setNominaTo(projection.getNominaTo().replace("/", ""));
        if (projection.getParameters() != null) {
            projection.getParameters().forEach(param -> {
                param.setPeriod(param.getPeriod().replace("/", ""));
                param.setRange(param.getRange().replace("/", ""));
                param.setPeriodRetroactive(param.getPeriodRetroactive().replace("/", ""));
            });
        }
    }
}

