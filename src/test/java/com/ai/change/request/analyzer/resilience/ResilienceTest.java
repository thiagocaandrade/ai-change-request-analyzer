package com.ai.change.request.analyzer.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cenarios integrados da politica unica de resiliencia com persistencia real de eventos de
 * auditoria (H2) e reconstrucao por trace_id.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResilienceTest {

  @Autowired private ResilienceExecutor executor;

  @Autowired private TraceEventRepository traceEventRepository;

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void timeoutThenRecoveryIsReconstructibleByTraceId() {
    MDC.put("trace_id", "trace-res-1");
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

    List<TraceEvent> events = traceEventRepository.findByTraceIdOrderByCreatedAtAsc("trace-res-1");
    assertThat(events).hasSize(2);
    assertThat(events.get(0).getStatus()).isEqualTo("failed");
    assertThat(events.get(0).getError()).contains("TimeoutException");
    assertThat(events.get(1).getStatus()).isEqualTo("ok");
    assertThat(events).allSatisfy(e -> assertThat(e.getTraceId()).isEqualTo("trace-res-1"));
  }

  @Test
  void exhaustedRetriesUseExplicitMarkedFallback() {
    MDC.put("trace_id", "trace-res-2");
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
            () -> "resultado_degradado");

    assertThat(result).isEqualTo("resultado_degradado");
    assertThat(calls.get()).isEqualTo(3);

    List<TraceEvent> events = traceEventRepository.findByTraceIdOrderByCreatedAtAsc("trace-res-2");
    assertThat(events).hasSize(4);
    assertThat(events.get(3).getStatus()).isEqualTo("degraded");
    assertThat(events.get(3).getError()).contains("fallback_after_3_attempts");
    assertThat(events)
        .filteredOn(e -> "failed".equals(e.getStatus()))
        .hasSize(3)
        .allSatisfy(e -> assertThat(e.getError()).startsWith("attempt="));
  }

  @Test
  void agentUnavailablePropagatesCriticalFailureWithCause() throws IOException {
    MDC.put("trace_id", "trace-res-3");
    HttpServer server = startFailingServer();
    try {
      AgentClient client =
          new AgentClient(
              RestClient.builder(),
              "http://localhost:" + server.getAddress().getPort(),
              1000,
              executor);

      assertThatThrownBy(() -> client.analyze("req-res-3", "texto", "trace-res-3"))
          .isInstanceOf(AgentUnavailableException.class)
          .hasCauseInstanceOf(RestClientException.class);
    } finally {
      server.stop(0);
    }

    List<TraceEvent> events = traceEventRepository.findByTraceIdOrderByCreatedAtAsc("trace-res-3");
    assertThat(events).hasSize(4);
    assertThat(events)
        .filteredOn(e -> "failed".equals(e.getStatus()))
        .hasSize(4)
        .allSatisfy(e -> assertThat(e.getNode()).isEqualTo("agent"));
    assertThat(events.get(3).getError()).contains("exhausted_after_3_attempts");
  }

  private HttpServer startFailingServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/analyze",
        exchange -> {
          byte[] bytes = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
          exchange.close();
        });
    server.start();
    return server;
  }

  private static void sleepUninterruptibly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
