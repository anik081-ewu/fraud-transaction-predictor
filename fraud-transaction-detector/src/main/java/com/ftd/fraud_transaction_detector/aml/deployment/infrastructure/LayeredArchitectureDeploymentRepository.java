package com.ftd.fraud_transaction_detector.aml.deployment.infrastructure;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LayeredArchitectureDeploymentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LayeredArchitectureDeploymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LayeredDeploymentPointer> lockPointer(String peerGroupCode) {
        return jdbcTemplate.query(pointerSql() + """
                WITH (UPDLOCK, HOLDLOCK)
                WHERE peer_group_code = :peerGroupCode
                """, Map.of("peerGroupCode", peerGroupCode), this::mapPointer).stream().findFirst();
    }

    public Optional<LayeredDeploymentPointer> findPointer(String peerGroupCode) {
        return jdbcTemplate.query(pointerSql() + " WHERE peer_group_code = :peerGroupCode",
                Map.of("peerGroupCode", peerGroupCode), this::mapPointer).stream().findFirst();
    }

    public List<LayeredDeploymentPointer> pointers() {
        return jdbcTemplate.query(pointerSql() + " ORDER BY peer_group_code", this::mapPointer);
    }

    public Optional<LayeredDeploymentEvent> findEvent(UUID actionId) {
        return jdbcTemplate.query(eventSql() + " WHERE action_id = :actionId",
                Map.of("actionId", actionId), this::mapEvent).stream().findFirst();
    }

    public List<LayeredDeploymentEvent> history(String peerGroupCode) {
        String filter = peerGroupCode == null ? "" : " WHERE peer_group_code = :peerGroupCode";
        Map<String, ?> parameters = peerGroupCode == null ? Map.of() : Map.of("peerGroupCode", peerGroupCode);
        return jdbcTemplate.query(eventSql() + filter + " ORDER BY performed_at DESC, deployment_id DESC",
                parameters, this::mapEvent);
    }

    public void promote(
            LayeredDeploymentPointer pointer,
            LayeredDeploymentPointer current,
            LayeredDeploymentEvent event
    ) {
        MapSqlParameterSource parameters = pointerParameters(pointer);
        if (current == null) {
            jdbcTemplate.update("""
                    INSERT INTO dbo.aml_layered_deployment_pointers (
                        peer_group_code, deployment_mode, risk_policy_version,
                        hst_model_version, online_ocsvm_model_version, validation_id,
                        canary_percentage, pointer_version, activated_by, activated_at
                    ) VALUES (
                        :peerGroupCode, :mode, :policyVersion,
                        :hstVersion, :ocsvmVersion, :validationId,
                        :canaryPercentage, :pointerVersion, :activatedBy, :activatedAt
                    )
                    """, parameters);
        } else {
            int updated = jdbcTemplate.update("""
                    UPDATE dbo.aml_layered_deployment_pointers
                    SET deployment_mode = :mode,
                        risk_policy_version = :policyVersion,
                        hst_model_version = :hstVersion,
                        online_ocsvm_model_version = :ocsvmVersion,
                        validation_id = :validationId,
                        canary_percentage = :canaryPercentage,
                        pointer_version = :pointerVersion,
                        activated_by = :activatedBy,
                        activated_at = :activatedAt
                    WHERE peer_group_code = :peerGroupCode
                      AND pointer_version = :expectedPointerVersion
                    """, parameters.addValue("expectedPointerVersion", current.pointerVersion()));
            if (updated != 1) throw new IllegalStateException("Layered deployment pointer changed concurrently");
        }
        insertEvent(event);
    }

    public void rollback(
            LayeredDeploymentPointer current,
            LayeredDeploymentPointer fallback,
            LayeredDeploymentEvent event
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE dbo.aml_layered_deployment_pointers
                SET deployment_mode = :mode,
                    canary_percentage = 0,
                    pointer_version = :pointerVersion,
                    activated_by = :activatedBy,
                    activated_at = :activatedAt
                WHERE peer_group_code = :peerGroupCode
                  AND pointer_version = :expectedPointerVersion
                  AND deployment_mode = 'LAYERED_ACTIVE'
                """, pointerParameters(fallback).addValue("expectedPointerVersion", current.pointerVersion()));
        if (updated != 1) throw new IllegalStateException("Layered deployment pointer changed concurrently");
        insertEvent(event);
    }

    private void insertEvent(LayeredDeploymentEvent event) {
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_layered_deployment_events (
                    deployment_id, action_id, deployment_action, peer_group_code,
                    previous_mode, activated_mode, risk_policy_version,
                    hst_model_version, online_ocsvm_model_version, validation_id,
                    previous_canary_percentage, activated_canary_percentage,
                    reason, performed_by, performed_at
                ) VALUES (
                    :deploymentId, :actionId, :action, :peerGroupCode,
                    :previousMode, :activatedMode, :policyVersion,
                    :hstVersion, :ocsvmVersion, :validationId,
                    :previousCanary, :activatedCanary,
                    :reason, :performedBy, :performedAt
                )
                """, new MapSqlParameterSource()
                .addValue("deploymentId", event.deploymentId()).addValue("actionId", event.actionId())
                .addValue("action", event.deploymentAction()).addValue("peerGroupCode", event.peerGroupCode())
                .addValue("previousMode", event.previousMode()).addValue("activatedMode", event.activatedMode())
                .addValue("policyVersion", event.riskPolicyVersion()).addValue("hstVersion", event.hstModelVersion())
                .addValue("ocsvmVersion", event.onlineOcSvmModelVersion()).addValue("validationId", event.validationId())
                .addValue("previousCanary", event.previousCanaryPercentage())
                .addValue("activatedCanary", event.activatedCanaryPercentage())
                .addValue("reason", event.reason()).addValue("performedBy", event.performedBy())
                .addValue("performedAt", Timestamp.from(event.performedAt())));
    }

    private MapSqlParameterSource pointerParameters(LayeredDeploymentPointer pointer) {
        return new MapSqlParameterSource()
                .addValue("peerGroupCode", pointer.peerGroupCode()).addValue("mode", pointer.deploymentMode())
                .addValue("policyVersion", pointer.riskPolicyVersion()).addValue("hstVersion", pointer.hstModelVersion())
                .addValue("ocsvmVersion", pointer.onlineOcSvmModelVersion()).addValue("validationId", pointer.validationId())
                .addValue("canaryPercentage", pointer.canaryPercentage()).addValue("pointerVersion", pointer.pointerVersion())
                .addValue("activatedBy", pointer.activatedBy()).addValue("activatedAt", Timestamp.from(pointer.activatedAt()));
    }

    private String pointerSql() {
        return """
                SELECT peer_group_code, deployment_mode, risk_policy_version,
                       hst_model_version, online_ocsvm_model_version, validation_id,
                       canary_percentage, pointer_version, activated_by, activated_at
                FROM dbo.aml_layered_deployment_pointers
                """;
    }

    private String eventSql() {
        return """
                SELECT deployment_id, action_id, deployment_action, peer_group_code,
                       previous_mode, activated_mode, risk_policy_version,
                       hst_model_version, online_ocsvm_model_version, validation_id,
                       previous_canary_percentage, activated_canary_percentage,
                       reason, performed_by, performed_at
                FROM dbo.aml_layered_deployment_events
                """;
    }

    private LayeredDeploymentPointer mapPointer(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LayeredDeploymentPointer(
                resultSet.getString("peer_group_code"), resultSet.getString("deployment_mode"),
                resultSet.getString("risk_policy_version"), resultSet.getString("hst_model_version"),
                resultSet.getString("online_ocsvm_model_version"), resultSet.getObject("validation_id", UUID.class),
                resultSet.getInt("canary_percentage"), resultSet.getLong("pointer_version"),
                resultSet.getString("activated_by"), resultSet.getTimestamp("activated_at").toInstant()
        );
    }

    private LayeredDeploymentEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LayeredDeploymentEvent(
                resultSet.getObject("deployment_id", UUID.class), resultSet.getObject("action_id", UUID.class),
                resultSet.getString("deployment_action"), resultSet.getString("peer_group_code"),
                resultSet.getString("previous_mode"), resultSet.getString("activated_mode"),
                resultSet.getString("risk_policy_version"), resultSet.getString("hst_model_version"),
                resultSet.getString("online_ocsvm_model_version"), resultSet.getObject("validation_id", UUID.class),
                nullableInteger(resultSet, "previous_canary_percentage"),
                resultSet.getInt("activated_canary_percentage"), resultSet.getString("reason"),
                resultSet.getString("performed_by"), resultSet.getTimestamp("performed_at").toInstant()
        );
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
