package com.ftd.fraud_transaction_detector.cases.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "case_notes")
public class CaseNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_record_id", nullable = false)
    private CaseRecord caseRecord;
    @Column(name = "note_text", nullable = false)
    private String noteText;
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public CaseRecord getCaseRecord() { return caseRecord; }
    public void setCaseRecord(CaseRecord caseRecord) { this.caseRecord = caseRecord; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
