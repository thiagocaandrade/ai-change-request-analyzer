package com.ai.change.request.analyzer.observability;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Registro unico de eventos de execucao: emite log estruturado com campos padronizados e persiste o
 * evento de auditoria correlacionado por trace_id/request_id do MDC. Falha de persistencia e
 * registrada e nunca derruba o fluxo da analise.
 */
@Service
public class TraceService {

  private static final Logger log = LoggerFactory.getLogger(TraceService.class);

  private final TraceEventRepository repository;

  public TraceService(TraceEventRepository repository) {
    this.repository = repository;
  }

  /** Registra evento simples (inicio de etapa). */
  public void record(String node, String event) {
    record(node, event, null, null, null, null, null, null, null);
  }

  /** Registra evento completo com campos padronizados; nenhum campo pode conter segredo. */
  public void record(
      String node,
      String event,
      Long durationMs,
      String status,
      String error,
      String risk,
      String tool,
      String model) {
    record(node, event, durationMs, status, error, risk, tool, model, null);
  }

  /** Registra evento com detalhe opcional (ex.: fontes recuperadas pelo RAG). */
  public void record(
      String node,
      String event,
      Long durationMs,
      String status,
      String error,
      String risk,
      String tool,
      String model,
      String detail) {
    String traceId = MDC.get("trace_id");
    String requestId = MDC.get("request_id");
    log.info(
        "trace_event node={} event={} duration_ms={} status={} error={} risk={} tool={} model={}",
        node,
        event,
        durationMs,
        status,
        error,
        risk,
        tool,
        model);
    try {
      repository.save(
          new TraceEvent(
              traceId,
              requestId,
              node,
              event,
              durationMs,
              status,
              error,
              risk,
              tool,
              model,
              detail,
              Instant.now()));
    } catch (Exception e) {
      log.warn(
          "trace_event_persist_failed node={} event={} error={} trace_id={}",
          node,
          event,
          e.getClass().getSimpleName(),
          traceId);
    }
  }

  /** Eventos de uma execucao em ordem cronologica. */
  public List<TraceEvent> findByTraceId(String traceId) {
    return repository.findByTraceIdOrderByCreatedAtAsc(traceId);
  }

  /** Duracao em ms desde um inicio medido em nanossegundos. */
  public static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
