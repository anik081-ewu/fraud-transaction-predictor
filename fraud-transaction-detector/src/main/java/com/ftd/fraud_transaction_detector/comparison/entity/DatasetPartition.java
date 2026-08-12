package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "dataset_partitions")
public class DatasetPartition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_dataset_id", nullable = false)
    private UploadedDataset uploadedDataset;

    @Column(name = "partition_no", nullable = false, unique = true, length = 50)
    private String partitionNo;

    @Column(name = "partition_label", nullable = false, length = 100)
    private String partitionLabel;

    @Column(name = "partition_size", nullable = false)
    private Integer partitionSize;

    @Column(name = "ordering_strategy", nullable = false, length = 50)
    private String orderingStrategy;

    @Column(name = "start_row_no", nullable = false)
    private Integer startRowNo;

    @Column(name = "end_row_no", nullable = false)
    private Integer endRowNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public UploadedDataset getUploadedDataset() {
        return uploadedDataset;
    }

    public void setUploadedDataset(UploadedDataset uploadedDataset) {
        this.uploadedDataset = uploadedDataset;
    }

    public String getPartitionNo() {
        return partitionNo;
    }

    public void setPartitionNo(String partitionNo) {
        this.partitionNo = partitionNo;
    }

    public String getPartitionLabel() {
        return partitionLabel;
    }

    public void setPartitionLabel(String partitionLabel) {
        this.partitionLabel = partitionLabel;
    }

    public Integer getPartitionSize() {
        return partitionSize;
    }

    public void setPartitionSize(Integer partitionSize) {
        this.partitionSize = partitionSize;
    }

    public String getOrderingStrategy() {
        return orderingStrategy;
    }

    public void setOrderingStrategy(String orderingStrategy) {
        this.orderingStrategy = orderingStrategy;
    }

    public Integer getStartRowNo() {
        return startRowNo;
    }

    public void setStartRowNo(Integer startRowNo) {
        this.startRowNo = startRowNo;
    }

    public Integer getEndRowNo() {
        return endRowNo;
    }

    public void setEndRowNo(Integer endRowNo) {
        this.endRowNo = endRowNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
