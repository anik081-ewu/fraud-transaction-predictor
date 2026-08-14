from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class TransactionIn(BaseModel):
    transactionId: str
    accountId: str
    transactionAmount: float
    transactionType: str
    transactionDate: datetime
    location: str
    channel: str
    loginAttempts: int = 0
    accountBalance: float = 0.0


class CustomerIn(BaseModel):
    customerAge: Optional[int] = None
    customerOccupation: Optional[str] = None


class AccountProfileIn(BaseModel):
    previousTransactionDate: Optional[datetime] = None
    previousLocation: Optional[str] = None
    userAvgAmount: Optional[float] = None
    userMaxAmount: Optional[float] = None
    userAmountStd: Optional[float] = None
    userTxnCount: Optional[int] = None
    rolling7dAvgAmount: Optional[float] = None
    rolling30dAvgAmount: Optional[float] = None


class FraudPredictRequest(BaseModel):
    transaction: TransactionIn
    customer: CustomerIn = Field(default_factory=CustomerIn)
    accountProfile: AccountProfileIn = Field(default_factory=AccountProfileIn)


class FraudPredictResponse(BaseModel):
    transactionId: str
    accountId: str
    suspicious: bool
    riskLevel: str
    anomalyVotes: int
    modelResults: Dict[str, Any]
    featureSummary: Dict[str, Any]
    reasons: List[str]
    recommendedAction: str


class PersistedFeaturePredictRequest(BaseModel):
    transactionId: str
    accountId: str
    featureVersion: str
    modelFeatureSchema: str
    features: Dict[str, float]
    featureSummary: Dict[str, Any] = Field(default_factory=dict)
    reasons: List[str] = Field(default_factory=list)
    modelsDir: Optional[str] = None
    modelNames: Optional[List[str]] = None
    activeModelsDir: Optional[str] = None
    activeModelVersion: Optional[str] = None
    challengerModelsDir: Optional[str] = None
    challengerModelVersion: Optional[str] = None
    shadowOnlineSvmDir: Optional[str] = None
    shadowOnlineSvmVersion: Optional[str] = None
    learningMode: str = "UNSUPERVISED"


class ComparisonPredictRequest(BaseModel):
    transaction: TransactionIn
    customer: CustomerIn = Field(default_factory=CustomerIn)
    accountProfile: AccountProfileIn = Field(default_factory=AccountProfileIn)
    modelsDir: str
    modelNames: Optional[List[str]] = None


class ComparisonPredictResponse(BaseModel):
    transactionId: str
    accountId: str
    modelResults: Dict[str, Dict[str, Any]]
    featureSummary: Dict[str, Any]
    reasons: List[str]


class TrainingTransaction(BaseModel):
    transactionId: str
    accountId: str
    transactionAmount: float
    transactionType: str
    transactionDate: datetime
    location: str
    channel: str
    customerAge: Optional[int] = None
    customerOccupation: Optional[str] = None
    loginAttempts: int = 0
    accountBalance: float = 0.0
    fraudLabel: Optional[bool] = None


class TrainModelRequest(BaseModel):
    source: str
    requestedBy: str
    transactions: Optional[List[TrainingTransaction]] = None
    hyperparams: Optional[Dict[str, Any]] = None
    modelNames: Optional[List[str]] = None
    outputSubdir: Optional[str] = None
    evaluationTransactions: Optional[List[TrainingTransaction]] = None
    learningMode: str = "UNSUPERVISED"
    datasetPath: Optional[str] = None
    datasetChecksum: Optional[str] = None


class TrainModelResponse(BaseModel):
    status: str
    message: str
    trainedRows: int
    featureCount: int
    models: List[str]
    artifacts: Dict[str, str]
    artifactBasePath: Optional[str] = None
    metrics: Dict[str, Dict[str, Any]] = Field(default_factory=dict)


class ModelAgreementRequest(BaseModel):
    datasetPath: str
    datasetChecksum: str
    modelBundlePath: Optional[str] = None


class GrowthAnalysisRequest(BaseModel):
    datasetPath: str
    datasetChecksum: str
    percentages: List[int] = Field(default_factory=lambda: [10, 25, 50, 100])
    minimumRows: int = 200
    holdoutFraction: float = 0.20
    maximumEvaluationRows: int = 20_000
    isolationForestMaximumTrainingRows: int = 100_000
    isolationForestEstimators: int = 200
    autoencoderMaxTrainingRows: int = 50_000
    localOutlierFactorMaxTrainingRows: int = 50_000
    localOutlierFactorNeighbors: int = 35
    localOutlierFactorContamination: float = 0.05
    randomSeed: int = 42
    learningMode: str = "UNSUPERVISED"
    hyperparams: Optional[Dict[str, Any]] = None


class GrowthAnalysisResponse(BaseModel):
    status: str
    datasetRows: int
    featureCount: int
    featureVersion: str
    partitionPercentages: List[int]
    detectors: List[str]
    methodology: Dict[str, Any]
    results: List[Dict[str, Any]]
    ensembles: List[Dict[str, Any]] = Field(default_factory=list)


class ScorePercentilesRequest(BaseModel):
    source: Optional[str] = None
    requestedBy: Optional[str] = None
    transactions: List[TrainingTransaction]


class ScorePercentilesResponse(BaseModel):
    percentiles: List[int]
    lofDecision: List[float]
    svmDecision: List[float]
