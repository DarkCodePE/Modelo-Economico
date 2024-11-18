package ms.hispam.budget.service;
import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.*;
import ms.hispam.budget.dto.projections.AccountProjection;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.sqlserver.ModEconomicoProceso;
import ms.hispam.budget.event.SseReportService;
import ms.hispam.budget.util.ModEconomicoBatchPreparedStatementSetter;
import ms.hispam.budget.util.ModEconomicoRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;


import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "SEND_DATA_AZURE")
public class SendDataToAzure {

    private final PlatformTransactionManager transactionManager;
    private final AsyncTaskExecutor asyncTaskExecutor;
    private final SseReportService sseReportService;
    private final JdbcTemplate sqlServerJdbcTemplate;
    private static final int BATCH_SIZE = 2000; // Reducido de 5000 a 2000
    private static final int CHUNK_SIZE = 5000; // Reducido de 10000 a 5000

    @Autowired
    public SendDataToAzure(
            @Qualifier("reportTaskExecutor") AsyncTaskExecutor asyncTaskExecutor,
            @Qualifier("sqlServerTransactionManager") PlatformTransactionManager transactionManager,
            SseReportService sseReportService, JdbcTemplate sqlServerJdbcTemplate) {
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.sseReportService = sseReportService;
        this.transactionManager = transactionManager;
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
    }

