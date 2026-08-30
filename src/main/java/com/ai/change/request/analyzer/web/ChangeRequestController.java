package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.config.TraceIdFilter;
import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.qa.QaReviewRecord;
import com.ai.change.request.analyzer.qa.QaReviewRecordRepository;
import com.ai.change.request.analyzer.service.AgentResultMapper;
import com.ai.change.request.analyzer.service.AnalysisService;
import com.ai.change.request.analyzer.service.ApprovalService;
import com.ai.change.request.analyzer.web.ApprovalDtos.ApprovalRequest;
import com.ai.change.request.analyzer.web.ApprovalDtos.ApprovalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/change-requests")
public class ChangeRequestController {

  private static final Logger log = LoggerFactory.getLogger(ChangeRequestController.class);

  private final ChangeRequestRepository repository;
  private final AgentClient agentClient;
  private final AgentResultMapper agentResultMapper;
  private final AnalysisService analysisService;
  private final ApprovalService approvalService;
  private final TraceService traceService;
  private final QaReviewRecordRepository qaReviewRecordRepository;

  public ChangeRequestController(
      ChangeRequestRepository repository,
      AgentClient agentClient,
      AgentResultMapper agentResultMapper,
      AnalysisService analysisService,
      ApprovalService approvalService,
      TraceService traceService,
      QaReviewRecordRepository qaReviewRecordRepository) {
    this.repository = repository;
    this.agentClient = agentClient;
    this.agentResultMapper = agentResultMapper;
    this.analysisService = analysisService;
    this.approvalService = approvalService;
    this.traceService = traceService;
    this.qaReviewRecordRepository = qaReviewRecordRepository;
  }

  @PostMapping
  public ResponseEntity<ChangeRequestResponse> create(
      @Valid @RequestBody CreateChangeRequestRequest body, HttpServletRequest httpRequest) {
    long start = System.nanoTime();
    String traceId = (String) httpRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    traceService.record("pipeline", "analysis_started");
    ChangeRequest request = new ChangeRequest();
    request.setText(body.text());
    request.setTraceId(traceId);
    request.setStatus(ChangeRequestStatus.PENDING);
    request = repository.save(request);

    try {
      var agentResponse = agentClient.analyze(request.getId().toString(), body.text(), traceId);
      ChangeAnalysis analysis = agentResultMapper.toAnalysis(request, agentResponse.result());
      analysisService.persistAnalysis(
          request,
          analysis,
          agentResultMapper.toSecurityEvents(agentResponse.result()),
          agentResultMapper.toQa(agentResponse.result()));
      request.setStatus(ChangeRequestStatus.COMPLETED);
      traceService.record(
          "pipeline",
          "analysis_completed",
          TraceService.elapsedMs(start),
          "ok",
          null,
          analysis.getRiskAssessment() != null
              ? analysis.getRiskAssessment().getLevel().name()
              : null,
          null,
          null);
    } catch (AgentUnavailableException e) {
      request.setStatus(ChangeRequestStatus.FAILED);
      request.setFailureReason("agent_unavailable: " + e.getMessage());
      traceService.record(
          "pipeline",
          "analysis_failed",
          TraceService.elapsedMs(start),
          "failed",
          "agent_unavailable",
          null,
          null,
          null);
    }
    repository.save(request);
    log.info("request_persisted id={} status={}", request.getId(), request.getStatus());
    HttpStatus httpStatus =
        request.getStatus() == ChangeRequestStatus.COMPLETED
            ? HttpStatus.CREATED
            : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(httpStatus).body(toResponse(request));
  }

  @GetMapping("/{id}")
  public ChangeRequestResponse get(@PathVariable UUID id) {
    return toResponse(loadRequest(id));
  }

  @PostMapping("/{id}/analysis")
  public ChangeRequestResponse submitAnalysis(
      @PathVariable UUID id, @Valid @RequestBody CreateAnalysisRequest body) {
    analysisService.registerAnalysis(id, body);
    return toResponse(loadRequest(id));
  }

