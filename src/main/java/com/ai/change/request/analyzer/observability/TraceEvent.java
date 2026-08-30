package com.ai.change.request.analyzer.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de auditoria de execucao persistido; permite reconstruir qualquer execucao por trace_id.
 */
@Entity
@Table(name = "trace_event")
public class TraceEvent {

  @Id private UUID id;

  @Column(length = 64)
  private String traceId;

  @Column(length = 64)
  private String requestId;

  @Column(length = 64)
  private String node;

  @Column(length = 64)
  private String event;

  private Long durationMs;

  @Column(length = 32)
  private String status;

  @Column(length = 512)
  private String error;

  @Column(length = 16)
  private String risk;

  @Column(length = 64)
  private String tool;

  @Column(length = 128)
  private String model;

  @Column(nullable = false)
  private Instant createdAt;

  protected TraceEvent() {}

  public TraceEvent(
      String traceId,
      String requestId,
      String node,
      String event,
      Long durationMs,
      String status,
      String error,
      String risk,
      String tool,
      String model,
      Instant createdAt) {
    this.traceId = traceId;
    this.requestId = requestId;
    this.node = node;
    this.event = event;
    this.durationMs = durationMs;
    this.status = status;
    this.error = error;
    this.risk = risk;
    this.tool = tool;
    this.model = model;
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

  public String getTraceId() {
    return traceId;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getNode() {
    return node;
  }

  public String getEvent() {
    return event;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public String getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public String getRisk() {
    return risk;
  }

  public String getTool() {
    return tool;
  }

  public String getModel() {
    return model;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
