package com.ftd.fraud_transaction_detector.fraud.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_no", nullable = false, unique = true, length = 50)
    private String alertNo;

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "anomaly_votes", nullable = false)
    private Integer anomalyVotes;

    @Column(name = "iso_anomaly", nullable = false)
    private Boolean isoAnomaly = false;

    @Column(name = "lof_anomaly", nullable = false)
    private Boolean lofAnomaly = false;

    @Column(name = "svm_anomaly", nullable = false)
    private Boolean svmAnomaly = false;

    @Column(name = "anomaly_reason")
    private String anomalyReason;

    @Column(name = "recommended_action", length = 50)
    private String recommendedAction;

    @Column(name = "review_status", nullable = false, length = 30)
    private String reviewStatus = "PENDING";

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "str_file_path", length = 500)
    private String strFilePath;

    @Column(name = "str_generated_at")
    private Instant strGeneratedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getAlertNo() {
        return alertNo;
    }

    public void setAlertNo(String alertNo) {
        this.alertNo = alertNo;
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

    public Boolean getIsoAnomaly() {
        return isoAnomaly;
    }

    public void setIsoAnomaly(Boolean isoAnomaly) {
        this.isoAnomaly = isoAnomaly;
    }

    public Boolean getLofAnomaly() {
        return lofAnomaly;
    }

    public void setLofAnomaly(Boolean lofAnomaly) {
        this.lofAnomaly = lofAnomaly;
    }

    public Boolean getSvmAnomaly() {
        return svmAnomaly;
    }

    public void setSvmAnomaly(Boolean svmAnomaly) {
        this.svmAnomaly = svmAnomaly;
    }

    public String getAnomalyReason() {
        return anomalyReason;
    }

    public void setAnomalyReason(String anomalyReason) {
        this.anomalyReason = anomalyReason;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getStrFilePath() {
        return strFilePath;
    }

    public void setStrFilePath(String strFilePath) {
        this.strFilePath = strFilePath;
    }

    public Instant getStrGeneratedAt() {
        return strGeneratedAt;
    }

    public void setStrGeneratedAt(Instant strGeneratedAt) {
        this.strGeneratedAt = strGeneratedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

