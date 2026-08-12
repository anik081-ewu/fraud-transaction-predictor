package com.ftd.fraud_transaction_detector.casework;

import com.ftd.fraud_transaction_detector.cases.dto.CreateCaseRequest;
import com.ftd.fraud_transaction_detector.cases.dto.CaseActionRequest;
import com.ftd.fraud_transaction_detector.cases.dto.CaseResponse;
import com.ftd.fraud_transaction_detector.cases.service.CaseManagementService;
import org.springframework.stereotype.Service;

@Service
public class CaseCreationClient {
    private final CaseManagementService caseManagementService;

    public CaseCreationClient(CaseManagementService caseManagementService) {
        this.caseManagementService = caseManagementService;
    }

    public void createCase(AutomaticCaseRequested request) {
        CaseResponse createdCase = caseManagementService.createCase(new CreateCaseRequest(
                request.fraudAlertId(),
                request.transactionId(),
                request.accountId(),
                "Automatic anomaly case for transaction " + request.transactionId(),
                "HIGH".equalsIgnoreCase(request.riskLevel()) ? "HIGH" : "MEDIUM",
                null,
                "anomaly-engine"
        ));
        if ("HIGH".equalsIgnoreCase(request.riskLevel())
                && !"STR_GENERATED".equalsIgnoreCase(createdCase.status())) {
            caseManagementService.generateStrXml(
                    createdCase.id(),
                    new CaseActionRequest("anomaly-engine")
            );
        }
    }
}
