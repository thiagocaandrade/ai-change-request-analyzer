package com.ai.change.request.analyzer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AgentClientTest {

  private static final String SUCCESS_BODY =
      """
            {"request_id":"req-1","status":"completed","result":{"processed_text":"Alterar desconto VIP"}}
            """;

  private TraceEventRepository repository;
  private TraceService traceService;
  private ResilienceExecutor resilienceExecutor;

  @BeforeEach
  void setUp() {
    repository = mock(TraceEventRepository.class);
    traceService = new TraceService(repository);
    resilienceExecutor = new ResilienceExecutor(traceService, 0, 10);
  }

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void parsesSuccessfulResponseAndPropagatesTraceIdHeader() throws IOException {
    HttpServer server =
        startServer(
            (exchange, count) -> {
              assertThat(exchange.getRequestHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-9");
              respond(exchange, 200, SUCCESS_BODY);
            });
    try {
      AgentClient client = buildClient(server);
      var response = client.analyze("req-1", "Alterar desconto VIP", "trace-9");
      assertThat(response.requestId()).isEqualTo("req-1");
      assertThat(response.status()).isEqualTo("completed");
      assertThat(response.result()).containsEntry("processed_text", "Alterar desconto VIP");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void throwsAfterExhaustingRetries() throws IOException {
    AtomicInteger attempts = new AtomicInteger();
    HttpServer server =
        startServer(
            (exchange, count) -> {
              attempts.incrementAndGet();
              respond(exchange, 500, "{\"error\":\"boom\"}");
            });
    try {
      AgentClient client = buildClient(server);
      assertThatThrownBy(() -> client.analyze("req-2", "texto", "trace-10"))
          .isInstanceOf(AgentUnavailableException.class)
          .hasMessageContaining("3 tentativas")
          .hasCauseInstanceOf(RestClientException.class);
      assertThat(attempts.get()).isEqualTo(3);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void recoversWhenAgentAnswersOnSecondAttempt() throws IOException {
    AtomicInteger attempts = new AtomicInteger();
    HttpServer server =
        startServer(
            (exchange, count) -> {
              if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "{\"error\":\"transient\"}");
              } else {
                respond(exchange, 200, SUCCESS_BODY);
              }
            });
    try {
      AgentClient client = buildClient(server);
      var response = client.analyze("req-3", "texto", "trace-11");
      assertThat(response.status()).isEqualTo("completed");
      assertThat(attempts.get()).isEqualTo(2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void eachAttemptIsRecordedAsTraceEventWithCause() throws IOException {
    MDC.put("trace_id", "trace-agent-events");
    HttpServer server =
        startServer((exchange, count) -> respond(exchange, 500, "{\"error\":\"boom\"}"));
    try {
      AgentClient client = buildClient(server);
      assertThatThrownBy(() -> client.analyze("req-4", "texto", "trace-agent-events"))
          .isInstanceOf(AgentUnavailableException.class);
    } finally {
      server.stop(0);
    }
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(repository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .filteredOn(e -> "failed".equals(e.getStatus()) && e.getError().startsWith("attempt="))
        .hasSize(3)
        .allSatisfy(
            e -> {
              assertThat(e.getNode()).isEqualTo("agent");
              assertThat(e.getEvent()).isEqualTo("agent_analyze");
              assertThat(e.getTraceId()).isEqualTo("trace-agent-events");
            });
    assertThat(captor.getAllValues())
        .anyMatch(
            e ->
                "failed".equals(e.getStatus())
                    && e.getError().contains("exhausted_after_3_attempts"));
  }

  private AgentClient buildClient(HttpServer server) {
    return new AgentClient(
        RestClient.builder(),
        "http://localhost:" + server.getAddress().getPort(),
        1000,
        resilienceExecutor);
  }

  private HttpServer startServer(Handler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/analyze",
        exchange -> {
          handler.handle(exchange, 0);
          exchange.close();
        });
    server.start();
    return server;
  }

  private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  @FunctionalInterface
  private interface Handler {
    void handle(com.sun.net.httpserver.HttpExchange exchange, int count) throws IOException;
  }
}
