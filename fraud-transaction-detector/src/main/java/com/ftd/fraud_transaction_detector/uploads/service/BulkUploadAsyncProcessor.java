package com.ftd.fraud_transaction_detector.uploads.service;

import com.opencsv.CSVReader;
import com.ftd.fraud_transaction_detector.aml.training.application.BusinessDayService;
import com.ftd.fraud_transaction_detector.comparison.service.UploadedDatasetService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.service.ModelTrainingService;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import com.ftd.fraud_transaction_detector.uploads.entity.BulkUploadBatch;
import com.ftd.fraud_transaction_detector.uploads.repo.BulkUploadBatchRepository;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BulkUploadResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BulkUploadAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(BulkUploadAsyncProcessor.class);

    private final BulkUploadBatchRepository batchRepository;
    private final TransactionRepository transactionRepository;
    private final UploadedDatasetService uploadedDatasetService;
    private final ModelTrainingService modelTrainingService;
    private final BusinessDayService businessDayService;
    private final TaskExecutor taskExecutor;
    private final Environment environment;
    private final AppConfigService appConfigService;

    public BulkUploadAsyncProcessor(
            BulkUploadBatchRepository batchRepository,
            TransactionRepository transactionRepository,
            UploadedDatasetService uploadedDatasetService,
            ModelTrainingService modelTrainingService,
            BusinessDayService businessDayService,
            TaskExecutor taskExecutor,
            Environment environment,
            AppConfigService appConfigService
    ) {
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.uploadedDatasetService = uploadedDatasetService;
        this.modelTrainingService = modelTrainingService;
        this.businessDayService = businessDayService;
        this.taskExecutor = taskExecutor;
        this.environment = environment;
        this.appConfigService = appConfigService;
    }

    @Transactional
    public void process(Long batchId, byte[] fileBytes, String filename, String uploadedBy) {
        BulkUploadBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch not found: " + batchId));
        batch.setStatus("PROCESSING");
        batchRepository.save(batch);

        try {
            List<BulkUploadResponse.RowError> errors = new ArrayList<>();
            ExcelBulkUploadService.BulkCounts counts = filename.toLowerCase(Locale.ROOT).endsWith(".csv")
                    ? processCsv(fileBytes, batch.getId(), errors)
                    : processExcel(fileBytes, batch.getId(), errors);

            batch.setStatus("COMPLETED");
            batch.setTotalRows(counts.total());
            batch.setSuccessRows(counts.success());
            batch.setFailedRows(counts.failed());
            batchRepository.save(batch);

            if (counts.success() > 0) {
                uploadedDatasetService.registerSuccessfulUpload(batch);
                autoSealBusinessDays(batch, uploadedBy);
            }

            log.info("Async upload done: batch={} total={} success={} failed={}",
                    batch.getBatchNo(), counts.total(), counts.success(), counts.failed());
        } catch (Exception ex) {
            // Catch without rethrowing so the transaction commits with FAILED status
            batch.setStatus("FAILED");
            batchRepository.save(batch);
            log.error("Async upload failed for batch {}: {}", batch.getBatchNo(), ex.getMessage(), ex);
            return;
        }

        // Auto-training runs in a separate thread after the transaction commits
        if ("COMPLETED".equals(batch.getStatus()) && batch.getSuccessRows() > 0) {
            boolean autoTrain = Boolean.parseBoolean(
                    environment.getProperty("fraud.ml.auto-train-after-upload", "false"));
            if (autoTrain) {
                taskExecutor.execute(() -> {
                    try {
                        log.info("Auto-training after upload (batch={}, rows={})",
                                batch.getBatchNo(), batch.getSuccessRows());
                        modelTrainingService.trainFromDatabase("bulk-upload");
                    } catch (Exception ex) {
                        log.error("Auto-train failed (ignored): {}", ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    private void autoSealBusinessDays(BulkUploadBatch batch, String sealedBy) {
        try {
            String minStr = transactionRepository.findMinBusinessDateByUploadBatchId(batch.getId());
            String maxStr = transactionRepository.findMaxBusinessDateByUploadBatchId(batch.getId());
            if (minStr == null || maxStr == null) return;
            LocalDate minDate = LocalDate.parse(minStr);
            LocalDate maxDate = LocalDate.parse(maxStr);
            int sealed = businessDayService.closeRange(minDate, maxDate, sealedBy);
            log.info("Auto-sealed {} business date(s) for batch {} ({} → {})",
                    sealed, batch.getBatchNo(), minDate, maxDate);
        } catch (Exception ex) {
            log.warn("Auto-seal of business dates failed for batch {} (non-fatal): {}",
                    batch.getBatchNo(), ex.getMessage(), ex);
        }
    }

    private ExcelBulkUploadService.BulkCounts processExcel(
            byte[] bytes, Long batchId, List<BulkUploadResponse.RowError> errors) throws Exception {
        int total = 0, success = 0, failed = 0;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("Excel must contain at least one sheet");
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Excel header row is missing");
            Map<String, Integer> headerIndex = ExcelBulkUploadService.buildHeaderIndex(headerRow);
            ExcelBulkUploadService.validateRequiredColumns(headerIndex);
            ExcelBulkUploadService.validateLearningModeColumns(headerIndex, appConfigService.getLearningMode());

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelBulkUploadService.isRowBlank(row)) continue;
                total++;
                try {
                    transactionRepository.save(ExcelBulkUploadService.parseTransaction(row, headerIndex, batchId));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errors.add(new BulkUploadResponse.RowError(r + 1,
                            ex.getMessage() == null ? "Invalid row" : ex.getMessage()));
                }
            }
        }
        return new ExcelBulkUploadService.BulkCounts(total, success, failed);
    }

    private ExcelBulkUploadService.BulkCounts processCsv(
            byte[] bytes, Long batchId, List<BulkUploadResponse.RowError> errors) throws Exception {
        int total = 0, success = 0, failed = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
             CSVReader reader = new CSVReader(br)) {
            String[] header = reader.readNext();
            if (header == null) throw new IllegalArgumentException("CSV header row is missing");
            Map<String, Integer> headerIndex = ExcelBulkUploadService.buildHeaderIndex(header);
            ExcelBulkUploadService.validateRequiredColumns(headerIndex);
            ExcelBulkUploadService.validateLearningModeColumns(headerIndex, appConfigService.getLearningMode());

            String[] row;
            int rowNumber = 1;
            while ((row = reader.readNext()) != null) {
                rowNumber++;
                if (ExcelBulkUploadService.isRowBlank(row)) continue;
                total++;
                try {
                    transactionRepository.save(ExcelBulkUploadService.parseTransaction(row, headerIndex, batchId));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errors.add(new BulkUploadResponse.RowError(rowNumber,
                            ex.getMessage() == null ? "Invalid row" : ex.getMessage()));
                }
            }
        }
        return new ExcelBulkUploadService.BulkCounts(total, success, failed);
    }
}
