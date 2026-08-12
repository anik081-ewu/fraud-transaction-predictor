package com.ftd.fraud_transaction_detector.fraud.repo;

import com.ftd.fraud_transaction_detector.fraud.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {
}

