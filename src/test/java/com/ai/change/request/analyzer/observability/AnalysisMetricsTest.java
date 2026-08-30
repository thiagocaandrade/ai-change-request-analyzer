package com.ai.change.request.analyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AnalysisMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final AnalysisMetrics metrics = new AnalysisMetrics(registry);

  @Test
  void analysisDurationRecordsTimerValue() {
    metrics.recordAnalysis(150);
    metrics.recordAnalysis(50);

    double total =
        registry
            .get(AnalysisMetrics.ANALYSIS_DURATION)
            .timer()
            .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS);
    assertThat(total).isEqualTo(200.0);
  }

  @Test
  void countersIncrementIndependently() {
    metrics.llmCall();
    metrics.llmCall();
    metrics.llmCall();
    metrics.toolCall();
    metrics.toolCall();
    metrics.toolError();
    metrics.highRiskChange();
    metrics.promptInjection();
    metrics.promptInjection();
    metrics.validationFailure();

    assertThat(counter(AnalysisMetrics.LLM_CALLS)).isEqualTo(3.0);
    assertThat(counter(AnalysisMetrics.TOOL_CALLS)).isEqualTo(2.0);
    assertThat(counter(AnalysisMetrics.TOOL_ERRORS)).isEqualTo(1.0);
    assertThat(counter(AnalysisMetrics.HIGH_RISK_CHANGES)).isEqualTo(1.0);
    assertThat(counter(AnalysisMetrics.PROMPT_INJECTION_COUNT)).isEqualTo(2.0);
    assertThat(counter(AnalysisMetrics.VALIDATION_FAILURES)).isEqualTo(1.0);
  }

  @Test
  void allSevenMetricsAreRegisteredWithReadableNames() {
    assertThat(registry.get(AnalysisMetrics.ANALYSIS_DURATION).meter().getId().getName())
        .isEqualTo("analysis_duration");
    assertThat(registry.get(AnalysisMetrics.LLM_CALLS).meter().getId().getName())
        .isEqualTo("llm_calls");
    assertThat(registry.get(AnalysisMetrics.TOOL_CALLS).meter().getId().getName())
        .isEqualTo("tool_calls");
    assertThat(registry.get(AnalysisMetrics.TOOL_ERRORS).meter().getId().getName())
        .isEqualTo("tool_errors");
    assertThat(registry.get(AnalysisMetrics.HIGH_RISK_CHANGES).meter().getId().getName())
        .isEqualTo("high_risk_changes");
    assertThat(registry.get(AnalysisMetrics.PROMPT_INJECTION_COUNT).meter().getId().getName())
        .isEqualTo("prompt_injection_count");
    assertThat(registry.get(AnalysisMetrics.VALIDATION_FAILURES).meter().getId().getName())
        .isEqualTo("validation_failures");
  }

  private double counter(String name) {
    return registry.get(name).counter().count();
  }
}
