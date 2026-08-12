package com.ftd.fraud_transaction_detector.aml.risk.domain;

public interface RiskPolicyRepository {
    RiskPolicy findActive(String customerSegment);
}
