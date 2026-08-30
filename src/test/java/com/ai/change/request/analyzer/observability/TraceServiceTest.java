package com.ai.change.request.analyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceServiceTest {

  private TraceEventRepository repository;
  private TraceService service;

  @BeforeEach
  void setUp() {
    repository = mock(TraceEventRepository.class);
    service = new TraceService(repository);
  }

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void recordPersistsEventWithMdcCorrelation() {
    MDC.put("trace_id", "trace-svc-1");
    MDC.put("request_id", "req-svc-1");

    service.record("classify", "completed", 15L, "ok", null, null, null, "gpt-x");

    verify(repository).save(any(TraceEvent.class));
  }

  @Test
  void persistenceFailureNeverThrows() {
    when(repository.save(any(TraceEvent.class))).thenThrow(new RuntimeException("banco fora"));

    assertThatCode(() -> service.record("pipeline", "analysis_started")).doesNotThrowAnyException();
  }

  @Test
  void findByTraceIdDelegatesOrdered() {
    when(repository.findByTraceIdOrderByCreatedAtAsc("trace-svc-2")).thenReturn(List.of());

    assertThat(service.findByTraceId("trace-svc-2")).isEmpty();
  }
}
