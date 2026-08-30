package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.ClassificationResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactFindingDto;
import com.ai.change.request.analyzer.ai.dto.AiResults.RiskAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.memory.AnalysisMemoryService;
import com.ai.change.request.analyzer.rag.RagService;
import com.ai.change.request.analyzer.tools.CodeEvidenceService;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.AnalyzeCodeResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.AnalyzeImpactResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.AssessRiskRequest;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.AssessRiskResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.ClassifyResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.CodeFinding;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.EvidenceRequest;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.GenerateTestPlanRequest;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.GenerateTestPlanResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.HistoryHit;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.ImpactFinding;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.KnowledgeHit;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RetrieveHistoryResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RetrieveKnowledgeResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.TestRecommendation;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.TextRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno {@code /api/agent/**} consumido pelos nos do grafo LangGraph (sidecar Python).
 * Respostas tipadas; segredos nunca aparecem em logs ou respostas; toda execucao loga trace_id.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentGatewayController {

  private static final Logger log = LoggerFactory.getLogger(AgentGatewayController.class);

  private final AiAnalysisService aiAnalysisService;
  private final CodeEvidenceService codeEvidenceService;
  private final RagService ragService;
  private final AnalysisMemoryService memoryService;
  private final EvidenceRenderer evidenceRenderer;

  public AgentGatewayController(
      AiAnalysisService aiAnalysisService,
      CodeEvidenceService codeEvidenceService,
      RagService ragService,
      AnalysisMemoryService memoryService,
      EvidenceRenderer evidenceRenderer) {
    this.aiAnalysisService = aiAnalysisService;
    this.codeEvidenceService = codeEvidenceService;
    this.ragService = ragService;
    this.memoryService = memoryService;
    this.evidenceRenderer = evidenceRenderer;
  }

  @PostMapping("/classify")
  public ClassifyResponse classify(@Valid @RequestBody TextRequest body) {
    ClassificationResult result = aiAnalysisService.classify(body.changeText());
    logEndpoint("classify", result.degraded());
    return new ClassifyResponse(result.category(), result.notes(), result.degraded());
  }

  @PostMapping("/analyze-code")
  public AnalyzeCodeResponse analyzeCode(@Valid @RequestBody TextRequest body) {
    CodeEvidenceService.CodeEvidence evidence = codeEvidenceService.analyzeCode(body.changeText());
    List<CodeFinding> findings =
        evidence.findings().stream()
            .map(
                finding ->
                    new CodeFinding(
                        finding.area(),
                        finding.description(),
                        finding.severity(),
                        finding.file(),
                        finding.line()))
            .toList();
    logEndpoint("analyze-code", evidence.degraded());
    return new AnalyzeCodeResponse(findings, evidence.degraded());
  }

  @PostMapping("/retrieve-knowledge")
  public RetrieveKnowledgeResponse retrieveKnowledge(@Valid @RequestBody TextRequest body) {
    RagService.KnowledgeSearchResult result = ragService.search(body.changeText());
    List<KnowledgeHit> documents =
        result.hits().stream()
            .map(
                hit ->
                    new KnowledgeHit(
                        hit.source(), hit.documentId(), hit.chunkId(), hit.score(), hit.content()))
            .toList();
    logEndpoint("retrieve-knowledge", result.degraded());
    return new RetrieveKnowledgeResponse(documents, result.degraded());
  }

  @PostMapping("/retrieve-history")
  public RetrieveHistoryResponse retrieveHistory(@Valid @RequestBody TextRequest body) {
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByTerms(body.changeText());
    List<HistoryHit> findings =
        result.hits().stream().map(hit -> new HistoryHit(hit.requestId(), hit.summary())).toList();
    logEndpoint("retrieve-history", result.degraded());
    return new RetrieveHistoryResponse(findings, result.degraded());
  }

  @PostMapping("/analyze-impact")
  public AnalyzeImpactResponse analyzeImpact(@Valid @RequestBody EvidenceRequest body) {
    String evidence =
        evidenceRenderer.render(
            body.codeFindings(), body.retrievedDocuments(), body.historicalFindings());
    ImpactAnalysisResult result = aiAnalysisService.analyzeImpact(body.changeText(), evidence);
    List<ImpactFinding> findings = result.findings().stream().map(this::toImpactFinding).toList();
    logEndpoint("analyze-impact", result.degraded());
    return new AnalyzeImpactResponse(findings, result.degraded());
  }

  @PostMapping("/assess-risk")
  public AssessRiskResponse assessRisk(@Valid @RequestBody AssessRiskRequest body) {
    String evidence =
        evidenceRenderer.renderSections(
            Map.of(
                "CLASSIFICACAO", listOrEmpty(body.classification()),
                "IMPACTO", listOrEmpty(body.impactFindings())));
    RiskAnalysisResult result = aiAnalysisService.assessRisk(body.changeText(), evidence);
    logEndpoint("assess-risk", result.degraded());
    return new AssessRiskResponse(
        result.level(), result.confidence(), result.rationale(), result.degraded());
  }

  @PostMapping("/generate-test-plan")
  public GenerateTestPlanResponse generateTestPlan(
      @Valid @RequestBody GenerateTestPlanRequest body) {
    String evidence =
        evidenceRenderer.renderSections(
            Map.of(
                "RISCO", listOrEmpty(body.risk()),
                "CLASSIFICACAO", listOrEmpty(body.classification()),
                "IMPACTO", listOrEmpty(body.impactFindings())));
    TestPlanResult result = aiAnalysisService.generateTestPlan(body.changeText(), evidence);
    List<TestRecommendation> recommendations =
        result.recommendations().stream().map(this::toRecommendation).toList();
    logEndpoint("generate-test-plan", result.degraded());
    return new GenerateTestPlanResponse(recommendations, result.degraded());
  }

  private ImpactFinding toImpactFinding(ImpactFindingDto dto) {
    return new ImpactFinding(dto.component(), dto.description(), dto.severity());
  }

  private TestRecommendation toRecommendation(TestRecommendationDto dto) {
    return new TestRecommendation(dto.component(), dto.description(), dto.priority());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOrEmpty(Object raw) {
    if (raw instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    if (raw instanceof Map<?, ?> map) {
      return List.of((Map<String, Object>) map);
    }
    return List.of();
  }

  private void logEndpoint(String endpoint, boolean degraded) {
    log.info(
        "agent_endpoint_completed endpoint={} degraded={} trace_id={}",
        endpoint,
        degraded,
        MDC.get("trace_id"));
  }
}
