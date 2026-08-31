package com.ai.change.request.analyzer.devops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Evento de anomalia persistido: metrica, baseline, valor observado, desvio e severidade. */
@Entity
@Table(name = "anomaly_event")
public class AnomalyEvent {

  @Id private UUID id;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false, length = 32)
  private String metric;

  @Column(nullable = false)
  private double baseline;

  @Column(nullable = false)
  private double observed;

  @Column(nullable = false)
  private double deviation;

  @Column(nullable = false, length = 16)
  private String severity;

  @Column(nullable = false)
  private Instant createdAt;

  protected AnomalyEvent() {}

  public AnomalyEvent(
      String traceId,
      String metric,
      double baseline,
      double observed,
      double deviation,
      String severity,
      Instant createdAt) {
    this.traceId = traceId;
    this.metric = metric;
    this.baseline = baseline;
    this.observed = observed;
    this.deviation = deviation;
    this.severity = severity;
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

  public String getMetric() {
    return metric;
  }

  public double getBaseline() {
    return baseline;
  }

  public double getObserved() {
    return observed;
  }

  public double getDeviation() {
    return deviation;
  }

  public String getSeverity() {
    return severity;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
