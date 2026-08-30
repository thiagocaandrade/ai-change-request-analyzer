package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.RiskLevel;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaFindingDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaRecordDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.SecurityAssessmentDto;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
    UUID requestId,
    RiskLevel riskLevel,
    Double confidence,
    String rationale,
    List<CreateAnalysisRequest.FindingDto> findings,
    List<CreateAnalysisRequest.RecommendationDto> testRecommendations,
    Boolean approvalRequired,
    ApprovalStatus approvalStatus,
    SecurityAssessmentDto securityAssessment,
    QaView qa) {

  /** Visao dos registros QA persistidos da solicitacao para a pagina de resultado. */
  public record QaView(
      List<QaFindingDto> findings, List<QaRecordDto> records, boolean degraded) {}
}
