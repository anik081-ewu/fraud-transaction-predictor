package com.ftd.fraud_transaction_detector.cases.dto;

import java.util.List;

public record CasePageResponse(
        List<CaseSummaryResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        long allElements,
        long activeElements,
        long strGeneratedElements,
        long falsePositiveElements
) {
}
