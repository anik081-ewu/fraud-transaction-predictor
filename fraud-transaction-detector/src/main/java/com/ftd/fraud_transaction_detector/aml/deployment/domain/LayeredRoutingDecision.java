package com.ftd.fraud_transaction_detector.aml.deployment.domain;

public record LayeredRoutingDecision(
        LayeredDeploymentPointer pointer,
        boolean layeredCanarySelected,
        boolean isolationForestFallback
) {
    public static LayeredRoutingDecision legacy() {
        return new LayeredRoutingDecision(null, false, false);
    }
}
