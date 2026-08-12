package com.ftd.fraud_transaction_detector.aml.training.api;

import java.time.LocalDate;

public record CloseBusinessDateRangeRequest(
        LocalDate fromDate,
        LocalDate toDate,
        String closedBy
) {
}
