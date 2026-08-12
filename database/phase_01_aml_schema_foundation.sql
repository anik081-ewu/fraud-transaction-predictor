USE [fraud-transaction-detector];
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.transactions', 'U') IS NULL
    THROW 51001, 'Required table dbo.transactions does not exist.', 1;
GO

IF OBJECT_ID('dbo.fraud_prediction_logs', 'U') IS NULL
    THROW 51002, 'Required table dbo.fraud_prediction_logs does not exist.', 1;
GO

/*
 * Extend the existing authoritative transaction table without changing current
 * application writes. customer_id and business_date remain nullable until the
 * Phase 2 transaction pipeline starts populating them point-in-time.
 */
IF COL_LENGTH('dbo.transactions', 'customer_id') IS NULL
    ALTER TABLE dbo.transactions ADD customer_id VARCHAR(100) NULL;
GO

UPDATE dbo.transactions
SET customer_id = account_id
WHERE customer_id IS NULL;
GO

IF COL_LENGTH('dbo.transactions', 'business_date') IS NULL
    ALTER TABLE dbo.transactions ADD business_date DATE NULL;
GO

UPDATE dbo.transactions
SET business_date = CAST(transaction_date AS DATE)
WHERE business_date IS NULL;
GO

IF COL_LENGTH('dbo.transactions', 'processing_status') IS NULL
    ALTER TABLE dbo.transactions ADD processing_status VARCHAR(30) NOT NULL
        CONSTRAINT DF_transactions_processing_status DEFAULT 'COMPLETED' WITH VALUES;
GO

IF COL_LENGTH('dbo.transactions', 'feature_status') IS NULL
    ALTER TABLE dbo.transactions ADD feature_status VARCHAR(30) NOT NULL
        CONSTRAINT DF_transactions_feature_status DEFAULT 'NOT_STARTED' WITH VALUES;
GO

IF COL_LENGTH('dbo.transactions', 'prediction_status') IS NULL
    ALTER TABLE dbo.transactions ADD prediction_status VARCHAR(30) NOT NULL
        CONSTRAINT DF_transactions_prediction_status DEFAULT 'NOT_STARTED' WITH VALUES;
GO

IF COL_LENGTH('dbo.transactions', 'updated_at') IS NULL
    ALTER TABLE dbo.transactions ADD updated_at DATETIME2 NOT NULL
        CONSTRAINT DF_transactions_updated_at DEFAULT SYSUTCDATETIME() WITH VALUES;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_customer_date'
)
    CREATE INDEX IX_transactions_customer_date
        ON dbo.transactions(customer_id, transaction_date DESC);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_business_date'
)
    CREATE INDEX IX_transactions_business_date
        ON dbo.transactions(business_date);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_feature_status_business_date'
)
    CREATE INDEX IX_transactions_feature_status_business_date
        ON dbo.transactions(feature_status, business_date);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_prediction_status_business_date'
)
    CREATE INDEX IX_transactions_prediction_status_business_date
        ON dbo.transactions(prediction_status, business_date);
GO

