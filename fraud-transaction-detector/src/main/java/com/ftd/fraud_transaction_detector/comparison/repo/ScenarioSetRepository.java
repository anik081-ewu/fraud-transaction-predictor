package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.ScenarioSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioSetRepository extends JpaRepository<ScenarioSet, Long> {
    List<ScenarioSet> findAllByOrderByCreatedAtDescIdDesc();
}
