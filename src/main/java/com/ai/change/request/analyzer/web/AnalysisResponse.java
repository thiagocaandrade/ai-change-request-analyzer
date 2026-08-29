package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.RiskLevel;
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
    ApprovalStatus approvalStatus) {}
