package com.ftd.fraud_transaction_detector.cases.repo;

import com.ftd.fraud_transaction_detector.cases.entity.CaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaseRecordRepository extends JpaRepository<CaseRecord, Long> {
    List<CaseRecord> findAllByOrderByCreatedAtDescIdDesc();
    Optional<CaseRecord> findByFraudAlertId(Long fraudAlertId);
    Optional<CaseRecord> findFirstByTransactionIdOrderByCreatedAtDescIdDesc(String transactionId);
}
