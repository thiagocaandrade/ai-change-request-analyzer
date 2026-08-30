package com.ai.change.request.analyzer.qa;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registro de uma execucao de etapa QA (revisao de codigo ou geracao/refinamento de testes): prompt
 * versionado usado, resultado estruturado, iteracoes, flag de degradacao e trace_id, vinculado a
 * solicitacao (e a analise, quando ja existente).
 */
@Entity
@Table(name = "qa_review_record")
public class QaReviewRecord {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "change_request_id", nullable = false)
  private ChangeRequest changeRequest;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "change_analysis_id")
  private ChangeAnalysis analysis;

  @Column(nullable = false, length = 32)
  private String stage;

  @Column(nullable = false, length = 32)
  private String promptVersion;

  @Column(columnDefinition = "text")
  private String resultJson;

  @Column(nullable = false)
  private boolean degraded;

  @Column(nullable = false)
  private int iterations;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "reviewRecord",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<QaFinding> findings = new ArrayList<>();

  protected QaReviewRecord() {}

  public QaReviewRecord(
      ChangeRequest changeRequest,
      String stage,
      String promptVersion,
      String resultJson,
      boolean degraded,
      int iterations,
      String traceId,
      Instant createdAt) {
    this.changeRequest = changeRequest;
    this.stage = stage;
    this.promptVersion = promptVersion;
    this.resultJson = resultJson;
    this.degraded = degraded;
    this.iterations = iterations;
    this.traceId = traceId;
    this.createdAt = createdAt;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public ChangeRequest getChangeRequest() {
    return changeRequest;
  }

  public ChangeAnalysis getAnalysis() {
    return analysis;
  }

  public void setAnalysis(ChangeAnalysis analysis) {
    this.analysis = analysis;
  }

  public String getStage() {
    return stage;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getResultJson() {
    return resultJson;
  }

  public boolean isDegraded() {
    return degraded;
  }

  public int getIterations() {
    return iterations;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<QaFinding> getFindings() {
    return findings;
  }

  public void addFinding(QaFinding finding) {
    findings.add(finding);
    finding.setReviewRecord(this);
  }
}
