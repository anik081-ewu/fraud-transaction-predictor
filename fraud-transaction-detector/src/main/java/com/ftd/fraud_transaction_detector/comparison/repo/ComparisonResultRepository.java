package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComparisonResultRepository extends JpaRepository<ComparisonResult, Long> {
    List<ComparisonResult> findByComparisonRunIdOrderByDatasetPartitionIdAscScenarioIdAscModelNameAsc(Long comparisonRunId);
}
