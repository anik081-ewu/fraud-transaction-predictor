package com.ftd.fraud_transaction_detector.aml.research.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.research.domain.LayerScores;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.function.Consumer;

@Repository
public class ShadowLayerScoreReader {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ShadowLayerScoreReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) FROM dbo.aml_shadow_predictions", Long.class);
        return count == null ? 0 : count;
    }

    public void forEachOldest(Consumer<ShadowLayerScoreRow> consumer) {
        jdbcTemplate.query("""
                SELECT peer_group_code, customer_behaviour_score, peer_behaviour_score,
                       component_scores_json, rule_score, hard_rule_override
                FROM dbo.aml_shadow_predictions
                ORDER BY evaluated_at, shadow_prediction_id
                """, (RowCallbackHandler) resultSet -> consumer.accept(new ShadowLayerScoreRow(
                resultSet.getString("peer_group_code"),
                scores(resultSet.getString("component_scores_json"),
                        resultSet.getDouble("customer_behaviour_score"),
                        resultSet.getDouble("peer_behaviour_score"),
                        resultSet.getDouble("rule_score"),
                        resultSet.getBoolean("hard_rule_override"))
        )));
    }

    private LayerScores scores(
            String componentScoresJson,
            double customerBehaviourScore,
            double peerBehaviourScore,
            double ruleScore,
            boolean hardRuleOverride
    ) {
        if (componentScoresJson == null || componentScoresJson.isBlank()) {
            return new LayerScores(customerBehaviourScore, peerBehaviourScore, 0.0, ruleScore, hardRuleOverride);
        }
        try {
            JsonNode node = objectMapper.readTree(componentScoresJson);
            double mlEnsemble;
            if (node.has("mlEnsemble")) {
                mlEnsemble = number(node, "mlEnsemble", 0.0);
            } else {
                // backward-compatible read: average non-zero scores from old 3-field layout
                mlEnsemble = legacyMlEnsemble(node);
            }
            return new LayerScores(
                    number(node, "customerBehaviour", customerBehaviourScore),
                    number(node, "peerBehaviour", peerBehaviourScore),
                    mlEnsemble,
                    number(node, "rules", ruleScore),
                    hardRuleOverride
            );
        } catch (Exception exception) {
            return new LayerScores(customerBehaviourScore, peerBehaviourScore, 0.0, ruleScore, hardRuleOverride);
        }
    }

    private double legacyMlEnsemble(JsonNode node) {
        double isoForest = number(node, "isolationForest", -1.0);
        double hst = number(node, "halfSpaceTrees", -1.0);
        double onlineSvm = number(node, "onlineOneClassSvm", -1.0);
        double sum = 0.0;
        int count = 0;
        if (isoForest >= 0.0) { sum += isoForest; count++; }
        if (hst >= 0.0) { sum += hst; count++; }
        if (onlineSvm >= 0.0) { sum += onlineSvm; count++; }
        return count > 0 ? sum / count : 0.0;
    }

    private double number(JsonNode node, String field, double fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : fallback;
    }

    public record ShadowLayerScoreRow(String peerGroupCode, LayerScores scores) {
    }
}
