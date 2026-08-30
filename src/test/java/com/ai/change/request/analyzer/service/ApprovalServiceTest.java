package com.ai.change.request.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ApprovalDecision;
import com.ai.change.request.analyzer.domain.ApprovalRepository;
import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ApprovalConflictException;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(ApprovalService.class)
@ActiveProfiles("test")
class ApprovalServiceTest {

  @Autowired private ApprovalService service;

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private ApprovalRepository approvalRepository;

  private ChangeRequest createRequest(boolean required) {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-approval-" + UUID.randomUUID());
    request.setApproval(new Approval(request, required, ApprovalStatus.PENDING));
    return changeRequestRepository.save(request);
  }

  @Test
  void approvedDecisionRecordsAllFields() {
    ChangeRequest request = createRequest(true);

    Approval approval =
        service.decide(request.getId(), "revisora", ApprovalDecision.APPROVED, "trace-decisao");

    assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(approval.getApprover()).isEqualTo("revisora");
    assertThat(approval.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(approval.getDecidedAt()).isNotNull();
    assertThat(approval.getTraceId()).isEqualTo("trace-decisao");

    Approval persisted = approvalRepository.findById(approval.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(persisted.getApprover()).isEqualTo("revisora");
    assertThat(persisted.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(persisted.getDecidedAt()).isNotNull();
  }

  @Test
  void rejectedDecisionRecordsAllFields() {
    ChangeRequest request = createRequest(true);

    Approval approval =
        service.decide(request.getId(), "revisora", ApprovalDecision.REJECTED, "trace-decisao");

    assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    assertThat(approval.getDecision()).isEqualTo(ApprovalDecision.REJECTED);
    assertThat(approval.getDecidedAt()).isNotNull();
  }

  @Test
  void secondDecisionIsRejectedWithConflict() {
    ChangeRequest request = createRequest(true);
    service.decide(request.getId(), "revisora", ApprovalDecision.APPROVED, "trace-1");

    assertThatThrownBy(
            () -> service.decide(request.getId(), "outra", ApprovalDecision.REJECTED, "trace-2"))
        .isInstanceOf(ApprovalConflictException.class);

    Approval persisted = approvalRepository.findById(request.getApproval().getId()).orElseThrow();
    assertThat(persisted.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(persisted.getApprover()).isEqualTo("revisora");
  }

  @Test
  void decisionWithoutRequiredApprovalIsRejectedWithConflict() {
    ChangeRequest request = createRequest(false);

    assertThatThrownBy(
            () -> service.decide(request.getId(), "revisora", ApprovalDecision.APPROVED, "trace-1"))
        .isInstanceOf(ApprovalConflictException.class);

    ChangeRequest loaded = changeRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getApproval().getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(loaded.getApproval().getDecision()).isNull();
  }

  @Test
  void decisionForUnknownRequestThrowsNotFound() {
    assertThatThrownBy(
            () -> service.decide(UUID.randomUUID(), "revisora", ApprovalDecision.APPROVED, "t"))
        .isInstanceOf(ChangeRequestNotFoundException.class);
  }
}
