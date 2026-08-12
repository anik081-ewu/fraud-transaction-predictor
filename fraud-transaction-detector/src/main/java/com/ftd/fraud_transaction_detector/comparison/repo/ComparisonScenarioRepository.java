package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonScenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComparisonScenarioRepository extends JpaRepository<ComparisonScenario, Long> {
    List<ComparisonScenario> findByScenarioSetIdOrderByCreatedAtAscIdAsc(Long scenarioSetId);
}
