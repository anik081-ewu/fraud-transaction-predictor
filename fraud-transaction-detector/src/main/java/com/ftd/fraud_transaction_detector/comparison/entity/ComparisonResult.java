package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "comparison_results")
public class ComparisonResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comparison_run_id", nullable = false)
    private ComparisonRun comparisonRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private ComparisonScenario scenario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_partition_id", nullable = false)
    private DatasetPartition datasetPartition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "raw_prediction")
    private Integer rawPrediction;

    @Column(name = "anomaly_vote", nullable = false)
    private Integer anomalyVote;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "suspicious")
    private Boolean suspicious;

    @Column(name = "recommended_action", length = 50)
    private String recommendedAction;

    @Column(name = "score_value")
    private Double scoreValue;

    @Column(name = "decision_value")
    private Double decisionValue;

    @Column(name = "reasons_json")
    private String reasonsJson;

    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "prediction_duration_ms")
    private Long predictionDurationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public ComparisonRun getComparisonRun() {
        return comparisonRun;
    }

    public void setComparisonRun(ComparisonRun comparisonRun) {
        this.comparisonRun = comparisonRun;
    }

    public ComparisonScenario getScenario() {
        return scenario;
    }

    public void setScenario(ComparisonScenario scenario) {
        this.scenario = scenario;
    }

    public DatasetPartition getDatasetPartition() {
        return datasetPartition;
    }

    public void setDatasetPartition(DatasetPartition datasetPartition) {
        this.datasetPartition = datasetPartition;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(ModelVersion modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Integer getRawPrediction() {
        return rawPrediction;
    }

    public void setRawPrediction(Integer rawPrediction) {
        this.rawPrediction = rawPrediction;
    }

    public Integer getAnomalyVote() {
        return anomalyVote;
    }

    public void setAnomalyVote(Integer anomalyVote) {
        this.anomalyVote = anomalyVote;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getSuspicious() {
        return suspicious;
    }

    public void setSuspicious(Boolean suspicious) {
        this.suspicious = suspicious;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public Double getScoreValue() {
        return scoreValue;
    }

    public void setScoreValue(Double scoreValue) {
        this.scoreValue = scoreValue;
    }

    public Double getDecisionValue() {
        return decisionValue;
    }

    public void setDecisionValue(Double decisionValue) {
        this.decisionValue = decisionValue;
    }

    public String getReasonsJson() {
        return reasonsJson;
    }

    public void setReasonsJson(String reasonsJson) {
        this.reasonsJson = reasonsJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public Long getPredictionDurationMs() {
        return predictionDurationMs;
    }

    public void setPredictionDurationMs(Long predictionDurationMs) {
        this.predictionDurationMs = predictionDurationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
