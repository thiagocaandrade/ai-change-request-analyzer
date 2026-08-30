package com.ai.change.request.analyzer.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Metricas de execucao da analise (Micrometer), expostas via Actuator. */
@Component
public class AnalysisMetrics {

  public static final String ANALYSIS_DURATION = "analysis_duration";
  public static final String LLM_CALLS = "llm_calls";
  public static final String TOOL_CALLS = "tool_calls";
  public static final String TOOL_ERRORS = "tool_errors";
  public static final String HIGH_RISK_CHANGES = "high_risk_changes";
  public static final String PROMPT_INJECTION_COUNT = "prompt_injection_count";
  public static final String VALIDATION_FAILURES = "validation_failures";
  public static final String QA_REVIEWS = "qa_reviews";
  public static final String QA_REFINEMENTS = "qa_refinements";

  private final Timer analysisDuration;
  private final Counter llmCalls;
  private final Counter toolCalls;
  private final Counter toolErrors;
  private final Counter highRiskChanges;
  private final Counter promptInjectionCount;
  private final Counter validationFailures;
  private final Counter qaReviews;
  private final Counter qaRefinements;

  public AnalysisMetrics(MeterRegistry registry) {
    this.analysisDuration =
        Timer.builder(ANALYSIS_DURATION)
            .description("Duracao total das analises")
            .register(registry);
    this.llmCalls = Counter.builder(LLM_CALLS).description("Chamadas ao modelo").register(registry);
    this.toolCalls =
        Counter.builder(TOOL_CALLS).description("Execucoes de tool").register(registry);
    this.toolErrors =
        Counter.builder(TOOL_ERRORS).description("Falhas de execucao de tool").register(registry);
    this.highRiskChanges =
        Counter.builder(HIGH_RISK_CHANGES)
            .description("Analises com risco HIGH")
            .register(registry);
    this.promptInjectionCount =
        Counter.builder(PROMPT_INJECTION_COUNT)
            .description("Eventos de prompt injection persistidos")
            .register(registry);
    this.validationFailures =
        Counter.builder(VALIDATION_FAILURES)
            .description("Saidas de LLM invalidas rejeitadas")
            .register(registry);
    this.qaReviews =
        Counter.builder(QA_REVIEWS).description("Execucoes de code review com IA").register(registry);
    this.qaRefinements =
        Counter.builder(QA_REFINEMENTS)
            .description("Iteracoes de refinamento de recomendacoes de teste")
            .register(registry);
  }

  public void recordAnalysis(long durationMs) {
    analysisDuration.record(durationMs, TimeUnit.MILLISECONDS);
  }

  public void llmCall() {
    llmCalls.increment();
  }

  public void toolCall() {
    toolCalls.increment();
  }

  public void toolError() {
    toolErrors.increment();
  }

  public void highRiskChange() {
    highRiskChanges.increment();
  }

  public void promptInjection() {
    promptInjectionCount.increment();
  }

  public void validationFailure() {
    validationFailures.increment();
  }

  public void qaReview() {
    qaReviews.increment();
  }

  public void qaRefinement() {
    qaRefinements.increment();
  }
}