    public CompletableFuture<Void> processPlannerDataAsync(
            ParametersByProjection parameters,
            List<ProjectionDTO> data,
            Bu bu,
            String user,
            double totalAmount,
            String sessionId,
            List<AccountProjection> accountProjections) {

        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Iniciando eliminación de datos anteriores para BU: {}", bu.getBu());
                sseReportService.sendUpdate(sessionId, "procesando", "Iniciando eliminación de datos anteriores para BU", 0);
                // Eliminar registros existentes para la BU antes de insertar nuevos datos
                deleteExistingRecords(bu.getBu());
                log.info("Iniciando inserción de nuevos datos para BU: {}", bu.getBu());
                // Procesar e insertar nuevos datos
                processDataInChunks(parameters, data, bu, user, totalAmount, sessionId, accountProjections);
                sseReportService.sendUpdate(sessionId, "completado",
                        "Procesamiento de MODECONOMICO completado", 100);

            } catch (Exception e) {
                log.error("Error en el procesamiento asíncrono de datos", e);
                sseReportService.sendUpdate(sessionId, "fallido",
                        "Error en el procesamiento de MODECONOMICO: " + e.getMessage(), 100);
                throw new RuntimeException("Error en el procesamiento de datos", e);
            }
        }, asyncTaskExecutor);
    }

    /**
     * Elimina los registros existentes en MODECONOMICO_Proceso para una BU específica.
     */
    private void deleteExistingRecords(String pais) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactionTemplate.execute(status -> {
            try {
                int deletedRows = sqlServerJdbcTemplate.update(
                        "DELETE FROM MODECONOMICO_Proceso WHERE Pais = ?", pais);
                log.info("Eliminados {} registros de MODECONOMICO_Proceso para Pais: {}", deletedRows, pais);
            } catch (DataAccessException e) {
                log.error("Error al eliminar registros de MODECONOMICO_Proceso para Pais: {}", pais, e);
                status.setRollbackOnly();
                throw new RuntimeException("Error al eliminar registros de MODECONOMICO_Proceso", e);
            }
            return null;
        });
    }
    /**
     * Procesa los datos en fragmentos (chunks) para insertar en la base de datos.
     */
    private void processDataInChunks(
            ParametersByProjection parameters,
            List<ProjectionDTO> data,
            Bu bu,
            String user,
            double totalAmount,
            String sessionId,
            List<AccountProjection> accountProjections) {

        int totalItems = calculateTotalItems(data);
        AtomicInteger processedItems = new AtomicInteger(0);
        sseReportService.sendUpdate(sessionId, "procesando", "Preparando datos para MODECONOMICO", 5);

        int totalDataSize = data.size();
        int numChunks = (totalDataSize + CHUNK_SIZE - 1) / CHUNK_SIZE;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int chunkIndex = 0; chunkIndex < numChunks; chunkIndex++) {
            int startIndex = chunkIndex * CHUNK_SIZE;
            int endIndex = Math.min(startIndex + CHUNK_SIZE, totalDataSize);
            List<ProjectionDTO> chunkData = data.subList(startIndex, endIndex);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processChunkWithTransaction(parameters, chunkData, bu, user, totalAmount,
                            processedItems, totalItems, sessionId, accountProjections), asyncTaskExecutor);
            futures.add(future);
        }

        // Esperar a que todos los chunks se procesen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void processChunkWithTransaction(
            ParametersByProjection parameters,
            List<ProjectionDTO> chunkData,
            Bu bu,
            String user,
            double totalAmount,
            AtomicInteger processedItems,
            int totalItems,
            String sessionId,
            List<AccountProjection> accountProjections) {

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            try {
                Queue<ModEconomicoRecord> recordsQueue = new LinkedList<>();
                AtomicLong recordCount = new AtomicLong(0);

                prepareRecords(parameters, chunkData, bu, user, totalAmount, recordsQueue,
                        recordCount, accountProjections);
                processBatchesInTransaction(recordsQueue, processedItems, totalItems, sessionId);

                return null;
            } catch (Exception e) {
                log.error("Error procesando chunk en transacción", e);
                status.setRollbackOnly();
                throw new RuntimeException("Error procesando chunk", e);
            }
        });
    }

    @Transactional("sqlServerTransactionManager")
    public void processBatchesInTransaction(
            Queue<ModEconomicoRecord> recordsQueue,
            AtomicInteger processedItems,
            int totalItems,
            String sessionId) {

        List<ModEconomicoRecord> batch = new ArrayList<>(BATCH_SIZE);

        while (!recordsQueue.isEmpty()) {
            ModEconomicoRecord record = recordsQueue.poll();
            if (record != null) {
                batch.add(record);
            }

            if (batch.size() >= BATCH_SIZE || (recordsQueue.isEmpty() && !batch.isEmpty())) {
                try {
                    sqlServerJdbcTemplate.batchUpdate(
                            "INSERT INTO MODECONOMICO_Proceso (FechaEjecucionProyeccion, UsuarioEjecuta, FechaTraspaso, UsuarioTraspaso, QRegistros, ImporteTotal, Pais, Proyeccion, ProyeccionNombre, PeriodoEjecucion, PositionID, ID_SSFF, ActividadFuncional, Ceco, Concepto, CuentaSAP, Mes, Monto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            new ModEconomicoBatchPreparedStatementSetter(batch)
                    );

                    int processed = processedItems.addAndGet(batch.size());
                    int progress = (int) ((processed / (double) totalItems) * 100);
                    sseReportService.sendUpdate(sessionId, "procesando",
                            String.format("Procesando registros MODECONOMICO: %d de %d", processed, totalItems),
                            progress);

                    batch.clear();
                } catch (Exception e) {
                    log.error("Error al insertar registros en la base de datos con JdbcTemplate", e);
                    throw new RuntimeException("Error al insertar registros con JdbcTemplate", e);
                }
            }
        }
    }

    private void prepareRecords(
            ParametersByProjection parameters,
            List<ProjectionDTO> data,
            Bu bu,
            String user,
            double totalAmount,
            Queue<ModEconomicoRecord> recordsQueue,
            AtomicLong recordCount,
            List<AccountProjection> accountProjections) {

        LocalDateTime currentDate = LocalDateTime.now();
        Map<String, String> actividadFuncionalCache = new HashMap<>();
        Map<String, String> cecoCache = new HashMap<>();

        for (ProjectionDTO projectionDTO : data) {
            String po = projectionDTO.getPo();

            // Usar caché para actividad funcional y CECO
            String actividadFuncional = actividadFuncionalCache.computeIfAbsent(po,
                    k -> getActividadFuncional(parameters, k));
            String ceco = cecoCache.computeIfAbsent(po,
                    k -> getCeco(parameters, projectionDTO));
            Map<String, AccountProjection> mapaComponentesValidos = accountProjections.stream()
                    .collect(Collectors.toMap(AccountProjection::getVcomponent, Function.identity(), (existingValue, newValue) -> newValue));

            for (PaymentComponentDTO component : projectionDTO.getComponents()) {
                for (MonthProjection monthProjection : component.getProjections()) {

                    // Validar datos antes de crear el registro
                    if (isValidRecord(projectionDTO, component, monthProjection)) {
                        int month;
                        String monthStr = monthProjection.getMonth();

                        // Manejar diferentes formatos de mes
                        if (monthStr.contains("/")) {
                            String[] parts = monthStr.split("/");
                            if (parts.length > 1) {
                                try {
                                    month = Integer.parseInt(parts[1]);
                                } catch (NumberFormatException e) {
                                    log.error("Formato de mes inválido: {}", monthStr, e);
                                    throw new IllegalArgumentException("Formato de mes inválido: " + monthStr, e);
                                }
                            } else {
                                log.error("Formato de mes inválido: {}", monthStr);
                                throw new IllegalArgumentException("Formato de mes inválido: " + monthStr);
                            }
                        } else if (monthStr.length() >= 6) {
                            try {
                                month = Integer.parseInt(monthStr.substring(4, 6));
                            } catch (NumberFormatException e) {
                                log.error("Formato de mes inválido: {}", monthStr, e);
                                throw new IllegalArgumentException("Formato de mes inválido: " + monthStr, e);
                            }
                        } else {
                            log.error("Formato de mes inválido: {}", monthStr);
                            throw new IllegalArgumentException("Formato de mes inválido: " + monthStr);
                        }
                        ModEconomicoRecord record = new ModEconomicoRecord(
                                currentDate,
                                user,
                                currentDate,
                                user,
                                recordCount.incrementAndGet(),
                                totalAmount,
                                bu.getBu(),
                                parameters.getPeriod(),
                                "PPTO24",
                                Integer.parseInt(parameters.getPeriod().substring(0, 4)),
                                po,
                                projectionDTO.getIdssff(),
                                actividadFuncional,
                                ceco,
                                component.getPaymentComponent(),
                                mapaComponentesValidos.get(component.getPaymentComponent()).getAccount(),
                                month,
                                monthProjection.getAmount().doubleValue()
                        );
                        recordsQueue.offer(record);
                    }
                }
            }
        }
    }

    private boolean isValidRecord(ProjectionDTO projectionDTO, PaymentComponentDTO component, MonthProjection monthProjection) {
        // Implementa aquí las validaciones necesarias para asegurar la integridad de los datos
        return projectionDTO != null && component != null && monthProjection != null;
    }

    private int calculateTotalItems(List<ProjectionDTO> data) {
        return data.stream()
                .mapToInt(proj -> proj.getComponents().stream()
                        .mapToInt(comp -> comp.getProjections().size())
                        .sum())
                .sum();
    }

    private String getActividadFuncional(ParametersByProjection parameters, String po) {
        return parameters.getBaseExtern().getData().stream()
                .filter(r -> po.equals(r.get("po")))
                .findFirst()
                .map(r -> r.get("AF"))
                .map(Object::toString)
                .orElse("");
    }

    private String getCeco(ParametersByProjection parameters, ProjectionDTO projectionDTO) {
        return parameters.getBc().getData().stream()
                .filter(r -> projectionDTO.getPo().equals(r.get("po")))
                .findFirst()
                .map(r -> {
                    Object cecoVal = r.get("CECO");
                    return (cecoVal != null && !cecoVal.toString().trim().isEmpty())
                            ? cecoVal.toString()
                            : projectionDTO.getCCostos();
                })
                .orElse(projectionDTO.getCCostos());
    }
    private ModEconomicoProceso convertToEntity(ModEconomicoRecord record) {
        ModEconomicoProceso entity = new ModEconomicoProceso();
        entity.setFechaEjecucionProyeccion(record.fechaEjecucion());
        entity.setUsuarioEjecuta(record.usuarioEjecuta());
        entity.setFechaTraspaso(record.fechaTraspaso());
        entity.setUsuarioTraspaso(record.usuarioTraspaso());
        entity.setQRegistros(record.qRegistros());
        entity.setImporteTotal(record.importeTotal());
        entity.setPais(record.pais());
        entity.setProyeccion(record.proyeccion());
        entity.setProyeccionNombre(record.proyeccionNombre());
        entity.setPeriodoEjecucion(record.periodoEjecucion());
        entity.setPositionID(record.positionId());
        entity.setIdSSFF(record.idSsff());
        entity.setActividadFuncional(record.actividadFuncional());
        entity.setCeco(record.ceco());
        entity.setConcepto(record.concepto());
        entity.setCuentaSAP(record.cuentaSap());
        entity.setMes(record.mes());
        entity.setMonto(record.monto());
        return entity;
    }
}
