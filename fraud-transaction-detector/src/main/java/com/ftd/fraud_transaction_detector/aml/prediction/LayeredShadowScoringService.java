package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScoringContext;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.risk.application.WeightedRiskAggregationEngine;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEvaluationContext;
import com.ftd.fraud_transaction_detector.aml.rules.engine.DeterministicAmlRuleEngine;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class LayeredShadowScoringService {

    private final CustomerBehaviourScorer customerScorer;
    private final PeerBehaviourScorer peerScorer;
    private final DeterministicAmlRuleEngine ruleEngine;
    private final WeightedRiskAggregationEngine aggregationEngine;
    private final RiskPolicyRepository riskPolicyRepository;
    private final LayeredShadowPredictionRepository repository;
    private final AppConfigService appConfigService;
    private final Clock clock;

    @Autowired
    public LayeredShadowScoringService(
            CustomerBehaviourScorer customerScorer,
            PeerBehaviourScorer peerScorer,
            DeterministicAmlRuleEngine ruleEngine,
            WeightedRiskAggregationEngine aggregationEngine,
            RiskPolicyRepository riskPolicyRepository,
            LayeredShadowPredictionRepository repository,
            AppConfigService appConfigService
    ) {
        this(customerScorer, peerScorer, ruleEngine, aggregationEngine, riskPolicyRepository,
                repository, appConfigService, Clock.systemUTC());
    }

    LayeredShadowScoringService(
            CustomerBehaviourScorer customerScorer,
            PeerBehaviourScorer peerScorer,
            DeterministicAmlRuleEngine ruleEngine,
            WeightedRiskAggregationEngine aggregationEngine,
            RiskPolicyRepository riskPolicyRepository,
            LayeredShadowPredictionRepository repository,
            AppConfigService appConfigService,
            Clock clock
    ) {
        this.customerScorer = customerScorer;
        this.peerScorer = peerScorer;
        this.ruleEngine = ruleEngine;
        this.aggregationEngine = aggregationEngine;
        this.riskPolicyRepository = riskPolicyRepository;
        this.repository = repository;
        this.appConfigService = appConfigService;
        this.clock = clock;
    }

    public FinalRiskResult score(
            TransactionFeatureVector features,
            FraudPredictionResponse mlResponse
    ) {
        Instant evaluatedAt = Instant.now(clock);
        String segment = features.peer().peerGroupCode();
        BehaviourScoringContext behaviourContext = new BehaviourScoringContext(segment, evaluatedAt, Map.of());
        CustomerBehaviourScore customer = customerScorer.score(features, behaviourContext);
        PeerBehaviourScore peer = peerScorer.score(features, behaviourContext);
        RuleEngineResult rules = ruleEngine.evaluate(features, new RuleEvaluationContext(
                evaluatedAt,
                Map.of(
                        DeterministicAmlRuleEngine.REPORTING_THRESHOLD_ATTRIBUTE,
                        appConfigService.getStructuringReportingThreshold(BigDecimal.valueOf(10_000)).doubleValue()
                )
        ));
        MlModelScores modelScores = modelScores(mlResponse);
        Map<String, Double> modelAllocations = appConfigService.getEnabledRiskPolicyModelWeights();
        return aggregationEngine.aggregate(
                customer, peer, modelScores, rules, riskPolicyRepository.findActive(segment), modelAllocations
        );
    }

    public LayeredShadowComparison evaluateAndPersist(
            TransactionFeatureVector features,
            FraudPredictionResponse legacy
    ) {
        long started = System.nanoTime();
        Instant evaluatedAt = Instant.now(clock);
        String segment = features.peer().peerGroupCode();
        BehaviourScoringContext behaviourContext = new BehaviourScoringContext(segment, evaluatedAt, Map.of());
        CustomerBehaviourScore customer = customerScorer.score(features, behaviourContext);
        PeerBehaviourScore peer = peerScorer.score(features, behaviourContext);
        RuleEngineResult rules = ruleEngine.evaluate(features, new RuleEvaluationContext(
                evaluatedAt,
                Map.of(
                        DeterministicAmlRuleEngine.REPORTING_THRESHOLD_ATTRIBUTE,
                        appConfigService.getStructuringReportingThreshold(BigDecimal.valueOf(10_000)).doubleValue()
                )
        ));
        MlModelScores modelScores = modelScores(legacy);
        Map<String, Double> modelAllocations = appConfigService.getEnabledRiskPolicyModelWeights();
        FinalRiskResult layered = aggregationEngine.aggregate(
                customer, peer, modelScores, rules, riskPolicyRepository.findActive(segment), modelAllocations
        );
        LayeredShadowComparison comparison = new LayeredShadowComparison(
                UUID.randomUUID(), features.transactionId(), features.accountId(), features.featureVersion(),
                legacy.riskLevel(), legacy.suspicious(), legacy.anomalyVotes(),
                customer, peer, modelScores, rules, layered,
                legacy.suspicious() != layered.suspicious(),
                !Objects.equals(legacy.riskLevel(), layered.riskLevel().name()),
                legacy.suspicious() && layered.suspicious(),
                evaluatedAt, Math.max(0L, (System.nanoTime() - started) / 1_000_000)
        );
        repository.insert(comparison);
        return comparison;
    }

    private MlModelScores modelScores(FraudPredictionResponse legacy) {
        Map<String, MlModelScore> scores = new LinkedHashMap<>();
        addModel(scores, legacy, "IsolationForest", "ISOLATION_FOREST",
                "ISOLATION_FOREST_MARGIN_PROXY_V1", "ISOLATION_FOREST_HIGH_ANOMALY_SCORE",
                "ISOLATION_FOREST_DEFAULT");
        addModel(scores, legacy, "LOF", "LOCAL_OUTLIER_FACTOR",
                "LOF_MARGIN_PROXY_V1", "LOF_HIGH_ANOMALY_SCORE", "LOF_DEFAULT");
        addModel(scores, legacy, "Autoencoder", "AUTOENCODER",
                "AUTOENCODER_RECONSTRUCTION_MARGIN_PROXY_V1", "AUTOENCODER_HIGH_ANOMALY_SCORE", "AUTOENCODER_DEFAULT");
        addModel(scores, legacy, "XGBoost", "XGBOOST_CLASSIFIER",
                "SUPERVISED_PROBABILITY_V1", "XGBOOST_HIGH_FRAUD_PROBABILITY", "XGBOOST_DEFAULT");
        addModel(scores, legacy, "RandomForestClassifier", "RANDOM_FOREST_CLASSIFIER",
                "SUPERVISED_PROBABILITY_V1", "RANDOM_FOREST_HIGH_FRAUD_PROBABILITY", "RANDOM_FOREST_DEFAULT");
        addModel(scores, legacy, "LogisticRegression", "LOGISTIC_REGRESSION",
                "SUPERVISED_PROBABILITY_V1", "LOGISTIC_REGRESSION_HIGH_FRAUD_PROBABILITY", "LOGISTIC_REGRESSION_DEFAULT");
        return new MlModelScores(scores);
    }

    private void addModel(
            Map<String, MlModelScore> target,
            FraudPredictionResponse response,
            String responseKey,
            String modelType,
            String defaultNormalizationVersion,
            String anomalyReason,
            String defaultModelVersion
    ) {
        if (response.modelResults() == null) return;
        Object rawResult = response.modelResults().get(responseKey);
        if (!(rawResult instanceof Map<?, ?> result)) return;
        Double normalized = number(result.get("normalizedScore"));
        if (normalized == null) normalized = number(result.get("fraudProbability"));
        Double rawScore = number(result.get("rawScore"));
        if (rawScore == null) rawScore = number(result.get("fraudProbability"));
        if (rawScore == null) rawScore = number(result.get("score"));
        if (rawScore == null) rawScore = number(result.get("scoreSamples"));
        if (rawScore == null) rawScore = number(result.get("decisionFunction"));
        boolean anomaly = Boolean.TRUE.equals(result.get("anomaly"));
        if (normalized == null) {
            normalized = inferredNormalizedScore(result, rawScore, anomaly);
        }
        if (normalized == null || rawScore == null) return;
        String version = string(result.get("modelVersion"), defaultModelVersion);
        target.put(modelType, new MlModelScore(
                modelType, version,
                new NormalizedScore(
                        rawScore, clamp(normalized),
                        string(result.get("normalizationVersion"), defaultNormalizationVersion)
                ),
                anomaly,
                riskBand(normalized), anomaly ? List.of(anomalyReason) : List.of()
        ));
    }

    private Double inferredNormalizedScore(Map<?, ?> result, Double rawScore, boolean anomaly) {
        Double decision = number(result.get("decisionFunction"));
        if (decision != null) {
            return clamp(1.0 / (1.0 + Math.exp(decision * 6.0)));
        }
        if (rawScore != null) {
            return anomaly ? 0.85 : 0.15;
        }
        return null;
    }

    private RiskBand riskBand(double score) {
        if (score >= 0.75) return RiskBand.HIGH;
        if (score >= 0.60) return RiskBand.MEDIUM;
        if (score >= 0.40) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }

    private Double number(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : null;
    }

    private String string(Object value, String defaultValue) {
        return value == null || value.toString().isBlank() ? defaultValue : value.toString().trim();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
