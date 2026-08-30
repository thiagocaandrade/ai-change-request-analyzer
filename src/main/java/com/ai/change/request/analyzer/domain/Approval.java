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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval")
public class Approval {

  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "change_request_id", nullable = false, unique = true)
  private ChangeRequest changeRequest;

  @Column(nullable = false)
  private boolean required;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ApprovalStatus status;

  @Column(length = 64)
  private String approver;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private ApprovalDecision decision;

  private Instant decidedAt;

  @Column(length = 64)
  private String traceId;

  @Column(nullable = false)
  private Instant createdAt;

  protected Approval() {}

  public Approval(ChangeRequest changeRequest, boolean required, ApprovalStatus status) {
    this.changeRequest = changeRequest;
    this.required = required;
    this.status = status;
  }

  /** Registra a decisao humana; apenas PENDING pode transitar para APPROVED/REJECTED. */
  public void decide(String approver, ApprovalDecision decision, String traceId) {
    this.approver = approver;
    this.decision = decision;
    this.decidedAt = Instant.now();
    this.traceId = traceId;
    this.status =
        decision == ApprovalDecision.APPROVED ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
  }

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

  public boolean isRequired() {
    return required;
  }

  public ApprovalStatus getStatus() {
    return status;
  }

  public void setStatus(ApprovalStatus status) {
    this.status = status;
  }

  public String getApprover() {
    return approver;
  }

  public ApprovalDecision getDecision() {
    return decision;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
