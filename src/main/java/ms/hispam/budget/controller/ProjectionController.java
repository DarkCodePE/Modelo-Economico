package ms.hispam.budget.controller;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.countries.ConventArgDTO;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.ProjectionHistory;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.entity.mysql.UserSession;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.service.ProjectionHistoryService;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.service.ReportDownloadService;
import ms.hispam.budget.service.UserSessionService;
import ms.hispam.budget.util.Shared;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j(topic = "PROJECTION_CONTROLLER")
@RestController
@RequestMapping("budget/v1")
@CrossOrigin(origins = "*", allowCredentials = "false")
public class ProjectionController {

    @Autowired
    private ProjectionService service;
    @Autowired
    private ReportDownloadService reportDownloadService;
    @Autowired
    private  ReportJobRepository reportJobRepository;
    @Autowired
    private SseReportService sseReportService;
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private ProjectionHistoryService projectionHistoryService;

    @PostMapping("/projection")
    public Page<ProjectionDTO> getProjection(@RequestBody @Valid ParametersByProjection projection) {
        Shared.replaceSLash(projection);
        ////log.debug("Projection: {}", projection);
        return service.getProjection(projection);
    }

    @PostMapping("/new-projection")
    public ProjectionSecondDTO getNewProjection(
            @RequestBody ParametersByProjection projection,
            @RequestHeader String user,
            @RequestParam(required = false) String reportName
    ) {
        //log.debug("Projection: {}", projection);
        Shared.replaceSLash(projection);
        String sessionId = userSessionService.createOrUpdateSession(user);
        String finalReportName = (reportName != null && !reportName.isEmpty())
                ? reportName
                : generateReportName(user, projection.getPeriod());
        return service.getNewProjection(projection, sessionId, finalReportName);
    }

    @PostMapping("/data-base")
    public DataBaseMainReponse getDataBase(@RequestBody DataRequest dataRequest) {

        return service.getDataBase(dataRequest);
    }

    @GetMapping("/convent-arg")
    public List<ConventArgDTO> getConveniosArg() {
        return service.getConventArg();
    }

    @GetMapping("/bu")
    public List<Bu> accessBu(@RequestHeader String user) {

        return service.findByBuAccess(user);
    }
    @GetMapping("/componentByCountry")
    public Config getComponentBy(@RequestParam String bu) {
        return service.getComponentByBu(bu);
    }

    @GetMapping("/componentByCountryV2")
    public Config getComponentByV2(@RequestParam String bu) {
        return service.getComponentByBuV2(bu);
    }

    @PostMapping("/save-projection")
    public Boolean saveProjection(@RequestBody ParameterHistorial projection ,@RequestHeader String user) {
        projection.setPeriod(projection.getPeriod().replace("/",""));

        return service.saveProjection(projection,user);
    }
    @GetMapping("/historial")
    public List<HistorialProjectionDTO> getHistoricalProjection(@RequestHeader String user) {
        return service.getHistorial(user);
    }

    @GetMapping("/get-projection-historical")
    public ProjectionInformation getHistorialProjection(@RequestParam Integer id) {
        return service.getHistorialProjection(id);
    }


