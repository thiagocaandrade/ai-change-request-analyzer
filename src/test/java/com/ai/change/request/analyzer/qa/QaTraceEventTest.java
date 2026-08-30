package com.ai.change.request.analyzer.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewFindingDto;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** Reconstrucao por trace_id dos eventos QA (qa_review, qa_refinement) em ordem cronologica. */
@DataJpaTest
@ActiveProfiles("test")
class QaTraceEventTest {

  @Autowired private TraceEventRepository traceEventRepository;

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void qaEventsAreReconstructedByTraceIdInChronologicalOrder() {
    MDC.put("trace_id", "trace-qa-events");
    QaCodeReviewService codeReviewService = mock(QaCodeReviewService.class);
    AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
    TraceService traceService = new TraceService(traceEventRepository);
    QaService qaService =
        new QaService(
            codeReviewService,
            aiAnalysisService,
            new EvidenceRenderer(new ObjectMapper()),
            new RiskMatrixService(),
            traceService,
            new AnalysisMetrics(new SimpleMeterRegistry()),
            new ObjectMapper());

    when(codeReviewService.review(anyString(), nullable(String.class)))
        .thenReturn(
            new QaCodeReviewService.ReviewOutcome(
                new CodeReviewResult(
                    List.of(
                        new CodeReviewFindingDto(
                            "discount-service",
                            "teste de regressao ausente",
                            "HIGH",
                            "business-rules.md")),
                    List.of(),
                    false),
                List.of(),
                false));
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("billing-api", "testar cobranca", "HIGH")),
                false),
            new TestPlanResult(
                List.of(
                    new TestRecommendationDto(
                        "discount-service", "cobrir regra de desconto", "HIGH")),
                false));

    qaService.generateTestPlanWithQa("Alterar desconto VIP", null, Map.of(), Map.of(), List.of());

    List<String> events =
        traceService.findByTraceId("trace-qa-events").stream()
            .map(event -> event.getNode() + ":" + event.getEvent())
            .toList();
    assertThat(events)
        .containsSubsequence(
            "qa_review:started",
            "qa_review:completed",
            "qa_refinement:refining",
            "qa_refinement:completed");
    assertThat(traceEventRepository.findByTraceIdOrderByCreatedAtAsc("trace-qa-events"))
        .allSatisfy(event -> assertThat(event.getTraceId()).isEqualTo("trace-qa-events"));
  }
}
