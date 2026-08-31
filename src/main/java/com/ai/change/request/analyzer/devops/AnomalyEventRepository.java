package com.ai.change.request.analyzer.devops;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio dos eventos de anomalia. */
public interface AnomalyEventRepository extends JpaRepository<AnomalyEvent, UUID> {

  List<AnomalyEvent> findByTraceIdOrderByCreatedAtAsc(String traceId);
}
