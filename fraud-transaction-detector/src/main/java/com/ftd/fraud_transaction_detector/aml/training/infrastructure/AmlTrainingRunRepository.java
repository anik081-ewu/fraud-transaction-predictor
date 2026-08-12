package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.training.api.CreateTrainingRunRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AmlTrainingRunRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AmlTrainingRunRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AmlTrainingRun create(CreateTrainingRunRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_training_runs (
                    training_run_id, training_type, feature_version, model_type,
                    model_segment, from_business_date, to_business_date,
                    cutoff_timestamp, status, created_at
                ) VALUES (
                    :id, :trainingType, :featureVersion, :modelType,
                    :modelSegment, :fromDate, :toDate,
                    :cutoffTimestamp, 'CREATED', SYSUTCDATETIME()
                )
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("trainingType", request.trainingType().name())
                .addValue("featureVersion", request.featureVersion().trim())
                .addValue("modelType", normalizeModelType(request.modelType()))
                .addValue("modelSegment", normalize(request.modelSegment()))
                .addValue("fromDate", request.fromBusinessDate()).addValue("toDate", request.toBusinessDate())
                .addValue("cutoffTimestamp", request.cutoffTimestamp()));
        return findRequired(id);
    }

    public AmlTrainingRun createReadySibling(AmlTrainingRun source, String modelType) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_training_runs (
                    training_run_id, training_type, feature_version, model_type,
                    model_segment, from_business_date, to_business_date,
                    cutoff_timestamp, requested_row_count, exported_row_count,
                    dataset_path, dataset_checksum, status, completed_at, created_at
                ) VALUES (
                    :id, :trainingType, :featureVersion, :modelType,
                    :modelSegment, :fromDate, :toDate,
                    :cutoffTimestamp, :requestedRows, :exportedRows,
                    :datasetPath, :datasetChecksum, 'DATASET_READY', SYSUTCDATETIME(), SYSUTCDATETIME()
                )
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("trainingType", source.trainingType().name())
                .addValue("featureVersion", source.featureVersion()).addValue("modelType", modelType)
                .addValue("modelSegment", source.modelSegment()).addValue("fromDate", source.fromBusinessDate())
                .addValue("toDate", source.toBusinessDate()).addValue("cutoffTimestamp", source.cutoffTimestamp())
                .addValue("requestedRows", source.requestedRowCount()).addValue("exportedRows", source.exportedRowCount())
                .addValue("datasetPath", source.datasetPath()).addValue("datasetChecksum", source.datasetChecksum()));
        return findRequired(id);
    }

    private static final String SELECT_COLUMNS = """
            training_run_id, training_type, feature_version, model_type,
            model_segment, from_business_date, to_business_date,
            cutoff_timestamp, requested_row_count, exported_row_count,
            learned_row_count, dataset_path, dataset_checksum,
            base_model_version, candidate_model_version, status, failure_reason,
            started_at, completed_at, created_at,
            progress_stage, progress_current, progress_total
            """;

    private static final org.springframework.jdbc.core.RowMapper<AmlTrainingRun> ROW_MAPPER =
            (resultSet, rowNumber) -> new AmlTrainingRun(
                    resultSet.getObject("training_run_id", UUID.class),
                    com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType.valueOf(resultSet.getString("training_type")),
                    resultSet.getString("feature_version"), resultSet.getString("model_type"),
                    resultSet.getString("model_segment"), resultSet.getDate("from_business_date").toLocalDate(),
                    resultSet.getDate("to_business_date").toLocalDate(),
                    resultSet.getTimestamp("cutoff_timestamp").toLocalDateTime(),
                    nullableLong(resultSet, "requested_row_count"), nullableLong(resultSet, "exported_row_count"),
                    nullableLong(resultSet, "learned_row_count"),
                    resultSet.getString("dataset_path"), resultSet.getString("dataset_checksum"),
                    resultSet.getString("base_model_version"), resultSet.getString("candidate_model_version"),
                    resultSet.getString("status"), resultSet.getString("failure_reason"),
                    nullableInstant(resultSet.getTimestamp("started_at")),
                    nullableInstant(resultSet.getTimestamp("completed_at")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getString("progress_stage"),
                    nullableLong(resultSet, "progress_current"),
                    nullableLong(resultSet, "progress_total")
            );

    public Optional<AmlTrainingRun> find(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM dbo.aml_training_runs WHERE training_run_id = :id",
                Map.of("id", id), ROW_MAPPER
        ).stream().findFirst();
    }

    public List<AmlTrainingRun> listRecent() {
        return jdbcTemplate.query(
                "SELECT TOP (100) " + SELECT_COLUMNS
                        + " FROM dbo.aml_training_runs ORDER BY created_at DESC, training_run_id DESC",
                Map.of(), ROW_MAPPER
        );
    }

    /** Live sub-phase progress, polled by the training operations UI. */
    public void updateProgress(UUID id, String stage, long current, long total) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET progress_stage = :stage, progress_current = :current, progress_total = :total
                WHERE training_run_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("stage", stage)
                .addValue("current", current).addValue("total", total));
    }

    public AmlTrainingRun findRequired(UUID id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("AML training run not found: " + id));
    }

    public boolean queue(UUID id) {
        return jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'QUEUED', failure_reason = NULL
                WHERE training_run_id = :id
                  AND status IN ('CREATED', 'FAILED')
                """, Map.of("id", id)) == 1;
    }

    public void markExporting(UUID id, long requestedRows) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'EXPORTING', requested_row_count = :requestedRows,
                    started_at = SYSUTCDATETIME(), completed_at = NULL,
                    failure_reason = NULL
                WHERE training_run_id = :id AND status = 'QUEUED'
                """, Map.of("id", id, "requestedRows", requestedRows));
    }

    public void complete(UUID id, long exportedRows, String datasetPath, String checksum) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'DATASET_READY', exported_row_count = :exportedRows,
                    dataset_path = :datasetPath, dataset_checksum = :checksum,
                    completed_at = SYSUTCDATETIME(), failure_reason = NULL,
                    progress_stage = 'EXPORTED',
                    progress_current = :exportedRows, progress_total = :exportedRows
                WHERE training_run_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("exportedRows", exportedRows)
                .addValue("datasetPath", datasetPath).addValue("checksum", checksum));
    }

    public boolean startTraining(UUID id, String baseModelVersion) {
        return jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'TRAINING', base_model_version = :baseModelVersion,
                    started_at = SYSUTCDATETIME(), completed_at = NULL,
                    failure_reason = NULL,
                    progress_stage = 'TRAINING', progress_current = 0, progress_total = 0
                WHERE training_run_id = :id
                  AND status IN ('DATASET_READY', 'TRAINING_FAILED')
                  AND dataset_path IS NOT NULL
                  AND dataset_checksum IS NOT NULL
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("baseModelVersion", normalize(baseModelVersion))) == 1;
    }

    public boolean completeCandidate(UUID id, String candidateModelVersion, long learnedRowCount) {
        return jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'CANDIDATE_READY', candidate_model_version = :candidateModelVersion,
                    learned_row_count = :learnedRowCount, completed_at = SYSUTCDATETIME(),
                    failure_reason = NULL
                    -- progress_* is deliberately not touched: one model finishing does not
                    -- mean the pipeline is done. TrainingPipelineJobLauncher owns the
                    -- snapshot run's progress and marks COMPLETED only when all models land.
                WHERE training_run_id = :id AND status = 'TRAINING'
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("candidateModelVersion", candidateModelVersion)
                .addValue("learnedRowCount", learnedRowCount)) == 1;
    }

    public boolean failTraining(UUID id, String reason) {
        return jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'TRAINING_FAILED', failure_reason = :reason,
                    completed_at = SYSUTCDATETIME()
                WHERE training_run_id = :id AND status = 'TRAINING'
                """, Map.of("id", id, "reason", abbreviate(reason, 4000))) == 1;
    }

    public void fail(UUID id, String reason) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_training_runs
                SET status = 'FAILED', failure_reason = :reason,
                    completed_at = SYSUTCDATETIME()
                WHERE training_run_id = :id
                """, Map.of("id", id, "reason", abbreviate(reason, 4000)));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeModelType(String value) {
        return value == null || value.isBlank() ? "HALF_SPACE_TREES" : value.trim();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) return "Unknown export failure";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
