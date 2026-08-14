package com.ftd.fraud_transaction_detector.cases.service;

import com.ftd.fraud_transaction_detector.cases.dto.*;
import com.ftd.fraud_transaction_detector.cases.entity.CaseNote;
import com.ftd.fraud_transaction_detector.cases.entity.CaseRecord;
import com.ftd.fraud_transaction_detector.cases.repo.CaseNoteRepository;
import com.ftd.fraud_transaction_detector.cases.repo.CaseRecordRepository;
import com.ftd.fraud_transaction_detector.fraud.service.FraudAlertReviewService;
import com.ftd.fraud_transaction_detector.fraud.entity.FraudPredictionLog;
import com.ftd.fraud_transaction_detector.fraud.repo.FraudPredictionLogRepository;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CaseManagementService {
    private final CaseRecordRepository caseRecordRepository;
    private final CaseNoteRepository caseNoteRepository;
    private final TransactionRepository transactionRepository;
    private final FraudAlertReviewService fraudAlertReviewService;
    private final StrXmlService strXmlService;
    private final FraudPredictionLogRepository predictionLogRepository;

    public CaseManagementService(CaseRecordRepository caseRecordRepository, CaseNoteRepository caseNoteRepository,
                                 TransactionRepository transactionRepository,
                                 FraudAlertReviewService fraudAlertReviewService, StrXmlService strXmlService,
                                 FraudPredictionLogRepository predictionLogRepository) {
        this.caseRecordRepository = caseRecordRepository;
        this.caseNoteRepository = caseNoteRepository;
        this.transactionRepository = transactionRepository;
        this.fraudAlertReviewService = fraudAlertReviewService;
        this.strXmlService = strXmlService;
        this.predictionLogRepository = predictionLogRepository;
    }

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        validateCreate(request);
        CaseRecord existing = request.fraudAlertId() == null
                ? caseRecordRepository.findFirstByTransactionIdOrderByCreatedAtDescIdDesc(request.transactionId().trim()).orElse(null)
                : caseRecordRepository.findByFraudAlertId(request.fraudAlertId()).orElse(null);
        if (existing != null) return toResponse(existing);

        Instant now = Instant.now();
        CaseRecord record = new CaseRecord();
        record.setCaseNo("CASE-" + now.toEpochMilli());
        record.setFraudAlertId(request.fraudAlertId());
        record.setTransactionId(request.transactionId().trim());
        record.setAccountId(request.accountId().trim());
        record.setTitle(request.title().trim());
        record.setStatus("OPEN");
        record.setPriority(defaultPriority(request.priority()));
        record.setAssignedTo(blankToNull(request.assignedTo()));
        record.setCreatedBy(blankToDefault(request.createdBy(), "system"));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return toResponse(caseRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public CasePageResponse listCases(String query, String status, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")
        ));
        var casePage = caseRecordRepository.findAll(specification(query, status), pageable);
        Map<String, Long> counts = caseRecordRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> ((Number) row[1]).longValue()
                ));
        long resolved = counts.getOrDefault("FALSE_POSITIVE", 0L)
                + counts.getOrDefault("STR_GENERATED", 0L)
                + counts.getOrDefault("CLOSED", 0L);
        long allCases = counts.values().stream().mapToLong(Long::longValue).sum();
        return new CasePageResponse(
                casePage.getContent().stream().map(this::toSummary).toList(),
                casePage.getTotalElements(), casePage.getTotalPages(), casePage.getNumber(), casePage.getSize(),
                allCases,
                Math.max(0, allCases - resolved),
                counts.getOrDefault("STR_GENERATED", 0L),
                counts.getOrDefault("FALSE_POSITIVE", 0L)
        );
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(Long caseId) { return toResponse(getCaseRecord(caseId)); }

    @Transactional
    public CaseResponse updateStatus(Long caseId, UpdateCaseStatusRequest request) {
        CaseRecord record = getCaseRecord(caseId);
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Case status is required");
        }
        record.setStatus(request.status().trim().toUpperCase());
        record.setAssignedTo(blankToNull(request.assignedTo()));
        record.setUpdatedAt(Instant.now());
        return toResponse(caseRecordRepository.save(record));
    }

    @Transactional
    public CaseResponse addNote(Long caseId, AddCaseNoteRequest request) {
        CaseRecord record = getCaseRecord(caseId);
        if (request == null || request.noteText() == null || request.noteText().isBlank()) {
            throw new IllegalArgumentException("Case note is required");
        }
        addSystemNote(record, request.noteText().trim(), blankToDefault(request.createdBy(), "system"));
        record.setUpdatedAt(Instant.now());
        caseRecordRepository.save(record);
        return toResponse(record);
    }

    @Transactional
    public CaseResponse markFalsePositive(Long caseId, CaseActionRequest request) {
        CaseRecord record = getCaseRecord(caseId);
        if ("STR_GENERATED".equals(record.getStatus())) {
            throw new IllegalStateException("A case with generated STR cannot be marked false positive");
        }
        String performedBy = defaultUser(request == null ? null : request.performedBy());
        if (record.getFraudAlertId() != null) {
            fraudAlertReviewService.markFalsePositive(record.getFraudAlertId(), performedBy);
        }
        record.setStatus("FALSE_POSITIVE");
        record.setUpdatedAt(Instant.now());
        caseRecordRepository.save(record);
        addSystemNote(record, "Marked as false positive.", performedBy);
        return toResponse(record);
    }

    @Transactional
    public GeneratedStrXml generateStrXml(Long caseId, CaseActionRequest request) {
        CaseRecord record = getCaseRecord(caseId);
        if ("FALSE_POSITIVE".equals(record.getStatus())) {
            throw new IllegalStateException("A false-positive case cannot generate an STR");
        }
        String performedBy = defaultUser(request == null ? null : request.performedBy());
        Transaction transaction = transactionRepository.findByTransactionId(record.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + record.getTransactionId()));
        String fileName = "STR-" + record.getCaseNo() + ".xml";
        byte[] content = strXmlService.generate(record, transaction, performedBy);
        if (record.getFraudAlertId() != null) {
            fraudAlertReviewService.markStrGenerated(record.getFraudAlertId(), performedBy, fileName);
        }
        record.setStatus("STR_GENERATED");
        record.setUpdatedAt(Instant.now());
        caseRecordRepository.save(record);
        addSystemNote(record, "Generated draft STR XML file " + fileName + ".", performedBy);
        return new GeneratedStrXml(fileName, content);
    }

    private void validateCreate(CreateCaseRequest request) {
        if (request == null) throw new IllegalArgumentException("Create case request is required");
        if (request.transactionId() == null || request.transactionId().isBlank()) throw new IllegalArgumentException("transactionId is required");
        if (request.accountId() == null || request.accountId().isBlank()) throw new IllegalArgumentException("accountId is required");
        if (request.title() == null || request.title().isBlank()) throw new IllegalArgumentException("title is required");
    }

    private CaseRecord getCaseRecord(Long caseId) {
        return caseRecordRepository.findById(caseId).orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
    }

    private void addSystemNote(CaseRecord record, String text, String createdBy) {
        CaseNote note = new CaseNote();
        note.setCaseRecord(record);
        note.setNoteText(text);
        note.setCreatedBy(createdBy);
        note.setCreatedAt(Instant.now());
        caseNoteRepository.save(note);
    }

    private CaseResponse toResponse(CaseRecord record) {
        List<CaseNoteResponse> notes = caseNoteRepository.findByCaseRecordIdOrderByCreatedAtAscIdAsc(record.getId()).stream()
                .map(note -> new CaseNoteResponse(note.getId(), note.getNoteText(), note.getCreatedBy(), note.getCreatedAt())).toList();
        CasePredictionEvidenceResponse evidence = predictionLogRepository
                .findFirstByTransactionIdOrderByCreatedAtDescIdDesc(record.getTransactionId())
                .map(this::toEvidence)
                .orElse(null);
        return new CaseResponse(record.getId(), record.getCaseNo(), record.getFraudAlertId(), record.getTransactionId(),
                record.getAccountId(), record.getTitle(), record.getStatus(), record.getPriority(), record.getAssignedTo(),
                record.getCreatedBy(), record.getCreatedAt(), record.getUpdatedAt(), notes, evidence);
    }

    private CaseSummaryResponse toSummary(CaseRecord record) {
        return new CaseSummaryResponse(
                record.getId(), record.getCaseNo(), record.getFraudAlertId(), record.getTransactionId(),
                record.getAccountId(), record.getTitle(), record.getStatus(), record.getPriority(),
                record.getAssignedTo(), record.getCreatedBy(), record.getCreatedAt(), record.getUpdatedAt()
        );
    }

    private Specification<CaseRecord> specification(String query, String status) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            String normalizedStatus = status == null ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(normalizedStatus)) {
                predicates.add(builder.not(root.get("status").in("FALSE_POSITIVE", "STR_GENERATED", "CLOSED")));
            } else if (!"ALL".equals(normalizedStatus)) {
                predicates.add(builder.equal(root.get("status"), normalizedStatus));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("caseNo")), pattern),
                        builder.like(builder.lower(root.get("transactionId")), pattern),
                        builder.like(builder.lower(root.get("accountId")), pattern),
                        builder.like(builder.lower(root.get("title")), pattern)
                ));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private CasePredictionEvidenceResponse toEvidence(FraudPredictionLog log) {
        return new CasePredictionEvidenceResponse(
                log.getRiskLevel(), log.getAnomalyVotes(), log.getSuspiciousFlag(), log.getModelVersion(),
                log.getFeatureVersion(), log.getIncrementalModelScore(), log.getBatchModelScore(),
                log.getReasonCodes(), log.getLearningDecision(), log.getLearningDecisionReason(), log.getCreatedAt()
        );
    }

    private static String defaultPriority(String value) { return value == null || value.isBlank() ? "MEDIUM" : value.trim().toUpperCase(); }
    private static String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String defaultUser(String value) { return blankToDefault(value, "system"); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
