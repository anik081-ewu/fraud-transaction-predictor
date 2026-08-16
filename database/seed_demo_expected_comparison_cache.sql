USE [fraud-transaction-detector];
GO

/*
    PRESENTATION-ONLY CACHE SEED

    This inserts clearly labelled expected/demo results for the supervised and
    unsupervised comparison pages. It does not alter model artifacts or production
    scoring. The next real comparison run creates a newer study and replaces this view.

    The application intentionally excludes requested_by = 'demo-expected-cache' from
    supervised cache reuse, so "Run or load comparison" performs real computation.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;

/* Keep the script rerunnable in the same SSMS query window. */
DROP TABLE IF EXISTS #policy_metrics;
DROP TABLE IF EXISTS #ensemble_metrics;
DROP TABLE IF EXISTS #supervised_metrics;

BEGIN TRANSACTION;

DECLARE @unsupervisedRun UNIQUEIDENTIFIER = COALESCE(
    (
        SELECT TOP (1) training_run_id
        FROM dbo.aml_training_runs
        WHERE dataset_path IS NOT NULL
          AND dataset_checksum IS NOT NULL
          AND model_type IN ('UNSUPERVISED_ENSEMBLE', 'ISOLATION_FOREST',
                             'AUTOENCODER', 'BEHAVIORAL_CLUSTER_OUTLIER')
        ORDER BY CASE WHEN model_type = 'UNSUPERVISED_ENSEMBLE' THEN 0 ELSE 1 END,
                 created_at DESC
    ),
    /* Demo-only fallback when the database was reset after switching learning mode. */
    (
        SELECT TOP (1) training_run_id
        FROM dbo.aml_training_runs
        WHERE dataset_path IS NOT NULL
          AND dataset_checksum IS NOT NULL
          AND status IN ('DATASET_READY', 'CANDIDATE_READY')
        ORDER BY CASE WHEN model_type = 'SUPERVISED_ENSEMBLE' THEN 0 ELSE 1 END,
                 created_at DESC
    )
);

DECLARE @supervisedRun UNIQUEIDENTIFIER = (
    SELECT TOP (1) training_run_id
    FROM dbo.aml_training_runs
    WHERE dataset_path IS NOT NULL
      AND dataset_checksum IS NOT NULL
      AND model_type IN ('SUPERVISED_ENSEMBLE', 'XGBOOST_CLASSIFIER',
                         'RANDOM_FOREST_CLASSIFIER', 'EXTRA_TREES_CLASSIFIER')
      AND status IN ('DATASET_READY', 'CANDIDATE_READY')
    ORDER BY CASE WHEN model_type = 'SUPERVISED_ENSEMBLE' THEN 0 ELSE 1 END,
             created_at DESC
);

IF @unsupervisedRun IS NULL
    THROW 51200, 'No exported training snapshot exists for the demo view.', 1;

IF @supervisedRun IS NULL
    THROW 51200, 'No exported supervised training snapshot exists.', 1;

DECLARE @policyVersion NVARCHAR(100) = COALESCE(
    (SELECT config_value FROM dbo.app_config WHERE config_key = 'aml.risk.policy.version'),
    'AML_RISK_POLICY_V3'
);

/* Prevent an old long-running row from taking precedence over the demo result. */
UPDATE dbo.aml_growth_studies
SET status = 'FAILED', failure_reason = 'Superseded by one-time presentation cache.',
    completed_at = SYSUTCDATETIME()
WHERE status IN ('QUEUED', 'RUNNING');

UPDATE dbo.aml_supervised_growth_studies
SET status = 'FAILED', failure_reason = 'Superseded by one-time presentation cache.',
    completed_at = SYSUTCDATETIME()
WHERE status IN ('QUEUED', 'RUNNING');

/* -------------------------------------------------------------------------- */
/* Unsupervised expected growth result                                         */
/* -------------------------------------------------------------------------- */

DECLARE @unsupervisedStudy UNIQUEIDENTIFIER = NEWID();

