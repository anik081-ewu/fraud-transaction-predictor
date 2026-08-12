package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredRoutingDecision;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.LayeredArchitectureDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.prediction.LayeredShadowComparison;
import com.ftd.fraud_transaction_detector.aml.risk.application.WeightedRiskAggregationEngine;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Service
public class LayeredProductionRoutingService {

    private final LayeredArchitectureDeploymentRepository repository;

    public LayeredProductionRoutingService(LayeredArchitectureDeploymentRepository repository) {
        this.repository = repository;
    }

    public LayeredRoutingDecision resolve(TransactionFeatureVector features) {
        String peerGroup = features.peer().peerGroupCode();
        LayeredDeploymentPointer pointer = repository.findPointer(peerGroup).orElse(null);
        if (pointer == null) return LayeredRoutingDecision.legacy();
        if ("ISOLATION_FOREST_FALLBACK".equals(pointer.deploymentMode())) {
            return new LayeredRoutingDecision(pointer, false, true);
        }
        boolean selected = "LAYERED_ACTIVE".equals(pointer.deploymentMode())
                && canaryBucket(features.accountId()) <= pointer.canaryPercentage();
        return new LayeredRoutingDecision(pointer, selected, false);
    }

    public boolean compatible(
            LayeredRoutingDecision decision,
            LayeredShadowComparison comparison
    ) {
        if (!decision.layeredCanarySelected() || decision.pointer() == null || comparison == null) return false;
        var hst = comparison.modelScores().get("HALF_SPACE_TREES");
        var ocsvm = comparison.modelScores().get("ONLINE_ONE_CLASS_SVM");
        return Objects.equals(decision.pointer().riskPolicyVersion(), comparison.layeredResult().riskPolicyVersion())
                && hst != null && Objects.equals(decision.pointer().hstModelVersion(), hst.modelVersion())
                && ocsvm != null && Objects.equals(decision.pointer().onlineOcSvmModelVersion(), ocsvm.modelVersion());
    }

    int canaryBucket(String accountId) {
        if (accountId == null || accountId.isBlank()) return 100;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(accountId.getBytes(StandardCharsets.UTF_8));
            long value = Integer.toUnsignedLong(ByteBuffer.wrap(digest).getInt());
            return (int) (value % 100L) + 1;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for deterministic canary routing", exception);
        }
    }
}
