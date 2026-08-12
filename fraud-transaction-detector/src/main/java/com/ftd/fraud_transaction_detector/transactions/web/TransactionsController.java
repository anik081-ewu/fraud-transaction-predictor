package com.ftd.fraud_transaction_detector.transactions.web;

import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.service.TransactionCreateService;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import com.ftd.fraud_transaction_detector.transactions.web.dto.CreateTransactionRequest;
import com.ftd.fraud_transaction_detector.transactions.web.dto.CreateTransactionResponse;
import com.ftd.fraud_transaction_detector.uploads.repo.BulkUploadBatchRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionsController {

    private final TransactionRepository transactionRepository;
    private final BulkUploadBatchRepository batchRepository;
    private final TransactionCreateService transactionCreateService;

    public TransactionsController(
            TransactionRepository transactionRepository,
            BulkUploadBatchRepository batchRepository,
            TransactionCreateService transactionCreateService
    ) {
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.transactionCreateService = transactionCreateService;
    }

    @PostMapping
    public CreateTransactionResponse createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return transactionCreateService.create(request);
    }

    @GetMapping
    public Page<Transaction> listTransactions(
            @RequestParam(value = "batchNo", required = false) String batchNo,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        if (query != null && !query.isBlank()) {
            String normalizedQuery = query.trim();
            return transactionRepository.findByTransactionIdContainingIgnoreCaseOrAccountIdContainingIgnoreCase(
                    normalizedQuery,
                    normalizedQuery,
                    pageable
            );
        }
        if (batchNo == null || batchNo.isBlank()) {
            return transactionRepository.findAll(pageable);
        }
        Long batchId = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchNo))
                .getId();
        return transactionRepository.findByUploadBatchId(batchId, pageable);
    }

    @GetMapping("/{transactionId}")
    public Transaction getTransaction(@PathVariable String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }
}
