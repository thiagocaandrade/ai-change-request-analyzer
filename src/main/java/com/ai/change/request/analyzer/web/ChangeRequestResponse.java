package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.domain.RiskLevel;
import java.util.UUID;

public record ChangeRequestResponse(
    UUID id,
    String text,
    ChangeRequestStatus status,
    String traceId,
    String failureReason,
    AnalysisSummary analysis) {

  public record AnalysisSummary(
      RiskLevel riskLevel,
      Boolean approvalRequired,
      ApprovalStatus approvalStatus,
      int findingsCount,
      int recommendationsCount) {}
}
