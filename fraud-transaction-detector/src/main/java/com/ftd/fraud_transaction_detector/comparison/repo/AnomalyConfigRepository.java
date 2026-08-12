package com.ftd.fraud_transaction_detector.comparison.repo;

import com.ftd.fraud_transaction_detector.comparison.entity.AnomalyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnomalyConfigRepository extends JpaRepository<AnomalyConfig, Long> {
    List<AnomalyConfig> findAllByOrderByCreatedAtDescIdDesc();

    Optional<AnomalyConfig> findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc();
}
