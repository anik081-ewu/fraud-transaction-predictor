package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.UploadedDataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedDatasetRepository extends JpaRepository<UploadedDataset, Long> {
    Optional<UploadedDataset> findBySourceBatchId(Long sourceBatchId);

    List<UploadedDataset> findAllByOrderByUploadedAtDescIdDesc();
}
