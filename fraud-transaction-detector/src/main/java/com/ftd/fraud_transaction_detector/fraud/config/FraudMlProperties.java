package com.ftd.fraud_transaction_detector.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param trainingTimeoutSeconds ceiling for the model training call. Training all
 *                               models on a full snapshot takes minutes — One-Class SVM alone
 *                               is O(n^2) in row count — so this must be far larger than a
 *                               normal request timeout.
 */
@ConfigurationProperties(prefix = "fraud.ml")
public record FraudMlProperties(String baseUrl, Integer trainingTimeoutSeconds) {

    private static final int DEFAULT_TRAINING_TIMEOUT_SECONDS = 1800;

    public int trainingTimeoutSecondsOrDefault() {
        return trainingTimeoutSeconds == null || trainingTimeoutSeconds <= 0
                ? DEFAULT_TRAINING_TIMEOUT_SECONDS
                : trainingTimeoutSeconds;
    }
}
