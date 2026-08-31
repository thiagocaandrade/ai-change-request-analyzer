package com.ai.change.request.analyzer.devops;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.dto.AiResults.LogAnalysisResult;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Analise assistida de logs de pipeline (build/teste): redacao de segredos, varredura
 * deterministica de instrucoes injetadas (o log e DADO NAO CONFIÁVEL), chamada ao modelo via {@link
 * AiAnalysisService} (structured output, retry limitado, fallback degradado) e persistencia do
 * registro correlacionado por trace_id.
 *
 * <p>A analise NUNCA altera arquivos de pipeline: produz apenas diagnostico e recomendacao para
 * revisao humana.
 */
@Service
public class LogAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(LogAnalysisService.class);

  public static final String PROMPT_LOG_ANALYSIS = "log-analysis-v1";
  public static final String NODE_LOG_ANALYSIS = "log_analysis";

  private static final Pattern KEY_VALUE_SENSITIVE =
      Pattern.compile(
          "(?i)\\b(token|secret|password|passwd|api[_-]?key|authorization|bearer)\\b(\\s*[:=]\\s*)\\S+");
  private static final Pattern BEARER_SPACED =
      Pattern.compile("(?i)\\b(bearer)\\s+([A-Za-z0-9._-]{8,})");

  private final AiAnalysisService aiAnalysisService;
  private final SecurityAssessmentService securityAssessmentService;
  private final TraceService traceService;
  private final LogAnalysisRecordRepository repository;
  private final ObjectMapper objectMapper;

  public LogAnalysisService(
      AiAnalysisService aiAnalysisService,
      SecurityAssessmentService securityAssessmentService,
      TraceService traceService,
      LogAnalysisRecordRepository repository,
      ObjectMapper objectMapper) {
    this.aiAnalysisService = aiAnalysisService;
    this.securityAssessmentService = securityAssessmentService;
    this.traceService = traceService;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * Resultado da analise com o JSON estruturado persistido e os eventos de seguranca detectados.
   */
  public record LogOutcome(
      UUID recordId,
      LogAnalysisResult result,
      String resultJson,
      List<SecurityEvent> securityEvents,
      String promptVersion,
      String traceId) {}

  public LogOutcome analyze(String logContent) {
    String traceId = MDC.get("trace_id");
    long start = System.nanoTime();
    traceService.record(NODE_LOG_ANALYSIS, "log_analysis_started");

    String redacted = redact(logContent == null ? "" : logContent);

    List<SecurityEvent> securityEvents =
        securityAssessmentService.scan(redacted, SecurityAssessmentService.SOURCE_LOG);
    for (SecurityEvent event : securityEvents) {
      traceService.record(
          NODE_LOG_ANALYSIS,
          "security_event",
          null,
          event.action(),
          null,
          null,
          null,
          null,
          event.evidence());
      log.warn(
          "log_security_event type={} source={} evidence={} trace_id={}",
          event.type(),
          event.source(),
          event.evidence(),
          traceId);
    }

    LogAnalysisResult result = aiAnalysisService.analyzeLogs(redacted);
    String resultJson = toJson(result);
    LogAnalysisRecord record =
        new LogAnalysisRecord(
            PROMPT_LOG_ANALYSIS,
            resultJson,
            result.confidence(),
            Boolean.TRUE.equals(result.degraded()),
            traceId,
            Instant.now());
    UUID recordId;
    try {
      recordId = repository.save(record).getId();
    } catch (Exception e) {
      log.warn(
          "log_analysis_persist_failed error={} trace_id={}",
          e.getClass().getSimpleName(),
          traceId);
      recordId = null;
    }

    traceService.record(
        NODE_LOG_ANALYSIS,
        "log_analysis_completed",
        TraceService.elapsedMs(start),
        Boolean.TRUE.equals(result.degraded()) ? "degraded" : "ok",
        null,
        null,
        null,
        null,
        result.failedStep());
    return new LogOutcome(
        recordId, result, resultJson, securityEvents, PROMPT_LOG_ANALYSIS, traceId);
  }

  /** Redacao simples de padroes sensiveis antes do envio ao modelo (mesma regra do CI). */
  static String redact(String content) {
    String redacted = BEARER_SPACED.matcher(content).replaceAll("$1 ***REDACTED***");
    return KEY_VALUE_SENSITIVE.matcher(redacted).replaceAll("$1$2***REDACTED***");
  }

  private String toJson(LogAnalysisResult result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("falha ao serializar resultado da analise de logs", e);
    }
  }
}
