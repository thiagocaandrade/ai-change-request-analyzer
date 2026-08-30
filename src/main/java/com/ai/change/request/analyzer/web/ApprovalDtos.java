package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.ApprovalDecision;
import com.ai.change.request.analyzer.domain.ApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Contrato do endpoint de aprovacao humana. */
public final class ApprovalDtos {

  private ApprovalDtos() {}

  public record ApprovalRequest(
      @NotBlank @Size(max = 64) String approver, @NotNull ApprovalDecision decision) {}

  public record ApprovalResponse(
      ApprovalStatus approvalStatus,
      String approver,
      ApprovalDecision decision,
      Instant decidedAt,
      String traceId) {}
}
