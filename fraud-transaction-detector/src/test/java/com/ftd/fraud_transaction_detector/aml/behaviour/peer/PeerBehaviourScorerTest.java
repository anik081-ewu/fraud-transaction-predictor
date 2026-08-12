package com.ftd.fraud_transaction_detector.aml.behaviour.peer;

import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScoringContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.BehaviorFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TimeFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerBehaviourScorerTest {

    private final PeerBehaviourScorer scorer = new PeerBehaviourScorer();

    @Test
    void returnsNormalForTransactionNearSpecificPeerBaseline() {
        PeerBehaviourScore result = scorer.score(
                vector(peer("RETAIL_SALARIED", 1.05, 0.10, 0.55, 0.02)),
                context("RETAIL")
        );

        assertEquals("RETAIL_SALARIED", result.peerGroup());
        assertEquals(1.0, result.confidence());
        assertEquals(RiskBand.NORMAL, result.riskBand());
        assertTrue(result.score().normalizedScore() < 0.10);
    }

    @Test
    void explainsStrongDeviationFromSpecificPeerGroup() {
        PeerBehaviourScore result = scorer.score(
                vector(peer("RETAIL_SALARIED", 9.0, 8.0, 0.99, 0.60)),
                context("RETAIL")
        );

        assertEquals(RiskBand.HIGH, result.riskBand());
        assertTrue(result.reasonCodes().contains("AMOUNT_ABOVE_8X_PEER_AVERAGE"));
        assertTrue(result.reasonCodes().contains("PEER_FREQUENCY_ABOVE_99TH_PERCENTILE"));
        assertTrue(result.reasonCodes().contains("TRANSACTION_ABOVE_50_PERCENT_EXPECTED_MONTHLY_TURNOVER"));
    }

    @Test
    void usesParentSegmentWhenSpecificGroupIsUnavailable() {
        PeerBehaviourScore result = scorer.score(
                vector(peer(null, 4.0, 4.0, 0.95, 0.25)),
                context("RETAIL")
        );

        assertEquals("RETAIL", result.peerGroup());
        assertEquals(0.85, result.confidence());
        assertTrue(result.reasonCodes().contains("PEER_BASELINE_PARENT_SEGMENT"));
    }

    @Test
    void returnsZeroEvidenceWhenNoPeerStatisticsExist() {
        PeerBehaviourScore result = scorer.score(
                vector(peer(null, null, null, null, null)),
                context(null)
        );

        assertEquals("GLOBAL", result.peerGroup());
        assertEquals(0.0, result.confidence());
        assertEquals(0.0, result.score().normalizedScore());
        assertTrue(result.reasonCodes().contains("PEER_BASELINE_GLOBAL"));
        assertTrue(result.reasonCodes().contains("PEER_BASELINE_UNAVAILABLE"));
    }

    @Test
    void replayIsDeterministicAndMonotonicAcrossPeerDeviation() {
        double normal = scoreForRatio(1.0);
        double elevated = scoreForRatio(3.0);
        double extreme = scoreForRatio(8.0);

        assertEquals(extreme, scoreForRatio(8.0));
        assertTrue(normal < elevated);
        assertTrue(elevated < extreme);
    }

    private double scoreForRatio(double ratio) {
        return scorer.score(
                vector(peer("RETAIL_SALARIED", ratio, ratio - 1.0, 0.50, 0.02)),
                context("RETAIL")
        ).score().normalizedScore();
    }

    private BehaviourScoringContext context(String segment) {
        return new BehaviourScoringContext(
                segment, Instant.parse("2026-08-05T00:00:00Z"), Map.of()
        );
    }

    private PeerFeatures peer(
            String group,
            Double amountRatio,
            Double zScore,
            Double frequencyPercentile,
            Double turnoverRatio
    ) {
        return new PeerFeatures(
                group, 100.0, 100.0, 20.0, amountRatio, zScore, frequencyPercentile,
                "SALARIED", "LOW", 5_000.0, turnoverRatio
        );
    }

    private TransactionFeatureVector vector(PeerFeatures peer) {
        return new TransactionFeatureVector(
                "TX-1", "CUSTOMER-1", "ACCOUNT-1",
                LocalDate.of(2026, 8, 5), LocalDateTime.of(2026, 8, 5, 12, 0),
                "AML_FEATURES_V2",
                new AmountFeatures(100, 10_000.0, 0.01, 100.0, 100.0, 100.0, 100.0,
                        10.0, 120.0, 80.0, 1.0, 1.0, 0.0),
                new BehaviorFeatures(1.0, 0.0, 0.0, 1, 1, 1, 60.0),
                new TimeFeatures(12, 3, false, false, 60.0),
                new VelocityFeatures(1, 1, 1, 1, 1, 100, 100, 100, 100, 100, 1, 1, 1, 0, 0, 0),
                new NoveltyFeatures(false, false, false, false, false),
                new ProfileFeatures(30, 30, 30, 1.0, ProfileStatus.ESTABLISHED),
                peer, "AML_MODEL_FEATURES_V1", Map.of(), Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
