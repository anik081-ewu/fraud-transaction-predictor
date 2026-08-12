package com.ftd.fraud_transaction_detector.aml.behaviour.customer;

import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScoringContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

import com.ftd.fraud_transaction_detector.aml.risk.infrastructure.AppConfigRiskPolicyRepository;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomerBehaviourScorer implements BehaviourScorer {

    private final AppConfigRepository appConfigRepository;

    @Autowired
    public CustomerBehaviourScorer(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
        this.fixedPolicy = null;
    }

    CustomerBehaviourScorer(CustomerBehaviourScoringPolicy policy) {
        this.appConfigRepository = null;
        this.fixedPolicy = policy;
    }

    private final CustomerBehaviourScoringPolicy fixedPolicy;

    private CustomerBehaviourScoringPolicy policy() {
        if (fixedPolicy != null) return fixedPolicy;
        CustomerBehaviourScoringPolicy defaults = CustomerBehaviourScoringPolicy.transparentV1();
        return new CustomerBehaviourScoringPolicy(
                defaults.normalizationVersion(),
                optionalDouble(AppConfigRiskPolicyRepository.CB_AMOUNT_WEIGHT, defaults.amountWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.CB_FREQUENCY_WEIGHT, defaults.frequencyWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.CB_TIME_GAP_WEIGHT, defaults.timeGapWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.CB_NOVELTY_WEIGHT, defaults.noveltyWeight()),
                optionalDouble(AppConfigRiskPolicyRepository.CB_UNUSUAL_HOUR_WEIGHT, defaults.unusualHourWeight()),
                defaults.confidenceFloor(),
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
    public CustomerBehaviourScore score(
            TransactionFeatureVector features,
            BehaviourScoringContext context
    ) {
        if (features == null) throw new IllegalArgumentException("features are required");
        if (context == null) throw new IllegalArgumentException("context is required");

        CustomerBehaviourScoringPolicy policy = policy();
        List<String> reasons = new ArrayList<>();
        double amountScore = amountScore(features.amount(), reasons);
        double frequencyScore = frequencyScore(features.velocity(), reasons);
        double timeGapScore = timeGapScore(features, reasons);
        double noveltyScore = noveltyScore(features.novelty(), reasons);
        double unusualHourScore = features.novelty().unusualTransactionHour() ? 1.0 : 0.0;

        double unadjusted = clamp(
                amountScore * policy.amountWeight()
                        + frequencyScore * policy.frequencyWeight()
                        + timeGapScore * policy.timeGapWeight()
                        + noveltyScore * policy.noveltyWeight()
                        + unusualHourScore * policy.unusualHourWeight()
        );
        double confidence = clamp(features.profile().confidence());
        double confidenceMultiplier = policy.confidenceFloor()
                + ((1.0 - policy.confidenceFloor()) * confidence);
        double normalized = clamp(unadjusted * confidenceMultiplier);
        if (confidence < 0.50) reasons.add("LOW_CUSTOMER_PROFILE_CONFIDENCE");

        return new CustomerBehaviourScore(
                new NormalizedScore(normalized * 100.0, normalized, policy.normalizationVersion()),
                riskBand(normalized, policy),
                confidence,
                reasons
        );
    }

    private double amountScore(AmountFeatures amount, List<String> reasons) {
        Double averageRatio = amount.amountVsLast30Average();
        Double medianRatio = amount.amountVsLast30Median();
        Double zScore = amount.amountZScoreLast30();
        addAmountReasons(averageRatio, zScore, reasons);
        return Math.max(
                Math.max(ratioDeviation(averageRatio), ratioDeviation(medianRatio)),
                positiveZDeviation(zScore)
        );
    }

    private void addAmountReasons(Double averageRatio, Double zScore, List<String> reasons) {
        if (averageRatio != null) {
            if (averageRatio >= 8.0) reasons.add("AMOUNT_ABOVE_8X_RECENT_AVERAGE");
            else if (averageRatio >= 4.0) reasons.add("AMOUNT_ABOVE_4X_RECENT_AVERAGE");
            else if (averageRatio >= 2.0) reasons.add("AMOUNT_ABOVE_2X_RECENT_AVERAGE");
        }
        if (zScore != null && zScore >= 3.0) reasons.add("AMOUNT_ZSCORE_ABOVE_3");
    }

    private double frequencyScore(VelocityFeatures velocity, List<String> reasons) {
        double tenMinutes = excessFrequency(velocity.transactionCount10Minutes(), 1, 5);
        double oneHour = excessFrequency(velocity.transactionCount1Hour(), 1, 10);
        double twentyFourHours = excessFrequency(velocity.transactionCount24Hours(), 1, 40);
        if (velocity.transactionCount10Minutes() >= 5) reasons.add("CUSTOMER_FREQUENCY_BURST_10M");
        if (velocity.transactionCount1Hour() >= 10) reasons.add("CUSTOMER_FREQUENCY_BURST_1H");
        return Math.max(tenMinutes, Math.max(oneHour, twentyFourHours));
    }

    private double timeGapScore(TransactionFeatureVector features, List<String> reasons) {
        Double expected = features.behavior().last30AverageTimeGapMinutes();
        Double current = features.time().minutesSincePreviousTransaction();
        if (expected == null || current == null || expected <= 0.0 || current >= expected) return 0.0;
        double score = clamp(1.0 - (current / expected));
        if (score >= 0.75) reasons.add("RAPID_TRANSACTION_VS_CUSTOMER_BASELINE");
        return score;
    }

    private double noveltyScore(NoveltyFeatures novelty, List<String> reasons) {
        double score = 0.0;
        if (novelty.newBeneficiary()) {
            score += 0.40;
            reasons.add("NEW_BENEFICIARY");
        }
        if (novelty.newLocation()) {
            score += 0.20;
            reasons.add("NEW_LOCATION");
        }
        if (novelty.newChannel()) {
            score += 0.20;
            reasons.add("NEW_CHANNEL");
        }
        if (novelty.newDevice()) {
            score += 0.20;
            reasons.add("NEW_DEVICE");
        }
        if (novelty.unusualTransactionHour()) reasons.add("UNUSUAL_TRANSACTION_HOUR");
        return clamp(score);
    }

    private RiskBand riskBand(double score, CustomerBehaviourScoringPolicy policy) {
        if (score >= policy.highRiskThreshold()) return RiskBand.HIGH;
        if (score >= policy.mediumRiskThreshold()) return RiskBand.MEDIUM;
        if (score >= policy.lowRiskThreshold()) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }

    private static double ratioDeviation(Double ratio) {
        if (ratio == null || !Double.isFinite(ratio) || ratio <= 1.0) return 0.0;
        return clamp(1.0 - Math.exp(-(ratio - 1.0) / 2.5));
    }

    private static double positiveZDeviation(Double zScore) {
        if (zScore == null || !Double.isFinite(zScore) || zScore <= 0.0) return 0.0;
        return clamp(1.0 - Math.exp(-zScore / 3.0));
    }

    private static double excessFrequency(int value, int baseline, int saturationDelta) {
        return clamp((value - baseline) / (double) saturationDelta);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
