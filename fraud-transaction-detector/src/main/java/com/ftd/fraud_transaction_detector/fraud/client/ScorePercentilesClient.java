package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesResponse;

public interface ScorePercentilesClient {
    ScorePercentilesResponse compute(ScorePercentilesRequest request);
}

