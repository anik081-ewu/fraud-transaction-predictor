package com.ftd.fraud_transaction_detector.aml.validation.domain;

import java.time.Instant;
import java.util.UUID;

public record SyntheticScenarioLabel(
        UUID scenarioLabelId,
        String transactionId,
        String scenarioCode,
        boolean expectedSuspicious,
        String labeledBy,
        Instant createdAt
) {
}
