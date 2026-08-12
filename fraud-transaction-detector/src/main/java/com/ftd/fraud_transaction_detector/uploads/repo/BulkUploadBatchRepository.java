package com.ftd.fraud_transaction_detector.uploads.repo;

import com.ftd.fraud_transaction_detector.uploads.entity.BulkUploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BulkUploadBatchRepository extends JpaRepository<BulkUploadBatch, Long> {
    Optional<BulkUploadBatch> findByBatchNo(String batchNo);
    Optional<BulkUploadBatch> findFirstByStatusOrderByUploadedAtDesc(String status);
}

