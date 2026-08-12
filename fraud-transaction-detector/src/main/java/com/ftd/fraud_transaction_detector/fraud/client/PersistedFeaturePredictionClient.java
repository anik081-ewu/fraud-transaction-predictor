package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.PersistedFeaturePredictRequest;

public interface PersistedFeaturePredictionClient {
    ComparisonPredictResponse predict(PersistedFeaturePredictRequest request);
}
