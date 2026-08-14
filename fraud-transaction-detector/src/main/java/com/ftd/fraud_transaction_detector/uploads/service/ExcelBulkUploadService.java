package com.ftd.fraud_transaction_detector.uploads.service;

import com.opencsv.CSVReader;
import com.ftd.fraud_transaction_detector.comparison.dto.UploadedDatasetResponse;
import com.ftd.fraud_transaction_detector.comparison.service.UploadedDatasetService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.service.ModelTrainingService;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import com.ftd.fraud_transaction_detector.uploads.entity.BulkUploadBatch;
import com.ftd.fraud_transaction_detector.uploads.repo.BulkUploadBatchRepository;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BatchSummaryResponse;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BulkUploadResponse;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BulkUploadStartResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ExcelBulkUploadService {

    private static final Logger log = LoggerFactory.getLogger(ExcelBulkUploadService.class);

    static final List<String> REQUIRED_COLUMNS = List.of(
            "transaction_id",
            "account_id",
            "transaction_amount",
            "transaction_type",
            "transaction_date",
            "location",
            "channel",
            "login_attempts",
            "account_balance"
    );

    private final BulkUploadBatchRepository batchRepository;
    private final TransactionRepository transactionRepository;
    private final UploadedDatasetService uploadedDatasetService;
    private final ModelTrainingService modelTrainingService;
    private final TaskExecutor taskExecutor;
    private final Environment environment;
    private final BulkUploadAsyncProcessor asyncProcessor;
    private final AppConfigService appConfigService;

    public ExcelBulkUploadService(
            BulkUploadBatchRepository batchRepository,
            TransactionRepository transactionRepository,
            UploadedDatasetService uploadedDatasetService,
            ModelTrainingService modelTrainingService,
            TaskExecutor taskExecutor,
            Environment environment,
            BulkUploadAsyncProcessor asyncProcessor,
            AppConfigService appConfigService
    ) {
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.uploadedDatasetService = uploadedDatasetService;
        this.modelTrainingService = modelTrainingService;
        this.taskExecutor = taskExecutor;
        this.environment = environment;
        this.asyncProcessor = asyncProcessor;
        this.appConfigService = appConfigService;
    }

    public BulkUploadStartResponse startUpload(MultipartFile file, String uploadedBy) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

        BulkUploadBatch batch = new BulkUploadBatch();
        batch.setBatchNo("BATCH-" + Instant.now().toEpochMilli());
        batch.setFileName(filename);
        batch.setUploadedBy(uploadedBy);
        batch.setUploadedAt(Instant.now());
        batch.setStatus("QUEUED");
        batch.setTotalRows(0);
        batch.setSuccessRows(0);
        batch.setFailedRows(0);
        batchRepository.saveAndFlush(batch);

        Long batchId = batch.getId();
        taskExecutor.execute(() -> asyncProcessor.process(batchId, fileBytes, filename, uploadedBy));

        return new BulkUploadStartResponse(batch.getBatchNo(), "QUEUED");
    }

    public BatchSummaryResponse getBatchSummary(String batchNo) {
        BulkUploadBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchNo));
        return mapToSummary(batch);
    }

    public BatchSummaryResponse getLatestCompletedBatch() {
        BulkUploadBatch batch = batchRepository.findFirstByStatusOrderByUploadedAtDesc("COMPLETED")
                .orElseThrow(() -> new IllegalStateException("No completed batch found"));
        return mapToSummary(batch);
    }

    private BatchSummaryResponse mapToSummary(BulkUploadBatch batch) {
        LocalDate minDate = parseDate(transactionRepository.findMinBusinessDateByUploadBatchId(batch.getId()));
        LocalDate maxDate = parseDate(transactionRepository.findMaxBusinessDateByUploadBatchId(batch.getId()));
        return new BatchSummaryResponse(
                batch.getBatchNo(), batch.getFileName(), batch.getTotalRows(),
                batch.getSuccessRows(), batch.getFailedRows(), batch.getUploadedBy(),
                batch.getUploadedAt(), batch.getStatus(), minDate, maxDate
        );
    }

    private static LocalDate parseDate(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }

    @Transactional
    public BulkUploadResponse upload(MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        String originalName = file.getOriginalFilename() == null ? "transactions.xlsx" : file.getOriginalFilename();

        String batchNo = "BATCH-" + Instant.now().toEpochMilli();
        BulkUploadBatch batch = new BulkUploadBatch();
        batch.setBatchNo(batchNo);
        batch.setFileName(originalName);
        batch.setUploadedBy(uploadedBy);
        batch.setUploadedAt(Instant.now());
        batch.setStatus("PROCESSING");
        batch.setTotalRows(0);
        batch.setSuccessRows(0);
        batch.setFailedRows(0);
        batchRepository.save(batch);

        List<BulkUploadResponse.RowError> errors = new ArrayList<>();
        int total = 0;
        int success = 0;
        int failed = 0;

        try {
            String lower = originalName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".csv")) {
                BulkCounts counts = processCsv(file, batch.getId(), errors);
                total = counts.total;
                success = counts.success;
                failed = counts.failed;
            } else {
                BulkCounts counts = processExcel(file, batch.getId(), errors);
                total = counts.total;
                success = counts.success;
                failed = counts.failed;
            }
        } catch (IllegalArgumentException ex) {
            batch.setStatus("FAILED");
            batch.setTotalRows(total);
            batch.setSuccessRows(success);
            batch.setFailedRows(failed);
            batchRepository.save(batch);
            throw ex;
        } catch (Exception ex) {
            batch.setStatus("FAILED");
            batch.setTotalRows(total);
            batch.setSuccessRows(success);
            batch.setFailedRows(failed);
            batchRepository.save(batch);
            throw new IllegalArgumentException("Failed to process Excel: " + ex.getMessage());
        }

        batch.setStatus("COMPLETED");
        batch.setTotalRows(total);
        batch.setSuccessRows(success);
        batch.setFailedRows(failed);
        batchRepository.save(batch);

        UploadedDatasetResponse uploadedDataset = null;
        if (success > 0) {
            uploadedDataset = uploadedDatasetService.registerSuccessfulUpload(batch);
        }

        triggerBaselineTrainingIfEnabled(success);

        return new BulkUploadResponse(
                batchNo,
                originalName,
                total,
                success,
                failed,
                errors,
                uploadedDataset == null ? null : uploadedDataset.id(),
                uploadedDataset == null ? null : uploadedDataset.datasetNo()
        );
    }

    private void triggerBaselineTrainingIfEnabled(int successRows) {
        if (successRows <= 0) return;
        boolean enabled = Boolean.parseBoolean(environment.getProperty("fraud.ml.auto-train-after-upload", "false"));
        if (!enabled) return;
        taskExecutor.execute(() -> {
            try {
                log.info("Auto-training ML models after bulk upload (successRows={})", successRows);
                modelTrainingService.trainFromDatabase("bulk-upload");
                log.info("Auto-training ML models completed");
            } catch (Exception ex) {
                log.error("Auto-training ML models failed (ignored): {}", ex.getMessage(), ex);
            }
        });
    }

    record BulkCounts(int total, int success, int failed) {
    }

    private BulkCounts processExcel(MultipartFile file, Long batchId, List<BulkUploadResponse.RowError> errors) throws Exception {
        int total = 0, success = 0, failed = 0;
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Excel must contain at least one sheet");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel header row is missing");
            }
            Map<String, Integer> headerIndex = buildHeaderIndex(headerRow);
            validateRequiredColumns(headerIndex);
            validateLearningModeColumns(headerIndex, appConfigService.getLearningMode());

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) {
                    continue;
                }
                total++;
                int rowNumber = r + 1;
                try {
                    Transaction txn = parseTransaction(row, headerIndex, batchId);
                    transactionRepository.save(txn);
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errors.add(new BulkUploadResponse.RowError(rowNumber, ex.getMessage() == null ? "Invalid row" : ex.getMessage()));
                }
            }
        }
        return new BulkCounts(total, success, failed);
    }

    private BulkCounts processCsv(MultipartFile file, Long batchId, List<BulkUploadResponse.RowError> errors) throws Exception {
        int total = 0, success = 0, failed = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVReader reader = new CSVReader(br)) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalArgumentException("CSV header row is missing");
            }
            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            validateRequiredColumns(headerIndex);
            validateLearningModeColumns(headerIndex, appConfigService.getLearningMode());

            String[] row;
            int rowNumber = 1;
            while ((row = reader.readNext()) != null) {
                rowNumber++;
                if (isRowBlank(row)) continue;
                total++;
                try {
                    Transaction txn = parseTransaction(row, headerIndex, batchId);
                    transactionRepository.save(txn);
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errors.add(new BulkUploadResponse.RowError(rowNumber, ex.getMessage() == null ? "Invalid row" : ex.getMessage()));
                }
            }
        }
        return new BulkCounts(total, success, failed);
    }

    static void validateRequiredColumns(Map<String, Integer> headerIndex) {
        for (String required : REQUIRED_COLUMNS) {
            if (!headerIndex.containsKey(required)) {
                throw new IllegalArgumentException("Missing required column: " + required);
            }
        }
    }

    static void validateLearningModeColumns(Map<String, Integer> headerIndex, String learningMode) {
        if ("SUPERVISED".equalsIgnoreCase(learningMode) && !headerIndex.containsKey("fraud_label")) {
            throw new IllegalArgumentException(
                    "Supervised Learning requires a FraudLabel column containing 1, 0, or blank values"
            );
        }
    }

    static Map<String, Integer> buildHeaderIndex(Row headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (Cell cell : headerRow) {
            String v = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
            if (v == null) continue;
            String key = normalizeHeader(v);
            if (!key.isBlank()) index.put(key, cell.getColumnIndex());
        }
        return index;
    }

    static Map<String, Integer> buildHeaderIndex(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = normalizeHeader(header[i]);
            if (!key.isBlank()) index.put(key, i);
        }
        return index;
    }

    static String normalizeHeader(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        // Insert underscores for CamelCase transitions: TransactionID -> Transaction_ID
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        s = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace(' ', '_');
        // Remove non-alphanumeric/underscore and collapse underscores
        s = s.replaceAll("[^a-z0-9_]", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s;
    }

    static boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean isRowBlank(String[] row) {
        for (String s : row) {
            if (s != null && !s.trim().isEmpty()) return false;
        }
        return true;
    }

    static Transaction parseTransaction(Row row, Map<String, Integer> headerIndex, Long uploadBatchId) {
        Transaction txn = new Transaction();
        txn.setTransactionId(readRequiredString(row, headerIndex, "transaction_id"));
        txn.setAccountId(readRequiredString(row, headerIndex, "account_id"));
        txn.setTransactionType(readRequiredString(row, headerIndex, "transaction_type"));
        txn.setLocation(readRequiredString(row, headerIndex, "location"));
        txn.setChannel(readRequiredString(row, headerIndex, "channel"));

        txn.setTransactionAmount(readRequiredBigDecimal(row, headerIndex, "transaction_amount"));
        txn.setLoginAttempts(readIntOrDefault(row, headerIndex, "login_attempts", 0));
        txn.setAccountBalance(readBigDecimalOrDefault(row, headerIndex, "account_balance", BigDecimal.ZERO));

        txn.setCustomerAge(readOptionalInt(row, headerIndex, "customer_age"));
        txn.setCustomerOccupation(readOptionalString(row, headerIndex, "customer_occupation"));

        txn.setTransactionDate(readRequiredDateTime(row, headerIndex, "transaction_date"));
        txn.setCustomerId(txn.getAccountId());
        txn.setBusinessDate(txn.getTransactionDate().toLocalDate());
        txn.setSourceType("BULK_UPLOAD");
        txn.setUploadBatchId(uploadBatchId);
        txn.setCreatedAt(Instant.now());
        applyFraudLabel(txn, readOptionalInt(row, headerIndex, "fraud_label"));
        return txn;
    }

    static Transaction parseTransaction(String[] row, Map<String, Integer> headerIndex, Long uploadBatchId) {
        Transaction txn = new Transaction();
        txn.setTransactionId(readRequiredString(row, headerIndex, "transaction_id"));
        txn.setAccountId(readRequiredString(row, headerIndex, "account_id"));
        txn.setTransactionType(readRequiredString(row, headerIndex, "transaction_type"));
        txn.setLocation(readRequiredString(row, headerIndex, "location"));
        txn.setChannel(readRequiredString(row, headerIndex, "channel"));

        txn.setTransactionAmount(readRequiredBigDecimal(row, headerIndex, "transaction_amount"));
        txn.setLoginAttempts(readIntOrDefault(row, headerIndex, "login_attempts", 0));
        txn.setAccountBalance(readBigDecimalOrDefault(row, headerIndex, "account_balance", BigDecimal.ZERO));

        txn.setCustomerAge(readOptionalInt(row, headerIndex, "customer_age"));
        txn.setCustomerOccupation(readOptionalString(row, headerIndex, "customer_occupation"));

        txn.setTransactionDate(readRequiredDateTime(row, headerIndex, "transaction_date"));
        txn.setCustomerId(txn.getAccountId());
        txn.setBusinessDate(txn.getTransactionDate().toLocalDate());
        txn.setSourceType("BULK_UPLOAD");
        txn.setUploadBatchId(uploadBatchId);
        txn.setCreatedAt(Instant.now());
        applyFraudLabel(txn, readOptionalInt(row, headerIndex, "fraud_label"));
        return txn;
    }

    private static void applyFraudLabel(Transaction transaction, Integer label) {
        if (label == null) return;
        if (label != 0 && label != 1) {
            throw new IllegalArgumentException("fraud_label must be 0, 1, or blank");
        }
        transaction.setFraudLabel(label == 1);
        transaction.setLabelSource("IMPORTED_DATASET");
        transaction.setLabeledBy("bulk-upload");
        transaction.setLabeledAt(Instant.now());
    }

    static String readRequiredString(Row row, Map<String, Integer> headerIndex, String col) {
        String v = readOptionalString(row, headerIndex, col);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static String readOptionalString(Row row, Map<String, Integer> headerIndex, String col) {
        Integer idx = headerIndex.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        return cell.toString().trim();
    }

    static String readRequiredString(String[] row, Map<String, Integer> headerIndex, String col) {
        String v = readOptionalString(row, headerIndex, col);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static String readOptionalString(String[] row, Map<String, Integer> headerIndex, String col) {
        Integer idx = headerIndex.get(col);
        if (idx == null || idx < 0 || idx >= row.length) return null;
        String v = row[idx];
        return v == null ? null : v.trim();
    }

    static Integer readRequiredInt(Row row, Map<String, Integer> headerIndex, String col) {
        Integer v = readOptionalInt(row, headerIndex, col);
        if (v == null) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static Integer readOptionalInt(Row row, Map<String, Integer> headerIndex, String col) {
        Integer idx = headerIndex.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
            String s = cell.toString().trim();
            if (s.isBlank()) return null;
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(col + " must be an integer");
        }
    }

    static Integer readRequiredInt(String[] row, Map<String, Integer> headerIndex, String col) {
        Integer v = readOptionalInt(row, headerIndex, col);
        if (v == null) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static Integer readOptionalInt(String[] row, Map<String, Integer> headerIndex, String col) {
        String s = readOptionalString(row, headerIndex, col);
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(col + " must be an integer");
        }
    }

    static BigDecimal readRequiredBigDecimal(Row row, Map<String, Integer> headerIndex, String col) {
        BigDecimal v = readOptionalBigDecimal(row, headerIndex, col);
        if (v == null) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static BigDecimal readOptionalBigDecimal(Row row, Map<String, Integer> headerIndex, String col) {
        Integer idx = headerIndex.get(col);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            String s = cell.toString().trim();
            if (s.isBlank()) return null;
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(col + " must be a number");
        }
    }

    static BigDecimal readRequiredBigDecimal(String[] row, Map<String, Integer> headerIndex, String col) {
        BigDecimal v = readOptionalBigDecimal(row, headerIndex, col);
        if (v == null) throw new IllegalArgumentException(col + " is required");
        return v;
    }

    static Integer readIntOrDefault(Row row, Map<String, Integer> headerIndex, String col, int defaultValue) {
        Integer v = readOptionalInt(row, headerIndex, col);
        return v == null ? defaultValue : v;
    }

    static Integer readIntOrDefault(String[] row, Map<String, Integer> headerIndex, String col, int defaultValue) {
        Integer v = readOptionalInt(row, headerIndex, col);
        return v == null ? defaultValue : v;
    }

    static BigDecimal readBigDecimalOrDefault(Row row, Map<String, Integer> headerIndex, String col, BigDecimal defaultValue) {
        BigDecimal v = readOptionalBigDecimal(row, headerIndex, col);
        return v == null ? defaultValue : v;
    }

    static BigDecimal readBigDecimalOrDefault(String[] row, Map<String, Integer> headerIndex, String col, BigDecimal defaultValue) {
        BigDecimal v = readOptionalBigDecimal(row, headerIndex, col);
        return v == null ? defaultValue : v;
    }

    static BigDecimal readOptionalBigDecimal(String[] row, Map<String, Integer> headerIndex, String col) {
        String s = readOptionalString(row, headerIndex, col);
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(col + " must be a number");
        }
    }

    static LocalDateTime readRequiredDateTime(Row row, Map<String, Integer> headerIndex, String col) {
        Integer idx = headerIndex.get(col);
        if (idx == null) throw new IllegalArgumentException(col + " is required");
        Cell cell = row.getCell(idx);
        if (cell == null) throw new IllegalArgumentException(col + " is required");

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return LocalDateTime.ofInstant(cell.getDateCellValue().toInstant(), ZoneId.systemDefault());
        }
        String raw = cell.toString().trim();
        if (raw.isBlank()) throw new IllegalArgumentException(col + " is required");
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(col + " must be ISO datetime like 2026-05-05T15:30:00");
        }
    }

    static LocalDateTime readRequiredDateTime(String[] row, Map<String, Integer> headerIndex, String col) {
        String raw = readOptionalString(row, headerIndex, col);
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(col + " is required");
        // Accept ISO-8601 (preferred) and common US-style "M/d/yyyy H:mm"
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy H:mm"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(col + " must be ISO datetime like 2026-05-05T15:30:00");
        }
    }
}
