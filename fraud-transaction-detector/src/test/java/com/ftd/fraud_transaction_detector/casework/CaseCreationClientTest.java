package com.ftd.fraud_transaction_detector.casework;

import com.ftd.fraud_transaction_detector.cases.dto.CaseActionRequest;
import com.ftd.fraud_transaction_detector.cases.dto.CaseResponse;
import com.ftd.fraud_transaction_detector.cases.dto.CreateCaseRequest;
import com.ftd.fraud_transaction_detector.cases.service.CaseManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseCreationClientTest {

    @Mock
    private CaseManagementService caseManagementService;

    @Test
    void mediumRiskCreatesCaseWithoutGeneratingStr() {
        when(caseManagementService.createCase(any())).thenReturn(caseResponse("OPEN"));
        CaseCreationClient client = new CaseCreationClient(caseManagementService);

        client.createCase(request("MEDIUM"));

        ArgumentCaptor<CreateCaseRequest> requestCaptor = ArgumentCaptor.forClass(CreateCaseRequest.class);
        verify(caseManagementService).createCase(requestCaptor.capture());
        assertEquals("MEDIUM", requestCaptor.getValue().priority());
        verify(caseManagementService, never()).generateStrXml(any(), any());
    }

    @Test
    void highRiskCreatesCaseAndGeneratesStr() {
        when(caseManagementService.createCase(any())).thenReturn(caseResponse("OPEN"));
        CaseCreationClient client = new CaseCreationClient(caseManagementService);

        client.createCase(request("HIGH"));

        ArgumentCaptor<CreateCaseRequest> createCaptor = ArgumentCaptor.forClass(CreateCaseRequest.class);
        ArgumentCaptor<CaseActionRequest> actionCaptor = ArgumentCaptor.forClass(CaseActionRequest.class);
        verify(caseManagementService).createCase(createCaptor.capture());
        verify(caseManagementService).generateStrXml(org.mockito.ArgumentMatchers.eq(12L), actionCaptor.capture());
        assertEquals("HIGH", createCaptor.getValue().priority());
        assertEquals("anomaly-engine", actionCaptor.getValue().performedBy());
    }

    @Test
    void repeatedHighRiskEventDoesNotRegenerateExistingStr() {
        when(caseManagementService.createCase(any())).thenReturn(caseResponse("STR_GENERATED"));
        CaseCreationClient client = new CaseCreationClient(caseManagementService);

        client.createCase(request("HIGH"));

        verify(caseManagementService, never()).generateStrXml(any(), any());
    }

    private AutomaticCaseRequested request(String riskLevel) {
        return new AutomaticCaseRequested(4L, "TXN-1", "AC-1", riskLevel, 3);
    }

    private CaseResponse caseResponse(String status) {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        return new CaseResponse(
                12L, "CASE-12", 4L, "TXN-1", "AC-1", "Automatic case",
                status, "HIGH", null, "anomaly-engine", now, now, List.of(), null
        );
    }
}
