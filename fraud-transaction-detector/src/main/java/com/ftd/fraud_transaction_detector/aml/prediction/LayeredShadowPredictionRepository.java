package com.ftd.fraud_transaction_detector.aml.prediction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LayeredShadowPredictionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LayeredShadowPredictionRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(LayeredShadowComparison comparison) {
        var layered = comparison.layeredResult();
        var customer = comparison.customerScore();
        var peer = comparison.peerScore();
        var rules = comparison.ruleResult();
        var hst = comparison.modelScores().get("HALF_SPACE_TREES");
        var onlineSvm = comparison.modelScores().get("ONLINE_ONE_CLASS_SVM");
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_shadow_predictions (
                    shadow_prediction_id, transaction_id, account_id, peer_group_code, feature_version,
                    risk_policy_version, legacy_risk_level, legacy_suspicious,
                    legacy_anomaly_votes, layered_final_risk_score, layered_risk_level,
                    layered_suspicious, hard_rule_override,
                    customer_behaviour_score, customer_behaviour_confidence,
                    peer_behaviour_score, peer_behaviour_confidence,
                    hst_score, hst_model_version, online_ocsvm_score, online_ocsvm_model_version,
                    rule_score, suspicious_changed, risk_level_changed, alert_overlap,
                    component_scores_json, triggered_rules_json, reason_codes_json,
                    layered_result_json, evaluated_at, duration_ms
                ) VALUES (
                    :id, :transactionId, :accountId, :peerGroupCode, :featureVersion,
                    :policyVersion, :legacyRiskLevel, :legacySuspicious,
                    :legacyVotes, :layeredScore, :layeredRiskLevel,
                    :layeredSuspicious, :hardOverride,
                    :customerScore, :customerConfidence,
                    :peerScore, :peerConfidence,
                    :hstScore, :hstVersion, :onlineSvmScore, :onlineSvmVersion,
                    :ruleScore, :suspiciousChanged, :riskLevelChanged, :alertOverlap,
                    :componentScoresJson, :triggeredRulesJson, :reasonCodesJson,
                    :layeredResultJson, :evaluatedAt, :durationMs
                )
                """, new MapSqlParameterSource()
                .addValue("id", comparison.shadowPredictionId())
                .addValue("transactionId", comparison.transactionId())
                .addValue("accountId", comparison.accountId())
                .addValue("peerGroupCode", peer.peerGroup())
                .addValue("featureVersion", comparison.featureVersion())
                .addValue("policyVersion", layered.riskPolicyVersion())
                .addValue("legacyRiskLevel", comparison.legacyRiskLevel())
                .addValue("legacySuspicious", comparison.legacySuspicious())
                .addValue("legacyVotes", comparison.legacyAnomalyVotes())
                .addValue("layeredScore", layered.finalRiskScore())
                .addValue("layeredRiskLevel", layered.riskLevel().name())
                .addValue("layeredSuspicious", layered.suspicious())
                .addValue("hardOverride", layered.hardRuleOverride())
                .addValue("customerScore", customer.score().normalizedScore())
                .addValue("customerConfidence", customer.confidence())
                .addValue("peerScore", peer.score().normalizedScore())
                .addValue("peerConfidence", peer.confidence())
                .addValue("hstScore", hst == null ? null : hst.score().normalizedScore())
                .addValue("hstVersion", hst == null ? null : hst.modelVersion())
                .addValue("onlineSvmScore", onlineSvm == null ? null : onlineSvm.score().normalizedScore())
                .addValue("onlineSvmVersion", onlineSvm == null ? null : onlineSvm.modelVersion())
                .addValue("ruleScore", rules.score().normalizedScore())
                .addValue("suspiciousChanged", comparison.suspiciousChanged())
                .addValue("riskLevelChanged", comparison.riskLevelChanged())
                .addValue("alertOverlap", comparison.alertOverlap())
                .addValue("componentScoresJson", json(layered.componentScores()))
                .addValue("triggeredRulesJson", json(rules.triggeredRules()))
                .addValue("reasonCodesJson", json(layered.reasonCodes()))
                .addValue("layeredResultJson", json(layered))
                .addValue("evaluatedAt", comparison.evaluatedAt())
                .addValue("durationMs", comparison.durationMs()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize layered shadow result", exception);
        }
    }
}
