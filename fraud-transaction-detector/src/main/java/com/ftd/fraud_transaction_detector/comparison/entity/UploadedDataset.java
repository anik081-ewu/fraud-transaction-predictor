package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "uploaded_datasets")
public class UploadedDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_no", nullable = false, unique = true, length = 50)
    private String datasetNo;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "source_batch_id")
    private Long sourceBatchId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "snapshot_max_transaction_id")
    private Long snapshotMaxTransactionId;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "notes", length = 500)
    private String notes;

    public Long getId() {
        return id;
    }

    public String getDatasetNo() {
        return datasetNo;
    }

    public void setDatasetNo(String datasetNo) {
        this.datasetNo = datasetNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(Long sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSnapshotMaxTransactionId() {
        return snapshotMaxTransactionId;
    }

    public void setSnapshotMaxTransactionId(Long snapshotMaxTransactionId) {
        this.snapshotMaxTransactionId = snapshotMaxTransactionId;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
