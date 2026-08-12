package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "anomaly_configs")
public class AnomalyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_no", nullable = false, unique = true, length = 50)
    private String configNo;

    @Column(name = "config_name", nullable = false, length = 150)
    private String configName;

    @Column(name = "enabled_models_json", nullable = false)
    private String enabledModelsJson;

    @Column(name = "voting_strategy", nullable = false, length = 50)
    private String votingStrategy;

    @Column(name = "suspicious_vote_threshold", nullable = false)
    private Integer suspiciousVoteThreshold;

    @Column(name = "high_risk_vote_threshold", nullable = false)
    private Integer highRiskVoteThreshold;

    @Column(name = "medium_risk_vote_threshold", nullable = false)
    private Integer mediumRiskVoteThreshold;

    @Column(name = "gating_enabled", nullable = false)
    private Boolean gatingEnabled = Boolean.TRUE;

    @Column(name = "gating_config_json")
    private String gatingConfigJson;

    @Column(name = "dataset_partition_id")
    private Long datasetPartitionId;

    @Column(name = "artifact_base_path", length = 500)
    private String artifactBasePath;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.FALSE;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getConfigNo() {
        return configNo;
    }

    public void setConfigNo(String configNo) {
        this.configNo = configNo;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getEnabledModelsJson() {
        return enabledModelsJson;
    }

    public void setEnabledModelsJson(String enabledModelsJson) {
        this.enabledModelsJson = enabledModelsJson;
    }

    public String getVotingStrategy() {
        return votingStrategy;
    }

    public void setVotingStrategy(String votingStrategy) {
        this.votingStrategy = votingStrategy;
    }

    public Integer getSuspiciousVoteThreshold() {
        return suspiciousVoteThreshold;
    }

    public void setSuspiciousVoteThreshold(Integer suspiciousVoteThreshold) {
        this.suspiciousVoteThreshold = suspiciousVoteThreshold;
    }

    public Integer getHighRiskVoteThreshold() {
        return highRiskVoteThreshold;
    }

    public void setHighRiskVoteThreshold(Integer highRiskVoteThreshold) {
        this.highRiskVoteThreshold = highRiskVoteThreshold;
    }

    public Integer getMediumRiskVoteThreshold() {
        return mediumRiskVoteThreshold;
    }

    public void setMediumRiskVoteThreshold(Integer mediumRiskVoteThreshold) {
        this.mediumRiskVoteThreshold = mediumRiskVoteThreshold;
    }

    public Boolean getGatingEnabled() {
        return gatingEnabled;
    }

    public void setGatingEnabled(Boolean gatingEnabled) {
        this.gatingEnabled = gatingEnabled;
    }

    public String getGatingConfigJson() {
        return gatingConfigJson;
    }

    public void setGatingConfigJson(String gatingConfigJson) {
        this.gatingConfigJson = gatingConfigJson;
    }

    public Long getDatasetPartitionId() {
        return datasetPartitionId;
    }

    public void setDatasetPartitionId(Long datasetPartitionId) {
        this.datasetPartitionId = datasetPartitionId;
    }

    public String getArtifactBasePath() {
        return artifactBasePath;
    }

    public void setArtifactBasePath(String artifactBasePath) {
        this.artifactBasePath = artifactBasePath;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
