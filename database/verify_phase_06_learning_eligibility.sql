USE [fraud-transaction-detector];
GO

SELECT
    eligibility_status,
    eligible_for_incremental_model,
    eligible_for_trusted_profile,
    eligible_for_batch_training,
    COUNT_BIG(*) AS transaction_count
FROM dbo.aml_feature_learning_status
GROUP BY
    eligibility_status,
    eligible_for_incremental_model,
    eligible_for_trusted_profile,
    eligible_for_batch_training
ORDER BY eligibility_status;
GO

SELECT COUNT_BIG(*) AS feature_rows_without_learning_decision
FROM dbo.aml_transaction_features feature
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.aml_feature_learning_status learning
    WHERE learning.transaction_id = feature.transaction_id
);
GO

SELECT COUNT_BIG(*) AS suspicious_rows_incorrectly_eligible
FROM dbo.aml_feature_learning_status learning
WHERE learning.eligibility_status IN ('WAIT_FOR_REVIEW', 'DO_NOT_LEARN')
  AND (
      learning.eligible_for_incremental_model = 1
      OR learning.eligible_for_trusted_profile = 1
      OR learning.eligible_for_batch_training = 1
  );
GO
