package com.ai.change.request.analyzer.devops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Execucao de pipeline registrada para deteccao de anomalia e tendencia de falha. */
@Entity
@Table(name = "pipeline_run")
public class PipelineRun {

  @Id private UUID id;

  @Column(nullable = false)
  private long durationMs;

  @Column(nullable = false)
  private boolean success;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false)
  private Instant createdAt;

  protected PipelineRun() {}

  public PipelineRun(long durationMs, boolean success, String traceId, Instant createdAt) {
    this.durationMs = durationMs;
    this.success = success;
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

  public long getDurationMs() {
    return durationMs;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
