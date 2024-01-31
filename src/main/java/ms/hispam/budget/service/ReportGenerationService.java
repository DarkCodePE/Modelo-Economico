package ms.hispam.budget.service;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.DataBaseMainReponse;
import ms.hispam.budget.dto.ExcelReportDTO;
import ms.hispam.budget.dto.ParameterDownload;
import ms.hispam.budget.dto.projections.ComponentProjection;
import ms.hispam.budget.entity.mysql.ReportJob;
import ms.hispam.budget.repository.mysql.ReportJobRepository;
import ms.hispam.budget.util.XlsReportService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@Slf4j(topic = "REPORT_GENERATION_SERVICE")
public class ReportGenerationService {
    private final XlsReportService xlsReportService;
    private final ReportJobRepository reportJobRepository;
    private final Executor executor;
    public ReportGenerationService(XlsReportService xlsReportService, ReportJobRepository reportJobRepository, Executor executor) {
        this.xlsReportService = xlsReportService;
        this.reportJobRepository = reportJobRepository;
        this.executor = executor;
    }
    // Método para iniciar la generación del reporte y manejar la notificación al usuario
    @Async
    public CompletableFuture<ExcelReportDTO> startReportGeneration(ParameterDownload projection, List<ComponentProjection> components, DataBaseMainReponse dataBase, String userContact) {
        return CompletableFuture.supplyAsync(() -> {
            // Crea un nuevo trabajo de generación de informes de forma asíncrona
            ReportJob job = new ReportJob();
            job.setStatus("en progreso");
            job.setIdSsff(userContact);
            reportJobRepository.save(job);

            // Construye el DTO del reporte con estado "en progreso"
            ExcelReportDTO reportInProgress = ExcelReportDTO.builder()
                    .id(job.getId())
                    .status(job.getStatus())
                    .URL(job.getReportUrl())
                    .build();
            log.info("Reporte en progreso: {}", reportInProgress);

            // Inicia la generación del reporte de manera asíncrona sin bloquear el hilo
            xlsReportService.generateAndCompleteReportAsync(projection, components, dataBase, userContact, job, userContact);

            // Retorna el DTO del reporte, que ya fue construido
            return reportInProgress;
        }, executor); //
    }

}
