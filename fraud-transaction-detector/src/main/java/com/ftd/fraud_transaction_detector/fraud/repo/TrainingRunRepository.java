package com.ftd.fraud_transaction_detector.fraud.repo;

import com.ftd.fraud_transaction_detector.fraud.entity.TrainingRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingRunRepository extends JpaRepository<TrainingRun, Long> {
}
