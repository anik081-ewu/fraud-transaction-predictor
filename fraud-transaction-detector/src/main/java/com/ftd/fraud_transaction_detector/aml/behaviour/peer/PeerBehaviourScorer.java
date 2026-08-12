package com.ftd.fraud_transaction_detector.aml.behaviour.peer;

import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScoringContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.risk.infrastructure.AppConfigRiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PeerBehaviourScorer implements BehaviourScorer {

    private final AppConfigRepository appConfigRepository;
    private final PeerBehaviourScoringPolicy fixedPolicy;

    @Autowired
    public PeerBehaviourScorer(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
        this.fixedPolicy = null;
    }

    PeerBehaviourScorer(PeerBehaviourScoringPolicy policy) {
        this.appConfigRepository = null;
        this.fixedPolicy = policy;
    }

    private PeerBehaviourScoringPolicy policy() {
        if (fixedPolicy != null) return fixedPolicy;
        PeerBehaviourScoringPolicy defaults = PeerBehaviourScoringPolicy.transparentV1();
        return new PeerBehaviourScoringPolicy(
                defaults.normalizationVersion(),
                optionalDouble(AppConfigRiskPolicyRepository.PB_AMOUNT_WEIGHT, defaults.amountWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.PB_FREQUENCY_WEIGHT, defaults.frequencyWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.PB_EXPECTED_TURNOVER_WEIGHT, defaults.expectedTurnoverWeight()),
                defaults.specificGroupConfidence(),
                defaults.parentSegmentConfidence(),
                defaults.globalConfidence(),
                defaults.lowRiskThreshold(),
                defaults.mediumRiskThreshold(),
                defaults.highRiskThreshold()
        );
    }

    private double optionalDouble(String key, double defaultValue) {
        try {
            return appConfigRepository.findById(key)
                    .map(c -> c.getConfigValue())
                    .map(String::trim)
                    .filter(v -> !v.isBlank())
                    .map(Double::parseDouble)
                    .filter(Double::isFinite)
                    .orElse(defaultValue);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public PeerBehaviourScore score(
            TransactionFeatureVector features,
            BehaviourScoringContext context
    ) {
        if (features == null) throw new IllegalArgumentException("features are required");
        if (context == null) throw new IllegalArgumentException("context is required");

        PeerFeatures peer = features.peer();
        if (peer == null) throw new IllegalArgumentException("peer features are required");

        List<String> reasons = new ArrayList<>();
        PeerBehaviourScoringPolicy policy = policy();
        PeerBaseline baseline = resolveBaseline(peer, context, policy, reasons);
        double amountScore = amountScore(peer, reasons);
        double frequencyScore = frequencyScore(peer.peerFrequencyPercentile(), reasons);
        double turnoverScore = expectedTurnoverScore(peer.amountVsExpectedTurnover(), reasons);
        double completeness = completeness(peer);
        double confidence = clamp(completeness * baseline.confidence());

        if (confidence == 0.0) reasons.add("PEER_BASELINE_UNAVAILABLE");
        else if (confidence < 0.60) reasons.add("LOW_PEER_BASELINE_CONFIDENCE");

        double unadjusted = clamp(
                amountScore * policy.amountWeight()
                        + frequencyScore * policy.frequencyWeight()
                        + turnoverScore * policy.expectedTurnoverWeight()
        );
        double normalized = clamp(unadjusted * confidence);

        return new PeerBehaviourScore(
                baseline.group(),
                new NormalizedScore(normalized * 100.0, normalized, policy.normalizationVersion()),
                riskBand(normalized, policy),
                confidence,
                reasons
        );
    }

    private PeerBaseline resolveBaseline(
            PeerFeatures peer,
            BehaviourScoringContext context,
            PeerBehaviourScoringPolicy policy,
            List<String> reasons
    ) {
        if (hasText(peer.peerGroupCode()) && !"GLOBAL".equalsIgnoreCase(peer.peerGroupCode())) {
            String group = peer.peerGroupCode().trim();
            if (group.contains("_AGE_")) {
                return new PeerBaseline(group, policy.specificGroupConfidence());
            }
            reasons.add("PEER_BASELINE_PARENT_SEGMENT");
            return new PeerBaseline(group, policy.parentSegmentConfidence());
        }
        if (hasText(context.customerSegment()) && !"GLOBAL".equalsIgnoreCase(context.customerSegment())) {
            reasons.add("PEER_BASELINE_PARENT_SEGMENT");
            return new PeerBaseline(context.customerSegment().trim(), policy.parentSegmentConfidence());
        }
        reasons.add("PEER_BASELINE_GLOBAL");
        return new PeerBaseline("GLOBAL", policy.globalConfidence());
    }

    private double amountScore(PeerFeatures peer, List<String> reasons) {
        Double ratio = peer.amountVsPeerAverage();
        Double zScore = peer.peerAmountZScore();
        if (ratio != null) {
            if (ratio >= 8.0) reasons.add("AMOUNT_ABOVE_8X_PEER_AVERAGE");
            else if (ratio >= 4.0) reasons.add("AMOUNT_ABOVE_4X_PEER_AVERAGE");
            else if (ratio >= 2.0) reasons.add("AMOUNT_ABOVE_2X_PEER_AVERAGE");
        }
        if (zScore != null && zScore >= 3.0) reasons.add("PEER_AMOUNT_ZSCORE_ABOVE_3");
        return Math.max(ratioDeviation(ratio), positiveZDeviation(zScore));
    }

    private double frequencyScore(Double percentile, List<String> reasons) {
        if (percentile == null || !Double.isFinite(percentile)) return 0.0;
        double boundedPercentile = clamp(percentile);
        if (boundedPercentile >= 0.99) reasons.add("PEER_FREQUENCY_ABOVE_99TH_PERCENTILE");
        else if (boundedPercentile >= 0.95) reasons.add("PEER_FREQUENCY_ABOVE_95TH_PERCENTILE");
        return clamp((boundedPercentile - 0.50) / 0.50);
    }

    private double expectedTurnoverScore(Double ratio, List<String> reasons) {
        if (ratio == null || !Double.isFinite(ratio) || ratio <= 0.05) return 0.0;
        if (ratio >= 0.50) reasons.add("TRANSACTION_ABOVE_50_PERCENT_EXPECTED_MONTHLY_TURNOVER");
        else if (ratio >= 0.25) reasons.add("TRANSACTION_ABOVE_25_PERCENT_EXPECTED_MONTHLY_TURNOVER");
        return clamp((ratio - 0.05) / 0.45);
    }

    private double completeness(PeerFeatures peer) {
        double completeness = 0.0;
        if (isFinite(peer.amountVsPeerAverage()) || isFinite(peer.peerAmountZScore())) completeness += 0.60;
        if (isFinite(peer.peerFrequencyPercentile())) completeness += 0.25;
        if (isFinite(peer.amountVsExpectedTurnover())) completeness += 0.15;
        return completeness;
    }

    private RiskBand riskBand(double score, PeerBehaviourScoringPolicy policy) {
        if (score >= policy.highRiskThreshold()) return RiskBand.HIGH;
        if (score >= policy.mediumRiskThreshold()) return RiskBand.MEDIUM;
        if (score >= policy.lowRiskThreshold()) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }

    private static double ratioDeviation(Double ratio) {
        if (!isFinite(ratio) || ratio <= 1.0) return 0.0;
        return clamp(1.0 - Math.exp(-(ratio - 1.0) / 2.5));
    }

    private static double positiveZDeviation(Double zScore) {
        if (!isFinite(zScore) || zScore <= 0.0) return 0.0;
        return clamp(1.0 - Math.exp(-zScore / 3.0));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record PeerBaseline(String group, double confidence) {
    }
}
