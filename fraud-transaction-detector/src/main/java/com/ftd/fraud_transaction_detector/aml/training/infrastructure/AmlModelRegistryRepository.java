package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AmlModelRegistryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AmlModelRegistryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertCandidate(AmlModelRegistryEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_model_registry (
                    model_version, model_type, model_segment, feature_version,
                    training_run_id, artifact_path, artifact_checksum, dataset_checksum,
                    base_model_version, feature_schema_checksum, status, artifact_size_bytes,
                    anomaly_rate, validation_row_count, alert_count, average_score,
                    score_p95, score_p99, parameters_json, metrics_json, registered_by, created_at
                ) VALUES (
                    :modelVersion, :modelType, :modelSegment, :featureVersion,
                    :trainingRunId, :artifactPath, :artifactChecksum, :datasetChecksum,
                    :baseModelVersion, :featureSchemaChecksum, 'CANDIDATE', :artifactSizeBytes,
                    :anomalyRate, :validationRowCount, :alertCount, :averageScore,
                    :scoreP95, :scoreP99, :parametersJson, :metricsJson, :registeredBy, SYSUTCDATETIME()
                )
                """, parameters(entry));
    }

    public Optional<AmlModelRegistryEntry> find(String modelVersion) {
        return jdbcTemplate.query(selectSql() + " WHERE model_version = :modelVersion",
                Map.of("modelVersion", modelVersion), this::map).stream().findFirst();
    }

    public AmlModelRegistryEntry findRequired(String modelVersion) {
        return find(modelVersion).orElseThrow(
                () -> new IllegalArgumentException("AML model version not found: " + modelVersion));
    }

    public Optional<AmlModelRegistryEntry> findLatestCompatibleHst(String featureVersion, String modelSegment) {
        return findLatestCompatible("HALF_SPACE_TREES", featureVersion, modelSegment);
    }

    public Optional<AmlModelRegistryEntry> findLatestCompatible(
            String modelType,
            String featureVersion,
            String modelSegment
    ) {
        if (modelType == null || modelType.isBlank()) {
            throw new IllegalArgumentException("modelType is required");
        }
        String segmentFilter = modelSegment == null || modelSegment.isBlank()
                ? " AND registry.model_segment IS NULL\n"
                : " AND (registry.model_segment = :modelSegment OR registry.model_segment IS NULL)\n";
        String order = modelSegment == null || modelSegment.isBlank()
                ? " ORDER BY registry.created_at DESC"
                : " ORDER BY CASE WHEN registry.model_segment = :modelSegment THEN 0 ELSE 1 END, registry.created_at DESC";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("modelType", modelType.trim().toUpperCase())
                .addValue("featureVersion", featureVersion)
                .addValue("modelSegment", modelSegment);
        return jdbcTemplate.query("""
                SELECT TOP (1)
                       registry.model_version, registry.model_type, registry.model_segment,
                       registry.feature_version, registry.training_run_id, registry.artifact_path,
                       registry.artifact_checksum, registry.dataset_checksum, registry.base_model_version,
                       registry.feature_schema_checksum, registry.status, registry.artifact_size_bytes,
                       runs.learned_row_count, registry.anomaly_rate, registry.validation_row_count,
                       registry.alert_count, registry.average_score, registry.score_p95, registry.score_p99,
                       registry.parameters_json, registry.metrics_json, registry.registered_by, registry.created_at
                FROM dbo.aml_model_registry registry
                INNER JOIN dbo.aml_training_runs runs ON runs.training_run_id = registry.training_run_id
                WHERE registry.model_type = :modelType
                  AND registry.feature_version = :featureVersion
                  AND registry.status IN ('CANDIDATE', 'VALIDATED', 'APPROVED', 'CHALLENGER', 'CHAMPION')
                """ + segmentFilter + order, parameters, this::map).stream().findFirst();
    }

    public List<AmlModelRegistryEntry> search(String status, String modelType, String modelSegment) {
        StringBuilder sql = new StringBuilder(selectSql()).append(" WHERE 1 = 1");
        Map<String, Object> parameters = new HashMap<>();
        appendFilter(sql, parameters, "registry.status", "status", status);
        appendFilter(sql, parameters, "registry.model_type", "model_type", modelType);
        appendFilter(sql, parameters, "registry.model_segment", "model_segment", modelSegment);
        sql.append(" ORDER BY created_at DESC, model_version DESC");
        return jdbcTemplate.query(sql.toString(), parameters, this::map);
    }

    private MapSqlParameterSource parameters(AmlModelRegistryEntry entry) {
        return new MapSqlParameterSource()
                .addValue("modelVersion", entry.modelVersion()).addValue("modelType", entry.modelType())
                .addValue("modelSegment", entry.modelSegment()).addValue("featureVersion", entry.featureVersion())
                .addValue("trainingRunId", entry.trainingRunId()).addValue("artifactPath", entry.artifactPath())
                .addValue("artifactChecksum", entry.artifactChecksum()).addValue("datasetChecksum", entry.datasetChecksum())
                .addValue("baseModelVersion", entry.baseModelVersion())
                .addValue("featureSchemaChecksum", entry.featureSchemaChecksum())
                .addValue("artifactSizeBytes", entry.artifactSizeBytes()).addValue("anomalyRate", entry.anomalyRate())
                .addValue("validationRowCount", entry.validationRowCount()).addValue("alertCount", entry.alertCount())
                .addValue("averageScore", entry.averageScore()).addValue("scoreP95", entry.scoreP95())
                .addValue("scoreP99", entry.scoreP99()).addValue("parametersJson", entry.parametersJson())
                .addValue("metricsJson", entry.metricsJson()).addValue("registeredBy", entry.registeredBy());
    }

    private String selectSql() {
        return """
                SELECT registry.model_version, registry.model_type, registry.model_segment,
                       registry.feature_version, registry.training_run_id, registry.artifact_path,
                       registry.artifact_checksum, registry.dataset_checksum, registry.base_model_version,
                       registry.feature_schema_checksum, registry.status, registry.artifact_size_bytes,
                       runs.learned_row_count, registry.anomaly_rate, registry.validation_row_count,
                       registry.alert_count, registry.average_score, registry.score_p95, registry.score_p99,
                       registry.parameters_json, registry.metrics_json, registry.registered_by, registry.created_at
                FROM dbo.aml_model_registry registry
                INNER JOIN dbo.aml_training_runs runs ON runs.training_run_id = registry.training_run_id
                """;
    }

    private AmlModelRegistryEntry map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AmlModelRegistryEntry(
                resultSet.getString("model_version"), resultSet.getString("model_type"),
                resultSet.getString("model_segment"), resultSet.getString("feature_version"),
                resultSet.getObject("training_run_id", java.util.UUID.class),
                resultSet.getString("artifact_path"), resultSet.getString("artifact_checksum"),
                resultSet.getString("dataset_checksum"), resultSet.getString("base_model_version"),
                resultSet.getString("feature_schema_checksum"), resultSet.getString("status"),
                nullableLong(resultSet, "artifact_size_bytes"), nullableLong(resultSet, "learned_row_count"),
                nullableDouble(resultSet, "anomaly_rate"), nullableLong(resultSet, "validation_row_count"),
                nullableLong(resultSet, "alert_count"), nullableDouble(resultSet, "average_score"),
                nullableDouble(resultSet, "score_p95"), nullableDouble(resultSet, "score_p99"),
                resultSet.getString("parameters_json"), resultSet.getString("metrics_json"),
                resultSet.getString("registered_by"), nullableInstant(resultSet.getTimestamp("created_at"))
        );
    }

    private void appendFilter(
            StringBuilder sql,
            Map<String, Object> parameters,
            String column,
            String parameter,
            String value
    ) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append(" = :").append(parameter);
        parameters.put(parameter, value.trim());
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
