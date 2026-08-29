package com.ai.change.request.analyzer.domain;

import org.springframework.stereotype.Component;

@Component
public final class RiskPolicy {

  public record RiskDecision(boolean approvalRequired, ApprovalStatus approvalStatus) {}

  public RiskDecision evaluate(RiskLevel level, Double confidence) {
    if (level == null) {
      return new RiskDecision(false, ApprovalStatus.PENDING);
    }
    if (confidence == null || confidence < 0.0 || confidence > 1.0) {
      throw new InvalidConfidenceException(confidence);
    }
    return new RiskDecision(level == RiskLevel.HIGH, ApprovalStatus.PENDING);
  }
}
