package com.ftd.fraud_transaction_detector.casework;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AutomaticCaseCreationListener {

    private final CaseCreationClient caseCreationClient;

    public AutomaticCaseCreationListener(CaseCreationClient caseCreationClient) {
        this.caseCreationClient = caseCreationClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void createCase(AutomaticCaseRequested request) {
        caseCreationClient.createCase(request);
    }
}
