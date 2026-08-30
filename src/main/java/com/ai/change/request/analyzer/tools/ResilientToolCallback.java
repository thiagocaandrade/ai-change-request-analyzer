package com.ai.change.request.analyzer.tools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Decorador de tool com timeout por execucao, retry limitado (max. 2) e logs com trace_id.
 * Esgotados os retries, registra a falha e devolve erro estruturado — a analise segue.
 */
public class ResilientToolCallback implements ToolCallback {

  private static final Logger log = LoggerFactory.getLogger(ResilientToolCallback.class);

  /** 1 tentativa inicial + 2 retries. */
  private static final int MAX_ATTEMPTS = 3;

  private final ToolCallback delegate;
  private final long timeoutMs;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public ResilientToolCallback(ToolCallback delegate, long timeoutMs) {
    this.delegate = delegate;
    this.timeoutMs = timeoutMs;
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
    String traceId = MDC.get("trace_id");
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        Future<String> future = executor.submit(() -> delegate.call(toolInput));
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        log.warn("tool_timeout tool={} attempt={} trace_id={}", tool, attempt, traceId);
      } catch (Exception e) {
        log.warn(
            "tool_attempt_failed tool={} attempt={} error={} trace_id={}",
            tool,
            attempt,
            e.getClass().getSimpleName(),
            traceId);
      }
      if (attempt < MAX_ATTEMPTS) {
        sleep(200L * attempt);
      }
    }
    log.error(
        "tool_failed_after_retries tool={} attempts={} trace_id={}", tool, MAX_ATTEMPTS, traceId);
    return "{\"error\":\"tool_failed_after_retries\",\"tool\":\""
        + tool
        + "\",\"message\":\""
        + "falha apos "
        + MAX_ATTEMPTS
        + " tentativas\"}";
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrompido durante retry da tool", e);
    }
  }
}
