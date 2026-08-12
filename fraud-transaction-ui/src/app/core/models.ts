export interface BulkUploadStartResponse {
  batchNo: string;
  status: string;
}

export interface BatchSummaryResponse {
  batchNo: string;
  fileName: string;
  totalRows: number | null;
  successRows: number | null;
  failedRows: number | null;
  uploadedBy: string | null;
  uploadedAt: string;
  status: string;
  minTransactionDate?: string | null;
  maxTransactionDate?: string | null;
}

export interface BulkUploadResponse {
  batchNo: string;
  fileName: string;
  totalRows: number;
  successRows: number;
  failedRows: number;
  uploadedDatasetId?: number | null;
  uploadedDatasetNo?: string | null;
  errors: Array<{ rowNumber: number; message: string }>;
}

export interface DatasetPartition {
  id: number;
  partitionNo: string;
  partitionLabel: string;
  partitionSize: number;
  orderingStrategy: string;
  startRowNo: number;
  endRowNo: number;
  createdAt: string;
}

export interface UploadedDataset {
  id: number;
  datasetNo: string;
  fileName: string;
  totalRows: number;
  sourceBatchId?: number | null;
  sourceType: 'UPLOAD_BATCH' | 'DATABASE_SNAPSHOT';
  snapshotMaxTransactionId?: number | null;
  uploadedBy?: string | null;
  uploadedAt: string;
  status: string;
  notes?: string | null;
  partitions: DatasetPartition[];
}

export interface ModelVersion {
  id: number;
  modelVersionNo: string;
  trainingRunId: number;
  datasetPartitionId: number;
  modelName: string;
  partitionSize: number;
  artifactBasePath: string;
  featureColumnsPath?: string | null;
  scalerPath?: string | null;
  modelPath: string;
  metricsJson?: string | null;
  isActive: boolean;
  lifecycleStatus: string;
  promotedAt?: string | null;
  promotedBy?: string | null;
  createdAt: string;
}

