package com.ftd.fraud_transaction_detector.fraud.dto;

import java.util.List;

public record ScorePercentilesRequest(
        String source,
        String requestedBy,
        List<TrainModelRequest.TrainingTransaction> transactions
) {
}

