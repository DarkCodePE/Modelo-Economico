package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.poi.ss.usermodel.*;

import static com.fasterxml.jackson.databind.type.LogicalType.DateTime;

@Service
@Slf4j(topic = "XLS_SHEET_CREATION_SERVICE")
public class XlsSheetCreationService {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Lock lock = new ReentrantLock();
    private final Object syncLock = new Object(); // Objeto de sincronización separado

    public String generateUniqueSheetName(Workbook workbook, String baseName) {
        lock.lock();
        try {
            String uniqueName = truncateSheetName(baseName);
            int index = 1;
            while (workbook.getSheet(uniqueName) != null) {
                String suffix = " (" + index++ + ")";
                uniqueName = truncateSheetName(baseName) + suffix;
                if (uniqueName.length() > 31) {
                    uniqueName = truncateSheetName(baseName.substring(0, 31 - suffix.length())) + suffix;
                }
            }
            return uniqueName;
        } finally {
            lock.unlock();
        }
    }

    private String truncateSheetName(String sheetName) {
        return sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
    }

    /*public CompletableFuture<Sheet> createSheet(Workbook workbook, String sheetName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (syncLock) { // Usar el objeto de sincronización separado
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet != null) {
                    // Limpiar la hoja existente antes de reutilizarla
                    for (int i = sheet.getLastRowNum(); i >= 0; i--) {
                        Row row = sheet.getRow(i);
                        if (row != null) {
                            sheet.removeRow(row);
                        }
                    }
                    log.info("Sheet name already exists, reusing and clearing: {}", sheetName);
                    return sheet; // Reutilizar la hoja existente
                } else {
                    log.info("Creating new sheet: {}", sheetName);
                    return workbook.createSheet(sheetName); // Crear una nueva hoja si no existe
                }
            }
        }, executorService);
    }*/
    private final Object sheetLock = new Object(); // Objeto de bloqueo final
    public CompletableFuture<SXSSFSheet> createOrReuseSheet(SXSSFWorkbook workbook, String sheetName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (sheetLock) {
                SXSSFSheet sheet = workbook.getSheet(sheetName);
                if (sheet != null) {
                    log.info("Sheet name already exists, reusing: {}", sheetName);
                    return sheet;
                } else {
                    log.info("Creating new sheet: {}", sheetName);
                    return workbook.createSheet(sheetName);
                }
            }
        }, executorService);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}