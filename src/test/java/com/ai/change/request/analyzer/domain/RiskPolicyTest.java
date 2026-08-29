package com.ai.change.request.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RiskPolicyTest {

  private final RiskPolicy policy = new RiskPolicy();

  @Test
  void highRiskRequiresApproval() {
    RiskPolicy.RiskDecision decision = policy.evaluate(RiskLevel.HIGH, 0.9);

    assertThat(decision.approvalRequired()).isTrue();
    assertThat(decision.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void highRiskRequiresApprovalRegardlessOfExternalSuggestion() {
    RiskLevel externalSuggestion = RiskLevel.LOW;

    RiskPolicy.RiskDecision decision = policy.evaluate(RiskLevel.HIGH, 0.9);

    assertThat(externalSuggestion).isNotEqualTo(RiskLevel.HIGH);
    assertThat(decision.approvalRequired()).isTrue();
  }

  @Test
  void mediumRiskDoesNotRequireApproval() {
    RiskPolicy.RiskDecision decision = policy.evaluate(RiskLevel.MEDIUM, 0.7);

    assertThat(decision.approvalRequired()).isFalse();
    assertThat(decision.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void lowRiskDoesNotRequireApproval() {
    RiskPolicy.RiskDecision decision = policy.evaluate(RiskLevel.LOW, 0.9);

    assertThat(decision.approvalRequired()).isFalse();
  }

  @Test
  void confidenceAboveOneIsRejected() {
    assertThatThrownBy(() -> policy.evaluate(RiskLevel.MEDIUM, 1.5))
        .isInstanceOf(InvalidConfidenceException.class);
  }

  @Test
  void negativeConfidenceIsRejected() {
    assertThatThrownBy(() -> policy.evaluate(RiskLevel.MEDIUM, -0.1))
        .isInstanceOf(InvalidConfidenceException.class);
  }

  @Test
  void missingConfidenceWithLevelIsRejected() {
    assertThatThrownBy(() -> policy.evaluate(RiskLevel.HIGH, null))
        .isInstanceOf(InvalidConfidenceException.class);
  }

  @Test
  void absentRiskDoesNotRequireApproval() {
    RiskPolicy.RiskDecision decision = policy.evaluate(null, null);

    assertThat(decision.approvalRequired()).isFalse();
    assertThat(decision.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void boundaryConfidenceValuesAreAccepted() {
    assertThat(policy.evaluate(RiskLevel.LOW, 0.0).approvalRequired()).isFalse();
    assertThat(policy.evaluate(RiskLevel.HIGH, 1.0).approvalRequired()).isTrue();
  }
}
