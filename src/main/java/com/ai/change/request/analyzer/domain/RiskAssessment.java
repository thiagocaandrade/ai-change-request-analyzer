package com.ai.change.request.analyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "risk_assessment")
public class RiskAssessment {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "change_analysis_id", nullable = false, unique = true)
  private ChangeAnalysis analysis;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private RiskLevel level;

  @Column(nullable = false)
  private Double confidence;

  @Column(columnDefinition = "text")
  private String rationale;

  protected RiskAssessment() {}

  public RiskAssessment(RiskLevel level, Double confidence, String rationale) {
    this.level = level;
    this.confidence = confidence;
    this.rationale = rationale;
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

  public ChangeAnalysis getAnalysis() {
    return analysis;
  }

  public void setAnalysis(ChangeAnalysis analysis) {
    this.analysis = analysis;
  }

  public RiskLevel getLevel() {
    return level;
  }

  public Double getConfidence() {
    return confidence;
  }

  public String getRationale() {
    return rationale;
  }
}
