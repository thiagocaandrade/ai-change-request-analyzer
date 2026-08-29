package com.ai.change.request.analyzer.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "change_analysis")
public class ChangeAnalysis {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "change_request_id", nullable = false, unique = true)
  private ChangeRequest changeRequest;

  @OneToOne(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
  private RiskAssessment riskAssessment;

  @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ImpactFinding> findings = new ArrayList<>();

  @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TestRecommendation> recommendations = new ArrayList<>();

  @Column(nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public ChangeRequest getChangeRequest() {
    return changeRequest;
  }

  public void setChangeRequest(ChangeRequest changeRequest) {
    this.changeRequest = changeRequest;
  }

  public RiskAssessment getRiskAssessment() {
    return riskAssessment;
  }

  public void setRiskAssessment(RiskAssessment riskAssessment) {
    this.riskAssessment = riskAssessment;
    if (riskAssessment != null) {
      riskAssessment.setAnalysis(this);
    }
  }

  public List<ImpactFinding> getFindings() {
    return findings;
  }

  public void addFinding(ImpactFinding finding) {
    findings.add(finding);
    finding.setAnalysis(this);
  }

  public List<TestRecommendation> getRecommendations() {
    return recommendations;
  }

  public void addRecommendation(TestRecommendation recommendation) {
    recommendations.add(recommendation);
    recommendation.setAnalysis(this);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
