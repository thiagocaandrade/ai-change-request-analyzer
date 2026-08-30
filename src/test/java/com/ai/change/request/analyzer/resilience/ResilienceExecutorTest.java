package com.ai.change.request.analyzer.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class ResilienceExecutorTest {

  private TraceEventRepository repository;
  private TraceService traceService;
  private ResilienceExecutor executor;

  @BeforeEach
  void setUp() {
    repository = mock(TraceEventRepository.class);
    traceService = new TraceService(repository);
    executor = new ResilienceExecutor(traceService, 0, 10);
  }

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void immediateSuccessRecordsCompletedEvent() {
    MDC.put("trace_id", "trace-res-1");

    String result = executor.execute("agent", "analyze", () -> "ok", 5000, null);

    assertThat(result).isEqualTo("ok");
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(1)).save(captor.capture());
    TraceEvent event = captor.getValue();
    assertThat(event.getNode()).isEqualTo("agent");
    assertThat(event.getEvent()).isEqualTo("analyze");
    assertThat(event.getStatus()).isEqualTo("ok");
    assertThat(event.getDurationMs()).isNotNull();
    assertThat(event.getTraceId()).isEqualTo("trace-res-1");
  }

  @Test
  void timeoutThenRecoverySucceedsWithinRetryLimit() {
    AtomicInteger calls = new AtomicInteger();
    String result =
        executor.execute(
            "retrieve_knowledge",
            "rag_search",
            () -> {
              if (calls.incrementAndGet() == 1) {
                sleepUninterruptibly(300);
              }
              return "hits";
            },
            100,
            null);

    assertThat(result).isEqualTo("hits");
    assertThat(calls.get()).isEqualTo(2);
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .anyMatch(e -> "failed".equals(e.getStatus()) && e.getError().contains("TimeoutException"));
    assertThat(captor.getAllValues()).anyMatch(e -> "ok".equals(e.getStatus()));
  }

  @Test
  void exhaustedRetriesReturnExplicitMarkedFallback() {
    AtomicInteger calls = new AtomicInteger();
    String result =
        executor.execute(
            "classify",
            "llm_call",
            () -> {
              calls.incrementAndGet();
              throw new IllegalStateException("modelo fora");
            },
            5000,
            () -> "fallback_degradado");

    assertThat(result).isEqualTo("fallback_degradado");
    assertThat(calls.get()).isEqualTo(3);
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(e -> "failed".equals(e.getStatus()))
        .hasSize(3)
        .allSatisfy(e -> assertThat(e.getError()).matches("attempt=\\d: IllegalStateException"));
    assertThat(captor.getAllValues())
        .anyMatch(
            e ->
                "degraded".equals(e.getStatus())
                    && e.getError().contains("fallback_after_3_attempts"));
  }

  @Test
  void criticalFailurePropagatesWithCauseWhenNoFallback() {
    AtomicInteger calls = new AtomicInteger();
    IllegalStateException root = new IllegalStateException("indisponivel");

    assertThatThrownBy(
            () ->
                executor.execute(
                    "agent",
                    "agent_analyze",
                    () -> {
                      calls.incrementAndGet();
                      throw root;
                    },
                    5000,
                    null))
        .isInstanceOf(ResilienceExhaustedException.class)
        .hasCause(root);
    assertThat(calls.get()).isEqualTo(3);
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .anyMatch(
            e ->
                "failed".equals(e.getStatus())
                    && e.getError().contains("exhausted_after_3_attempts")
                    && e.getError().contains("IllegalStateException"));
  }

  @Test
  void eachAttemptIsRecordedWithAttemptNumber() {
    AtomicInteger calls = new AtomicInteger();
    executor.execute(
        "search_code",
        "tool_call",
        () -> {
          calls.incrementAndGet();
          throw new IllegalStateException("boom");
        },
        5000,
        () -> "degraded");

    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(e -> "failed".equals(e.getStatus()))
        .extracting(TraceEvent::getError)
        .containsExactlyInAnyOrder(
            "attempt=1: IllegalStateException",
            "attempt=2: IllegalStateException",
            "attempt=3: IllegalStateException");
  }

  private static void sleepUninterruptibly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
