package com.ftd.fraud_transaction_detector.aml.validation.infrastructure;

import com.ftd.fraud_transaction_detector.aml.validation.domain.ChallengerMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.ModelValidationReport;
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
public class ModelValidationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ModelValidationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChallengerMetrics calculate(String modelVersion, double threshold, Instant from, Instant to) {
        MapSqlParameterSource parameters = parameters(modelVersion, threshold, from, to);
        Aggregate aggregate = jdbcTemplate.queryForObject("""
                WITH scored AS (
                    SELECT prediction.transaction_id,
                           prediction.incremental_model_score,
                           CASE WHEN prediction.incremental_model_score >= :threshold THEN 1 ELSE 0 END candidate_flag,
                           CASE WHEN prediction.suspicious_flag = 1 THEN 1 ELSE 0 END production_flag,
                           review.review_status
                    FROM dbo.fraud_prediction_logs prediction
                    OUTER APPLY (
                        SELECT TOP (1) alert.review_status
                        FROM dbo.fraud_alerts alert
                        WHERE alert.transaction_id = prediction.transaction_id
                        ORDER BY alert.created_at DESC, alert.id DESC
                    ) review
                    WHERE prediction.model_version = :modelVersion
                      AND prediction.incremental_model_score IS NOT NULL
                      AND prediction.created_at >= :fromTimestamp
                      AND prediction.created_at <= :toTimestamp
                )
                SELECT COUNT_BIG(*) sample_count,
                       COALESCE(SUM(candidate_flag), 0) candidate_count,
                       COALESCE(SUM(production_flag), 0) production_count,
                       COALESCE(SUM(CASE WHEN candidate_flag = 1 AND production_flag = 1 THEN 1 ELSE 0 END), 0) overlap_count,
                       COALESCE(SUM(CASE WHEN candidate_flag = 1 AND production_flag = 0 THEN 1 ELSE 0 END), 0) candidate_only_count,
                       COALESCE(SUM(CASE WHEN candidate_flag = 0 AND production_flag = 1 THEN 1 ELSE 0 END), 0) production_only_count,
                       AVG(incremental_model_score) average_score,
                       STDEV(incremental_model_score) score_stddev,
                       COALESCE(SUM(CASE WHEN candidate_flag = 1 AND production_flag = 1
                                         AND review_status IN ('FALSE_POSITIVE', 'STR_GENERATED') THEN 1 ELSE 0 END), 0) reviewed_overlap_count,
                       COALESCE(SUM(CASE WHEN candidate_flag = 1 AND production_flag = 1
                                         AND review_status = 'FALSE_POSITIVE' THEN 1 ELSE 0 END), 0) false_positive_count,
                       COALESCE(SUM(CASE WHEN candidate_flag = 1 AND production_flag = 1
                                         AND review_status = 'STR_GENERATED' THEN 1 ELSE 0 END), 0) str_count
                FROM scored
                """, parameters, (resultSet, rowNumber) -> new Aggregate(
                resultSet.getLong("sample_count"), resultSet.getLong("candidate_count"),
                resultSet.getLong("production_count"), resultSet.getLong("overlap_count"),
                resultSet.getLong("candidate_only_count"), resultSet.getLong("production_only_count"),
                nullableDouble(resultSet, "average_score"), nullableDouble(resultSet, "score_stddev"),
                resultSet.getLong("reviewed_overlap_count"), resultSet.getLong("false_positive_count"),
                resultSet.getLong("str_count")
        ));
        if (aggregate == null || aggregate.sampleCount == 0) return empty();
        Percentiles percentiles = jdbcTemplate.queryForObject("""
                SELECT TOP (1)
                       PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY incremental_model_score) OVER () score_p50,
                       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY incremental_model_score) OVER () score_p95,
                       PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY incremental_model_score) OVER () score_p99
                FROM dbo.fraud_prediction_logs
                WHERE model_version = :modelVersion
                  AND incremental_model_score IS NOT NULL
                  AND created_at >= :fromTimestamp AND created_at <= :toTimestamp
                """, parameters, (resultSet, rowNumber) -> new Percentiles(
                nullableDouble(resultSet, "score_p50"), nullableDouble(resultSet, "score_p95"),
                nullableDouble(resultSet, "score_p99")
        ));
        Double dailyStability = jdbcTemplate.queryForObject("""
                WITH daily AS (
                    SELECT CAST(created_at AS DATE) business_date,
                           AVG(CASE WHEN incremental_model_score >= :threshold THEN 1.0 ELSE 0.0 END) anomaly_rate
                    FROM dbo.fraud_prediction_logs
                    WHERE model_version = :modelVersion
                      AND incremental_model_score IS NOT NULL
                      AND created_at >= :fromTimestamp AND created_at <= :toTimestamp
                    GROUP BY CAST(created_at AS DATE)
                )
                SELECT STDEV(anomaly_rate) FROM daily
                """, parameters, Double.class);
        long union = aggregate.candidateCount + aggregate.productionCount - aggregate.overlapCount;
        double reviewedPrecision = aggregate.reviewedOverlapCount == 0 ? Double.NaN
                : (double) aggregate.strCount / aggregate.reviewedOverlapCount;
        return new ChallengerMetrics(
                aggregate.sampleCount, aggregate.candidateCount, aggregate.productionCount,
                aggregate.overlapCount, aggregate.candidateOnlyCount, aggregate.productionOnlyCount,
                rate(aggregate.candidateCount, aggregate.sampleCount),
                rate(aggregate.productionCount, aggregate.sampleCount),
                rate(aggregate.sampleCount - aggregate.candidateOnlyCount - aggregate.productionOnlyCount,
                        aggregate.sampleCount),
                union == 0 ? null : rate(aggregate.overlapCount, union),
                aggregate.averageScore, aggregate.scoreStddev,
                percentiles == null ? null : percentiles.p50,
                percentiles == null ? null : percentiles.p95,
                percentiles == null ? null : percentiles.p99,
                dailyStability, aggregate.reviewedOverlapCount, aggregate.falsePositiveCount,
                aggregate.strCount, Double.isNaN(reviewedPrecision) ? null : reviewedPrecision
        );
    }

    public void save(ModelValidationReport report, String metricsJson) {
        ChallengerMetrics metrics = report.metrics();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_model_validations (
                    validation_id, model_version, comparison_target, window_started_at, window_ended_at,
                    sample_count, candidate_anomaly_count, production_alert_count, overlap_count,
                    candidate_only_count, production_only_count, candidate_anomaly_rate,
                    production_alert_rate, agreement_rate, alert_jaccard, average_score, score_stddev,
                    score_p50, score_p95, score_p99, daily_anomaly_rate_stddev,
                    reviewed_overlap_count, false_positive_overlap_count, str_overlap_count,
                    reviewed_precision, validation_status, failure_reason, metrics_json,
                    validated_by, validated_at
                ) VALUES (
                    :validationId, :modelVersion, :comparisonTarget, :windowStartedAt, :windowEndedAt,
                    :sampleCount, :candidateCount, :productionCount, :overlapCount,
                    :candidateOnlyCount, :productionOnlyCount, :candidateRate,
                    :productionRate, :agreementRate, :alertJaccard, :averageScore, :scoreStddev,
                    :scoreP50, :scoreP95, :scoreP99, :dailyStddev,
                    :reviewedCount, :falsePositiveCount, :strCount,
                    :reviewedPrecision, :validationStatus, :failureReason, :metricsJson,
                    :validatedBy, :validatedAt
                )
                """, new MapSqlParameterSource()
                .addValue("validationId", report.validationId()).addValue("modelVersion", report.modelVersion())
                .addValue("comparisonTarget", report.comparisonTarget())
                .addValue("windowStartedAt", Timestamp.from(report.windowStartedAt()))
                .addValue("windowEndedAt", Timestamp.from(report.windowEndedAt()))
                .addValue("sampleCount", metrics.sampleCount()).addValue("candidateCount", metrics.candidateAnomalyCount())
                .addValue("productionCount", metrics.productionAlertCount()).addValue("overlapCount", metrics.overlapCount())
                .addValue("candidateOnlyCount", metrics.candidateOnlyCount()).addValue("productionOnlyCount", metrics.productionOnlyCount())
                .addValue("candidateRate", metrics.candidateAnomalyRate()).addValue("productionRate", metrics.productionAlertRate())
                .addValue("agreementRate", metrics.agreementRate()).addValue("alertJaccard", metrics.alertJaccard())
                .addValue("averageScore", metrics.averageScore()).addValue("scoreStddev", metrics.scoreStandardDeviation())
                .addValue("scoreP50", metrics.scoreP50()).addValue("scoreP95", metrics.scoreP95()).addValue("scoreP99", metrics.scoreP99())
                .addValue("dailyStddev", metrics.dailyAnomalyRateStandardDeviation())
                .addValue("reviewedCount", metrics.reviewedOverlapCount()).addValue("falsePositiveCount", metrics.falsePositiveOverlapCount())
                .addValue("strCount", metrics.strOverlapCount()).addValue("reviewedPrecision", metrics.reviewedPrecision())
                .addValue("validationStatus", report.validationStatus()).addValue("failureReason", report.failureReason())
                .addValue("metricsJson", metricsJson).addValue("validatedBy", report.validatedBy())
                .addValue("validatedAt", Timestamp.from(report.validatedAt())));
    }

    public void markValidated(String modelVersion, ChallengerMetrics metrics) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_model_registry
                SET status = 'VALIDATED', anomaly_rate = :anomalyRate,
                    validation_row_count = :rowCount, alert_count = :alertCount,
                    average_score = :averageScore, score_p95 = :scoreP95, score_p99 = :scoreP99
                WHERE model_version = :modelVersion AND status IN ('CANDIDATE', 'VALIDATED')
                """, new MapSqlParameterSource()
                .addValue("modelVersion", modelVersion).addValue("anomalyRate", metrics.candidateAnomalyRate())
                .addValue("rowCount", metrics.sampleCount()).addValue("alertCount", metrics.candidateAnomalyCount())
                .addValue("averageScore", metrics.averageScore()).addValue("scoreP95", metrics.scoreP95())
                .addValue("scoreP99", metrics.scoreP99()));
    }

    public List<ModelValidationReport> findByModelVersion(String modelVersion) {
        return jdbcTemplate.query("""
                SELECT * FROM dbo.aml_model_validations
                WHERE model_version = :modelVersion
                ORDER BY validated_at DESC
                """, Map.of("modelVersion", modelVersion), this::mapReport);
    }

    private ModelValidationReport mapReport(ResultSet resultSet, int rowNumber) throws SQLException {
        ChallengerMetrics metrics = new ChallengerMetrics(
                resultSet.getLong("sample_count"), resultSet.getLong("candidate_anomaly_count"),
                resultSet.getLong("production_alert_count"), resultSet.getLong("overlap_count"),
                resultSet.getLong("candidate_only_count"), resultSet.getLong("production_only_count"),
                resultSet.getDouble("candidate_anomaly_rate"), resultSet.getDouble("production_alert_rate"),
                resultSet.getDouble("agreement_rate"), nullableDouble(resultSet, "alert_jaccard"),
                nullableDouble(resultSet, "average_score"), nullableDouble(resultSet, "score_stddev"),
                nullableDouble(resultSet, "score_p50"), nullableDouble(resultSet, "score_p95"),
                nullableDouble(resultSet, "score_p99"), nullableDouble(resultSet, "daily_anomaly_rate_stddev"),
                resultSet.getLong("reviewed_overlap_count"), resultSet.getLong("false_positive_overlap_count"),
                resultSet.getLong("str_overlap_count"), nullableDouble(resultSet, "reviewed_precision")
        );
        return new ModelValidationReport(
                resultSet.getObject("validation_id", UUID.class), resultSet.getString("model_version"),
                resultSet.getString("comparison_target"), resultSet.getTimestamp("window_started_at").toInstant(),
                resultSet.getTimestamp("window_ended_at").toInstant(), metrics,
                resultSet.getString("validation_status"), resultSet.getString("failure_reason"),
                resultSet.getString("validated_by"), resultSet.getTimestamp("validated_at").toInstant()
        );
    }

    private MapSqlParameterSource parameters(String modelVersion, double threshold, Instant from, Instant to) {
        return new MapSqlParameterSource().addValue("modelVersion", modelVersion).addValue("threshold", threshold)
                .addValue("fromTimestamp", Timestamp.from(from)).addValue("toTimestamp", Timestamp.from(to));
    }

    private ChallengerMetrics empty() {
        return new ChallengerMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null, null, null, 0, 0, 0, null);
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private record Aggregate(
            long sampleCount, long candidateCount, long productionCount, long overlapCount,
            long candidateOnlyCount, long productionOnlyCount, Double averageScore, Double scoreStddev,
            long reviewedOverlapCount, long falsePositiveCount, long strCount
    ) {}

    private record Percentiles(Double p50, Double p95, Double p99) {}
}
