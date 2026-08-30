package com.ai.change.request.analyzer.qa;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaBlockDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaFindingDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Persistencia dos registros QA (revisao + geracao) vinculados a solicitacao, com dedupe por
 * (request, stage, traceId) e falha de persistencia nunca derrubando o fluxo (mesmo padrao dos
 * security events).
 */
@Service
public class QaRecordService {

  private static final Logger log = LoggerFactory.getLogger(QaRecordService.class);

  public static final String STAGE_CODE_REVIEW = "CODE_REVIEW";
  public static final String STAGE_TEST_GENERATION = "TEST_GENERATION";

  public static final String PROMPT_CODE_REVIEW = "code-review-v1";
  public static final String PROMPT_TEST_GENERATION = "test-generation-v1";

  private final QaReviewRecordRepository qaReviewRecordRepository;
  private final ChangeRequestRepository changeRequestRepository;

  public QaRecordService(
      QaReviewRecordRepository qaReviewRecordRepository,
      ChangeRequestRepository changeRequestRepository) {
    this.qaReviewRecordRepository = qaReviewRecordRepository;
    this.changeRequestRepository = changeRequestRepository;
  }

  /** Persiste os registros QA do gateway usando o requestId do payload (como security events). */
  public void persistOutcome(String requestId, QaService.QaOutcome outcome) {
    if (requestId == null || requestId.isBlank() || outcome == null) {
      return;
    }
    try {
      UUID id = UUID.fromString(requestId);
      changeRequestRepository
          .findById(id)
          .ifPresent(
              request -> {
                ChangeAnalysis analysis = request.getAnalysis();
                persistRecord(
                    request,
                    analysis,
                    STAGE_CODE_REVIEW,
                    PROMPT_CODE_REVIEW,
                    outcome.reviewResultJson(),
                    outcome.degraded(),
                    0,
                    toQaFindings(outcome));
                persistRecord(
                    request,
                    analysis,
                    STAGE_TEST_GENERATION,
                    PROMPT_TEST_GENERATION,
                    outcome.generationResultJson(),
                    outcome.degraded(),
                    outcome.refinementIterations(),
                    List.of());
              });
    } catch (IllegalArgumentException e) {
      log.warn(
          "qa_record_invalid_request_id request_id={} trace_id={}",
          requestId,
          MDC.get("trace_id"));
    }
  }

  /** Persiste o registro de revisao trazido pelo bloco qa (fluxo da analise, sem gateway). */
  public void persist(ChangeRequest request, ChangeAnalysis analysis, QaBlockDto qa) {
    if (request == null || qa == null || qa.record() == null) {
      return;
    }
    persistRecord(
        request,
        analysis,
        qa.record().stage(),
        qa.record().promptVersion(),
        qa.record().resultJson(),
        qa.record().degraded(),
        qa.record().iterations(),
        qa.findings() == null ? List.of() : qa.findings());
  }

  private void persistRecord(
      ChangeRequest request,
      ChangeAnalysis analysis,
      String stage,
      String promptVersion,
      String resultJson,
      boolean degraded,
      int iterations,
      List<QaFindingDto> findings) {
    if (request == null || request.getId() == null) {
      return;
    }
    try {
      String traceId = MDC.get("trace_id");
      String effectiveTraceId = traceId != null ? traceId : request.getTraceId();
      if (qaReviewRecordRepository.existsByChangeRequestIdAndStageAndTraceId(
          request.getId(), stage, effectiveTraceId)) {
        return;
      }
      QaReviewRecord record =
          new QaReviewRecord(
              request,
              stage,
              promptVersion,
              resultJson,
              degraded,
              iterations,
              effectiveTraceId,
              Instant.now());
      record.setAnalysis(analysis);
      for (QaFindingDto finding : findings) {
        if (finding == null || finding.component() == null || finding.description() == null) {
          continue;
        }
        record.addFinding(
            new QaFinding(
                finding.component(),
                finding.description(),
                finding.severity(),
                finding.source()));
      }
      qaReviewRecordRepository.save(record);
    } catch (Exception e) {
      log.warn(
          "qa_record_persist_failed request_id={} stage={} error={} trace_id={}",
          request.getId(),
          stage,
          e.getClass().getSimpleName(),
          MDC.get("trace_id"));
    }
  }

  private List<QaFindingDto> toQaFindings(QaService.QaOutcome outcome) {
    return outcome.review().findings().stream()
        .map(
            finding ->
                new QaFindingDto(
                    finding.component(),
                    finding.description(),
                    finding.severity(),
                    finding.source()))
        .toList();
  }
}
