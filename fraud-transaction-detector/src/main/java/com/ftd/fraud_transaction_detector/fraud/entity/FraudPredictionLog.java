package com.ftd.fraud_transaction_detector.fraud.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "fraud_prediction_logs")
public class FraudPredictionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "anomaly_votes")
    private Integer anomalyVotes;

    @Column(name = "request_json")
    private String requestJson;

    @Column(name = "response_json")
    private String responseJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "feature_version", length = 30)
    private String featureVersion;

    @Column(name = "suspicious_flag")
    private Boolean suspiciousFlag;

    @Column(name = "learning_decision", length = 30)
    private String learningDecision;

    @Column(name = "learning_decision_reason", length = 500)
    private String learningDecisionReason;

    @Column(name = "reason_codes")
    private String reasonCodes;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "incremental_model_score")
    private Double incrementalModelScore;

    @Column(name = "batch_model_score")
    private Double batchModelScore;

    @Column(name = "risk_policy_version", length = 50)
    private String riskPolicyVersion;

    @Column(name = "final_risk_score")
    private Double finalRiskScore;

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getAnomalyVotes() {
        return anomalyVotes;
    }

    public void setAnomalyVotes(Integer anomalyVotes) {
        this.anomalyVotes = anomalyVotes;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getFeatureVersion() {
        return featureVersion;
    }

    public void setFeatureVersion(String featureVersion) {
        this.featureVersion = featureVersion;
    }

    public Boolean getSuspiciousFlag() {
        return suspiciousFlag;
    }

    public void setSuspiciousFlag(Boolean suspiciousFlag) {
        this.suspiciousFlag = suspiciousFlag;
    }

    public String getLearningDecision() {
        return learningDecision;
    }

    public void setLearningDecision(String learningDecision) {
        this.learningDecision = learningDecision;
    }

    public String getLearningDecisionReason() {
        return learningDecisionReason;
    }

    public void setLearningDecisionReason(String learningDecisionReason) {
        this.learningDecisionReason = learningDecisionReason;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(String reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Double getIncrementalModelScore() {
        return incrementalModelScore;
    }

    public void setIncrementalModelScore(Double incrementalModelScore) {
        this.incrementalModelScore = incrementalModelScore;
    }

    public Double getBatchModelScore() {
        return batchModelScore;
    }

    public void setBatchModelScore(Double batchModelScore) {
        this.batchModelScore = batchModelScore;
    }

    public String getRiskPolicyVersion() {
        return riskPolicyVersion;
    }

    public void setRiskPolicyVersion(String riskPolicyVersion) {
        this.riskPolicyVersion = riskPolicyVersion;
    }

    public Double getFinalRiskScore() {
        return finalRiskScore;
    }

    public void setFinalRiskScore(Double finalRiskScore) {
        this.finalRiskScore = finalRiskScore;
    }
}
