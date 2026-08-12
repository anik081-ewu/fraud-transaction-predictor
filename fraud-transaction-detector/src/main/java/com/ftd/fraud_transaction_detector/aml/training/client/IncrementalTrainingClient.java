package com.ftd.fraud_transaction_detector.aml.training.client;

public interface IncrementalTrainingClient {
    IncrementalTrainingResponse train(IncrementalTrainingRequest request);
}