INSERT INTO dbo.aml_growth_studies (
    study_id, training_run_id, status, feature_version, dataset_rows, feature_count,
    partition_percentages, methodology_json, requested_by, started_at, completed_at, created_at
) VALUES (
    @unsupervisedStudy, @unsupervisedRun, 'COMPLETED', 'AML_FEATURES_V4', 265000, 72,
    '10,25,50,100',
    N'{"learningMode":"UNSUPERVISED","resultOrigin":"DEMO_EXPECTED","warning":"Presentation-only expected values; run a real study to replace them."}',
    'demo-expected-cache', SYSUTCDATETIME(), SYSUTCDATETIME(), DATEADD(MILLISECOND, 100, SYSUTCDATETIME())
);

INSERT INTO dbo.aml_growth_metrics (
    study_id, detector, partition_percentage, partition_rows, training_rows,
    learned_rows, evaluation_rows, excess_mass_auc, score_skewness, rank_stability,
    anomaly_rate, alert_count, threshold, average_score, score_p50, score_p95,
    score_p99, training_duration_ms, rows_per_second, bounded_training_sample
)
VALUES
(@unsupervisedStudy,'ISOLATION_FOREST',10,26500,23850,23850,2650,0.742,0.78,0.82,0.0240,64,-0.036,-0.081,-0.074,-0.029,-0.011,2450,9735,0),
(@unsupervisedStudy,'ISOLATION_FOREST',25,66250,59625,59625,6625,0.768,0.81,0.86,0.0184,122,-0.034,-0.079,-0.071,-0.027,-0.010,4180,14264,0),
(@unsupervisedStudy,'ISOLATION_FOREST',50,132500,119250,119250,13250,0.791,0.84,0.90,0.0148,196,-0.032,-0.076,-0.069,-0.025,-0.009,7240,16471,0),
(@unsupervisedStudy,'ISOLATION_FOREST',100,265000,238500,238500,26500,0.812,0.87,0.93,0.0116,307,-0.030,-0.073,-0.066,-0.023,-0.008,13680,17434,0),
(@unsupervisedStudy,'AUTOENCODER',10,26500,23850,23850,2650,0.761,0.86,0.80,0.0260,69,1.080,0.412,0.291,0.947,1.426,8210,2905,0),
(@unsupervisedStudy,'AUTOENCODER',25,66250,59625,59625,6625,0.804,0.91,0.85,0.0200,133,1.095,0.398,0.276,0.921,1.391,16480,3618,0),
(@unsupervisedStudy,'AUTOENCODER',50,132500,119250,119250,13250,0.842,0.95,0.90,0.0152,201,1.102,0.381,0.261,0.889,1.347,29930,3984,0),
(@unsupervisedStudy,'AUTOENCODER',100,265000,238500,238500,26500,0.873,0.98,0.94,0.0104,276,1.110,0.366,0.249,0.851,1.302,55840,4271,0),
(@unsupervisedStudy,'BEHAVIORAL_CLUSTER_OUTLIER',10,26500,23850,23850,2650,0.718,0.73,0.78,0.0280,74,2.410,0.884,0.713,2.105,3.280,2740,8704,0),
(@unsupervisedStudy,'BEHAVIORAL_CLUSTER_OUTLIER',25,66250,59625,59625,6625,0.754,0.78,0.84,0.0216,143,2.360,0.821,0.668,1.984,3.041,5290,11271,0),
(@unsupervisedStudy,'BEHAVIORAL_CLUSTER_OUTLIER',50,132500,119250,119250,13250,0.798,0.83,0.89,0.0160,212,2.290,0.762,0.621,1.861,2.904,10380,11488,0),
(@unsupervisedStudy,'BEHAVIORAL_CLUSTER_OUTLIER',100,265000,238500,238500,26500,0.836,0.88,0.93,0.0120,318,2.240,0.711,0.582,1.742,2.771,22190,10748,0);

/* -------------------------------------------------------------------------- */
/* Supervised expected growth, fusion, and full risk-policy replay             */
/* -------------------------------------------------------------------------- */

