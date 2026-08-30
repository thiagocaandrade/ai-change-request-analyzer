package com.ai.change.request.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.domain.InvalidConfidenceException;
import com.ai.change.request.analyzer.domain.RiskLevel;
import com.ai.change.request.analyzer.domain.RiskPolicy;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.security.SecurityAssessment;
import com.ai.change.request.analyzer.security.SecurityAssessmentRepository;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import com.ai.change.request.analyzer.web.CreateAnalysisRequest;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({
  AnalysisService.class,
  RiskPolicy.class,
  SecurityAssessmentService.class,
  AnalysisMetrics.class,
  TraceService.class,
  AnalysisServiceTest.MetricsConfig.class
})
@ActiveProfiles("test")
class AnalysisServiceTest {

  @TestConfiguration
  static class MetricsConfig {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Autowired private AnalysisService service;

  @Autowired private ChangeRequestRepository repository;

  @Autowired private SecurityAssessmentRepository securityAssessmentRepository;

  @Autowired private MeterRegistry meterRegistry;

  private ChangeRequest createRequest() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-" + UUID.randomUUID());
    return repository.save(request);
  }

  private CreateAnalysisRequest payload(RiskLevel level, Double confidence) {
    return new CreateAnalysisRequest(
        List.of(
            new CreateAnalysisRequest.FindingDto(
                "discount-service", "Desconto VIP alterado", "HIGH")),
        new CreateAnalysisRequest.RiskDto(level, confidence, "regra financeira"),
        List.of(
            new CreateAnalysisRequest.RecommendationDto(
                "discount-service",
                "Cobrir desconto VIP de 15%",
                "HIGH",
                "categoria financial_business_rule_regression: impacto=HIGH, probabilidade=MEDIUM -> HIGH (matriz deterministica)",
                "financial_business_rule_regression",
                true)));
  }

  @Test
  void highRiskMarksApprovalRequiredPending() {
    ChangeRequest request = createRequest();

    ChangeAnalysis analysis =
        service.registerAnalysis(request.getId(), payload(RiskLevel.HIGH, 0.95));

    assertThat(analysis.getRiskAssessment().getLevel()).isEqualTo(RiskLevel.HIGH);
    ChangeRequest loaded = repository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getApproval().isRequired()).isTrue();
    assertThat(loaded.getApproval().getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(loaded.getAnalysis().getFindings()).hasSize(1);
    assertThat(loaded.getAnalysis().getRecommendations()).hasSize(1);
  }

  @Test
  void lowRiskDoesNotRequireApproval() {
    ChangeRequest request = createRequest();

    service.registerAnalysis(request.getId(), payload(RiskLevel.LOW, 0.8));

    ChangeRequest loaded = repository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getApproval().isRequired()).isFalse();
    assertThat(loaded.getApproval().getStatus()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void externalSuggestionCannotOverrideHighRiskRule() {
    ChangeRequest request = createRequest();
    RiskLevel externalSuggestion = RiskLevel.LOW;

    service.registerAnalysis(request.getId(), payload(RiskLevel.HIGH, 0.9));

    assertThat(externalSuggestion).isEqualTo(RiskLevel.LOW);
    ChangeRequest loaded = repository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getApproval().isRequired()).isTrue();
  }

  @Test
  void invalidConfidenceRejectsRegistration() {
    ChangeRequest request = createRequest();

    assertThatThrownBy(
            () -> service.registerAnalysis(request.getId(), payload(RiskLevel.MEDIUM, 1.5)))
        .isInstanceOf(InvalidConfidenceException.class);

    ChangeRequest loaded = repository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getAnalysis()).isNull();
    assertThat(loaded.getApproval()).isNull();
  }

  @Test
  void unknownRequestRejected() {
    assertThatThrownBy(
            () -> service.registerAnalysis(UUID.randomUUID(), payload(RiskLevel.LOW, 0.5)))
        .isInstanceOf(ChangeRequestNotFoundException.class);
  }

  @Test
  void analysisWithoutRiskDoesNotRequireApproval() {
    ChangeRequest request = createRequest();
    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);

    service.persistAnalysis(request, analysis);

    ChangeRequest loaded = repository.findById(request.getId()).orElseThrow();
    assertThat(loaded.getAnalysis()).isNotNull();
    assertThat(loaded.getApproval().isRequired()).isFalse();
    assertThat(loaded.getApproval().getStatus()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void persistAnalysisPersistsSecurityEventsLinkedToRequest() {
    ChangeRequest request = createRequest();
    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);

    service.persistAnalysis(
        request,
        analysis,
        List.of(new SecurityEvent("prompt_injection", "code", "ignore as instruções", "IGNORED")));

    List<SecurityAssessment> events =
        securityAssessmentRepository.findByChangeRequestId(request.getId());
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getSource()).isEqualTo("code");
    assertThat(events.get(0).getAction()).isEqualTo("IGNORED");
    assertThat(events.get(0).getTraceId()).isEqualTo(request.getTraceId());
  }

  @Test
  void completedHighRiskAnalysisIncrementsMetrics() {
    ChangeRequest request = createRequest();
    double highRiskBefore = meterRegistry.get(AnalysisMetrics.HIGH_RISK_CHANGES).counter().count();
    long durationBefore = meterRegistry.get(AnalysisMetrics.ANALYSIS_DURATION).timer().count();

    service.registerAnalysis(request.getId(), payload(RiskLevel.HIGH, 0.95));

    assertThat(meterRegistry.get(AnalysisMetrics.HIGH_RISK_CHANGES).counter().count())
        .isEqualTo(highRiskBefore + 1.0);
    assertThat(meterRegistry.get(AnalysisMetrics.ANALYSIS_DURATION).timer().count())
        .isEqualTo(durationBefore + 1);
  }

  @Test
  void lowRiskAnalysisDoesNotIncrementHighRiskMetric() {
    ChangeRequest request = createRequest();
    double highRiskBefore = meterRegistry.get(AnalysisMetrics.HIGH_RISK_CHANGES).counter().count();

    service.registerAnalysis(request.getId(), payload(RiskLevel.LOW, 0.8));

    assertThat(meterRegistry.get(AnalysisMetrics.HIGH_RISK_CHANGES).counter().count())
        .isEqualTo(highRiskBefore);
  }
}
