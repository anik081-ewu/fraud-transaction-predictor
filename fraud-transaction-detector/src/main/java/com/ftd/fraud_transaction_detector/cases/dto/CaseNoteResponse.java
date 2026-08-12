package com.ftd.fraud_transaction_detector.cases.dto;

import java.time.Instant;

public record CaseNoteResponse(Long id, String noteText, String createdBy, Instant createdAt) {
}
