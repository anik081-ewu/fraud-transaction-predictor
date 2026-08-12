package com.ftd.fraud_transaction_detector.aml.research.infrastructure;

import com.ftd.fraud_transaction_detector.aml.research.domain.AgreementStudy;
import org.springframework.jdbc.core.RowMapper;
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
public class AgreementStudyRepository {

    private static final String COLUMNS = """
            study_id, training_run_id, status, evaluated_rows, model_count, result_json,
            requested_by, failure_reason, started_at, completed_at, created_at
            """;

    private static final RowMapper<AgreementStudy> MAPPER = (rs, rowNumber) -> new AgreementStudy(
            rs.getObject("study_id", UUID.class),
            rs.getObject("training_run_id", UUID.class),
            rs.getString("status"),
            nullableLong(rs, "evaluated_rows"),
            nullableInt(rs, "model_count"),
            rs.getString("result_json"),
            rs.getString("requested_by"),
            rs.getString("failure_reason"),
            nullableInstant(rs.getTimestamp("started_at")),
            nullableInstant(rs.getTimestamp("completed_at")),
            rs.getTimestamp("created_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AgreementStudyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(UUID trainingRunId, String requestedBy) {
        UUID studyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_agreement_studies (
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
                UPDATE dbo.aml_agreement_studies
                SET status = 'RUNNING', started_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, Map.of("studyId", studyId));
    }

    public void complete(UUID studyId, Long evaluatedRows, Integer modelCount, String resultJson) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_agreement_studies
                SET status = 'COMPLETED', evaluated_rows = :evaluatedRows,
                    model_count = :modelCount, result_json = :resultJson,
                    completed_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("evaluatedRows", evaluatedRows)
                .addValue("modelCount", modelCount)
                .addValue("resultJson", resultJson));
    }

    public void markFailed(UUID studyId, String reason) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_agreement_studies
                SET status = 'FAILED', failure_reason = :reason, completed_at = SYSUTCDATETIME()
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("reason", reason == null ? "Unknown failure"
                        : reason.length() <= 4000 ? reason : reason.substring(0, 4000)));
    }

    /**
     * In-flight study if one exists, else the newest completed result, else the newest
     * failure — so navigating away mid-run never makes the study look like it vanished.
     */
    public Optional<AgreementStudy> findLatestRelevant() {
        return jdbcTemplate.query(
                "SELECT TOP (1) " + COLUMNS + """
                         FROM dbo.aml_agreement_studies
                         ORDER BY CASE status
                                      WHEN 'RUNNING' THEN 0
                                      WHEN 'QUEUED' THEN 0
                                      WHEN 'COMPLETED' THEN 1
                                      ELSE 2
                                  END,
                                  created_at DESC
                        """,
                Map.of(), MAPPER
        ).stream().findFirst();
    }

    public Optional<AgreementStudy> find(UUID studyId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM dbo.aml_agreement_studies WHERE study_id = :studyId",
                Map.of("studyId", studyId), MAPPER
        ).stream().findFirst();
    }

    public List<AgreementStudy> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT TOP (:limit) " + COLUMNS
                        + " FROM dbo.aml_agreement_studies ORDER BY created_at DESC",
                Map.of("limit", limit), MAPPER
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
