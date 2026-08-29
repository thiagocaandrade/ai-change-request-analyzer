package com.ai.change.request.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ChangeAnalysisRepositoryTest {

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private ChangeAnalysisRepository changeAnalysisRepository;

  @Test
  void persistsAndRecoversFullAnalysisGraph() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-full-graph");

    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);
    analysis.addFinding(new ImpactFinding("discount-service", "Desconto VIP alterado", "HIGH"));
    analysis.addFinding(new ImpactFinding("discount-tests", "Testes existentes quebram", "MEDIUM"));
    analysis.setRiskAssessment(new RiskAssessment(RiskLevel.HIGH, 0.95, "regra financeira"));
    analysis.addRecommendation(
        new TestRecommendation("discount-service", "Cobrir desconto VIP de 15%", "HIGH"));

    request.setAnalysis(analysis);
    request.setApproval(new Approval(request, true, ApprovalStatus.PENDING));

    changeRequestRepository.save(request);

    ChangeRequest loaded = changeRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getAnalysis()).isNotNull();
    ChangeAnalysis loadedAnalysis = loaded.getAnalysis();
    assertThat(loadedAnalysis.getId()).isNotNull();
    assertThat(loadedAnalysis.getFindings()).hasSize(2);
    assertThat(loadedAnalysis.getRecommendations()).hasSize(1);
    assertThat(loadedAnalysis.getRiskAssessment()).isNotNull();
    assertThat(loadedAnalysis.getRiskAssessment().getLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(loadedAnalysis.getRiskAssessment().getConfidence()).isEqualTo(0.95);
    assertThat(loaded.getApproval()).isNotNull();
    assertThat(loaded.getApproval().isRequired()).isTrue();
    assertThat(loaded.getApproval().getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(loaded.getApproval().getCreatedAt()).isNotNull();
  }

  @Test
  void savesAndLoadsAnalysisWithoutRisk() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Renomear variavel interna");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-no-risk");

    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);
    request.setAnalysis(analysis);
    request.setApproval(new Approval(request, false, ApprovalStatus.PENDING));

    changeRequestRepository.save(request);

    ChangeRequest loaded = changeRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getAnalysis()).isNotNull();
    assertThat(loaded.getAnalysis().getRiskAssessment()).isNull();
    assertThat(loaded.getApproval().isRequired()).isFalse();
  }
}
