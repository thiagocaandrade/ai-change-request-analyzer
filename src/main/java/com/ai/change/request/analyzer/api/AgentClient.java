package com.ai.change.request.analyzer.api;

import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.api.dto.AnalyzeRequest;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import com.ai.change.request.analyzer.resilience.ResilienceExhaustedException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente do agente LangGraph (sidecar Python) com timeout, retry limitado e backoff via {@link
 * ResilienceExecutor}; cada tentativa e registrada em evento de auditoria. Esgotado o limite,
 * propaga {@link AgentUnavailableException} com a causa — nunca simula sucesso.
 */
@Component
public class AgentClient {

  private final RestClient restClient;
  private final long timeoutMs;
  private final ResilienceExecutor resilienceExecutor;

  public AgentClient(
      RestClient.Builder builder,
      @Value("${agent.url}") String agentUrl,
      @Value("${agent.timeout-ms:10000}") long timeoutMs,
      ResilienceExecutor resilienceExecutor) {
    this.timeoutMs = timeoutMs;
    this.resilienceExecutor = resilienceExecutor;
    var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
    this.restClient = builder.baseUrl(agentUrl).requestFactory(requestFactory).build();
  }

  public AgentResponse analyze(String requestId, String text, String traceId) {
    try {
      return resilienceExecutor.execute(
          "agent",
          "agent_analyze",
          () ->
              restClient
                  .post()
                  .uri("/analyze")
                  .header("X-Trace-Id", traceId)
                  .body(new AnalyzeRequest(requestId, text))
                  .retrieve()
                  .body(AgentResponse.class),
          timeoutMs,
          null);
    } catch (ResilienceExhaustedException e) {
      throw new AgentUnavailableException(
          "agente indisponivel apos " + ResilienceExecutor.MAX_ATTEMPTS + " tentativas",
          e.getCause() != null ? e.getCause() : e);
    }
  }
}
