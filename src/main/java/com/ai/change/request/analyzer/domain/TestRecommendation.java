package com.ai.change.request.analyzer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_recommendation")
public class TestRecommendation {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "change_analysis_id", nullable = false)
  private ChangeAnalysis analysis;

  @Column(nullable = false, length = 200)
  private String component;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(length = 16)
  private String priority;

  @Column(columnDefinition = "text")
  private String priorityJustification;

  @Column(length = 200)
  private String riskCategory;

  @Column private Boolean refined;

  protected TestRecommendation() {}

  public TestRecommendation(String component, String description, String priority) {
    this(component, description, priority, null, null, null);
  }

  public TestRecommendation(
      String component,
      String description,
      String priority,
      String priorityJustification,
      String riskCategory,
      Boolean refined) {
    this.component = component;
    this.description = description;
    this.priority = priority;
    this.priorityJustification = priorityJustification;
    this.riskCategory = riskCategory;
    this.refined = refined;
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

  public String getComponent() {
    return component;
  }

  public String getDescription() {
    return description;
  }

  public String getPriority() {
    return priority;
  }

  public String getPriorityJustification() {
    return priorityJustification;
  }

  public String getRiskCategory() {
    return riskCategory;
  }

  public Boolean getRefined() {
    return refined;
  }
}