    @DeleteMapping("/historial")
    public Boolean deleteHistorical(@RequestParam Integer id) {
        return service.deleteHistorical(id);
    }
    /*@PostMapping("/download-projection")
    public CompletableFuture<ExcelReportDTO> downloadProjection(@RequestBody ParameterDownload projection) {
        try {
            //log.debug("Projection: {}", projection);
            return service.downloadProjection(projection);
        } catch (Exception e) {
            // Aquí puedes manejar la excepción como prefieras. Por ejemplo, puedes registrar el error y luego lanzar una nueva excepción:
            log.error("Error al descargar la proyección", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al descargar la proyección", e);
        }
    }*/
    @PostMapping("/download-projection")
    public ExcelReportDTO downloadProjection(
            @RequestBody ParametersByProjection projection,
            @RequestHeader String user,
            @RequestParam Integer type,
            @RequestParam Integer idBu,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) Long historyId
    ) {
        try {
            // Si no se proporciona sessionId o no es válido, crear o actualizar la sesión
            String sessionId = userSessionService.createOrUpdateSession(user);
            //log.info("history id, {} ", historyId);
            ReportJob job = new ReportJob();
            //String taskId = UUID.randomUUID().toString();
            sseReportService.sendUpdate(sessionId, "iniciado", "Iniciando descarga", 0);
            job.setStatus("en progreso");
            job.setMonthBase(projection.getPeriod());
            job.setNominaRange(projection.getNominaFrom() + " - " + projection.getNominaTo());
            job.setCreationDate(java.time.LocalDateTime.now());
            job.setCode(sessionId);
            job.setIdBu(projection.getIdBu());
            job.setIdSsff(user);
            job.setTypeReport(type);
            // Generar el nombre del reporte
            String finalReportName = (reportName != null && !reportName.isEmpty())
                    ? reportName
                    : generateReportName(user, type, projection.getPeriod());
            job.setReportName(finalReportName);
            ReportJob jobDB =  reportJobRepository.save(job);
            ExcelReportDTO reportInProgress = ExcelReportDTO.builder()
                    .id(job.getId())
                    .code(jobDB.getCode())
                    .status("en progreso")
                    .sessionId(sessionId)
                    .build();
            if (type == 2)
                service.downloadPlannerAsync(projection, type, idBu, user, jobDB, sessionId);
            else if (type == 1)
                service.downloadProjection(projection, user, jobDB, idBu, sessionId, finalReportName, historyId);
            else if (type == 3)
                service.downloadCdgAsync(projection, type, idBu, user, jobDB, sessionId);
            // Retornar la respuesta inmediata con el estado "en progreso"
            return reportInProgress;
        } catch (Exception e) {
            log.error("Error al iniciar la descarga de la proyección", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al iniciar la descarga de la proyección", e);
        }
    }
    private String generateReportName(String user, Integer type, String period) {
        String reportType = getReportTypeName(type);
        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        return String.format("%s_%s_%s_%s", user, reportType, period, formattedTime);
    }
    private String generateReportName(String user, String period) {
        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        return String.format("%s_%s_%s", user, period, formattedTime);
    }
    private String getReportTypeName(Integer type) {
        return switch (type) {
            case 1 -> "Proyeccion";
            case 2 -> "Planner";
            case 3 -> "CDG";
            default -> "Desconocido";
        };
    }

    @GetMapping(path = "/status/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getReportStatus(@PathVariable String sessionId) {
        SseEmitter emitter = sseReportService.getOrCreateEmitter(sessionId);
        log.info("SSE emitter obtained for job: {}", sessionId);
        return emitter;
    }

    // Método para obtener el reporte generado
    @GetMapping("/report")
    @Transactional("mysqlTransactionManager")
    public List<ReportJob> getReport(@RequestHeader String user) {
        List<ReportJob> reportJobs = reportDownloadService.getReport(user);
        // Carga los parámetros de cada ReportJob
        reportJobs.forEach(reportJob -> reportJob.getParameters().size());
        return reportJobs;
    }
    //report bu
    @GetMapping("/report-bu")
    @Transactional("mysqlTransactionManager")
    public List<ReportJob> getReportBu(@RequestHeader String user,@RequestParam Integer idBu) {
        return reportDownloadService.getReportBu(user,idBu);
    }
    @GetMapping("/download-report")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String path) {
        try {
            byte[] file = reportDownloadService.getFile(path);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "report.xlsx");
            return new ResponseEntity<>(file, headers, HttpStatus.OK);
        } catch (RestClientException e) {
            log.error("Error downloading file: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("/download-historical")
    public ResponseEntity<byte[]> downloadProjectionHistorical(@RequestParam Integer id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "datos.xlsx");
        return new ResponseEntity<>(service.downloadProjectionHistorical(id), headers, 200);
    }

    @GetMapping("/account")
    public List<AccountProjection> getAccount(@RequestParam Integer id) {
        return service.getAccountsByBu(id);
    }
    @GetMapping("/rosseta")
    public List<RosetaDTO> getRosseta(@RequestParam Integer id) {
        return service.getRoseta(id);
    }
    @GetMapping("/save-money")
    public Boolean saveMoney(@RequestParam Integer id,@RequestParam String po) {
        return service.saveMoneyOdin(po,id);
    }

    @GetMapping("/get-position-baseline")
    public List<PositionBaseline> getPositionBaseline(@RequestParam String filter ,
                                                      @RequestParam String period,
                                                      @RequestParam String bu,
                                                      @RequestParam Integer idBu) {
        return service.getPositionBaseline(period,filter,bu,idBu);
    }

    // Endpoint para crear una nueva sesión
    @PostMapping("/create-session")
    public ResponseEntity<SessionResponseDTO> createSession(@RequestHeader String user) {
        String sessionId = userSessionService.createOrUpdateSession(user);
        SessionResponseDTO response = new SessionResponseDTO(sessionId);
        return ResponseEntity.ok(response);
    }
    /**
     * Endpoint para guardar una nueva proyección en el historial.
     *
     * @param saveRequest Objeto con los parámetros y resultado de la proyección
     * @return La proyección resultante.
     */
    //TODO SEND TO HASH
    @PostMapping("/save-projection-history")
    public ResponseEntity<ProjectionSecondDTO> saveProjection(
            @RequestBody ProjectionSaveRequestDTO saveRequest
    ) {
        try {
            projectionHistoryService.saveProjectionAsync(
                    saveRequest.getProjection(),
                    saveRequest.getProjectionResult(),
                    saveRequest.getSessionId(),
                    saveRequest.getReportName()
                    ,""
            );
            log.debug("saveRequest,{} -> ", saveRequest.getProjection());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } catch (Exception e) {
            log.error("Error al guardar la proyección: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    /**
     * Endpoint para obtener el historial de proyecciones de un usuario.
     *
     * @param user Correo del usuario
     * @return Lista de proyecciones históricas del usuario
     */
    @GetMapping("/history-projections")
    public List<ProjectionHistory> getUserProjections(@RequestHeader String user) {
        return projectionHistoryService.getUserProjections(user);
    }
    /**
     * Endpoint para obtener el historial de proyecciones.
     *
     * @return Lista de proyecciones históricas del usuario
     */
    @GetMapping("/history-projections-all")
    public List<ProjectionHistory> getAllProjections() {
        return projectionHistoryService.getAllProjections();
    }
    /**
     * Obtiene una proyección desde el historial basada en su ID y el ID del usuario.
     *
     * @param historyId Identificador único de la proyección en el historial
     * @return La proyección deserializada
     */
    @GetMapping("/history-projections-id")
    public ProjectionSaveRequestDTO getProjectionFromHistory(@RequestParam Long historyId) {
        log.info("historyId, {}", historyId);
        return projectionHistoryService.getProjectionFromHistory(historyId);
    }

    /**
     * Verifica si ya existe una proyección en el historial basada en los parámetros proporcionados.
     *
     * @param parameters Los parámetros de la proyección a verificar
     * @return Un objeto de respuesta que indica si la proyección ya existe o no.
     */
    @PostMapping("/check")
    public ResponseEntity<?> checkProjectionExists(@RequestBody ParametersByProjection parameters, @RequestParam String sessionId) {
        List<ProjectionHistory> validateHistoryList = projectionHistoryService.checkExistProjection(parameters);
        if (validateHistoryList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No existe una proyección con estos datos. Se procesede a generar una nueva version de esta proyección.");
        } else {
            return ResponseEntity.ok(validateHistoryList);
        }
    }
}
