package com.ftd.fraud_transaction_detector.aml.deployment.infrastructure;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.ActiveModelPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ModelDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ModelDeploymentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ModelDeploymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ActiveModelPointer> lockPointer(String modelType, String modelSegment) {
        return jdbcTemplate.query("""
                SELECT model_type, model_segment, active_model_version, previous_model_version,
                       pointer_version, activated_by, activated_at
                FROM dbo.aml_active_models WITH (UPDLOCK, HOLDLOCK)
                WHERE model_type = :modelType
                  AND model_segment_key = :segmentKey
                """, scope(modelType, modelSegment), this::mapPointer).stream().findFirst();
    }

    public Optional<ActiveModelPointer> findCompatiblePointer(String modelType, String modelSegment) {
        return jdbcTemplate.query("""
                SELECT TOP (1) model_type, model_segment, active_model_version, previous_model_version,
                       pointer_version, activated_by, activated_at
                FROM dbo.aml_active_models
                WHERE model_type = :modelType
                  AND (model_segment_key = :segmentKey OR model_segment_key = 'GLOBAL')
                ORDER BY CASE WHEN model_segment_key = :segmentKey THEN 0 ELSE 1 END
                """, scope(modelType, modelSegment), this::mapPointer).stream().findFirst();
    }

    public List<ActiveModelPointer> listPointers() {
        return jdbcTemplate.query("""
                SELECT model_type, model_segment, active_model_version, previous_model_version,
                       pointer_version, activated_by, activated_at
                FROM dbo.aml_active_models
                ORDER BY model_type, ISNULL(model_segment, 'GLOBAL')
                """, this::mapPointer);
    }

    public Optional<ModelDeploymentEvent> findEvent(UUID actionId) {
        return jdbcTemplate.query(eventSql() + " WHERE action_id = :actionId",
                Map.of("actionId", actionId), this::mapEvent).stream().findFirst();
    }

    public List<ModelDeploymentEvent> history(String modelType, String modelSegment) {
        MapSqlParameterSource parameters = scope(modelType, modelSegment);
        return jdbcTemplate.query(eventSql() + """
                 WHERE model_type = :modelType
                   AND ISNULL(model_segment, 'GLOBAL') = :segmentKey
                 ORDER BY performed_at DESC
                """, parameters, this::mapEvent);
    }

    public void promote(
            AmlModelRegistryEntry candidate,
            ActiveModelPointer current,
            ModelDeploymentEvent event
    ) {
        String previous = current == null ? null : current.activeModelVersion();
        if (previous != null) {
            int retired = jdbcTemplate.update("""
                    UPDATE dbo.aml_model_registry
                    SET status = 'CHALLENGER', retired_at = SYSUTCDATETIME()
                    WHERE model_version = :modelVersion AND status = 'CHAMPION'
                    """, Map.of("modelVersion", previous));
            if (retired != 1) throw new IllegalStateException("Current champion changed during promotion");
        }
        int promoted = jdbcTemplate.update("""
                UPDATE dbo.aml_model_registry
                SET status = 'CHAMPION', approved_at = SYSUTCDATETIME(),
                    deployed_at = SYSUTCDATETIME(), retired_at = NULL
                WHERE model_version = :modelVersion AND status = 'VALIDATED'
                """, Map.of("modelVersion", candidate.modelVersion()));
        if (promoted != 1) throw new IllegalStateException("Validated candidate changed during promotion");
        upsertPointer(candidate, previous, event.performedBy());
        insertEvent(event);
    }

    public void rollback(
            AmlModelRegistryEntry current,
            AmlModelRegistryEntry previous,
            ModelDeploymentEvent event
    ) {
        int demoted = jdbcTemplate.update("""
                UPDATE dbo.aml_model_registry
                SET status = 'CHALLENGER', retired_at = SYSUTCDATETIME()
                WHERE model_version = :modelVersion AND status = 'CHAMPION'
                """, Map.of("modelVersion", current.modelVersion()));
        if (demoted != 1) throw new IllegalStateException("Current champion changed during rollback");
        int restored = jdbcTemplate.update("""
                UPDATE dbo.aml_model_registry
                SET status = 'CHAMPION', deployed_at = SYSUTCDATETIME(), retired_at = NULL
                WHERE model_version = :modelVersion AND status = 'CHALLENGER'
                """, Map.of("modelVersion", previous.modelVersion()));
        if (restored != 1) throw new IllegalStateException("Previous champion is no longer rollback eligible");
        int pointerUpdated = jdbcTemplate.update("""
                UPDATE dbo.aml_active_models
                SET active_model_version = :activeModelVersion,
                    previous_model_version = :previousModelVersion,
                    pointer_version = pointer_version + 1,
                    activated_by = :actor,
                    activated_at = SYSUTCDATETIME()
                WHERE model_type = :modelType AND model_segment_key = :segmentKey
                """, scope(current.modelType(), current.modelSegment())
                .addValue("activeModelVersion", previous.modelVersion())
                .addValue("previousModelVersion", current.modelVersion())
                .addValue("actor", event.performedBy()));
        if (pointerUpdated != 1) throw new IllegalStateException("Active-model pointer changed during rollback");
        insertEvent(event);
    }

    public void autoPromote(AmlModelRegistryEntry candidate, String previousVersion, String actor) {
        if (previousVersion != null) {
            jdbcTemplate.update("""
                    UPDATE dbo.aml_model_registry
                    SET status = 'CHALLENGER', retired_at = SYSUTCDATETIME()
                    WHERE model_version = :modelVersion AND status = 'CHAMPION'
                    """, Map.of("modelVersion", previousVersion));
        }
        jdbcTemplate.update("""
                UPDATE dbo.aml_model_registry
                SET status = 'CHAMPION', approved_at = SYSUTCDATETIME(),
                    deployed_at = SYSUTCDATETIME(), retired_at = NULL
                WHERE model_version = :modelVersion AND status = 'CANDIDATE'
                """, Map.of("modelVersion", candidate.modelVersion()));
        upsertPointer(candidate, previousVersion, actor);
    }

    private void upsertPointer(AmlModelRegistryEntry candidate, String previous, String actor) {
        MapSqlParameterSource parameters = scope(candidate.modelType(), candidate.modelSegment())
                .addValue("activeModelVersion", candidate.modelVersion())
                .addValue("previousModelVersion", previous)
                .addValue("actor", actor);
        int updated = jdbcTemplate.update("""
                UPDATE dbo.aml_active_models
                SET active_model_version = :activeModelVersion,
                    previous_model_version = :previousModelVersion,
                    pointer_version = pointer_version + 1,
                    activated_by = :actor,
                    activated_at = SYSUTCDATETIME()
                WHERE model_type = :modelType AND model_segment_key = :segmentKey
                """, parameters);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO dbo.aml_active_models (
                        model_type, model_segment, active_model_version, previous_model_version,
                        pointer_version, activated_by, activated_at
                    ) VALUES (
                        :modelType, :modelSegment, :activeModelVersion, :previousModelVersion,
                        1, :actor, SYSUTCDATETIME()
                    )
                    """, parameters);
        }
    }

    private void insertEvent(ModelDeploymentEvent event) {
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_model_deployments (
                    deployment_id, action_id, deployment_action, model_type, model_segment,
                    previous_model_version, activated_model_version, reason, performed_by, performed_at
                ) VALUES (
                    :deploymentId, :actionId, :deploymentAction, :modelType, :modelSegment,
                    :previousModelVersion, :activatedModelVersion, :reason, :performedBy, :performedAt
                )
                """, new MapSqlParameterSource()
                .addValue("deploymentId", event.deploymentId()).addValue("actionId", event.actionId())
                .addValue("deploymentAction", event.deploymentAction()).addValue("modelType", event.modelType())
                .addValue("modelSegment", event.modelSegment()).addValue("previousModelVersion", event.previousModelVersion())
                .addValue("activatedModelVersion", event.activatedModelVersion()).addValue("reason", event.reason())
                .addValue("performedBy", event.performedBy()).addValue("performedAt", Timestamp.from(event.performedAt())));
    }

    private MapSqlParameterSource scope(String modelType, String modelSegment) {
        return new MapSqlParameterSource()
                .addValue("modelType", modelType)
                .addValue("modelSegment", modelSegment)
                .addValue("segmentKey", modelSegment == null || modelSegment.isBlank() ? "GLOBAL" : modelSegment);
    }

    private String eventSql() {
        return """
                SELECT deployment_id, action_id, deployment_action, model_type, model_segment,
                       previous_model_version, activated_model_version, reason, performed_by, performed_at
                FROM dbo.aml_model_deployments
                """;
    }

    private ActiveModelPointer mapPointer(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ActiveModelPointer(
                resultSet.getString("model_type"), resultSet.getString("model_segment"),
                resultSet.getString("active_model_version"), resultSet.getString("previous_model_version"),
                resultSet.getLong("pointer_version"), resultSet.getString("activated_by"),
                resultSet.getTimestamp("activated_at").toInstant()
        );
    }

    private ModelDeploymentEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ModelDeploymentEvent(
                resultSet.getObject("deployment_id", UUID.class), resultSet.getObject("action_id", UUID.class),
                resultSet.getString("deployment_action"), resultSet.getString("model_type"),
                resultSet.getString("model_segment"), resultSet.getString("previous_model_version"),
                resultSet.getString("activated_model_version"), resultSet.getString("reason"),
                resultSet.getString("performed_by"), instant(resultSet.getTimestamp("performed_at"))
        );
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
