package com.ai.change.request.analyzer.api;

import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.api.dto.AnalyzeRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AgentClient {

  private static final Logger log = LoggerFactory.getLogger(AgentClient.class);
  private static final int MAX_ATTEMPTS = 3;

  private final RestClient restClient;

  public AgentClient(
      RestClient.Builder builder,
      @Value("${agent.url}") String agentUrl,
      @Value("${agent.timeout-ms:10000}") long timeoutMs) {
    var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
    this.restClient = builder.baseUrl(agentUrl).requestFactory(requestFactory).build();
  }

  public AgentResponse analyze(String requestId, String text, String traceId) {
    int attempt = 0;
    while (true) {
      attempt++;
      try {
        return restClient
            .post()
            .uri("/analyze")
            .header("X-Trace-Id", traceId)
            .body(new AnalyzeRequest(requestId, text))
            .retrieve()
            .body(AgentResponse.class);
      } catch (RestClientException e) {
        if (attempt >= MAX_ATTEMPTS) {
          throw new AgentUnavailableException(
              "agente indisponivel apos " + MAX_ATTEMPTS + " tentativas", e);
        }
        long backoffMs = 500L * attempt;
        log.warn(
            "agent_attempt_failed attempt={} error={} retrying_in_ms={}",
            attempt,
            e.getClass().getSimpleName(),
            backoffMs);
        sleep(backoffMs);
      }
    }
  }

  private void sleep(long backoffMs) {
    try {
      Thread.sleep(backoffMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new AgentUnavailableException("interrompido durante retry ao agente", ie);
    }
  }
}
