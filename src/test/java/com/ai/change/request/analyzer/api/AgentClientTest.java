package com.ai.change.request.analyzer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AgentClientTest {

  private static final String SUCCESS_BODY =
      """
            {"request_id":"req-1","status":"completed","result":{"processed_text":"Alterar desconto VIP"}}
            """;

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
          .hasMessageContaining("3 tentativas");
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

  private AgentClient buildClient(HttpServer server) {
    return new AgentClient(
        RestClient.builder(), "http://localhost:" + server.getAddress().getPort(), 1000);
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
