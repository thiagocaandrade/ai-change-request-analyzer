package com.ai.change.request.analyzer.service;

import com.ai.change.request.analyzer.domain.Approval;
import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeAnalysisRepository;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ImpactFinding;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskLevel;
import com.ai.change.request.analyzer.domain.RiskPolicy;
import com.ai.change.request.analyzer.domain.TestRecommendation;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import com.ai.change.request.analyzer.web.CreateAnalysisRequest;
import com.ai.change.request.analyzer.web.GlobalExceptionHandler.ChangeRequestNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

  private final ChangeRequestRepository changeRequestRepository;
  private final ChangeAnalysisRepository changeAnalysisRepository;
  private final RiskPolicy riskPolicy;
  private final SecurityAssessmentService securityAssessmentService;
  private final AnalysisMetrics metrics;
  private final TraceService traceService;

  public AnalysisService(
      ChangeRequestRepository changeRequestRepository,
      ChangeAnalysisRepository changeAnalysisRepository,
      RiskPolicy riskPolicy,
      SecurityAssessmentService securityAssessmentService,
      AnalysisMetrics metrics,
      TraceService traceService) {
    this.changeRequestRepository = changeRequestRepository;
    this.changeAnalysisRepository = changeAnalysisRepository;
    this.riskPolicy = riskPolicy;
    this.securityAssessmentService = securityAssessmentService;
    this.metrics = metrics;
    this.traceService = traceService;
  }

  @Transactional
  public ChangeAnalysis registerAnalysis(UUID requestId, CreateAnalysisRequest payload) {
    long start = System.nanoTime();
    try {
      ChangeRequest request =
          changeRequestRepository
              .findById(requestId)
              .orElseThrow(() -> new ChangeRequestNotFoundException(requestId));

      ChangeAnalysis analysis = new ChangeAnalysis();
      analysis.setChangeRequest(request);

      for (CreateAnalysisRequest.FindingDto finding : payload.findings()) {
        analysis.addFinding(
            new ImpactFinding(finding.component(), finding.description(), finding.severity()));
      }

      CreateAnalysisRequest.RiskDto risk = payload.riskAssessment();
      RiskPolicy.RiskDecision decision = riskPolicy.evaluate(risk.level(), risk.confidence());
      analysis.setRiskAssessment(
          new RiskAssessment(risk.level(), risk.confidence(), risk.rationale()));

      for (CreateAnalysisRequest.RecommendationDto recommendation : payload.testRecommendations()) {
        analysis.addRecommendation(
            new TestRecommendation(
                recommendation.component(),
                recommendation.description(),
                recommendation.priority(),
                recommendation.priorityJustification(),
                recommendation.riskCategory(),
                recommendation.refined()));
      }

      persistAnalysis(request, analysis, decision);
      return analysis;
    } finally {
      metrics.recordAnalysis(TraceService.elapsedMs(start));
    }
  }

  @Transactional
  public ChangeAnalysis persistAnalysis(ChangeRequest request, ChangeAnalysis analysis) {
    return persistAnalysis(request, analysis, List.of());
  }

  /** Persiste a analise e os eventos de seguranca mapeados do resultado do agente. */
  @Transactional
  public ChangeAnalysis persistAnalysis(
      ChangeRequest request, ChangeAnalysis analysis, List<SecurityEvent> securityEvents) {
    long start = System.nanoTime();
    try {
      RiskAssessment riskAssessment = analysis.getRiskAssessment();
      RiskPolicy.RiskDecision decision =
          riskAssessment != null
              ? riskPolicy.evaluate(riskAssessment.getLevel(), riskAssessment.getConfidence())
              : riskPolicy.evaluate(null, null);
      persistAnalysis(request, analysis, decision);
      securityAssessmentService.persist(request, securityEvents);
      return analysis;
    } finally {
      metrics.recordAnalysis(TraceService.elapsedMs(start));
    }
  }

  private void persistAnalysis(
      ChangeRequest request, ChangeAnalysis analysis, RiskPolicy.RiskDecision decision) {
    RiskAssessment riskAssessment = analysis.getRiskAssessment();
    if (riskAssessment != null && riskAssessment.getLevel() == RiskLevel.HIGH) {
      metrics.highRiskChange();
    }
    request.setAnalysis(analysis);
    request.setApproval(
        new Approval(request, decision.approvalRequired(), decision.approvalStatus()));
    changeAnalysisRepository.save(analysis);
    changeRequestRepository.save(request);
    traceService.record(
        "pipeline",
        "analysis_persisted",
        null,
        "ok",
        null,
        riskAssessment != null ? riskAssessment.getLevel().name() : null,
        null,
        null);
  }
}
