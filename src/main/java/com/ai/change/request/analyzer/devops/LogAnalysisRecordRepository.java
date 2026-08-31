package com.ai.change.request.analyzer.devops;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio dos registros de analise de logs de pipeline. */
public interface LogAnalysisRecordRepository extends JpaRepository<LogAnalysisRecord, UUID> {

  List<LogAnalysisRecord> findByTraceIdOrderByCreatedAtAsc(String traceId);
}
