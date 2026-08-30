package com.ai.change.request.analyzer.observability;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso persistente aos eventos de auditoria de execucao. */
public interface TraceEventRepository extends JpaRepository<TraceEvent, UUID> {

  List<TraceEvent> findByTraceIdOrderByCreatedAtAsc(String traceId);
}
