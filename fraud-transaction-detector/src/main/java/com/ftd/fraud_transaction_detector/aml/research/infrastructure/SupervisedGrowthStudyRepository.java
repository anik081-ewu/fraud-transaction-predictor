package com.ftd.fraud_transaction_detector.aml.research.infrastructure;

import com.ftd.fraud_transaction_detector.aml.research.domain.SupervisedGrowthStudy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SupervisedGrowthStudyRepository {

    private static final String COLUMNS = """
            study_id, training_run_id, status, result_json, requested_by, failure_reason,
            started_at, completed_at, created_at
            """;

    private static final RowMapper<SupervisedGrowthStudy> MAPPER = (rs, rowNumber) -> new SupervisedGrowthStudy(
            rs.getObject("study_id", UUID.class),
            rs.getObject("training_run_id", UUID.class),
            rs.getString("status"),
            rs.getString("result_json"),
            rs.getString("requested_by"),
            rs.getString("failure_reason"),
            instant(rs.getTimestamp("started_at")),
            instant(rs.getTimestamp("completed_at")),
            rs.getTimestamp("created_at").toInstant()
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SupervisedGrowthStudyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SupervisedGrowthStudy> findReusable(UUID trainingRunId) {
        return jdbcTemplate.query("SELECT TOP (1) " + COLUMNS + """
                        FROM dbo.aml_supervised_growth_studies
                        WHERE training_run_id = :trainingRunId
                          AND status IN ('QUEUED', 'RUNNING', 'COMPLETED')
                          AND (status IN ('QUEUED', 'RUNNING') OR JSON_QUERY(result_json, '$.ensembles') IS NOT NULL)
                        ORDER BY CASE status WHEN 'COMPLETED' THEN 0 ELSE 1 END, created_at DESC
                        """, Map.of("trainingRunId", trainingRunId), MAPPER)
                .stream().findFirst();
    }

    public UUID create(UUID trainingRunId, String requestedBy) {
        UUID studyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_supervised_growth_studies (
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
                UPDATE dbo.aml_supervised_growth_studies
                SET status = 'RUNNING', started_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, Map.of("studyId", studyId));
    }

    public void complete(UUID studyId, String resultJson) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_supervised_growth_studies
                SET status = 'COMPLETED', result_json = :resultJson,
                    completed_at = SYSUTCDATETIME(), failure_reason = NULL
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("resultJson", resultJson));
    }

    public void markFailed(UUID studyId, String reason) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_supervised_growth_studies
                SET status = 'FAILED', failure_reason = :reason, completed_at = SYSUTCDATETIME()
                WHERE study_id = :studyId
                """, new MapSqlParameterSource()
                .addValue("studyId", studyId)
                .addValue("reason", abbreviate(reason)));
    }

    public Optional<SupervisedGrowthStudy> latestRelevant() {
        return jdbcTemplate.query("SELECT TOP (1) " + COLUMNS + """
                        FROM dbo.aml_supervised_growth_studies
                        ORDER BY CASE status
                                     WHEN 'RUNNING' THEN 0 WHEN 'QUEUED' THEN 0
                                     WHEN 'COMPLETED' THEN 1 ELSE 2
                                 END, created_at DESC
                        """, Map.of(), MAPPER).stream().findFirst();
    }

    public Optional<SupervisedGrowthStudy> find(UUID studyId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM dbo.aml_supervised_growth_studies WHERE study_id = :studyId",
                Map.of("studyId", studyId), MAPPER
        ).stream().findFirst();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String abbreviate(String value) {
        if (value == null) return "Unknown supervised growth-analysis failure";
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }
}
