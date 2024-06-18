package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    public CompletableFuture<Sheet> createSheet(Workbook workbook, String sheetName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (syncLock) { // Usar el objeto de sincronización separado
                if (workbook.getSheet(sheetName) != null) {
                    log.info("Sheet name already exists, skipping creation: {}", sheetName);
                    return null; // Return null to indicate the sheet was not created
                }
                return workbook.createSheet(sheetName);
            }
        }, executorService);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }
}