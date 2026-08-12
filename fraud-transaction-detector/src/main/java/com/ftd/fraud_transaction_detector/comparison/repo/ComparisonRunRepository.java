package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComparisonRunRepository extends JpaRepository<ComparisonRun, Long> {
    List<ComparisonRun> findAllByOrderByCreatedAtDescIdDesc();
}
