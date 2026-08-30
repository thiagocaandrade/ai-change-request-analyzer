package com.ai.change.request.analyzer.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewFindingDto;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class QaServiceTest {

  private final QaCodeReviewService codeReviewService = mock(QaCodeReviewService.class);
  private final AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
  private final EvidenceRenderer evidenceRenderer = new EvidenceRenderer(new ObjectMapper());
  private final RiskMatrixService riskMatrixService = new RiskMatrixService();
  private final TraceEventRepository traceEventRepository = mock(TraceEventRepository.class);
  private final TraceService traceService = new TraceService(traceEventRepository);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final AnalysisMetrics metrics = new AnalysisMetrics(meterRegistry);

  private final QaService qaService =
      new QaService(
          codeReviewService,
          aiAnalysisService,
          evidenceRenderer,
          riskMatrixService,
          traceService,
          metrics,
          new ObjectMapper());

  private final CodeReviewResult reviewWithFindings =
      new CodeReviewResult(
          List.of(
              new CodeReviewFindingDto(
                  "discount-service", "teste de regressao ausente", "HIGH", "business-rules.md")),
          List.of(),
          false);

  @Test
  void qaFindingsFlowAsEvidenceIntoTestGeneration() {
    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(
            new QaCodeReviewService.ReviewOutcome(
                reviewWithFindings, List.of(Map.of("source", "business-rules.md")), false));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(
                    new TestRecommendationDto(
                        "discount-service", "cobrir desconto VIP de 15%", "HIGH")),
                false));

    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            "Alterar desconto VIP", null, Map.of("level", "HIGH"), Map.of(), List.of());

    assertThat(outcome.recommendations()).hasSize(1);
    assertThat(outcome.recommendations().get(0).component()).isEqualTo("discount-service");

    ArgumentCaptor<String> evidence = ArgumentCaptor.forClass(String.class);
    verify(aiAnalysisService).generateTestPlan(anyString(), evidence.capture());
    assertThat(evidence.getValue()).contains("FINDINGS QA");
    assertThat(evidence.getValue()).contains("discount-service");
    assertThat(evidence.getValue()).contains("teste de regressao ausente");
  }

  @Test
  void validRecommendationOnFirstTryNeedsNoRefinement() {
    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(new QaCodeReviewService.ReviewOutcome(reviewWithFindings, List.of(), false));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("discount-service", "testar regra", "HIGH")),
                false));

    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            "Alterar desconto VIP", null, Map.of(), Map.of(), List.of());

    assertThat(outcome.refinementIterations()).isZero();
    assertThat(outcome.recommendations().get(0).refined()).isTrue();
    verify(aiAnalysisService, times(1)).generateTestPlan(anyString(), anyString());
  }

  @Test
  void invalidRecommendationIsRefinedWithinLimitAndRecorded() {
    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(new QaCodeReviewService.ReviewOutcome(reviewWithFindings, List.of(), false));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("billing-api", "testar cobranca", "HIGH")),
                false),
            new TestPlanResult(
                List.of(
                    new TestRecommendationDto(
                        "discount-service", "testar regra de desconto", "HIGH")),
                false));

    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            "Alterar desconto VIP", null, Map.of(), Map.of(), List.of());

    assertThat(outcome.refinementIterations()).isEqualTo(1);
    assertThat(outcome.recommendations().get(0).component()).isEqualTo("discount-service");
    assertThat(outcome.recommendations().get(0).refined()).isTrue();

    ArgumentCaptor<String> evidence = ArgumentCaptor.forClass(String.class);
    verify(aiAnalysisService, times(2)).generateTestPlan(anyString(), evidence.capture());
    assertThat(evidence.getAllValues().get(1)).contains("[FEEDBACK]");
    assertThat(evidence.getAllValues().get(1)).contains("billing-api");
    assertThat(meterRegistry.get(AnalysisMetrics.QA_REFINEMENTS).counter().count()).isEqualTo(1.0);
    verify(traceEventRepository, times(2))
        .save(Mockito.<TraceEvent>argThat(event -> "qa_refinement".equals(event.getNode())));
  }

  @Test
  void recommendationRemainsUnrefinedWhenLimitExhausted() {
    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(new QaCodeReviewService.ReviewOutcome(reviewWithFindings, List.of(), false));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("billing-api", "testar cobranca", "HIGH")),
                false));

    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            "Alterar desconto VIP", null, Map.of(), Map.of(), List.of());

    assertThat(outcome.refinementIterations()).isEqualTo(QaService.MAX_REFINEMENT_ITERATIONS);
    assertThat(outcome.recommendations()).hasSize(1);
    assertThat(outcome.recommendations().get(0).refined()).isFalse();
    verify(aiAnalysisService, times(1 + QaService.MAX_REFINEMENT_ITERATIONS))
        .generateTestPlan(anyString(), anyString());
    verify(traceEventRepository, times(1))
        .save(Mockito.<TraceEvent>argThat(event -> "limit_exhausted".equals(event.getEvent())));
  }

  @Test
  void degradedReviewProducesRecommendationsButNeverModifiesRepository() throws Exception {
    Path testDir = Path.of("src", "test", "java");
    List<String> before = new ArrayList<>();
    try (var files = Files.walk(testDir)) {
      files.filter(Files::isRegularFile).forEach(file -> before.add(file.toString()));
    }

    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(
            new QaCodeReviewService.ReviewOutcome(
                new CodeReviewResult(List.of(), List.of(), true), List.of(), true));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("unit", "teste unitario (degradado)", "MEDIUM")),
                true));

    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            "Alterar desconto VIP", "diff", Map.of(), Map.of(), List.of());

    assertThat(outcome.degraded()).isTrue();
    assertThat(outcome.recommendations()).isNotEmpty();
    assertThat(outcome.recommendations().get(0).priorityJustification()).isNotBlank();
    assertThat(outcome.recommendations().get(0).priority()).isIn("LOW", "MEDIUM", "HIGH");
    List<String> after = new ArrayList<>();
    try (var files = Files.walk(testDir)) {
      files.filter(Files::isRegularFile).forEach(file -> after.add(file.toString()));
    }
    assertThat(after).containsExactlyInAnyOrderElementsOf(before);
  }
}
