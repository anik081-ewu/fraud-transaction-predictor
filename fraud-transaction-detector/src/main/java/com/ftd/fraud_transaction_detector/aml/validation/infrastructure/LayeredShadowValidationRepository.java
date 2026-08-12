package com.ftd.fraud_transaction_detector.aml.validation.infrastructure;

import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationReport;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SegmentShadowMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioLabel;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioMetrics;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class LayeredShadowValidationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LayeredShadowValidationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LayeredShadowValidationMetrics calculate(
            String riskPolicyVersion,
            String peerGroupCode,
            Instant from,
            Instant to
    ) {
        MapSqlParameterSource parameters = parameters(riskPolicyVersion, peerGroupCode, from, to);
        Aggregate aggregate = jdbcTemplate.queryForObject(baseCte() + """
                SELECT COUNT_BIG(*) sample_count,
                       COUNT(DISTINCT CAST(base.evaluated_at AS DATE)) observation_days,
                       COALESCE(SUM(CASE WHEN base.legacy_suspicious = 1 THEN 1 ELSE 0 END), 0) legacy_alert_count,
                       COALESCE(SUM(CASE WHEN base.layered_suspicious = 1 THEN 1 ELSE 0 END), 0) layered_alert_count,
                       COALESCE(SUM(CASE WHEN base.legacy_suspicious = 1 AND base.layered_suspicious = 1 THEN 1 ELSE 0 END), 0) overlap_count,
                       COALESCE(SUM(CASE WHEN base.legacy_suspicious = 0 AND base.layered_suspicious = 1 THEN 1 ELSE 0 END), 0) layered_only_count,
                       COALESCE(SUM(CASE WHEN base.legacy_suspicious = 1 AND base.layered_suspicious = 0 THEN 1 ELSE 0 END), 0) legacy_only_count,
                       AVG(base.layered_final_risk_score) average_score,
                       STDEV(base.layered_final_risk_score) score_stddev,
                       AVG(CAST(base.duration_ms AS FLOAT)) average_latency_ms,
                       AVG(CASE WHEN base.hst_score IS NOT NULL THEN 1.0 ELSE 0.0 END) hst_availability,
                       AVG(CASE WHEN base.online_ocsvm_score IS NOT NULL THEN 1.0 ELSE 0.0 END) ocsvm_availability,
                       MAX(base.hst_model_version) hst_model_version,
                       COUNT(DISTINCT base.hst_model_version) hst_model_version_count,
                       MAX(base.online_ocsvm_model_version) ocsvm_model_version,
                       COUNT(DISTINCT base.online_ocsvm_model_version) ocsvm_model_version_count,
                       COALESCE(SUM(CASE WHEN base.layered_suspicious = 1
                                         AND review.review_status IN ('FALSE_POSITIVE', 'STR_GENERATED') THEN 1 ELSE 0 END), 0) reviewed_count,
                       COALESCE(SUM(CASE WHEN base.layered_suspicious = 1
                                         AND review.review_status = 'STR_GENERATED' THEN 1 ELSE 0 END), 0) reviewed_true_positive_count,
                       COALESCE(SUM(CASE WHEN base.layered_suspicious = 1
                                         AND review.review_status = 'FALSE_POSITIVE' THEN 1 ELSE 0 END), 0) reviewed_false_positive_count
                FROM base
                OUTER APPLY (
                    SELECT TOP (1) alert.review_status
                    FROM dbo.fraud_alerts alert
                    WHERE alert.transaction_id = base.transaction_id
                    ORDER BY alert.created_at DESC, alert.id DESC
                ) review
                """, parameters, (resultSet, rowNumber) -> new Aggregate(
                resultSet.getLong("sample_count"), resultSet.getInt("observation_days"),
                resultSet.getLong("legacy_alert_count"), resultSet.getLong("layered_alert_count"),
                resultSet.getLong("overlap_count"), resultSet.getLong("layered_only_count"),
                resultSet.getLong("legacy_only_count"), nullableDouble(resultSet, "average_score"),
                nullableDouble(resultSet, "score_stddev"), nullableDouble(resultSet, "average_latency_ms"),
                nullableDouble(resultSet, "hst_availability"), nullableDouble(resultSet, "ocsvm_availability"),
                resultSet.getString("hst_model_version"), resultSet.getInt("hst_model_version_count"),
                resultSet.getString("ocsvm_model_version"), resultSet.getInt("ocsvm_model_version_count"),
                resultSet.getLong("reviewed_count"), resultSet.getLong("reviewed_true_positive_count"),
                resultSet.getLong("reviewed_false_positive_count")
        ));
        if (aggregate == null || aggregate.sampleCount() == 0) return empty();

        Percentiles percentiles = jdbcTemplate.queryForObject(baseCte() + """
                SELECT TOP (1)
                       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY layered_final_risk_score) OVER () score_p50,
                       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY layered_final_risk_score) OVER () score_p95,
                       PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY layered_final_risk_score) OVER () score_p99,
                       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) OVER () latency_p95
                FROM base
                """, parameters, (resultSet, rowNumber) -> new Percentiles(
                nullableDouble(resultSet, "score_p50"), nullableDouble(resultSet, "score_p95"),
                nullableDouble(resultSet, "score_p99"), nullableDouble(resultSet, "latency_p95")
        ));
        TopRisk topRisk = percentiles == null || percentiles.scoreP99() == null
                ? new TopRisk(0, 0)
                : jdbcTemplate.queryForObject(baseCte() + """
                        SELECT COUNT_BIG(*) top_risk_count,
                               COALESCE(SUM(CASE WHEN legacy_suspicious = 1 THEN 1 ELSE 0 END), 0) top_risk_overlap_count
                        FROM base
                        WHERE layered_final_risk_score >= :topRiskThreshold
                        """, parameters.addValue("topRiskThreshold", percentiles.scoreP99()),
                (resultSet, rowNumber) -> new TopRisk(
                        resultSet.getLong("top_risk_count"), resultSet.getLong("top_risk_overlap_count")
                ));
        Double dailyStddev = jdbcTemplate.queryForObject(baseCte() + """
                , daily AS (
                    SELECT CAST(evaluated_at AS DATE) business_date,
                           AVG(CASE WHEN layered_suspicious = 1 THEN 1.0 ELSE 0.0 END) alert_rate
                    FROM base
                    GROUP BY CAST(evaluated_at AS DATE)
                )
                SELECT STDEV(alert_rate) FROM daily
                """, parameters, Double.class);
        List<SegmentShadowMetrics> segments = segmentMetrics(parameters);
        List<SyntheticScenarioMetrics> scenarios = syntheticMetrics(parameters);
        TrainingDurations training = trainingDurations(from, to);

        long union = aggregate.legacyAlertCount() + aggregate.layeredAlertCount() - aggregate.overlapCount();
        long syntheticExpected = scenarios.stream().mapToLong(SyntheticScenarioMetrics::expectedSuspiciousCount).sum();
        long syntheticDetected = scenarios.stream().mapToLong(SyntheticScenarioMetrics::detectedCount).sum();
        return new LayeredShadowValidationMetrics(
                aggregate.sampleCount(), aggregate.observationDays(), aggregate.legacyAlertCount(),
                aggregate.layeredAlertCount(), aggregate.overlapCount(), aggregate.layeredOnlyCount(),
                aggregate.legacyOnlyCount(), rate(aggregate.legacyAlertCount(), aggregate.sampleCount()),
                rate(aggregate.layeredAlertCount(), aggregate.sampleCount()),
                aggregate.legacyAlertCount() == 0 ? null
                        : (double) (aggregate.layeredAlertCount() - aggregate.legacyAlertCount()) / aggregate.legacyAlertCount(),
                rate(aggregate.sampleCount() - aggregate.layeredOnlyCount() - aggregate.legacyOnlyCount(),
                        aggregate.sampleCount()),
                union == 0 ? null : rate(aggregate.overlapCount(), union),
                topRisk == null ? 0 : topRisk.count(), topRisk == null ? 0 : topRisk.overlapCount(),
                topRisk == null || topRisk.count() == 0 ? null : rate(topRisk.overlapCount(), topRisk.count()),
                aggregate.averageScore(), aggregate.scoreStddev(),
                percentiles == null ? null : percentiles.scoreP50(),
                percentiles == null ? null : percentiles.scoreP95(),
                percentiles == null ? null : percentiles.scoreP99(), dailyStddev, segments,
                segments.stream().map(SegmentShadowMetrics::dailyAlertRateStandardDeviation)
                        .filter(java.util.Objects::nonNull).max(Double::compareTo).orElse(null),
                syntheticExpected, syntheticDetected,
                syntheticExpected == 0 ? null : rate(syntheticDetected, syntheticExpected), scenarios,
                aggregate.reviewedCount(), aggregate.reviewedTruePositiveCount(),
                aggregate.reviewedFalsePositiveCount(),
                aggregate.reviewedCount() == 0 ? null
                        : rate(aggregate.reviewedTruePositiveCount(), aggregate.reviewedCount()),
                aggregate.reviewedCount() == 0 ? null
                        : rate(aggregate.reviewedFalsePositiveCount(), aggregate.reviewedCount()),
                aggregate.averageLatencyMs(), percentiles == null ? null : percentiles.latencyP95(),
                training.count(), training.averageDurationMs(), training.maximumDurationMs(),
                aggregate.hstAvailability() == null ? 0.0 : aggregate.hstAvailability(),
                aggregate.ocsvmAvailability() == null ? 0.0 : aggregate.ocsvmAvailability(),
                aggregate.hstModelVersion(), aggregate.hstModelVersionCount(),
                aggregate.ocsvmModelVersion(), aggregate.ocsvmModelVersionCount()
        );
    }

    public void save(
            LayeredShadowValidationReport report,
            String blockingReasonsJson,
            String warningsJson,
            String metricsJson
    ) {
        LayeredShadowValidationMetrics metrics = report.metrics();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_layered_validations (
                    validation_id, risk_policy_version, peer_group_code,
                    window_started_at, window_ended_at, sample_count, observation_days,
                    legacy_alert_count, layered_alert_count, overlap_count, alert_jaccard,
                    top_risk_overlap_rate, daily_alert_rate_stddev, max_segment_daily_stddev,
                    synthetic_scenario_recall, reviewed_false_positive_rate,
                    prediction_latency_p95_ms, average_incremental_update_ms,
                    hst_availability_rate, online_ocsvm_availability_rate,
                    validation_status, blocking_reasons_json, warnings_json, metrics_json,
                    validated_by, validated_at
                ) VALUES (
                    :validationId, :policyVersion, :peerGroupCode,
                    :windowStartedAt, :windowEndedAt, :sampleCount, :observationDays,
                    :legacyAlertCount, :layeredAlertCount, :overlapCount, :alertJaccard,
                    :topRiskOverlapRate, :dailyStddev, :maxSegmentStddev,
                    :syntheticRecall, :falsePositiveRate,
                    :latencyP95, :averageUpdateMs,
                    :hstAvailability, :ocsvmAvailability,
                    :status, :blockingReasonsJson, :warningsJson, :metricsJson,
                    :validatedBy, :validatedAt
                )
                """, new MapSqlParameterSource()
                .addValue("validationId", report.validationId())
                .addValue("policyVersion", report.riskPolicyVersion())
                .addValue("peerGroupCode", report.peerGroupCode())
                .addValue("windowStartedAt", Timestamp.from(report.windowStartedAt()))
                .addValue("windowEndedAt", Timestamp.from(report.windowEndedAt()))
                .addValue("sampleCount", metrics.sampleCount())
                .addValue("observationDays", metrics.observationDays())
                .addValue("legacyAlertCount", metrics.legacyAlertCount())
                .addValue("layeredAlertCount", metrics.layeredAlertCount())
                .addValue("overlapCount", metrics.overlapCount())
                .addValue("alertJaccard", metrics.alertJaccard())
                .addValue("topRiskOverlapRate", metrics.topRiskOverlapRate())
                .addValue("dailyStddev", metrics.dailyLayeredAlertRateStandardDeviation())
                .addValue("maxSegmentStddev", metrics.maxSegmentDailyAlertRateStandardDeviation())
                .addValue("syntheticRecall", metrics.syntheticScenarioRecall())
                .addValue("falsePositiveRate", metrics.reviewedFalsePositiveRate())
                .addValue("latencyP95", metrics.predictionLatencyP95Ms())
                .addValue("averageUpdateMs", metrics.averageIncrementalUpdateMs())
                .addValue("hstAvailability", metrics.hstAvailabilityRate())
                .addValue("ocsvmAvailability", metrics.onlineOcSvmAvailabilityRate())
                .addValue("status", report.validationStatus())
                .addValue("blockingReasonsJson", blockingReasonsJson)
                .addValue("warningsJson", warningsJson)
                .addValue("metricsJson", metricsJson)
                .addValue("validatedBy", report.validatedBy())
                .addValue("validatedAt", Timestamp.from(report.validatedAt())));
    }

    public List<StoredValidation> findRecentStored() {
        return jdbcTemplate.query("""
                SELECT TOP (100) validation_id, risk_policy_version, peer_group_code,
                       window_started_at, window_ended_at, validation_status,
                       blocking_reasons_json, warnings_json, metrics_json,
                       validated_by, validated_at
                FROM dbo.aml_layered_validations
                ORDER BY validated_at DESC, validation_id DESC
                """, Map.of(), (resultSet, rowNumber) -> new StoredValidation(
                resultSet.getObject("validation_id", UUID.class),
                resultSet.getString("risk_policy_version"), resultSet.getString("peer_group_code"),
                resultSet.getTimestamp("window_started_at").toInstant(),
                resultSet.getTimestamp("window_ended_at").toInstant(),
                resultSet.getString("validation_status"), resultSet.getString("blocking_reasons_json"),
                resultSet.getString("warnings_json"), resultSet.getString("metrics_json"),
                resultSet.getString("validated_by"), resultSet.getTimestamp("validated_at").toInstant()
        ));
    }

    public java.util.Optional<StoredValidation> findStored(UUID validationId) {
        return jdbcTemplate.query("""
                SELECT validation_id, risk_policy_version, peer_group_code,
                       window_started_at, window_ended_at, validation_status,
                       blocking_reasons_json, warnings_json, metrics_json,
                       validated_by, validated_at
                FROM dbo.aml_layered_validations
                WHERE validation_id = :validationId
                """, Map.of("validationId", validationId), (resultSet, rowNumber) -> new StoredValidation(
                resultSet.getObject("validation_id", UUID.class),
                resultSet.getString("risk_policy_version"), resultSet.getString("peer_group_code"),
                resultSet.getTimestamp("window_started_at").toInstant(),
                resultSet.getTimestamp("window_ended_at").toInstant(),
                resultSet.getString("validation_status"), resultSet.getString("blocking_reasons_json"),
                resultSet.getString("warnings_json"), resultSet.getString("metrics_json"),
                resultSet.getString("validated_by"), resultSet.getTimestamp("validated_at").toInstant()
        )).stream().findFirst();
    }

    public SyntheticScenarioLabel saveScenarioLabel(SyntheticScenarioLabel label) {
        jdbcTemplate.update("""
                MERGE dbo.aml_shadow_scenario_labels AS target
                USING (VALUES (:transactionId, :scenarioCode)) AS source(transaction_id, scenario_code)
                ON target.transaction_id = source.transaction_id
                   AND target.scenario_code = source.scenario_code
                WHEN MATCHED THEN UPDATE SET
                    expected_suspicious = :expectedSuspicious,
                    labeled_by = :labeledBy,
                    created_at = :createdAt
                WHEN NOT MATCHED THEN INSERT (
                    scenario_label_id, transaction_id, scenario_code,
                    expected_suspicious, labeled_by, created_at
                ) VALUES (
                    :id, :transactionId, :scenarioCode,
                    :expectedSuspicious, :labeledBy, :createdAt
                );
                """, new MapSqlParameterSource()
                .addValue("id", label.scenarioLabelId()).addValue("transactionId", label.transactionId())
                .addValue("scenarioCode", label.scenarioCode()).addValue("expectedSuspicious", label.expectedSuspicious())
                .addValue("labeledBy", label.labeledBy()).addValue("createdAt", Timestamp.from(label.createdAt())));
        return jdbcTemplate.queryForObject("""
                SELECT scenario_label_id, transaction_id, scenario_code,
                       expected_suspicious, labeled_by, created_at
                FROM dbo.aml_shadow_scenario_labels
                WHERE transaction_id = :transactionId AND scenario_code = :scenarioCode
                """, Map.of("transactionId", label.transactionId(), "scenarioCode", label.scenarioCode()),
                (resultSet, rowNumber) -> new SyntheticScenarioLabel(
                        resultSet.getObject("scenario_label_id", UUID.class),
                        resultSet.getString("transaction_id"), resultSet.getString("scenario_code"),
                        resultSet.getBoolean("expected_suspicious"), resultSet.getString("labeled_by"),
                        resultSet.getTimestamp("created_at").toInstant()
                ));
    }

    public boolean shadowPredictionExists(String transactionId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.aml_shadow_predictions WHERE transaction_id = :transactionId
                """, Map.of("transactionId", transactionId), Long.class);
        return count != null && count > 0;
    }

    private List<SegmentShadowMetrics> segmentMetrics(MapSqlParameterSource parameters) {
        return jdbcTemplate.query(baseCte() + """
                , segment_totals AS (
                    SELECT COALESCE(peer_group_code, 'UNCLASSIFIED') peer_group_code,
                           COUNT_BIG(*) sample_count,
                           SUM(CASE WHEN layered_suspicious = 1 THEN 1 ELSE 0 END) alert_count
                    FROM base
                    GROUP BY COALESCE(peer_group_code, 'UNCLASSIFIED')
                ), segment_daily AS (
                    SELECT COALESCE(peer_group_code, 'UNCLASSIFIED') peer_group_code,
                           CAST(evaluated_at AS DATE) business_date,
                           AVG(CASE WHEN layered_suspicious = 1 THEN 1.0 ELSE 0.0 END) alert_rate
                    FROM base
                    GROUP BY COALESCE(peer_group_code, 'UNCLASSIFIED'), CAST(evaluated_at AS DATE)
                ), segment_stability AS (
                    SELECT peer_group_code, STDEV(alert_rate) daily_stddev
                    FROM segment_daily
                    GROUP BY peer_group_code
                )
                SELECT totals.peer_group_code, totals.sample_count, totals.alert_count,
                       CAST(totals.alert_count AS FLOAT) / NULLIF(totals.sample_count, 0) alert_rate,
                       stability.daily_stddev
                FROM segment_totals totals
                LEFT JOIN segment_stability stability ON stability.peer_group_code = totals.peer_group_code
                ORDER BY totals.peer_group_code
                """, parameters, (resultSet, rowNumber) -> new SegmentShadowMetrics(
                resultSet.getString("peer_group_code"), resultSet.getLong("sample_count"),
                resultSet.getLong("alert_count"), resultSet.getDouble("alert_rate"),
                nullableDouble(resultSet, "daily_stddev")
        ));
    }

    private List<SyntheticScenarioMetrics> syntheticMetrics(MapSqlParameterSource parameters) {
        return jdbcTemplate.query(baseCte() + """
                SELECT labels.scenario_code,
                       COUNT_BIG(*) expected_count,
                       SUM(CASE WHEN base.layered_suspicious = 1 THEN 1 ELSE 0 END) detected_count
                FROM base
                INNER JOIN dbo.aml_shadow_scenario_labels labels
                    ON labels.transaction_id = base.transaction_id
                   AND labels.expected_suspicious = 1
                GROUP BY labels.scenario_code
                ORDER BY labels.scenario_code
                """, parameters, (resultSet, rowNumber) -> {
            long expected = resultSet.getLong("expected_count");
            long detected = resultSet.getLong("detected_count");
            return new SyntheticScenarioMetrics(
                    resultSet.getString("scenario_code"), expected, detected,
                    expected == 0 ? null : rate(detected, expected)
            );
        });
    }

    private TrainingDurations trainingDurations(Instant from, Instant to) {
        TrainingDurations result = jdbcTemplate.queryForObject("""
                WITH durations AS (
                    SELECT CAST(DATEDIFF_BIG(MILLISECOND, started_at, completed_at) AS FLOAT) duration_ms
                    FROM dbo.aml_training_runs
                    WHERE training_type = 'DAILY_INCREMENTAL'
                      AND model_type IN ('HALF_SPACE_TREES', 'ONLINE_ONE_CLASS_SVM')
                      AND status = 'CANDIDATE_READY'
                      AND started_at IS NOT NULL AND completed_at IS NOT NULL
                      AND completed_at >= :fromTimestamp AND completed_at <= :toTimestamp
                )
                SELECT COUNT_BIG(*) update_count,
                       AVG(duration_ms) average_duration_ms,
                       MAX(duration_ms) maximum_duration_ms
                FROM durations
                """, new MapSqlParameterSource()
                .addValue("fromTimestamp", Timestamp.from(from)).addValue("toTimestamp", Timestamp.from(to)),
                (resultSet, rowNumber) -> new TrainingDurations(
                        resultSet.getLong("update_count"), nullableDouble(resultSet, "average_duration_ms"),
                        nullableDouble(resultSet, "maximum_duration_ms")
                ));
        return result == null ? new TrainingDurations(0, null, null) : result;
    }

    private String baseCte() {
        return """
                WITH ranked AS (
                    SELECT shadow.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY shadow.transaction_id, shadow.risk_policy_version
                               ORDER BY shadow.evaluated_at DESC, shadow.shadow_prediction_id DESC
                           ) row_number
                    FROM dbo.aml_shadow_predictions shadow
                    WHERE shadow.risk_policy_version = :policyVersion
                      AND shadow.evaluated_at >= :fromTimestamp
                      AND shadow.evaluated_at <= :toTimestamp
                      AND (:peerGroupCode IS NULL OR shadow.peer_group_code = :peerGroupCode)
                ), base AS (
                    SELECT * FROM ranked WHERE row_number = 1
                )
                """;
    }

    private MapSqlParameterSource parameters(
            String riskPolicyVersion,
            String peerGroupCode,
            Instant from,
            Instant to
    ) {
        return new MapSqlParameterSource().addValue("policyVersion", riskPolicyVersion)
                .addValue("peerGroupCode", peerGroupCode)
                .addValue("fromTimestamp", Timestamp.from(from)).addValue("toTimestamp", Timestamp.from(to));
    }

    private LayeredShadowValidationMetrics empty() {
        return new LayeredShadowValidationMetrics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, null,
                0, 0, null, null, null, null, null, null, null,
                List.of(), null, 0, 0, null, List.of(), 0, 0, 0,
                null, null, null, null, 0, null, null, 0, 0,
                null, 0, null, 0
        );
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private record Aggregate(
            long sampleCount, int observationDays, long legacyAlertCount, long layeredAlertCount,
            long overlapCount, long layeredOnlyCount, long legacyOnlyCount,
            Double averageScore, Double scoreStddev, Double averageLatencyMs,
            Double hstAvailability, Double ocsvmAvailability,
            String hstModelVersion, int hstModelVersionCount,
            String ocsvmModelVersion, int ocsvmModelVersionCount,
            long reviewedCount, long reviewedTruePositiveCount, long reviewedFalsePositiveCount
    ) {
    }

    private record Percentiles(Double scoreP50, Double scoreP95, Double scoreP99, Double latencyP95) {
    }

    private record TopRisk(long count, long overlapCount) {
    }

    private record TrainingDurations(long count, Double averageDurationMs, Double maximumDurationMs) {
    }

    public record StoredValidation(
            UUID validationId,
            String riskPolicyVersion,
            String peerGroupCode,
            Instant windowStartedAt,
            Instant windowEndedAt,
            String validationStatus,
            String blockingReasonsJson,
            String warningsJson,
            String metricsJson,
            String validatedBy,
            Instant validatedAt
    ) {
    }
}
