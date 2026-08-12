package com.ftd.fraud_transaction_detector.comparison.entity;

import com.ftd.fraud_transaction_detector.fraud.entity.TrainingRun;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "model_versions")
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_no", nullable = false, unique = true, length = 50)
    private String modelVersionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_run_id", nullable = false)
    private TrainingRun trainingRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_partition_id", nullable = false)
    private DatasetPartition datasetPartition;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "partition_size", nullable = false)
    private Integer partitionSize;

    @Column(name = "artifact_base_path", nullable = false, length = 500)
    private String artifactBasePath;

    @Column(name = "feature_columns_path", length = 500)
    private String featureColumnsPath;

    @Column(name = "scaler_path", length = 500)
    private String scalerPath;

    @Column(name = "model_path", nullable = false, length = 500)
    private String modelPath;

    @Column(name = "metrics_json")
    private String metricsJson;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.FALSE;

    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private String lifecycleStatus = "CANDIDATE";

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "promoted_by", length = 100)
    private String promotedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getModelVersionNo() {
        return modelVersionNo;
    }

    public void setModelVersionNo(String modelVersionNo) {
        this.modelVersionNo = modelVersionNo;
    }

    public TrainingRun getTrainingRun() {
        return trainingRun;
    }

    public void setTrainingRun(TrainingRun trainingRun) {
        this.trainingRun = trainingRun;
    }

    public DatasetPartition getDatasetPartition() {
        return datasetPartition;
    }

    public void setDatasetPartition(DatasetPartition datasetPartition) {
        this.datasetPartition = datasetPartition;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Integer getPartitionSize() {
        return partitionSize;
    }

    public void setPartitionSize(Integer partitionSize) {
        this.partitionSize = partitionSize;
    }

    public String getArtifactBasePath() {
        return artifactBasePath;
    }

    public void setArtifactBasePath(String artifactBasePath) {
        this.artifactBasePath = artifactBasePath;
    }

    public String getFeatureColumnsPath() {
        return featureColumnsPath;
    }

    public void setFeatureColumnsPath(String featureColumnsPath) {
        this.featureColumnsPath = featureColumnsPath;
    }

    public String getScalerPath() {
        return scalerPath;
    }

    public void setScalerPath(String scalerPath) {
        this.scalerPath = scalerPath;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Instant promotedAt) {
        this.promotedAt = promotedAt;
    }

    public String getPromotedBy() {
        return promotedBy;
    }

    public void setPromotedBy(String promotedBy) {
        this.promotedBy = promotedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
