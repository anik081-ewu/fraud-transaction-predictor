package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.DatasetPartition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetPartitionRepository extends JpaRepository<DatasetPartition, Long> {
    List<DatasetPartition> findByUploadedDatasetIdOrderByPartitionSizeAscIdAsc(Long uploadedDatasetId);

    boolean existsByUploadedDatasetIdAndPartitionSize(Long uploadedDatasetId, Integer partitionSize);
}
