package com.ftd.fraud_transaction_detector.cases.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "case_records")
public class CaseRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "case_no", nullable = false, unique = true, length = 50)
    private String caseNo;
    @Column(name = "fraud_alert_id")
    private Long fraudAlertId;
    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;
    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "status", nullable = false, length = 30)
    private String status;
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
    @Column(name = "created_by", length = 100)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getCaseNo() { return caseNo; }
    public void setCaseNo(String caseNo) { this.caseNo = caseNo; }
    public Long getFraudAlertId() { return fraudAlertId; }
    public void setFraudAlertId(Long fraudAlertId) { this.fraudAlertId = fraudAlertId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
