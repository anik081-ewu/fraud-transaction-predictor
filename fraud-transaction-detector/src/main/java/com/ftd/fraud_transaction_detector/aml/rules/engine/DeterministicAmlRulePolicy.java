package com.ftd.fraud_transaction_detector.aml.rules.engine;

public record DeterministicAmlRulePolicy(
        String normalizationVersion,
        double defaultReportingThreshold,
        int structuringMinimumCount24Hours,
        int rapidTransactionCount10Minutes,
        int highTransactionCount1Hour,
        int multipleBeneficiaryCount1Hour,
        int repeatedAmountCount24Hours,
        double highCustomerAmountRatio,
        double extremeCustomerAmountRatio,
        double highBalanceRatio,
        double highExpectedTurnoverRatio
) {
    public DeterministicAmlRulePolicy {
        if (normalizationVersion == null || normalizationVersion.isBlank()) {
            throw new IllegalArgumentException("normalizationVersion is required");
        }
        normalizationVersion = normalizationVersion.trim();
        if (!Double.isFinite(defaultReportingThreshold) || defaultReportingThreshold <= 0.0) {
            throw new IllegalArgumentException("defaultReportingThreshold must be positive");
        }
        if (structuringMinimumCount24Hours < 2 || rapidTransactionCount10Minutes < 2
                || highTransactionCount1Hour < rapidTransactionCount10Minutes
                || multipleBeneficiaryCount1Hour < 2 || repeatedAmountCount24Hours < 2) {
            throw new IllegalArgumentException("rule count thresholds are invalid");
        }
        if (!(highCustomerAmountRatio > 1.0 && extremeCustomerAmountRatio > highCustomerAmountRatio)) {
            throw new IllegalArgumentException("customer amount thresholds are invalid");
        }
        validateUnit(highBalanceRatio, "highBalanceRatio");
        validateUnit(highExpectedTurnoverRatio, "highExpectedTurnoverRatio");
    }

    public static DeterministicAmlRulePolicy transparentV1() {
        return new DeterministicAmlRulePolicy(
                "DETERMINISTIC_AML_RULES_V1",
                10_000.0,
                3, 5, 10, 4, 4,
                4.0, 8.0, 0.80, 0.50
        );
    }

    private static void validateUnit(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be greater than 0.0 and at most 1.0");
        }
    }
}
