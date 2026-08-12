package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record NoveltyFeatures(
        boolean newBeneficiary,
        boolean newLocation,
        boolean newChannel,
        boolean newDevice,
        boolean unusualTransactionHour
) {
}
