package com.ai.change.request.analyzer.security;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Evento de seguranca persistido vinculado a uma solicitacao de mudanca. */
@Entity
@Table(name = "security_assessment")
public class SecurityAssessment {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "change_request_id", nullable = false)
  private ChangeRequest changeRequest;

  @Column(nullable = false)
  private boolean detected;

  @Column(nullable = false, length = 32)
  private String type;

  @Column(nullable = false, length = 32)
  private String source;

  @Column(nullable = false, columnDefinition = "text")
  private String evidence;

  @Column(nullable = false, length = 16)
  private String action;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false)
  private Instant createdAt;

  protected SecurityAssessment() {}

  public SecurityAssessment(
      ChangeRequest changeRequest,
      boolean detected,
      String type,
      String source,
      String evidence,
      String action,
      String traceId,
      Instant createdAt) {
    this.changeRequest = changeRequest;
    this.detected = detected;
    this.type = type;
    this.source = source;
    this.evidence = evidence;
    this.action = action;
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

  public void setChangeRequest(ChangeRequest changeRequest) {
    this.changeRequest = changeRequest;
  }

  public boolean isDetected() {
    return detected;
  }

  public String getType() {
    return type;
  }

  public String getSource() {
    return source;
  }

  public String getEvidence() {
    return evidence;
  }

  public String getAction() {
    return action;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
