package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "comparison_scenarios")
public class ComparisonScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_no", nullable = false, unique = true, length = 50)
    private String scenarioNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_set_id", nullable = false)
    private ScenarioSet scenarioSet;

    @Column(name = "scenario_name", nullable = false, length = 150)
    private String scenarioName;

    @Column(name = "scenario_type", length = 100)
    private String scenarioType;

    @Column(name = "transaction_json", nullable = false)
    private String transactionJson;

    @Column(name = "customer_json")
    private String customerJson;

    @Column(name = "account_profile_json", nullable = false)
    private String accountProfileJson;

    @Column(name = "expected_notes", length = 500)
    private String expectedNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getScenarioNo() {
        return scenarioNo;
    }

    public void setScenarioNo(String scenarioNo) {
        this.scenarioNo = scenarioNo;
    }

    public ScenarioSet getScenarioSet() {
        return scenarioSet;
    }

    public void setScenarioSet(ScenarioSet scenarioSet) {
        this.scenarioSet = scenarioSet;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getTransactionJson() {
        return transactionJson;
    }

    public void setTransactionJson(String transactionJson) {
        this.transactionJson = transactionJson;
    }

    public String getCustomerJson() {
        return customerJson;
    }

    public void setCustomerJson(String customerJson) {
        this.customerJson = customerJson;
    }

    public String getAccountProfileJson() {
        return accountProfileJson;
    }

    public void setAccountProfileJson(String accountProfileJson) {
        this.accountProfileJson = accountProfileJson;
    }

    public String getExpectedNotes() {
        return expectedNotes;
    }

    public void setExpectedNotes(String expectedNotes) {
        this.expectedNotes = expectedNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