CREATE TABLE #supervised_metrics (
    partitionPercentage INT, partitionRows INT, trainingRows INT, validationRows INT,
    evaluationRows INT, detector VARCHAR(50), prAuc FLOAT, rocAuc FLOAT, brierScore FLOAT,
    decisionThreshold FLOAT, trainingDurationMs FLOAT, rowsPerSecond FLOAT,
    trueNegative INT, falsePositive INT, falseNegative INT, truePositive INT
);

INSERT INTO #supervised_metrics VALUES
(10,26500,15900,2650,5300,'XGBoost',0.58,0.88,0.052,0.62,2460,185000,4929,106,106,159),
(25,66250,39750,6625,13250,'XGBoost',0.65,0.91,0.044,0.60,4530,296000,12381,201,233,435),
(50,132500,79500,13250,26500,'XGBoost',0.71,0.93,0.038,0.58,8220,418000,24804,371,403,922),
(100,265000,159000,26500,53000,'XGBoost',0.76,0.95,0.031,0.56,14760,526000,49682,668,742,1908),
(10,26500,15900,2650,5300,'RandomForestClassifier',0.56,0.86,0.055,0.58,1830,42000,4950,85,117,148),
(25,66250,39750,6625,13250,'RandomForestClassifier',0.63,0.90,0.046,0.56,3540,61000,12424,159,254,413),
(50,132500,79500,13250,26500,'RandomForestClassifier',0.70,0.93,0.037,0.54,6570,78000,24878,297,445,880),
(100,265000,159000,26500,53000,'RandomForestClassifier',0.77,0.95,0.029,0.52,12030,92000,49767,583,827,1823),
(10,26500,15900,2650,5300,'ExtraTreesClassifier',0.57,0.87,0.054,0.59,1620,51000,4918,117,95,170),
(25,66250,39750,6625,13250,'ExtraTreesClassifier',0.64,0.91,0.045,0.57,3120,73000,12349,233,212,456),
(50,132500,79500,13250,26500,'ExtraTreesClassifier',0.72,0.94,0.036,0.55,5850,96000,24740,435,350,975),
(100,265000,159000,26500,53000,'ExtraTreesClassifier',0.78,0.96,0.028,0.53,10680,118000,49587,763,689,1961);

CREATE TABLE #ensemble_metrics (
    partitionPercentage INT, partitionRows INT, trainingRows INT, tuningRows INT,
    validationRows INT, evaluationRows INT, strategy VARCHAR(80), label VARCHAR(100),
    prAuc FLOAT, rocAuc FLOAT, brierScore FLOAT, decisionThreshold FLOAT,
    rowsPerSecond FLOAT, trueNegative INT, falsePositive INT, falseNegative INT, truePositive INT
);

INSERT INTO #ensemble_metrics VALUES
(10,26500,15900,2650,2650,5300,'TEMPORAL_STACKING','Leakage-safe temporal stacking',0.63,0.90,0.047,0.58,160000,4950,85,85,180),
(25,66250,39750,6625,6625,13250,'TEMPORAL_STACKING','Leakage-safe temporal stacking',0.70,0.93,0.039,0.56,244000,12402,180,191,477),
(50,132500,79500,13250,13250,26500,'TEMPORAL_STACKING','Leakage-safe temporal stacking',0.77,0.95,0.031,0.54,352000,24857,318,318,1007),
(100,265000,159000,26500,26500,53000,'TEMPORAL_STACKING','Leakage-safe temporal stacking',0.83,0.97,0.024,0.52,448000,49820,530,636,2014),
(10,26500,15900,2650,2650,5300,'WEIGHTED_PROBABILITY','Weighted probability ensemble',0.61,0.89,0.049,0.57,205000,4940,95,95,170),
(25,66250,39750,6625,6625,13250,'WEIGHTED_PROBABILITY','Weighted probability ensemble',0.68,0.92,0.041,0.55,308000,12381,201,212,456),
(50,132500,79500,13250,13250,26500,'WEIGHTED_PROBABILITY','Weighted probability ensemble',0.75,0.95,0.033,0.53,441000,24815,360,360,965),
(100,265000,159000,26500,26500,53000,'WEIGHTED_PROBABILITY','Weighted probability ensemble',0.81,0.96,0.026,0.51,557000,49756,594,678,1972),
(10,26500,15900,2650,2650,5300,'MAJORITY_VOTE','Majority vote (2 of 3)',0.55,0.87,0.053,0.50,620000,4961,74,117,148),
(25,66250,39750,6625,6625,13250,'MAJORITY_VOTE','Majority vote (2 of 3)',0.62,0.90,0.046,0.50,910000,12434,148,265,403),
(50,132500,79500,13250,13250,26500,'MAJORITY_VOTE','Majority vote (2 of 3)',0.69,0.93,0.038,0.50,1310000,24910,265,466,859),
(100,265000,159000,26500,26500,53000,'MAJORITY_VOTE','Majority vote (2 of 3)',0.74,0.95,0.030,0.50,1810000,49841,509,763,1887);

