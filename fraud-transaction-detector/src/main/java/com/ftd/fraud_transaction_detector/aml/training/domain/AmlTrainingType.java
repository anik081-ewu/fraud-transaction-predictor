package com.ftd.fraud_transaction_detector.aml.training.domain;

public enum AmlTrainingType {
    DAILY_INCREMENTAL,
    WEEKLY_BATCH,
    FULL_REBUILD,
    BACKTEST,
    REPLAY
}
