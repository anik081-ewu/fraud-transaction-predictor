package com.ftd.fraud_transaction_detector.cases.repo;

import com.ftd.fraud_transaction_detector.cases.entity.CaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {
    List<CaseNote> findByCaseRecordIdOrderByCreatedAtAscIdAsc(Long caseRecordId);
}
