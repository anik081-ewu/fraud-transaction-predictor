package com.ftd.fraud_transaction_detector.fraud.repo;

import com.ftd.fraud_transaction_detector.fraud.entity.FraudPredictionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudPredictionLogRepository extends JpaRepository<FraudPredictionLog, Long> {
    Optional<FraudPredictionLog> findFirstByTransactionIdOrderByCreatedAtDescIdDesc(String transactionId);
}
