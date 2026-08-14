package com.ftd.fraud_transaction_detector.transactions.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "transaction_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "location", nullable = false, length = 100)
    private String location;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "customer_age")
    private Integer customerAge;

    @Column(name = "customer_occupation", length = 100)
    private String customerOccupation;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "account_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal accountBalance = BigDecimal.ZERO;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "upload_batch_id")
    private Long uploadBatchId;

    @Column(name = "fraud_label")
    private Boolean fraudLabel;

    @Column(name = "label_source", length = 50)
    private String labelSource;

    @Column(name = "labeled_by", length = 100)
    private String labeledBy;

    @Column(name = "labeled_at")
    private Instant labeledAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus = "COMPLETED";

    @Column(name = "feature_status", nullable = false, length = 30)
    private String featureStatus = "NOT_STARTED";

    @Column(name = "prediction_status", nullable = false, length = 30)
    private String predictionStatus = "NOT_STARTED";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

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

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getCustomerAge() {
        return customerAge;
    }

    public void setCustomerAge(Integer customerAge) {
        this.customerAge = customerAge;
    }

    public String getCustomerOccupation() {
        return customerOccupation;
    }

    public void setCustomerOccupation(String customerOccupation) {
        this.customerOccupation = customerOccupation;
    }

    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public void setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getUploadBatchId() {
        return uploadBatchId;
    }

    public void setUploadBatchId(Long uploadBatchId) {
        this.uploadBatchId = uploadBatchId;
    }

    public Boolean getFraudLabel() {
        return fraudLabel;
    }

    public void setFraudLabel(Boolean fraudLabel) {
        this.fraudLabel = fraudLabel;
    }

    public String getLabelSource() {
        return labelSource;
    }

    public void setLabelSource(String labelSource) {
        this.labelSource = labelSource;
    }

    public String getLabeledBy() {
        return labeledBy;
    }

    public void setLabeledBy(String labeledBy) {
        this.labeledBy = labeledBy;
    }

    public Instant getLabeledAt() {
        return labeledAt;
    }

    public void setLabeledAt(Instant labeledAt) {
        this.labeledAt = labeledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getFeatureStatus() {
        return featureStatus;
    }

    public void setFeatureStatus(String featureStatus) {
        this.featureStatus = featureStatus;
    }

    public String getPredictionStatus() {
        return predictionStatus;
    }

    public void setPredictionStatus(String predictionStatus) {
        this.predictionStatus = predictionStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
