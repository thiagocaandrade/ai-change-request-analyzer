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
import com.ai.change.request.analyzer.service.AgentResultMapper;
import com.ai.change.request.analyzer.service.AnalysisService;
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

  public ChangeRequestController(
      ChangeRequestRepository repository,
      AgentClient agentClient,
      AgentResultMapper agentResultMapper,
      AnalysisService analysisService) {
    this.repository = repository;
    this.agentClient = agentClient;
    this.agentResultMapper = agentResultMapper;
    this.analysisService = analysisService;
  }

  @PostMapping
  public ResponseEntity<ChangeRequestResponse> create(
      @Valid @RequestBody CreateChangeRequestRequest body, HttpServletRequest httpRequest) {
    String traceId = (String) httpRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    ChangeRequest request = new ChangeRequest();
    request.setText(body.text());
    request.setTraceId(traceId);
    request.setStatus(ChangeRequestStatus.PENDING);
    request = repository.save(request);

    try {
      var agentResponse = agentClient.analyze(request.getId().toString(), body.text(), traceId);
      ChangeAnalysis analysis =
          agentResultMapper.toAnalysis(request, agentResponse.result());
      analysisService.persistAnalysis(request, analysis);
      request.setStatus(ChangeRequestStatus.COMPLETED);
    } catch (AgentUnavailableException e) {
      request.setStatus(ChangeRequestStatus.FAILED);
      request.setFailureReason("agent_unavailable: " + e.getMessage());
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

  @GetMapping("/{id}/analysis")
  public AnalysisResponse getAnalysis(@PathVariable UUID id) {
    ChangeRequest request = loadRequest(id);
    ChangeAnalysis analysis = request.getAnalysis();
    if (analysis == null) {
      throw new GlobalExceptionHandler.AnalysisNotFoundException(id);
    }
    RiskAssessment riskAssessment = analysis.getRiskAssessment();
    Approval approval = request.getApproval();
    return new AnalysisResponse(
        id,
        riskAssessment != null ? riskAssessment.getLevel() : null,
        riskAssessment != null ? riskAssessment.getConfidence() : null,
        riskAssessment != null ? riskAssessment.getRationale() : null,
        analysis.getFindings().stream()
            .map(f -> new CreateAnalysisRequest.FindingDto(
                f.getComponent(), f.getDescription(), f.getSeverity()))
            .toList(),
        analysis.getRecommendations().stream()
            .map(r -> new CreateAnalysisRequest.RecommendationDto(
                r.getComponent(), r.getDescription(), r.getPriority()))
            .toList(),
        approval != null ? approval.isRequired() : null,
        approval != null ? approval.getStatus() : null);
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