/* Immutable point-in-time feature rows. Application code must never update them. */
IF OBJECT_ID('dbo.aml_transaction_features', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_transaction_features (
        id BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_aml_transaction_features PRIMARY KEY,
        transaction_id VARCHAR(50) NOT NULL,
        customer_id VARCHAR(100) NOT NULL,
        account_id VARCHAR(50) NOT NULL,
        business_date DATE NOT NULL,
        transaction_date DATETIME2 NOT NULL,
        feature_version VARCHAR(30) NOT NULL,

        current_amount DECIMAL(19,4) NOT NULL,
        current_balance DECIMAL(19,4) NULL,
        amount_balance_ratio FLOAT NULL,
        transaction_hour INT NOT NULL,
        transaction_day_of_week INT NOT NULL,
        is_night BIT NOT NULL,
        is_weekend BIT NOT NULL,

        customer_history_count BIGINT NOT NULL,
        trusted_history_count BIGINT NOT NULL,
        recent_transaction_count INT NOT NULL,
        profile_confidence FLOAT NOT NULL,

        last_5_avg_amount FLOAT NULL,
        last_5_median_amount FLOAT NULL,
        last_30_avg_amount FLOAT NULL,
        last_30_median_amount FLOAT NULL,
        last_30_std_amount FLOAT NULL,
        last_30_max_amount FLOAT NULL,
        last_30_min_amount FLOAT NULL,
        amount_vs_last_30_avg FLOAT NULL,
        amount_vs_last_30_median FLOAT NULL,
        amount_z_score_last_30 FLOAT NULL,
        last_30_debit_ratio FLOAT NULL,
        last_30_credit_ratio FLOAT NULL,
        last_30_cash_ratio FLOAT NULL,
        last_30_unique_beneficiaries INT NULL,
        last_30_unique_locations INT NULL,
        last_30_unique_channels INT NULL,
        last_30_avg_time_gap_minutes FLOAT NULL,

        transaction_count_10m INT NOT NULL,
        transaction_count_1h INT NOT NULL,
        transaction_count_24h INT NOT NULL,
        transaction_count_7d INT NOT NULL,
        transaction_count_30d INT NOT NULL,
        amount_sum_10m FLOAT NOT NULL,
        amount_sum_1h FLOAT NOT NULL,
        amount_sum_24h FLOAT NOT NULL,
        amount_sum_7d FLOAT NOT NULL,
        amount_sum_30d FLOAT NOT NULL,
        unique_beneficiaries_1h INT NOT NULL,
        unique_beneficiaries_24h INT NOT NULL,
        unique_beneficiaries_7d INT NOT NULL,
        repeated_amount_count_24h INT NOT NULL,
        below_threshold_count_24h INT NOT NULL,
        below_threshold_amount_sum_24h FLOAT NOT NULL,

        new_beneficiary BIT NOT NULL,
        new_location BIT NOT NULL,
        new_channel BIT NOT NULL,
        new_device BIT NOT NULL,
        unusual_transaction_hour BIT NOT NULL,
        time_since_previous_transaction_minutes FLOAT NULL,

        peer_group_code VARCHAR(50) NULL,
        peer_avg_amount FLOAT NULL,
        peer_median_amount FLOAT NULL,
        peer_std_amount FLOAT NULL,
        amount_vs_peer_avg FLOAT NULL,
        peer_amount_z_score FLOAT NULL,
        peer_frequency_percentile FLOAT NULL,

        customer_type VARCHAR(50) NULL,
        customer_risk_rating VARCHAR(30) NULL,
        expected_monthly_turnover FLOAT NULL,
        amount_vs_expected_turnover FLOAT NULL,

        generated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_features_generated_at DEFAULT SYSUTCDATETIME(),
        generator_service_version VARCHAR(50) NOT NULL,

        CONSTRAINT UQ_aml_features_transaction UNIQUE (transaction_id),
        CONSTRAINT FK_aml_features_transaction FOREIGN KEY (transaction_id)
            REFERENCES dbo.transactions(transaction_id),
        CONSTRAINT CK_aml_features_profile_confidence
            CHECK (profile_confidence >= 0 AND profile_confidence <= 1)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_transaction_features') AND name = 'IX_aml_features_business_date')
    CREATE INDEX IX_aml_features_business_date ON dbo.aml_transaction_features(business_date);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_transaction_features') AND name = 'IX_aml_features_customer_date')
    CREATE INDEX IX_aml_features_customer_date ON dbo.aml_transaction_features(customer_id, transaction_date DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_transaction_features') AND name = 'IX_aml_features_version_date')
    CREATE INDEX IX_aml_features_version_date ON dbo.aml_transaction_features(feature_version, business_date);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_transaction_features') AND name = 'IX_aml_features_peer_group')
    CREATE INDEX IX_aml_features_peer_group ON dbo.aml_transaction_features(peer_group_code, business_date);
GO

IF OBJECT_ID('dbo.aml_customer_observed_profile', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_customer_observed_profile (
        customer_id VARCHAR(100) NOT NULL
            CONSTRAINT PK_aml_customer_observed_profile PRIMARY KEY,
        total_transaction_count BIGINT NOT NULL
            CONSTRAINT DF_aml_observed_count DEFAULT 0,
        total_transaction_amount DECIMAL(19,4) NOT NULL
            CONSTRAINT DF_aml_observed_amount DEFAULT 0,
        last_transaction_id VARCHAR(50) NULL,
        last_transaction_date DATETIME2 NULL,
        last_transaction_amount DECIMAL(19,4) NULL,
        last_location_code VARCHAR(100) NULL,
        last_channel VARCHAR(50) NULL,
        last_beneficiary_id VARCHAR(100) NULL,
        observed_avg_amount FLOAT NULL,
        observed_std_amount FLOAT NULL,
        observed_max_amount FLOAT NULL,
        updated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_observed_updated_at DEFAULT SYSUTCDATETIME(),
        row_version ROWVERSION NOT NULL
    );
END;
GO

IF OBJECT_ID('dbo.aml_customer_trusted_profile', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_customer_trusted_profile (
        customer_id VARCHAR(100) NOT NULL
            CONSTRAINT PK_aml_customer_trusted_profile PRIMARY KEY,
        trusted_transaction_count BIGINT NOT NULL
            CONSTRAINT DF_aml_trusted_count DEFAULT 0,
        trusted_avg_amount FLOAT NULL,
        trusted_variance_amount FLOAT NULL,
        trusted_std_amount FLOAT NULL,
        trusted_max_amount FLOAT NULL,
        trusted_min_amount FLOAT NULL,
        usual_start_hour INT NULL,
        usual_end_hour INT NULL,
        dominant_channel VARCHAR(50) NULL,
        dominant_location_code VARCHAR(100) NULL,
        profile_confidence FLOAT NOT NULL
            CONSTRAINT DF_aml_trusted_confidence DEFAULT 0,
        profile_status VARCHAR(30) NOT NULL
            CONSTRAINT DF_aml_trusted_status DEFAULT 'COLD_START',
        last_learned_transaction_id VARCHAR(50) NULL,
        last_learned_at DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_trusted_updated_at DEFAULT SYSUTCDATETIME(),
        row_version ROWVERSION NOT NULL,
        CONSTRAINT CK_aml_trusted_confidence
            CHECK (profile_confidence >= 0 AND profile_confidence <= 1),
        CONSTRAINT CK_aml_trusted_status CHECK (profile_status IN (
            'COLD_START', 'LOW_CONFIDENCE', 'DEVELOPING',
            'ESTABLISHED', 'FROZEN', 'UNDER_REVIEW'
        ))
    );
END;
GO

IF OBJECT_ID('dbo.aml_customer_recent_transactions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_customer_recent_transactions (
        customer_id VARCHAR(100) NOT NULL,
        transaction_id VARCHAR(50) NOT NULL,
        transaction_date DATETIME2 NOT NULL,
        transaction_amount DECIMAL(19,4) NOT NULL,
        transaction_type VARCHAR(50) NULL,
        channel VARCHAR(50) NULL,
        location_code VARCHAR(100) NULL,
        beneficiary_id VARCHAR(100) NULL,
        device_id VARCHAR(200) NULL,
        trusted_flag BIT NOT NULL,
        anomaly_risk_level VARCHAR(20) NULL,
        inserted_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_recent_inserted_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_aml_customer_recent_transactions
            PRIMARY KEY (customer_id, transaction_id),
        CONSTRAINT FK_aml_recent_transaction FOREIGN KEY (transaction_id)
            REFERENCES dbo.transactions(transaction_id)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_customer_recent_transactions') AND name = 'IX_aml_recent_customer_date')
    CREATE INDEX IX_aml_recent_customer_date
        ON dbo.aml_customer_recent_transactions(customer_id, transaction_date DESC);
GO

IF OBJECT_ID('dbo.aml_feature_learning_status', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_feature_learning_status (
        transaction_id VARCHAR(50) NOT NULL
            CONSTRAINT PK_aml_feature_learning_status PRIMARY KEY,
        eligibility_status VARCHAR(30) NOT NULL,
        eligibility_reason VARCHAR(500) NULL,
        eligible_for_incremental_model BIT NOT NULL,
        eligible_for_trusted_profile BIT NOT NULL,
        eligible_for_batch_training BIT NOT NULL,
        reviewed_by VARCHAR(100) NULL,
        reviewed_at DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_learning_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_aml_learning_transaction FOREIGN KEY (transaction_id)
            REFERENCES dbo.transactions(transaction_id),
        CONSTRAINT CK_aml_learning_status CHECK (eligibility_status IN (
            'LEARN_IMMEDIATELY', 'DELAYED_LEARNING', 'DO_NOT_LEARN', 'WAIT_FOR_REVIEW'
        ))
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_feature_learning_status') AND name = 'IX_aml_learning_incremental')
    CREATE INDEX IX_aml_learning_incremental
        ON dbo.aml_feature_learning_status(eligible_for_incremental_model, eligibility_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_feature_learning_status') AND name = 'IX_aml_learning_batch')
    CREATE INDEX IX_aml_learning_batch
        ON dbo.aml_feature_learning_status(eligible_for_batch_training, eligibility_status);
GO

IF OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_training_runs (
        training_run_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_training_runs PRIMARY KEY,
        training_type VARCHAR(50) NOT NULL,
        feature_version VARCHAR(30) NOT NULL,
        model_type VARCHAR(50) NOT NULL,
        model_segment VARCHAR(50) NULL,
        from_business_date DATE NOT NULL,
        to_business_date DATE NOT NULL,
        cutoff_timestamp DATETIME2 NOT NULL,
        requested_row_count BIGINT NULL,
        exported_row_count BIGINT NULL,
        learned_row_count BIGINT NULL,
        dataset_path VARCHAR(1000) NULL,
        dataset_checksum VARCHAR(200) NULL,
        base_model_version VARCHAR(100) NULL,
        candidate_model_version VARCHAR(100) NULL,
        status VARCHAR(30) NOT NULL,
        started_at DATETIME2 NULL,
        completed_at DATETIME2 NULL,
        failure_reason NVARCHAR(MAX) NULL,
        created_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_training_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_aml_training_type CHECK (training_type IN (
            'DAILY_INCREMENTAL', 'WEEKLY_BATCH', 'FULL_REBUILD', 'BACKTEST', 'REPLAY'
        )),
        CONSTRAINT CK_aml_training_dates CHECK (from_business_date <= to_business_date)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_training_runs') AND name = 'IX_aml_training_status_created')
    CREATE INDEX IX_aml_training_status_created
        ON dbo.aml_training_runs(status, created_at DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_training_runs') AND name = 'IX_aml_training_date_range')
    CREATE INDEX IX_aml_training_date_range
        ON dbo.aml_training_runs(from_business_date, to_business_date);
GO

IF OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_model_registry (
        model_version VARCHAR(100) NOT NULL
            CONSTRAINT PK_aml_model_registry PRIMARY KEY,
        model_type VARCHAR(50) NOT NULL,
        model_segment VARCHAR(50) NULL,
        feature_version VARCHAR(30) NOT NULL,
        training_run_id UNIQUEIDENTIFIER NOT NULL,
        artifact_path VARCHAR(1000) NOT NULL,
        artifact_checksum VARCHAR(200) NOT NULL,
        status VARCHAR(30) NOT NULL,
        anomaly_rate FLOAT NULL,
        validation_row_count BIGINT NULL,
        alert_count BIGINT NULL,
        average_score FLOAT NULL,
        score_p95 FLOAT NULL,
        score_p99 FLOAT NULL,
        created_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_registry_created_at DEFAULT SYSUTCDATETIME(),
        approved_at DATETIME2 NULL,
        deployed_at DATETIME2 NULL,
        retired_at DATETIME2 NULL,
        CONSTRAINT FK_aml_registry_training_run FOREIGN KEY (training_run_id)
            REFERENCES dbo.aml_training_runs(training_run_id),
        CONSTRAINT CK_aml_registry_status CHECK (status IN (
            'CANDIDATE', 'VALIDATED', 'APPROVED', 'CHAMPION',
            'CHALLENGER', 'REJECTED', 'RETIRED'
        ))
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_model_registry') AND name = 'IX_aml_registry_status_segment')
    CREATE INDEX IX_aml_registry_status_segment
        ON dbo.aml_model_registry(status, model_segment, model_type);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.aml_model_registry') AND name = 'IX_aml_registry_training_run')
    CREATE INDEX IX_aml_registry_training_run
        ON dbo.aml_model_registry(training_run_id);
GO

/* Additive audit fields on the current prediction log. All remain nullable in PR-01. */
IF COL_LENGTH('dbo.fraud_prediction_logs', 'feature_version') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD feature_version VARCHAR(30) NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'model_version') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD model_version VARCHAR(100) NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'customer_statistical_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD customer_statistical_score FLOAT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'incremental_model_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD incremental_model_score FLOAT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'batch_model_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD batch_model_score FLOAT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'rule_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD rule_score FLOAT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'final_risk_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD final_risk_score FLOAT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'suspicious_flag') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD suspicious_flag BIT NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'learning_decision') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD learning_decision VARCHAR(30) NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'learning_decision_reason') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD learning_decision_reason VARCHAR(500) NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'reason_codes') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD reason_codes NVARCHAR(MAX) NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'prediction_started_at') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD prediction_started_at DATETIME2 NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'prediction_completed_at') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD prediction_completed_at DATETIME2 NULL;
GO
IF COL_LENGTH('dbo.fraud_prediction_logs', 'prediction_duration_ms') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD prediction_duration_ms BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.fraud_prediction_logs') AND name = 'IX_prediction_logs_model_created')
    CREATE INDEX IX_prediction_logs_model_created
        ON dbo.fraud_prediction_logs(model_version, created_at DESC);
GO

PRINT 'Phase 01 AML schema foundation completed successfully.';
GO
