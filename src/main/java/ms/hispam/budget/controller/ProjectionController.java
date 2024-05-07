package ms.hispam.budget.controller;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.service.ProjectionService;
import ms.hispam.budget.service.ReportDownloadService;
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


import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j(topic = "PROJECTION_CONTROLLER")
@RestController
@RequestMapping("budget/v1")
@CrossOrigin("*")
public class ProjectionController {

    @Autowired
    private ProjectionService service;
    @Autowired
    private ReportDownloadService reportDownloadService;
    @Autowired
    private  ReportJobRepository reportJobRepository;

    @PostMapping("/projection")
    public Page<ProjectionDTO> getProjection(@RequestBody @Valid ParametersByProjection projection) {
        Shared.replaceSLash(projection);
        ////log.debug("Projection: {}", projection);
        return service.getProjection(projection);
    }

    @PostMapping("/new-projection")
    public ProjectionSecondDTO getNewProjection(@RequestBody @Valid ParametersByProjection projection) {
        //log.debug("Projection: {}", projection);
        Shared.replaceSLash(projection);
        return service.getNewProjection(projection);
    }

    @PostMapping("/data-base")
    public DataBaseMainReponse getDataBase(@RequestBody DataRequest dataRequest) {

        return service.getDataBase(dataRequest);
    }
    @GetMapping("/bu")
    public List<Bu> accessBu(@RequestHeader String user) {

        return service.findByBuAccess(user);
    }
    @GetMapping("/componentByCountry")
    public Config getComponentBy(@RequestParam String bu) {
        return service.getComponentByBu(bu);
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
    public ExcelReportDTO downloadProjection(@RequestBody ParametersByProjection projection, @RequestHeader String user, @RequestParam Integer type, @RequestParam Integer idBu) {
        try {
            ReportJob job = new ReportJob();
            String taskId = UUID.randomUUID().toString();
            job.setStatus("en progreso");
            job.setMonthBase(projection.getPeriod());
            job.setNominaRange(projection.getNominaFrom() + " - " + projection.getNominaTo());
            job.setCreationDate(java.time.LocalDateTime.now());
            job.setCode(taskId);
            job.setIdBu(projection.getIdBu());
            job.setIdSsff(user);
            job.setTypeReport(type);
            ReportJob jobDB =  reportJobRepository.save(job);
            ExcelReportDTO reportInProgress = ExcelReportDTO.builder()
                    .id(job.getId())
                    .code(jobDB.getCode())
                    .status("en progreso")
                    .build();
            if (type == 2)
                service.downloadPlannerAsync(projection, type, idBu, user, jobDB);
            else if (type == 1)
                service.downloadProjection(projection, user, jobDB, idBu);
            else if (type == 3)
                service.downloadCdgAsync(projection, type, idBu, user, jobDB);
            // Retornar la respuesta inmediata con el estado "en progreso"
            return reportInProgress;
        } catch (Exception e) {
            log.error("Error al iniciar la descarga de la proyección", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al iniciar la descarga de la proyección", e);
        }
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
        List<ReportJob> reportJobs = reportDownloadService.getReportBu(user,idBu);
        //log.debug("User: {}, BU: {}", user, idBu);
        reportJobs.forEach(reportJob -> reportJob.getParameters().size());
        return reportJobs;
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

}