  @PostMapping("/{id}/approval")
  public ApprovalResponse approve(
      @PathVariable UUID id,
      @Valid @RequestBody ApprovalRequest body,
      HttpServletRequest httpRequest) {
    String traceId = (String) httpRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    Approval approval = approvalService.decide(id, body.approver(), body.decision(), traceId);
    return new ApprovalResponse(
        approval.getStatus(),
        approval.getApprover(),
        approval.getDecision(),
        approval.getDecidedAt(),
        approval.getTraceId());
  }

  @GetMapping("/{id}/analysis")
  public AnalysisResponse getAnalysis(@PathVariable UUID id) {
    ChangeRequest request = loadRequest(id);
    ChangeAnalysis analysis = request.getAnalysis();
    if (analysis == null) {
      throw new GlobalExceptionHandler.AnalysisNotFoundException(id);
    }
    RiskAssessment riskAssessment = analysis.getRiskAssessment();
    Approval approval = request.getApproval();
    List<AgentGatewayDtos.SecurityEventDto> securityEvents =
        request.getSecurityAssessments().stream()
            .map(
                event ->
                    new AgentGatewayDtos.SecurityEventDto(
                        event.getType(), event.getSource(), event.getEvidence(), event.getAction()))
            .toList();
    return new AnalysisResponse(
        id,
        riskAssessment != null ? riskAssessment.getLevel() : null,
        riskAssessment != null ? riskAssessment.getConfidence() : null,
        riskAssessment != null ? riskAssessment.getRationale() : null,
        analysis.getFindings().stream()
            .map(
                f ->
                    new CreateAnalysisRequest.FindingDto(
                        f.getComponent(), f.getDescription(), f.getSeverity()))
            .toList(),
        analysis.getRecommendations().stream()
            .map(
                r ->
                    new CreateAnalysisRequest.RecommendationDto(
                        r.getComponent(),
                        r.getDescription(),
                        r.getPriority(),
                        r.getPriorityJustification(),
                        r.getRiskCategory(),
                        r.getRefined()))
            .toList(),
        approval != null ? approval.isRequired() : null,
        approval != null ? approval.getStatus() : null,
        new AgentGatewayDtos.SecurityAssessmentDto(!securityEvents.isEmpty(), securityEvents),
        toQaView(qaReviewRecordRepository.findByChangeRequestIdOrderByCreatedAtAsc(id)));
  }

  private AnalysisResponse.QaView toQaView(List<QaReviewRecord> records) {
    if (records.isEmpty()) {
      return null;
    }
    List<AgentGatewayDtos.QaFindingDto> findings =
        records.stream()
            .filter(record -> record.getStage().equals("CODE_REVIEW"))
            .flatMap(record -> record.getFindings().stream())
            .map(
                finding ->
                    new AgentGatewayDtos.QaFindingDto(
                        finding.getComponent(),
                        finding.getDescription(),
                        finding.getSeverity(),
                        finding.getSource()))
            .toList();
    List<AgentGatewayDtos.QaRecordDto> recordDtos =
        records.stream()
            .map(
                record ->
                    new AgentGatewayDtos.QaRecordDto(
                        record.getStage(),
                        record.getPromptVersion(),
                        record.getResultJson(),
                        record.isDegraded(),
                        record.getIterations(),
                        record.getTraceId()))
            .toList();
    boolean degraded = records.stream().anyMatch(QaReviewRecord::isDegraded);
    return new AnalysisResponse.QaView(findings, recordDtos, degraded);
  }

  private ChangeRequest loadRequest(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new GlobalExceptionHandler.ChangeRequestNotFoundException(id));
  }

  private ChangeRequestResponse toResponse(ChangeRequest request) {
    ChangeAnalysis analysis = request.getAnalysis();
    Approval approval = request.getApproval();
    RiskAssessment riskAssessment = analysis != null ? analysis.getRiskAssessment() : null;
    ChangeRequestResponse.AnalysisSummary summary =
        analysis != null
            ? new ChangeRequestResponse.AnalysisSummary(
                riskAssessment != null ? riskAssessment.getLevel() : null,
                approval != null ? approval.isRequired() : null,
                approval != null ? approval.getStatus() : null,
                analysis.getFindings().size(),
                analysis.getRecommendations().size())
            : null;
    return new ChangeRequestResponse(
        request.getId(),
        request.getText(),
        request.getStatus(),
        request.getTraceId(),
        request.getFailureReason(),
        summary);
  }
}
