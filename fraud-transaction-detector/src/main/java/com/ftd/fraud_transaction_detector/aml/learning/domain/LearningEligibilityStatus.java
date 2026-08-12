package com.ftd.fraud_transaction_detector.aml.learning.domain;

public enum LearningEligibilityStatus {
    LEARN_IMMEDIATELY,
    DELAYED_LEARNING,
    DO_NOT_LEARN,
    WAIT_FOR_REVIEW
}
