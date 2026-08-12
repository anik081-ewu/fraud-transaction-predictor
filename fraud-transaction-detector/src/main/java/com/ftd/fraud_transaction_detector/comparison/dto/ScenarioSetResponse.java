package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.ScenarioSet;

import java.time.Instant;

public record ScenarioSetResponse(
        Long id,
        String scenarioSetNo,
        String name,
        String description,
        String createdBy,
        Instant createdAt
) {
    public static ScenarioSetResponse from(ScenarioSet scenarioSet) {
        return new ScenarioSetResponse(
                scenarioSet.getId(),
                scenarioSet.getScenarioSetNo(),
                scenarioSet.getName(),
                scenarioSet.getDescription(),
                scenarioSet.getCreatedBy(),
                scenarioSet.getCreatedAt()
        );
    }
}
