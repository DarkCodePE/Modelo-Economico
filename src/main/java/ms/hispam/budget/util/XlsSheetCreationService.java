package ms.hispam.budget.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, Lock> sheetLocks;

    @Autowired
    public XlsSheetCreationService(ConcurrentHashMap<String, Lock> sheetLocks, ExecutorService executorService) {
        this.sheetLocks = sheetLocks;
        this.executorService = executorService;
    }


    public String generateUniqueSheetName(Workbook workbook, String baseName) {
        String truncatedBaseName = truncateSheetName(baseName);
        Lock lock = sheetLocks.computeIfAbsent(truncatedBaseName, k -> new ReentrantLock());
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

    public CompletableFuture<SXSSFSheet> createOrReuseSheet(SXSSFWorkbook workbook, String sheetName) {
        return CompletableFuture.supplyAsync(() -> {
            Lock lock = sheetLocks.computeIfAbsent(sheetName, k -> new ReentrantLock());
            lock.lock();
            try {
                SXSSFSheet sheet = workbook.getSheet(sheetName);
                if (sheet != null) {
                    log.info("Sheet name already exists, reusing: {}", sheetName);
                    return sheet;
                } else {
                    log.info("Creating new sheet: {}", sheetName);
                    return workbook.createSheet(sheetName);
                }
            } finally {
                lock.unlock();
            }
        }, executorService);
    }

}