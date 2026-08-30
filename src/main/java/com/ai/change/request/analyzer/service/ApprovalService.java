package com.ai.change.request.analyzer.service;

import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ApprovalDecision;
import com.ai.change.request.analyzer.domain.ApprovalRepository;
import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ApprovalConflictException;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisao humana sobre analise com aprovacao exigida: transicao apenas a partir de PENDING;
 * registra approver, decisao, momento e trace_id. A regra de obrigatoriedade permanece em {@code
 * RiskPolicy}.
 */
@Service
public class ApprovalService {

  private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

  private final ChangeRequestRepository changeRequestRepository;
  private final ApprovalRepository approvalRepository;

  public ApprovalService(
      ChangeRequestRepository changeRequestRepository, ApprovalRepository approvalRepository) {
    this.changeRequestRepository = changeRequestRepository;
    this.approvalRepository = approvalRepository;
  }

  @Transactional
  public Approval decide(
      UUID requestId, String approver, ApprovalDecision decision, String traceId) {
    ChangeRequest request =
        changeRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new ChangeRequestNotFoundException(requestId));
    Approval approval = request.getApproval();
    if (approval == null
        || !approval.isRequired()
        || approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ApprovalConflictException(requestId);
    }
    approval.decide(approver, decision, traceId);
    approvalRepository.save(approval);
    log.info(
        "approval_decided request_id={} approver={} decision={} trace_id={}",
        requestId,
        approver,
        decision,
        traceId);
    return approval;
  }
}
