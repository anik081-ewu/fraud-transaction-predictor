package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;

public interface ModelTrainingClient {
    TrainModelResponse train(TrainModelRequest request);
}

