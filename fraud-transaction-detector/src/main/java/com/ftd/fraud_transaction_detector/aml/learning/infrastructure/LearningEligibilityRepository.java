package com.ftd.fraud_transaction_detector.aml.learning.infrastructure;

import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityDecision;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningEligibilityRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LearningEligibilityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String transactionId, LearningEligibilityDecision decision) {
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_feature_learning_status (
                    transaction_id, eligibility_status, eligibility_reason,
                    eligible_for_incremental_model, eligible_for_trusted_profile,
                    eligible_for_batch_training, updated_at
                ) VALUES (
                    :transactionId, :status, :reason,
                    :incremental, :trustedProfile, :batchTraining, SYSUTCDATETIME()
                )
                """, new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("status", decision.status().name())
                .addValue("reason", decision.reason())
                .addValue("incremental", decision.eligibleForIncrementalModel())
                .addValue("trustedProfile", decision.eligibleForTrustedProfile())
                .addValue("batchTraining", decision.eligibleForBatchTraining()));
    }

    public boolean releaseFalsePositive(String transactionId, String reviewedBy) {
        int updated = jdbcTemplate.update("""
                UPDATE dbo.aml_feature_learning_status WITH (UPDLOCK, SERIALIZABLE)
                SET eligibility_status = 'LEARN_IMMEDIATELY',
                    eligibility_reason = 'Analyst confirmed false positive',
                    eligible_for_incremental_model = 1,
                    eligible_for_trusted_profile = 1,
                    eligible_for_batch_training = 1,
                    reviewed_by = :reviewedBy,
                    reviewed_at = SYSUTCDATETIME(),
                    updated_at = SYSUTCDATETIME()
                WHERE transaction_id = :transactionId
                  AND eligibility_status = 'WAIT_FOR_REVIEW'
                  AND eligible_for_trusted_profile = 0
                """, reviewParameters(transactionId, reviewedBy));
        return updated == 1;
    }

    public boolean rejectAsSuspicious(String transactionId, String reviewedBy, String reason) {
        int updated = jdbcTemplate.update("""
                UPDATE dbo.aml_feature_learning_status WITH (UPDLOCK, SERIALIZABLE)
                SET eligibility_status = 'DO_NOT_LEARN',
                    eligibility_reason = :reason,
                    eligible_for_incremental_model = 0,
                    eligible_for_trusted_profile = 0,
                    eligible_for_batch_training = 0,
                    reviewed_by = :reviewedBy,
                    reviewed_at = SYSUTCDATETIME(),
                    updated_at = SYSUTCDATETIME()
                WHERE transaction_id = :transactionId
                  AND eligibility_status IN ('WAIT_FOR_REVIEW', 'DELAYED_LEARNING')
                """, reviewParameters(transactionId, reviewedBy).addValue("reason", reason));
        return updated == 1;
    }

    private MapSqlParameterSource reviewParameters(String transactionId, String reviewedBy) {
        return new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("reviewedBy", reviewedBy);
    }
}
