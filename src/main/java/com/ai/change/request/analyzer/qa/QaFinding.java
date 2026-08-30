package com.ai.change.request.analyzer.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

/** Finding estruturado de revisao de codigo (etapa QA), vinculado ao registro da execucao. */
@Entity
@Table(name = "qa_finding")
public class QaFinding {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "qa_review_record_id", nullable = false)
  private QaReviewRecord reviewRecord;

  @Column(nullable = false, length = 200)
  private String component;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(length = 16)
  private String severity;

  @Column(length = 200)
  private String source;

  protected QaFinding() {}

  public QaFinding(String component, String description, String severity, String source) {
    this.component = component;
    this.description = description;
    this.severity = severity;
    this.source = source;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
  }

  public UUID getId() {
    return id;
  }

  public QaReviewRecord getReviewRecord() {
    return reviewRecord;
  }

  public void setReviewRecord(QaReviewRecord reviewRecord) {
    this.reviewRecord = reviewRecord;
  }

  public String getComponent() {
    return component;
  }

  public String getDescription() {
    return description;
  }

  public String getSeverity() {
    return severity;
  }

  public String getSource() {
    return source;
  }
}
