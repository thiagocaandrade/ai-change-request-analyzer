package com.ai.change.request.analyzer.tools;

import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Decorador de tool com timeout por execucao, retry limitado (max. 2) com backoff e registro de
 * cada tentativa em log estruturado e evento de auditoria (via {@link ResilienceExecutor}).
 * Esgotados os retries, registra a falha e devolve erro estruturado — a analise segue.
 */
public class ResilientToolCallback implements ToolCallback {

  private final ToolCallback delegate;
  private final long timeoutMs;
  private final ResilienceExecutor resilienceExecutor;
  private final AnalysisMetrics metrics;

  public ResilientToolCallback(
      ToolCallback delegate,
      long timeoutMs,
      ResilienceExecutor resilienceExecutor,
      AnalysisMetrics metrics) {
    this.delegate = delegate;
    this.timeoutMs = timeoutMs;
    this.resilienceExecutor = resilienceExecutor;
    this.metrics = metrics;
  }

  public ToolCallback delegate() {
    return delegate;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  @Override
  public String call(String toolInput) {
    String tool = delegate.getToolDefinition().name();
    return resilienceExecutor.execute(
        tool,
        "tool_call",
        () -> {
          metrics.toolCall();
          try {
            return delegate.call(toolInput);
          } catch (RuntimeException e) {
            metrics.toolError();
            throw e;
          }
        },
        timeoutMs,
        () ->
            "{\"error\":\"tool_failed_after_retries\",\"tool\":\""
                + tool
                + "\",\"message\":\""
                + "falha apos "
                + ResilienceExecutor.MAX_ATTEMPTS
                + " tentativas\"}",
        tool,
        null);
  }
}