CREATE TABLE #policy_metrics (
    partitionPercentage INT, partitionRows INT, evaluationRows INT,
    continuousPrAuc FLOAT, continuousRocAuc FLOAT, averageFinalScore FLOAT,
    caseTN INT, caseFP INT, caseFN INT, caseTP INT,
    strTN INT, strFP INT, strFN INT, strTP INT
);

INSERT INTO #policy_metrics VALUES
(10,26500,5300,0.65,0.91,0.112,4961,74,74,191,5003,32,127,138),
(25,66250,13250,0.72,0.94,0.108,12423,159,170,498,12518,64,297,371),
(50,132500,26500,0.79,0.96,0.105,24889,286,286,1039,25069,106,509,816),
(100,265000,53000,0.86,0.98,0.101,49862,488,583,2067,50138,212,1060,1590);

DECLARE @results NVARCHAR(MAX) = (
    SELECT partitionPercentage, partitionRows, trainingRows, validationRows, evaluationRows,
           detector, prAuc, prAuc / 0.05 AS prAucLift, rocAuc,
           CAST((trueNegative + truePositive) AS FLOAT) / evaluationRows AS accuracy,
           ((CAST(truePositive AS FLOAT) / NULLIF(truePositive + falseNegative, 0))
             + (CAST(trueNegative AS FLOAT) / NULLIF(trueNegative + falsePositive, 0))) / 2.0 AS balancedAccuracy,
           CAST(truePositive AS FLOAT) / NULLIF(truePositive + falsePositive, 0) AS precision,
           CAST(truePositive AS FLOAT) / NULLIF(truePositive + falseNegative, 0) AS recall,
           2.0 * truePositive / NULLIF(2.0 * truePositive + falsePositive + falseNegative, 0) AS f1,
           brierScore,
           CAST(truePositive + falsePositive AS FLOAT) / evaluationRows AS positiveRate,
           trueNegative, falsePositive, falseNegative, truePositive, decisionThreshold,
           trainingDurationMs, rowsPerSecond
    FROM #supervised_metrics
    ORDER BY detector, partitionPercentage
    FOR JSON PATH
);

