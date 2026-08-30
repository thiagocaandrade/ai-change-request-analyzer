package com.ai.change.request.analyzer.resilience;

import com.ai.change.request.analyzer.observability.TraceService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Politica unica de resiliencia para integracoes externas: timeout configurável por chamada, retry
 * limitado (1 tentativa + 2 retries), backoff crescente limitado, registro de cada tentativa em log
 * estruturado e em evento de auditoria, e fallback explicito marcado como degradado. Fallback nulo
 * propaga {@link ResilienceExhaustedException} com a causa — falha critica nunca e escondida.
 */
@Component
public class ResilienceExecutor {

  /** 1 tentativa inicial + 2 retries. */
  public static final int MAX_ATTEMPTS = 3;

  private static final Logger log = LoggerFactory.getLogger(ResilienceExecutor.class);

  private final TraceService traceService;
  private final long backoffMs;
  private final long maxBackoffMs;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public ResilienceExecutor(
      TraceService traceService,
      @Value("${resilience.backoff-ms:200}") long backoffMs,
      @Value("${resilience.max-backoff-ms:2000}") long maxBackoffMs) {
    this.traceService = traceService;
    this.backoffMs = backoffMs;
    this.maxBackoffMs = maxBackoffMs;
  }

  public <T> T execute(
      String node, String event, Supplier<T> operation, long timeoutMs, Supplier<T> fallback) {
    return execute(node, event, operation, timeoutMs, fallback, null, null);
  }

  /** Execucao resiliente com tool/model opcionais para o evento de auditoria. */
  public <T> T execute(
      String node,
      String event,
      Supplier<T> operation,
      long timeoutMs,
      Supplier<T> fallback,
      String tool,
      String model) {
    Exception lastError = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      long start = System.nanoTime();
      Future<T> future = executor.submit(operation::get);
      try {
        T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        traceService.record(
            node, event, TraceService.elapsedMs(start), "ok", null, null, tool, model);
        if (attempt > 1) {
          log.info(
              "resilience_recovered node={} event={} attempt={} trace_id={}",
              node,
              event,
              attempt,
              MDC.get("trace_id"));
        }
        return result;
      } catch (TimeoutException e) {
        lastError = e;
        future.cancel(true);
        recordAttemptFailure(node, event, start, attempt, "TimeoutException", tool, model);
        log.warn(
            "resilience_timeout node={} event={} attempt={} timeout_ms={} trace_id={}",
            node,
            event,
            attempt,
            timeoutMs,
            MDC.get("trace_id"));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ResilienceExhaustedException(node, event, e);
      } catch (Exception e) {
        Exception effective =
            e instanceof ExecutionException && e.getCause() instanceof Exception inner ? inner : e;
        lastError = effective;
        recordAttemptFailure(
            node, event, start, attempt, effective.getClass().getSimpleName(), tool, model);
        log.warn(
            "resilience_attempt_failed node={} event={} attempt={} error={} trace_id={}",
            node,
            event,
            attempt,
            effective.getClass().getSimpleName(),
            MDC.get("trace_id"));
      }
      if (attempt < MAX_ATTEMPTS) {
        sleep(backoffFor(attempt));
      }
    }
    if (fallback != null) {
      traceService.record(
          node,
          event,
          null,
          "degraded",
          "fallback_after_" + MAX_ATTEMPTS + "_attempts",
          null,
          tool,
          model);
      log.warn(
          "resilience_fallback node={} event={} attempts={} trace_id={}",
          node,
          event,
          MAX_ATTEMPTS,
          MDC.get("trace_id"));
      return fallback.get();
    }
    String cause = lastError == null ? "unknown" : lastError.getClass().getSimpleName();
    traceService.record(
        node,
        event,
        null,
        "failed",
        "exhausted_after_" + MAX_ATTEMPTS + "_attempts: " + cause,
        null,
        tool,
        model);
    throw new ResilienceExhaustedException(node, event, lastError);
  }

  private void recordAttemptFailure(
      String node,
      String event,
      long start,
      int attempt,
      String errorType,
      String tool,
      String model) {
    traceService.record(
        node,
        event,
        TraceService.elapsedMs(start),
        "failed",
        "attempt=" + attempt + ": " + errorType,
        null,
        tool,
        model);
  }

  private long backoffFor(int attempt) {
    return Math.min(backoffMs * attempt, maxBackoffMs);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrompido durante backoff de resiliência", e);
    }
  }
}
