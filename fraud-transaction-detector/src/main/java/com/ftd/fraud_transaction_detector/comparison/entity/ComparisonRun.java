package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "comparison_runs")
public class ComparisonRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comparison_run_no", nullable = false, unique = true, length = 50)
    private String comparisonRunNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_dataset_id", nullable = false)
    private UploadedDataset uploadedDataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_set_id", nullable = false)
    private ScenarioSet scenarioSet;

    @Column(name = "selected_partition_sizes", nullable = false, length = 200)
    private String selectedPartitionSizes;

    @Column(name = "selected_models", nullable = false, length = 500)
    private String selectedModels;

    @Column(name = "run_status", nullable = false, length = 30)
    private String runStatus;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getComparisonRunNo() {
        return comparisonRunNo;
    }

    public void setComparisonRunNo(String comparisonRunNo) {
        this.comparisonRunNo = comparisonRunNo;
    }

    public UploadedDataset getUploadedDataset() {
        return uploadedDataset;
    }

    public void setUploadedDataset(UploadedDataset uploadedDataset) {
        this.uploadedDataset = uploadedDataset;
    }

    public ScenarioSet getScenarioSet() {
        return scenarioSet;
    }

    public void setScenarioSet(ScenarioSet scenarioSet) {
        this.scenarioSet = scenarioSet;
    }

    public String getSelectedPartitionSizes() {
        return selectedPartitionSizes;
    }

    public void setSelectedPartitionSizes(String selectedPartitionSizes) {
        this.selectedPartitionSizes = selectedPartitionSizes;
    }

    public String getSelectedModels() {
        return selectedModels;
    }

    public void setSelectedModels(String selectedModels) {
        this.selectedModels = selectedModels;
    }

    public String getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(String runStatus) {
        this.runStatus = runStatus;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