DECLARE @ensembles NVARCHAR(MAX) = (
    SELECT partitionPercentage, partitionRows, trainingRows, tuningRows, validationRows,
           evaluationRows, strategy, label, 3 AS memberCount,
           JSON_QUERY('["XGBoost","RandomForestClassifier","ExtraTreesClassifier"]') AS members,
           'PROBABILITY' AS scoreType, prAuc, prAuc / 0.05 AS prAucLift, rocAuc,
           CAST((trueNegative + truePositive) AS FLOAT) / evaluationRows AS accuracy,
           ((CAST(truePositive AS FLOAT) / NULLIF(truePositive + falseNegative, 0))
             + (CAST(trueNegative AS FLOAT) / NULLIF(trueNegative + falsePositive, 0))) / 2.0 AS balancedAccuracy,
           CAST(truePositive AS FLOAT) / NULLIF(truePositive + falsePositive, 0) AS precision,
           CAST(truePositive AS FLOAT) / NULLIF(truePositive + falseNegative, 0) AS recall,
           2.0 * truePositive / NULLIF(2.0 * truePositive + falsePositive + falseNegative, 0) AS f1,
           brierScore,
           CAST(truePositive + falsePositive AS FLOAT) / evaluationRows AS positiveRate,
           trueNegative, falsePositive, falseNegative, truePositive, decisionThreshold,
           JSON_QUERY('{"XGBoost":0.38,"RandomForestClassifier":0.31,"ExtraTreesClassifier":0.31}') AS modelWeights,
           JSON_QUERY('{"origin":"DEMO_EXPECTED","selection":"chronological calibration"}') AS calibrationThresholdPolicy,
           rowsPerSecond
    FROM #ensemble_metrics
    ORDER BY strategy, partitionPercentage
    FOR JSON PATH
);

DECLARE @riskPolicies NVARCHAR(MAX) = (
    SELECT p.partitionPercentage, p.partitionRows, p.evaluationRows,
           'FULL_RISK_POLICY_BACKTEST_V2' AS protocol,
           @policyVersion AS policyVersion,
           'WEIGHTED_PROBABILITY' AS mlStrategy,
           'DEMO_EXPECTED' AS modelAllocationSource,
           JSON_QUERY('["XGBoost","RandomForestClassifier","ExtraTreesClassifier"]') AS selectedModels,
           JSON_QUERY('{"XGBoost":0.38,"RandomForestClassifier":0.31,"ExtraTreesClassifier":0.31}') AS modelWeights,
           JSON_QUERY('{"customerBehaviour":0.25,"peerBehaviour":0.10,"mlEnsemble":0.60,"rules":0.05}') AS componentWeights,
           JSON_QUERY('{"low":0.40,"mediumCase":0.65,"highStr":0.80}') AS thresholds,
           p.continuousPrAuc, p.continuousRocAuc, p.averageFinalScore,
           JSON_QUERY('{"customerBehaviour":0.16,"peerBehaviour":0.13,"mlEnsemble":0.31,"rules":0.08}') AS componentAverages,
           JSON_QUERY('["Presentation-only expected values; run a real comparison before making scientific claims."]') AS limitations,
           JSON_QUERY((SELECT
               CAST((p.caseTN + p.caseTP) AS FLOAT) / p.evaluationRows AS accuracy,
               ((CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFN, 0))
                 + (CAST(p.caseTN AS FLOAT) / NULLIF(p.caseTN + p.caseFP, 0))) / 2.0 AS balancedAccuracy,
               CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFP, 0) AS precision,
               CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFN, 0) AS recall,
               2.0 * p.caseTP / NULLIF(2.0 * p.caseTP + p.caseFP + p.caseFN, 0) AS f1,
               p.caseTN AS trueNegative, p.caseFP AS falsePositive, p.caseFN AS falseNegative, p.caseTP AS truePositive,
               p.caseFP + p.caseTP AS decisionCount,
               1000.0 * (p.caseFP + p.caseTP) / p.evaluationRows AS decisionsPer1000,
               p.caseTP AS fraudCaptured, p.caseFN AS fraudMissed
             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER)) AS caseDecision,
           JSON_QUERY((SELECT
               CAST((p.strTN + p.strTP) AS FLOAT) / p.evaluationRows AS accuracy,
               ((CAST(p.strTP AS FLOAT) / NULLIF(p.strTP + p.strFN, 0))
                 + (CAST(p.strTN AS FLOAT) / NULLIF(p.strTN + p.strFP, 0))) / 2.0 AS balancedAccuracy,
               CAST(p.strTP AS FLOAT) / NULLIF(p.strTP + p.strFP, 0) AS precision,
               CAST(p.strTP AS FLOAT) / NULLIF(p.strTP + p.strFN, 0) AS recall,
               2.0 * p.strTP / NULLIF(2.0 * p.strTP + p.strFP + p.strFN, 0) AS f1,
               p.strTN AS trueNegative, p.strFP AS falsePositive, p.strFN AS falseNegative, p.strTP AS truePositive,
               p.strFP + p.strTP AS decisionCount,
               1000.0 * (p.strFP + p.strTP) / p.evaluationRows AS decisionsPer1000,
               p.strTP AS fraudCaptured, p.strFN AS fraudMissed
             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER)) AS strDecision,
           JSON_QUERY((SELECT
               'WEIGHTED_PROBABILITY' AS baselineStrategy,
               (CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFP, 0))
                 - (CAST(e.truePositive AS FLOAT) / NULLIF(e.truePositive + e.falsePositive, 0)) AS precisionDelta,
               (CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFN, 0))
                 - (CAST(e.truePositive AS FLOAT) / NULLIF(e.truePositive + e.falseNegative, 0)) AS recallDelta,
               (2.0 * p.caseTP / NULLIF(2.0 * p.caseTP + p.caseFP + p.caseFN, 0))
                 - (2.0 * e.truePositive / NULLIF(2.0 * e.truePositive + e.falsePositive + e.falseNegative, 0)) AS f1Delta,
               (((CAST(p.caseTP AS FLOAT) / NULLIF(p.caseTP + p.caseFN, 0))
                 + (CAST(p.caseTN AS FLOAT) / NULLIF(p.caseTN + p.caseFP, 0))) / 2.0)
                 - (((CAST(e.truePositive AS FLOAT) / NULLIF(e.truePositive + e.falseNegative, 0))
                 + (CAST(e.trueNegative AS FLOAT) / NULLIF(e.trueNegative + e.falsePositive, 0))) / 2.0) AS balancedAccuracyDelta
             FOR JSON PATH, WITHOUT_ARRAY_WRAPPER)) AS versusMlEnsemble
    FROM #policy_metrics p
    INNER JOIN #ensemble_metrics e
      ON e.partitionPercentage = p.partitionPercentage
     AND e.strategy = 'WEIGHTED_PROBABILITY'
    ORDER BY p.partitionPercentage
    FOR JSON PATH
);

