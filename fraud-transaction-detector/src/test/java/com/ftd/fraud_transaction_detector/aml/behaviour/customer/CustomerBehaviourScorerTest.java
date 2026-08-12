package com.ftd.fraud_transaction_detector.aml.behaviour.customer;

import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScoringContext;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.BehaviorFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TimeFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionSnapshot;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerBehaviourScorerTest {

    private final CustomerBehaviourScorer scorer = new CustomerBehaviourScorer();
    private final BehaviourScoringContext context = new BehaviourScoringContext(
            "RETAIL_SALARIED", Instant.parse("2026-08-05T00:00:00Z"), Map.of()
    );

    @Test
    void returnsLowScoreForEstablishedNormalBehaviour() {
        CustomerBehaviourScore result = scorer.score(vector(1.05, 0.2, 1.0, false), context);

        assertEquals(RiskBand.NORMAL, result.riskBand());
        assertTrue(result.score().normalizedScore() < 0.10);
        assertTrue(result.reasonCodes().isEmpty());
    }

    @Test
    void explainsHighAmountAndCategoricalNovelty() {
        CustomerBehaviourScore result = scorer.score(vector(9.0, 8.0, 1.0, true), context);

        assertEquals(RiskBand.HIGH, result.riskBand());
        assertTrue(result.score().normalizedScore() >= 0.75);
        assertTrue(result.reasonCodes().contains("AMOUNT_ABOVE_8X_RECENT_AVERAGE"));
        assertTrue(result.reasonCodes().contains("NEW_BENEFICIARY"));
        assertTrue(result.reasonCodes().contains("UNUSUAL_TRANSACTION_HOUR"));
    }

    @Test
    void reducesCustomerEvidenceWhenProfileConfidenceIsLow() {
        CustomerBehaviourScore established = scorer.score(vector(9.0, 8.0, 1.0, true), context);
        CustomerBehaviourScore cold = scorer.score(vector(9.0, 8.0, 0.10, true), context);

        assertTrue(cold.score().normalizedScore() < established.score().normalizedScore());
        assertEquals(0.10, cold.confidence());
        assertTrue(cold.reasonCodes().contains("LOW_CUSTOMER_PROFILE_CONFIDENCE"));
    }

    @Test
    void historicalReplayProducesDeterministicMonotonicScores() {
        List<Double> firstReplay = List.of(100.0, 300.0, 900.0).stream()
                .map(this::replayScore)
                .toList();
        List<Double> secondReplay = List.of(100.0, 300.0, 900.0).stream()
                .map(this::replayScore)
                .toList();

        assertEquals(firstReplay, secondReplay);
        assertTrue(firstReplay.get(0) < firstReplay.get(1));
        assertTrue(firstReplay.get(1) < firstReplay.get(2));
    }

    private double replayScore(double amount) {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 5, 12, 0);
        List<HistoricalTransaction> history = IntStream.range(0, 30)
                .mapToObj(index -> new HistoricalTransaction(
                        "H-" + index,
                        BigDecimal.valueOf(90 + (index % 5) * 5),
                        currentTime.minusHours(index + 1L),
                        "DEBIT", "BRANCH", "DHAKA", "B-1", "DEVICE-1", true
                ))
                .toList();
        TransactionSnapshot current = new TransactionSnapshot(
                "TX-" + (int) amount, "CUSTOMER-1", "ACCOUNT-1",
                BigDecimal.valueOf(amount), BigDecimal.valueOf(10_000),
                "DEBIT", currentTime, "BRANCH", "DHAKA", "B-1", "DEVICE-1", 0, "SALARIED"
        );
        TrustedProfileSnapshot profile = new TrustedProfileSnapshot(
                30, 100.0, 50.0, Math.sqrt(50), 110.0, 90.0,
                8, 20, "BRANCH", "DHAKA", 1.0, ProfileStatus.ESTABLISHED
        );
        TransactionFeatureVector features = new FeatureEngineeringService().calculate(
                new FeatureContext(current, 30, profile, history),
                "AML_FEATURES_V2",
                BigDecimal.valueOf(10_000)
        );
        return scorer.score(features, context).score().normalizedScore();
    }

    private TransactionFeatureVector vector(
            double amountRatio,
            double zScore,
            double confidence,
            boolean anomalousContext
    ) {
        return new TransactionFeatureVector(
                "TX-1", "CUSTOMER-1", "ACCOUNT-1",
                java.time.LocalDate.of(2026, 8, 5),
                LocalDateTime.of(2026, 8, 5, anomalousContext ? 2 : 12, 0),
                "AML_FEATURES_V2",
                new AmountFeatures(
                        amountRatio * 100, 10_000.0, amountRatio / 100,
                        100.0, 100.0, 100.0, 100.0, 10.0, 130.0, 80.0,
                        amountRatio, amountRatio, zScore
                ),
                new BehaviorFeatures(1.0, 0.0, 0.0, 3, 1, 1, 60.0),
                new TimeFeatures(anomalousContext ? 2 : 12, 3, anomalousContext, false, 60.0),
                new VelocityFeatures(1, 1, 1, 5, 20, 100, 100, 100, 500, 2_000, 1, 1, 2, 0, 0, 0),
                new NoveltyFeatures(
                        anomalousContext, anomalousContext, anomalousContext,
                        anomalousContext, anomalousContext
                ),
                new ProfileFeatures(30, 30, 30, confidence, ProfileStatus.ESTABLISHED),
                new PeerFeatures("RETAIL_SALARIED", 100.0, 100.0, 10.0, amountRatio, zScore, 0.5,
                        "SALARIED", "LOW", 5_000.0, amountRatio / 50),
                "AML_MODEL_FEATURES_V1", Map.of("amount", amountRatio), Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