export interface AnomalyConfig {
  id: number;
  configNo: string;
  configName: string;
  enabledModels: string[];
  votingStrategy: string;
  suspiciousVoteThreshold: number;
  highRiskVoteThreshold: number;
  mediumRiskVoteThreshold: number;
  gatingEnabled: boolean;
  gatingConfig: Record<string, unknown>;
  datasetPartitionId?: number | null;
  artifactBasePath: string;
  isActive: boolean;
  createdBy?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface CustomerBehaviourSubWeights {
  amount: number;
  novelty: number;
  frequency: number;
  timeGap: number;
  unusualHour: number;
}

export interface PeerBehaviourSubWeights {
  amount: number;
  frequency: number;
  expectedTurnover: number;
}

export interface AmlRuleThresholds {
  reportingThreshold: number;
  structuringCount24h: number;
  rapidTxCount10m: number;
  highTxCount1h: number;
  multiBeneficiaryCount1h: number;
  repeatedAmountCount24h: number;
  highCustomerAmountRatio: number;
  extremeCustomerAmountRatio: number;
  highBalanceRatio: number;
  highExpectedTurnoverRatio: number;
}

export interface RiskPolicyConfig {
  policyVersion: string;
  customerBehaviourWeight: number;
  peerBehaviourWeight: number;
  mlEnsembleWeight: number;
  rulesWeight: number;
  customerBehaviourSubWeights: CustomerBehaviourSubWeights;
  peerBehaviourSubWeights: PeerBehaviourSubWeights;
  amlRuleThresholds: AmlRuleThresholds;
  models: RiskPolicyModelConfig[];
  incrementalSchedule: 'DAILY' | 'WEEKLY';
  batchSchedule: 'DAILY' | 'WEEKLY';
  lowRiskThreshold: number;
  mediumRiskThreshold: number;
  highRiskThreshold: number;
  updatedAt?: string | null;
}

export interface RiskPolicyModelConfig {
  modelKey: string;
  displayName: string;
  family: 'BATCH' | 'INCREMENTAL';
  enabled: boolean;
  weight: number;
  effectiveWeight: number;
  productionReady: boolean;
}

export interface RiskPolicyModelConfigUpdate {
  modelKey: string;
  enabled: boolean;
  weight: number;
}

export interface RiskPolicyConfigUpdateRequest {
  customerBehaviourWeight: number;
  peerBehaviourWeight: number;
  mlEnsembleWeight: number;
  rulesWeight: number;
  customerBehaviourSubWeights: CustomerBehaviourSubWeights;
  peerBehaviourSubWeights: PeerBehaviourSubWeights;
  amlRuleThresholds: AmlRuleThresholds;
  models: RiskPolicyModelConfigUpdate[];
  incrementalSchedule: 'DAILY' | 'WEEKLY';
  batchSchedule: 'DAILY' | 'WEEKLY';
  lowRiskThreshold: number;
  mediumRiskThreshold: number;
  highRiskThreshold: number;
}

export interface ColdStartConfigItem {
  configKey: string;
  configValue: string;
  valueType: string;
  description: string;
}

export interface ModelTuningItem {
  configKey: string;
  configValue: string;
  valueType: 'BOOLEAN' | 'INTEGER' | 'DECIMAL' | 'SELECT';
  groupName: string;
  displayName: string;
  description: string;
  minValue?: number | null;
  maxValue?: number | null;
  step?: string | null;
  options: string[];
}

export interface AmlModelRegistryEntry {
  modelVersion: string;
  modelType: string;
  modelSegment?: string | null;
  featureVersion: string;
  trainingRunId: string;
  artifactPath: string;
  artifactChecksum: string;
  datasetChecksum?: string | null;
  baseModelVersion?: string | null;
  featureSchemaChecksum?: string | null;
  status: 'CANDIDATE' | 'VALIDATED' | 'APPROVED' | 'CHAMPION' | 'CHALLENGER' | 'REJECTED' | 'RETIRED';
  artifactSizeBytes?: number | null;
  learnedRowCount?: number | null;
  anomalyRate?: number | null;
  validationRowCount?: number | null;
  alertCount?: number | null;
  averageScore?: number | null;
  scoreP95?: number | null;
  scoreP99?: number | null;
  parametersJson?: string | null;
  metricsJson?: string | null;
  registeredBy?: string | null;
  createdAt: string;
}

export interface ChallengerMetrics {
  sampleCount: number;
  candidateAnomalyCount: number;
  productionAlertCount: number;
  overlapCount: number;
  candidateOnlyCount: number;
  productionOnlyCount: number;
  candidateAnomalyRate: number;
  productionAlertRate: number;
  agreementRate: number;
  alertJaccard?: number | null;
  averageScore?: number | null;
  scoreStandardDeviation?: number | null;
  scoreP50?: number | null;
  scoreP95?: number | null;
  scoreP99?: number | null;
  dailyAnomalyRateStandardDeviation?: number | null;
  reviewedOverlapCount: number;
  falsePositiveOverlapCount: number;
  strOverlapCount: number;
  reviewedPrecision?: number | null;
}

export interface ModelValidationReport {
  validationId: string;
  modelVersion: string;
  comparisonTarget: string;
  windowStartedAt: string;
  windowEndedAt: string;
  metrics: ChallengerMetrics;
  validationStatus: 'PASSED' | 'FAILED' | 'INSUFFICIENT_DATA';
  failureReason?: string | null;
  validatedBy: string;
  validatedAt: string;
}

export interface ActiveModelPointer {
  modelType: string;
  modelSegment?: string | null;
  activeModelVersion: string;
  previousModelVersion?: string | null;
  pointerVersion: number;
  activatedBy: string;
  activatedAt: string;
}

export interface ModelDeploymentEvent {
  deploymentId: string;
  actionId: string;
  deploymentAction: 'PROMOTION' | 'ROLLBACK';
  modelType: string;
  modelSegment?: string | null;
  previousModelVersion?: string | null;
  activatedModelVersion: string;
  reason: string;
  performedBy: string;
  performedAt: string;
}

export interface LayeredShadowValidationMetrics {
  sampleCount: number;
  observationDays: number;
  legacyAlertCount: number;
  layeredAlertCount: number;
  overlapCount: number;
  layeredOnlyCount: number;
  legacyOnlyCount: number;
  legacyAlertRate: number;
  layeredAlertRate: number;
  alertVolumeChangeRate?: number | null;
  agreementRate: number;
  alertJaccard?: number | null;
  topRiskCount: number;
  topRiskOverlapCount: number;
  topRiskOverlapRate?: number | null;
  averageLayeredScore?: number | null;
  layeredScoreStandardDeviation?: number | null;
  layeredScoreP50?: number | null;
  layeredScoreP95?: number | null;
  layeredScoreP99?: number | null;
  dailyLayeredAlertRateStandardDeviation?: number | null;
  maxSegmentDailyAlertRateStandardDeviation?: number | null;
  syntheticExpectedSuspiciousCount: number;
  syntheticDetectedCount: number;
  syntheticScenarioRecall?: number | null;
  reviewedLayeredAlertCount: number;
  reviewedTruePositiveCount: number;
  reviewedFalsePositiveCount: number;
  reviewedPrecision?: number | null;
  reviewedFalsePositiveRate?: number | null;
  averagePredictionLatencyMs?: number | null;
  predictionLatencyP95Ms?: number | null;
  incrementalUpdateCount: number;
  averageIncrementalUpdateMs?: number | null;
  maximumIncrementalUpdateMs?: number | null;
  hstAvailabilityRate: number;
  onlineOcSvmAvailabilityRate: number;
  hstModelVersion?: string | null;
  distinctHstModelVersionCount: number;
  onlineOcSvmModelVersion?: string | null;
  distinctOnlineOcSvmModelVersionCount: number;
}

export interface LayeredShadowValidationReport {
  validationId: string;
  riskPolicyVersion: string;
  peerGroupCode?: string | null;
  windowStartedAt: string;
  windowEndedAt: string;
  metrics: LayeredShadowValidationMetrics;
  validationStatus: 'PASSED' | 'FAILED' | 'INSUFFICIENT_DATA';
  blockingReasons: string[];
  warnings: string[];
  validatedBy: string;
  validatedAt: string;
}

export interface LayeredDeploymentPointer {
  peerGroupCode: string;
  deploymentMode: 'LAYERED_ACTIVE' | 'ISOLATION_FOREST_FALLBACK';
  riskPolicyVersion: string;
  hstModelVersion: string;
  onlineOcSvmModelVersion: string;
  validationId: string;
  canaryPercentage: number;
  pointerVersion: number;
  activatedBy: string;
  activatedAt: string;
}

export interface LayeredDeploymentEvent {
  deploymentId: string;
  actionId: string;
  deploymentAction: 'PROMOTION' | 'CANARY_EXPANSION' | 'ROLLBACK';
  peerGroupCode: string;
  previousMode?: string | null;
  activatedMode: 'LAYERED_ACTIVE' | 'ISOLATION_FOREST_FALLBACK';
  riskPolicyVersion: string;
  hstModelVersion: string;
  onlineOcSvmModelVersion: string;
  validationId: string;
  previousCanaryPercentage?: number | null;
  activatedCanaryPercentage: number;
  reason: string;
  performedBy: string;
  performedAt: string;
}

export interface AmlTrainingRun {
  trainingRunId: string;
  trainingType: 'DAILY_INCREMENTAL' | 'WEEKLY_BATCH' | 'FULL_REBUILD' | 'BACKTEST' | 'REPLAY';
  featureVersion: string;
  modelType: string;
  modelSegment?: string | null;
  fromBusinessDate: string;
  toBusinessDate: string;
  cutoffTimestamp: string;
  requestedRowCount?: number | null;
  exportedRowCount?: number | null;
  learnedRowCount?: number | null;
  datasetPath?: string | null;
  datasetChecksum?: string | null;
  baseModelVersion?: string | null;
  candidateModelVersion?: string | null;
  status: string;
  failureReason?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  progressStage?: string | null;
  progressCurrent?: number | null;
  progressTotal?: number | null;
}

export interface DetectorGrowthMetric {
  partitionPercentage: number;
  partitionRows: number;
  trainingRows: number;
  learnedRows: number;
  evaluationRows: number;
  detector: string;
  anomalyRate: number;
  alertCount: number;
  threshold: number;
  averageScore: number;
  scoreP50: number;
  scoreP95: number;
  scoreP99: number;
  excessMassAuc: number;
  scoreSkewness: number;
  rankStability: number;
  trainingDurationMs: number;
  rowsPerSecond: number;
  boundedTrainingSample: boolean;
}

export interface GrowthAnalysisReport {
  status: string;
  datasetRows: number;
  featureCount: number;
  featureVersion: string;
  partitionPercentages: number[];
  detectors: string[];
  methodology: Record<string, unknown>;
  results: DetectorGrowthMetric[];
}

/** How much two models' flagged sets overlap. */
export interface AgreementPair {
  modelA: string;
  modelB: string;
  bothCount: number;
  eitherCount: number;
  jaccard: number;
}

export interface AgreementResult {
  evaluatedRows: number;
  modelCount: number;
  models: { modelType: string; flaggedCount: number; flaggedRate: number }[];
  pairs: AgreementPair[];
  consensus: { models: number; rowCount: number }[];
  flaggedByAny: number;
  flaggedByMajority: number;
  unanimousCount: number;
  skippedModels: Record<string, string>;
  durationMs: number;
}

/** A persisted model-agreement study; resultJson holds the full matrix. */
export interface AgreementStudy {
  studyId: string;
  trainingRunId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  evaluatedRows?: number | null;
  modelCount?: number | null;
  resultJson?: string | null;
  requestedBy?: string | null;
  failureReason?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
}

/** A persisted growth study — computed once, then read instantly by the comparison page. */
export interface GrowthStudy {
  studyId: string;
  trainingRunId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  featureVersion?: string | null;
  datasetRows?: number | null;
  featureCount?: number | null;
  partitionPercentages: number[];
  methodologyJson?: string | null;
  requestedBy?: string | null;
  failureReason?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  metrics: DetectorGrowthMetric[];
}

export interface LayerAblationMetric {
  partitionPercentage: number;
  evaluatedRows: number;
  variant: string;
  averageRiskScore: number;
  suspiciousRate: number;
  suspiciousCount: number;
  decisionChangesVsFull: number;
  decisionChangeRateVsFull: number;
  averageScoreDeltaVsFull: number;
  hardRuleOverrides: number;
}

export interface LayerAblationReport {
  status: string;
  availableRows: number;
  partitionPercentages: number[];
  variants: string[];
  methodology: Record<string, unknown>;
  results: LayerAblationMetric[];
}

export interface SystemHealth {
  service: string;
  architecture: string;
  status: string;
  modules: string[];
}

export interface CasePredictionEvidence {
  riskLevel?: string | null;
  anomalyVotes?: number | null;
  suspicious?: boolean | null;
  modelVersion?: string | null;
  featureVersion?: string | null;
  incrementalModelScore?: number | null;
  batchModelScore?: number | null;
  reasonCodes?: string | null;
  learningDecision?: string | null;
  learningDecisionReason?: string | null;
  predictedAt?: string | null;
}

export interface CaseNote {
  id: number;
  noteText: string;
  createdBy: string;
  createdAt: string;
}

export interface CaseRecord {
  id: number;
  caseNo: string;
  fraudAlertId?: number | null;
  transactionId: string;
  accountId: string;
  title: string;
  status: string;
  priority: string;
  assignedTo?: string | null;
  createdBy?: string | null;
  createdAt: string;
  updatedAt?: string | null;
  notes: CaseNote[];
  predictionEvidence?: CasePredictionEvidence | null;
}

export interface TransactionRecord {
  id: number;
  transactionId: string;
  accountId: string;
  transactionAmount: number;
  transactionType: string;
  transactionDate: string;
  location: string;
  channel: string;
  customerAge?: number | null;
  customerOccupation?: string | null;
  loginAttempts: number;
  accountBalance: number;
  sourceType: string;
  createdAt?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
