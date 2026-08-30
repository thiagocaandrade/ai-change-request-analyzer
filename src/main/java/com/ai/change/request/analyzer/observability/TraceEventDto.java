package com.ai.change.request.analyzer.observability;

import java.time.Instant;

/** Representacao de evento de auditoria na API de reconstrucao de execucao. */
public record TraceEventDto(
    String traceId,
    String requestId,
    String node,
    String event,
    Long durationMs,
    String status,
    String error,
    String risk,
    String tool,
    String model,
    String detail,
    Instant createdAt) {

  public static TraceEventDto from(TraceEvent event) {
    return new TraceEventDto(
        event.getTraceId(),
        event.getRequestId(),
        event.getNode(),
        event.getEvent(),
        event.getDurationMs(),
        event.getStatus(),
        event.getError(),
        event.getRisk(),
        event.getTool(),
        event.getModel(),
        event.getDetail(),
        event.getCreatedAt());
  }
}
