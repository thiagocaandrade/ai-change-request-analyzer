package com.ai.change.request.analyzer.devops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro persistido de uma analise de logs de pipeline assistida por IA: prompt versionado usado,
 * resultado estruturado, confidence, flag de degradacao e trace_id.
 */
@Entity
@Table(name = "log_analysis_record")
public class LogAnalysisRecord {

  @Id private UUID id;

  @Column(nullable = false, length = 32)
  private String promptVersion;

  @Column(nullable = false, columnDefinition = "text")
  private String resultJson;

  @Column(nullable = false)
  private double confidence;

  @Column(nullable = false)
  private boolean degraded;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false)
  private Instant createdAt;

  protected LogAnalysisRecord() {}

  public LogAnalysisRecord(
      String promptVersion,
      String resultJson,
      double confidence,
      boolean degraded,
      String traceId,
      Instant createdAt) {
    this.promptVersion = promptVersion;
    this.resultJson = resultJson;
    this.confidence = confidence;
    this.degraded = degraded;
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

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getResultJson() {
    return resultJson;
  }

  public double getConfidence() {
    return confidence;
  }

  public boolean isDegraded() {
    return degraded;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
