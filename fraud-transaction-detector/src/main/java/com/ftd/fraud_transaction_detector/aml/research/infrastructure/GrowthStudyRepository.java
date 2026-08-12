package com.ftd.fraud_transaction_detector.aml.research.infrastructure;

import com.ftd.fraud_transaction_detector.aml.research.domain.GrowthMetric;
import com.ftd.fraud_transaction_detector.aml.research.domain.GrowthStudy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class GrowthStudyRepository {

    private static final String STUDY_COLUMNS = """
            study_id, training_run_id, status, feature_version, dataset_rows, feature_count,
            partition_percentages, methodology_json, requested_by, failure_reason,
            started_at, completed_at, created_at
            """;

    private static final RowMapper<GrowthStudy> STUDY_MAPPER = (rs, rowNumber) -> new GrowthStudy(
            rs.getObject("study_id", UUID.class),
            rs.getObject("training_run_id", UUID.class),
            rs.getString("status"),
            rs.getString("feature_version"),
            nullableLong(rs, "dataset_rows"),
            nullableInt(rs, "feature_count"),
            parsePercentages(rs.getString("partition_percentages")),
            rs.getString("methodology_json"),
            rs.getString("requested_by"),
            rs.getString("failure_reason"),
            nullableInstant(rs.getTimestamp("started_at")),
            nullableInstant(rs.getTimestamp("completed_at")),
            rs.getTimestamp("created_at").toInstant(),
            List.of()
    );

    private static final RowMapper<GrowthMetric> METRIC_MAPPER = (rs, rowNumber) -> new GrowthMetric(
            rs.getString("detector"),
            rs.getInt("partition_percentage"),
            nullableLong(rs, "partition_rows"),
            nullableLong(rs, "training_rows"),
            nullableLong(rs, "learned_rows"),
            nullableLong(rs, "evaluation_rows"),
            nullableDouble(rs, "excess_mass_auc"),
            nullableDouble(rs, "score_skewness"),
            nullableDouble(rs, "rank_stability"),
            nullableDouble(rs, "anomaly_rate"),
            nullableLong(rs, "alert_count"),
            nullableDouble(rs, "threshold"),
            nullableDouble(rs, "average_score"),
            nullableDouble(rs, "score_p50"),
            nullableDouble(rs, "score_p95"),
            nullableDouble(rs, "score_p99"),
            nullableDouble(rs, "training_duration_ms"),
            nullableDouble(rs, "rows_per_second"),
            rs.getBoolean("bounded_training_sample")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GrowthStudyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(UUID trainingRunId, String requestedBy) {
        UUID studyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_growth_studies (
                    study_id, training_run_id, status, requested_by, created_at
                ) VALUES (:studyId, :trainingRunId, 'QUEUED', :requestedBy, SYSUTCDATETIME())
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("trainingRunId", trainingRunId)
                .addValue("requestedBy", requestedBy));
        return studyId;
    }

    public void markRunning(UUID studyId) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_growth_studies
                SET status = 'RUNNING', started_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, Map.of("studyId", studyId));
    }

    public void markFailed(UUID studyId, String reason) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_growth_studies
                SET status = 'FAILED', failure_reason = :reason, completed_at = SYSUTCDATETIME()
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("reason", abbreviate(reason)));
    }

    public void complete(
            UUID studyId,
            String featureVersion,
            Long datasetRows,
            Integer featureCount,
            List<Integer> partitionPercentages,
            String methodologyJson,
            List<GrowthMetric> metrics
    ) {
        insertMetrics(studyId, metrics);
        jdbcTemplate.update("""
                UPDATE dbo.aml_growth_studies
                SET status = 'COMPLETED', feature_version = :featureVersion,
                    dataset_rows = :datasetRows, feature_count = :featureCount,
                    partition_percentages = :partitions, methodology_json = :methodology,
                    completed_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("featureVersion", featureVersion)
                .addValue("datasetRows", datasetRows)
                .addValue("featureCount", featureCount)
                .addValue("partitions", partitionPercentages == null ? null : partitionPercentages.stream()
                        .map(String::valueOf).collect(Collectors.joining(",")))
                .addValue("methodology", methodologyJson));
    }

    private void insertMetrics(UUID studyId, List<GrowthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) return;
        MapSqlParameterSource[] batch = metrics.stream()
                .map(metric -> new MapSqlParameterSource()
                        .addValue("studyId", studyId)
                        .addValue("detector", metric.detector())
                        .addValue("partitionPercentage", metric.partitionPercentage())
                        .addValue("partitionRows", metric.partitionRows())
                        .addValue("trainingRows", metric.trainingRows())
                        .addValue("learnedRows", metric.learnedRows())
                        .addValue("evaluationRows", metric.evaluationRows())
                        .addValue("excessMassAuc", metric.excessMassAuc())
                        .addValue("scoreSkewness", metric.scoreSkewness())
                        .addValue("rankStability", metric.rankStability())
                        .addValue("anomalyRate", metric.anomalyRate())
                        .addValue("alertCount", metric.alertCount())
                        .addValue("threshold", metric.threshold())
                        .addValue("averageScore", metric.averageScore())
                        .addValue("scoreP50", metric.scoreP50())
                        .addValue("scoreP95", metric.scoreP95())
                        .addValue("scoreP99", metric.scoreP99())
                        .addValue("trainingDurationMs", metric.trainingDurationMs())
                        .addValue("rowsPerSecond", metric.rowsPerSecond())
                        .addValue("bounded", metric.boundedTrainingSample()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO dbo.aml_growth_metrics (
                    study_id, detector, partition_percentage, partition_rows, training_rows,
                    learned_rows, evaluation_rows, excess_mass_auc, score_skewness, rank_stability,
                    anomaly_rate, alert_count, threshold, average_score, score_p50, score_p95,
                    score_p99, training_duration_ms, rows_per_second, bounded_training_sample
                ) VALUES (
                    :studyId, :detector, :partitionPercentage, :partitionRows, :trainingRows,
                    :learnedRows, :evaluationRows, :excessMassAuc, :scoreSkewness, :rankStability,
                    :anomalyRate, :alertCount, :threshold, :averageScore, :scoreP50, :scoreP95,
                    :scoreP99, :trainingDurationMs, :rowsPerSecond, :bounded
                )
                """, batch);
    }

    public List<GrowthStudy> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT TOP (:limit) " + STUDY_COLUMNS
                        + " FROM dbo.aml_growth_studies ORDER BY created_at DESC",
                Map.of("limit", limit), STUDY_MAPPER
        );
    }

    /**
     * The study the page should show: an in-flight one if there is one, otherwise the newest
     * completed result, otherwise the newest failure.
     *
     * Filtering to COMPLETED alone made a running study vanish whenever the page was
     * remounted — the user saw "no study has been run yet" while one was actively running —
     * and hid failures entirely, so a study that died left no trace in the UI.
     */
    public Optional<GrowthStudy> findLatestRelevant() {
        return jdbcTemplate.query(
                "SELECT TOP (1) " + STUDY_COLUMNS + """
                         FROM dbo.aml_growth_studies
                         ORDER BY CASE status
                                      WHEN 'RUNNING' THEN 0
                                      WHEN 'QUEUED' THEN 0
                                      WHEN 'COMPLETED' THEN 1
                                      ELSE 2
                                  END,
                                  created_at DESC
                        """,
                Map.of(), STUDY_MAPPER
        ).stream().findFirst().map(this::withMetrics);
    }

    public Optional<GrowthStudy> find(UUID studyId) {
        return jdbcTemplate.query(
                "SELECT " + STUDY_COLUMNS + " FROM dbo.aml_growth_studies WHERE study_id = :studyId",
                Map.of("studyId", studyId), STUDY_MAPPER
        ).stream().findFirst().map(this::withMetrics);
    }

    private GrowthStudy withMetrics(GrowthStudy study) {
        List<GrowthMetric> metrics = jdbcTemplate.query("""
                SELECT detector, partition_percentage, partition_rows, training_rows, learned_rows,
                       evaluation_rows, excess_mass_auc, score_skewness, rank_stability,
                       anomaly_rate, alert_count, threshold, average_score, score_p50, score_p95,
                       score_p99, training_duration_ms, rows_per_second, bounded_training_sample
                FROM dbo.aml_growth_metrics
                WHERE study_id = :studyId
                ORDER BY detector, partition_percentage
                """, Map.of("studyId", study.studyId()), METRIC_MAPPER);
        return new GrowthStudy(
                study.studyId(), study.trainingRunId(), study.status(), study.featureVersion(),
                study.datasetRows(), study.featureCount(), study.partitionPercentages(),
                study.methodologyJson(), study.requestedBy(), study.failureReason(),
                study.startedAt(), study.completedAt(), study.createdAt(), metrics
        );
    }

    private static List<Integer> parsePercentages(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }

    private static String abbreviate(String value) {
        if (value == null) return "Unknown growth-analysis failure";
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() || !Double.isFinite(value) ? null : value;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
