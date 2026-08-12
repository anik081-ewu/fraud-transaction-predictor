package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {
    List<ModelVersion> findByDatasetPartitionIdOrderByCreatedAtDescIdDesc(Long datasetPartitionId);

    List<ModelVersion> findByTrainingRunIdOrderByModelNameAsc(Long trainingRunId);

    List<ModelVersion> findByModelNameAndIsActiveTrue(String modelName);
}
