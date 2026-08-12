package com.ftd.fraud_transaction_detector.comparison.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "scenario_sets")
public class ScenarioSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_set_no", nullable = false, unique = true, length = 50)
    private String scenarioSetNo;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getScenarioSetNo() {
        return scenarioSetNo;
    }

    public void setScenarioSetNo(String scenarioSetNo) {
        this.scenarioSetNo = scenarioSetNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
