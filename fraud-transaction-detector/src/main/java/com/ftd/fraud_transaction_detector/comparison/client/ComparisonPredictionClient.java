package com.ftd.fraud_transaction_detector.comparison.client;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;

public interface ComparisonPredictionClient {
    ComparisonPredictResponse compare(ComparisonPredictRequest request);
}
