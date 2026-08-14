package com.ftd.fraud_transaction_detector.cases.repo;

import com.ftd.fraud_transaction_detector.cases.entity.CaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CaseRecordRepository extends JpaRepository<CaseRecord, Long>, JpaSpecificationExecutor<CaseRecord> {
    Optional<CaseRecord> findByFraudAlertId(Long fraudAlertId);
    Optional<CaseRecord> findFirstByTransactionIdOrderByCreatedAtDescIdDesc(String transactionId);

    @Query("select c.status, count(c) from CaseRecord c group by c.status")
    List<Object[]> countByStatus();
}
