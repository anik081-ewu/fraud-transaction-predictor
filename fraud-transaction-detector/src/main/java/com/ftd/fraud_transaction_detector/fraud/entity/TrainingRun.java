package com.ftd.fraud_transaction_detector.fraud.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "training_runs")
public class TrainingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "training_run_no", nullable = false, unique = true, length = 50)
    private String runNo;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "training_row_count")
    private Integer trainingRowCount;

    @Column(name = "feature_count")
    private Integer featureCount;

    @Column(name = "models_json")
    private String modelsJson;

    @Column(name = "artifacts_json")
    private String artifactsJson;

    @Column(name = "hyperparams_json")
    private String hyperparamsJson;

    @Column(name = "response_status", length = 30)
    private String responseStatus;

    @Column(name = "message")
    private String message;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    public Long getId() {
        return id;
    }

    public String getRunNo() {
        return runNo;
    }

    public void setRunNo(String runNo) {
        this.runNo = runNo;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTrainingRowCount() {
        return trainingRowCount;
    }

    public void setTrainingRowCount(Integer trainingRowCount) {
        this.trainingRowCount = trainingRowCount;
    }

    public Integer getFeatureCount() {
        return featureCount;
    }

    public void setFeatureCount(Integer featureCount) {
        this.featureCount = featureCount;
    }

    public String getModelsJson() {
        return modelsJson;
    }

    public void setModelsJson(String modelsJson) {
        this.modelsJson = modelsJson;
    }

    public String getArtifactsJson() {
        return artifactsJson;
    }

    public void setArtifactsJson(String artifactsJson) {
        this.artifactsJson = artifactsJson;
    }

    public String getHyperparamsJson() {
        return hyperparamsJson;
    }

    public void setHyperparamsJson(String hyperparamsJson) {
        this.hyperparamsJson = hyperparamsJson;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
