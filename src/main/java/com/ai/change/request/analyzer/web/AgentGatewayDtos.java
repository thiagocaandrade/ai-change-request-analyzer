package com.ai.change.request.analyzer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** DTOs tipados do contrato interno {@code /api/agent/**} consumido pelo sidecar Python. */
public final class AgentGatewayDtos {

  private AgentGatewayDtos() {}

  public record TextRequest(@NotBlank @Size(max = 4000) String changeText, String requestId) {}

  public record ClassifyResponse(String category, String notes, boolean degraded) {}

  public record CodeFinding(
      String area, String description, String severity, String file, Integer line) {}

  public record AnalyzeCodeResponse(List<CodeFinding> findings, boolean degraded) {}

  public record KnowledgeHit(
      String source, String documentId, String chunkId, Double score, String content) {}

  public record RetrieveKnowledgeResponse(List<KnowledgeHit> documents, boolean degraded) {}

  public record HistoryHit(String requestId, String summary) {}

  public record RetrieveHistoryResponse(List<HistoryHit> findings, boolean degraded) {}

  public record SecurityAssessmentRequest(
      @NotBlank @Size(max = 4000) String changeText, String requestId) {}

  public record SecurityEventDto(String type, String source, String evidence, String action) {}

  /** Avaliacao de seguranca tipada: indicador de deteccao e lista de eventos. */
  public record SecurityAssessmentDto(boolean detected, List<SecurityEventDto> events) {}

  public record EvidenceRequest(
      @NotBlank @Size(max = 4000) String changeText,
      List<Map<String, Object>> codeFindings,
      List<Map<String, Object>> retrievedDocuments,
      List<Map<String, Object>> historicalFindings) {}

  public record ImpactFinding(String component, String description, String severity) {}

  public record AnalyzeImpactResponse(List<ImpactFinding> findings, boolean degraded) {}

  public record AssessRiskRequest(
      @NotBlank @Size(max = 4000) String changeText,
      Map<String, Object> classification,
      List<Map<String, Object>> impactFindings) {}

  public record AssessRiskResponse(
      String level, Double confidence, String rationale, boolean degraded) {}

  public record GenerateTestPlanRequest(
      @NotBlank @Size(max = 4000) String changeText,
      Map<String, Object> risk,
      Map<String, Object> classification,
      List<Map<String, Object>> impactFindings) {}

  public record TestRecommendation(String component, String description, String priority) {}

  public record GenerateTestPlanResponse(
      List<TestRecommendation> recommendations, boolean degraded) {}
}