DECLARE @supervisedResult NVARCHAR(MAX) = (
    SELECT 'COMPLETED' AS status, 265000 AS datasetRows, 72 AS featureCount,
           'AML_FEATURES_V4' AS featureVersion,
           JSON_QUERY('[10,25,50,100]') AS partitionPercentages,
           JSON_QUERY('["XGBoost","RandomForestClassifier","ExtraTreesClassifier"]') AS detectors,
           JSON_QUERY('{"learningMode":"SUPERVISED","protocol":"FULL_RISK_POLICY_BACKTEST_V2","resultOrigin":"DEMO_EXPECTED","warning":"Presentation-only expected values; run a real comparison to replace them."}') AS methodology,
           JSON_QUERY(@results) AS results,
           JSON_QUERY(@ensembles) AS ensembles,
           JSON_QUERY(@riskPolicies) AS riskPolicyEvaluations
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
);

DECLARE @supervisedStudy UNIQUEIDENTIFIER = NEWID();

INSERT INTO dbo.aml_supervised_growth_studies (
    study_id, training_run_id, status, result_json, requested_by,
    started_at, completed_at, created_at
) VALUES (
    @supervisedStudy, @supervisedRun, 'COMPLETED', @supervisedResult,
    'demo-expected-cache', SYSUTCDATETIME(), SYSUTCDATETIME(),
    DATEADD(MILLISECOND, 200, SYSUTCDATETIME())
);

DROP TABLE IF EXISTS #policy_metrics;
DROP TABLE IF EXISTS #ensemble_metrics;
DROP TABLE IF EXISTS #supervised_metrics;

COMMIT TRANSACTION;

SELECT 'UNSUPERVISED' AS comparison_type, @unsupervisedStudy AS study_id, @unsupervisedRun AS training_run_id
UNION ALL
SELECT 'SUPERVISED', @supervisedStudy, @supervisedRun;
GO
