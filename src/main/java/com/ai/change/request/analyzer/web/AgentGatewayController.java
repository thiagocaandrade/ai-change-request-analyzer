package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.ClassificationResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactFindingDto;
import com.ai.change.request.analyzer.ai.dto.AiResults.RiskAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.SecurityAnalysisResult;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.memory.AnalysisMemoryService;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.qa.QaRecordService;
import com.ai.change.request.analyzer.qa.QaService;
import com.ai.change.request.analyzer.rag.RagService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
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
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaBlockDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaFindingDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaRecordDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RetrieveHistoryResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RetrieveKnowledgeResponse;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RiskMatrixEntryDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.SecurityAssessmentDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.SecurityAssessmentRequest;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.SecurityEventDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.TestRecommendation;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.TextRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno {@code /api/agent/**} consumido pelos nos do grafo LangGraph (sidecar Python).
 * Respostas tipadas; segredos nunca aparecem em logs ou respostas; toda execucao loga trace_id. O
 * conteudo retornado pelos gateways de coleta e varrido deterministicamente em busca de instrucoes
 * injetadas, com eventos persistidos vinculados a solicitacao (quando informada).
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
  private final SecurityAssessmentService securityAssessmentService;
  private final ChangeRequestRepository changeRequestRepository;
  private final TraceService traceService;
  private final QaService qaService;
  private final QaRecordService qaRecordService;

  public AgentGatewayController(
      AiAnalysisService aiAnalysisService,
      CodeEvidenceService codeEvidenceService,
      RagService ragService,
      AnalysisMemoryService memoryService,
      EvidenceRenderer evidenceRenderer,
      SecurityAssessmentService securityAssessmentService,
      ChangeRequestRepository changeRequestRepository,
      TraceService traceService,
      QaService qaService,
      QaRecordService qaRecordService) {
    this.aiAnalysisService = aiAnalysisService;
    this.codeEvidenceService = codeEvidenceService;
    this.ragService = ragService;
    this.memoryService = memoryService;
    this.evidenceRenderer = evidenceRenderer;
    this.securityAssessmentService = securityAssessmentService;
    this.changeRequestRepository = changeRequestRepository;
    this.traceService = traceService;
    this.qaService = qaService;
    this.qaRecordService = qaRecordService;
  }

  @PostMapping("/classify")
  public ClassifyResponse classify(@Valid @RequestBody TextRequest body) {
    long start = System.nanoTime();
    traceService.record("classify", "started");
    ClassificationResult result = aiAnalysisService.classify(body.changeText());
    recordCompleted("classify", start, result.degraded());
    return new ClassifyResponse(result.category(), result.notes(), result.degraded());
  }

  @PostMapping("/analyze-code")
  public AnalyzeCodeResponse analyzeCode(@Valid @RequestBody TextRequest body) {
    long start = System.nanoTime();
    traceService.record("analyze-code", "started");
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
    scanAndPersist(
        body.requestId(),
        findings.stream().map(CodeFinding::description).toList(),
        SecurityAssessmentService.SOURCE_CODE);
    recordCompleted("analyze-code", start, evidence.degraded());
    return new AnalyzeCodeResponse(findings, evidence.degraded());
  }

  @PostMapping("/retrieve-knowledge")
  public RetrieveKnowledgeResponse retrieveKnowledge(@Valid @RequestBody TextRequest body) {
    long start = System.nanoTime();
    traceService.record("retrieve-knowledge", "started");
    RagService.KnowledgeSearchResult result = ragService.search(body.changeText());
    List<KnowledgeHit> documents =
        result.hits().stream()
            .map(
                hit ->
                    new KnowledgeHit(
                        hit.source(), hit.documentId(), hit.chunkId(), hit.score(), hit.content()))
            .toList();
    scanAndPersist(
        body.requestId(),
        documents.stream().map(KnowledgeHit::content).toList(),
        SecurityAssessmentService.SOURCE_KNOWLEDGE);
    recordCompleted("retrieve-knowledge", start, result.degraded());
    return new RetrieveKnowledgeResponse(documents, result.degraded());
  }

  @PostMapping("/retrieve-history")
  public RetrieveHistoryResponse retrieveHistory(@Valid @RequestBody TextRequest body) {
    long start = System.nanoTime();
    traceService.record("retrieve-history", "started");
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByTerms(body.changeText());
    List<HistoryHit> findings =
        result.hits().stream().map(hit -> new HistoryHit(hit.requestId(), hit.summary())).toList();
    scanAndPersist(
        body.requestId(),
        findings.stream().map(HistoryHit::summary).toList(),
        SecurityAssessmentService.SOURCE_HISTORY);
    recordCompleted("retrieve-history", start, result.degraded());
    return new RetrieveHistoryResponse(findings, result.degraded());
  }

  /**
   * Avaliacao de seguranca do texto da solicitacao: varredura deterministica + sugestao validada do
   * LLM, com decisao final (uniao, dedupe, acao) deterministica no Java. Eventos persistidos quando
   * a solicitacao e informada; falha de persistencia nao derruba o endpoint.
   */
  @PostMapping("/security-assessment")
  public SecurityAssessmentDto securityAssessment(
      @Valid @RequestBody SecurityAssessmentRequest body) {
    long start = System.nanoTime();
    traceService.record("security-assessment", "started");
    SecurityAnalysisResult suggestion = aiAnalysisService.analyzeSecurity(body.changeText(), "");
    List<SecurityEvent> events =
        securityAssessmentService.assess(
            body.changeText(),
            SecurityAssessmentService.SOURCE_REQUEST_TEXT,
            suggestion.findings());
    persistEvents(body.requestId(), events);
    recordCompleted("security-assessment", start, suggestion.degraded());
    return toSecurityAssessmentDto(events);
  }

  @PostMapping("/analyze-impact")
  public AnalyzeImpactResponse analyzeImpact(@Valid @RequestBody EvidenceRequest body) {
    long start = System.nanoTime();
    traceService.record("analyze-impact", "started");
    String evidence =
        evidenceRenderer.render(
            body.codeFindings(), body.retrievedDocuments(), body.historicalFindings());
    ImpactAnalysisResult result = aiAnalysisService.analyzeImpact(body.changeText(), evidence);
    List<ImpactFinding> findings = result.findings().stream().map(this::toImpactFinding).toList();
    recordCompleted("analyze-impact", start, result.degraded());
    return new AnalyzeImpactResponse(findings, result.degraded());
  }

  @PostMapping("/assess-risk")
  public AssessRiskResponse assessRisk(@Valid @RequestBody AssessRiskRequest body) {
    long start = System.nanoTime();
    traceService.record("assess-risk", "started");
    String evidence =
        evidenceRenderer.renderSections(
            Map.of(
                "CLASSIFICACAO", listOrEmpty(body.classification()),
                "IMPACTO", listOrEmpty(body.impactFindings())));
    RiskAnalysisResult result = aiAnalysisService.assessRisk(body.changeText(), evidence);
    traceService.record(
        "assess-risk",
        "completed",
        TraceService.elapsedMs(start),
        result.degraded() ? "degraded" : "ok",
        null,
        result.level(),
        null,
        null);
    return new AssessRiskResponse(
        result.level(), result.confidence(), result.rationale(), result.degraded());
  }

  /**
   * Orquestra a etapa QA dentro do estagio de geracao de testes: RAG → code review → matriz de
   * risco deterministica → geracao/refinamento → resposta com bloco {@code qa} (findings +
   * recomendacoes priorizadas + registro). Registros QA persistidos no gateway via requestId;
   * conteudo recuperado e varrido em busca de instrucoes injetadas.
   */
  @PostMapping("/generate-test-plan")
  public GenerateTestPlanResponse generateTestPlan(
      @Valid @RequestBody GenerateTestPlanRequest body) {
    long start = System.nanoTime();
    traceService.record("generate-test-plan", "started");
    QaService.QaOutcome outcome =
        qaService.generateTestPlanWithQa(
            body.changeText(),
            body.diff(),
            body.risk(),
            body.classification(),
            body.impactFindings());
    scanAndPersist(
        body.requestId(),
        outcome.documents().stream()
            .map(document -> String.valueOf(document.get("content")))
            .toList(),
        SecurityAssessmentService.SOURCE_KNOWLEDGE);
    qaRecordService.persistOutcome(body.requestId(), outcome);
    List<TestRecommendation> recommendations =
        outcome.recommendations().stream().map(this::toRecommendation).toList();
    QaBlockDto qa =
        new QaBlockDto(
            outcome.review().findings().stream().map(this::toQaFinding).toList(),
            recommendations,
            outcome.matrix().stream().map(this::toMatrixEntry).toList(),
            outcome.degraded(),
            new QaRecordDto(
                QaRecordService.STAGE_CODE_REVIEW,
                QaRecordService.PROMPT_CODE_REVIEW,
                outcome.reviewResultJson(),
                outcome.degraded(),
                0,
                MDC.get("trace_id")));
    recordCompleted("generate-test-plan", start, outcome.degraded());
    return new GenerateTestPlanResponse(recommendations, outcome.degraded(), qa);
  }

  private void scanAndPersist(String requestId, List<String> contents, String source) {
    List<SecurityEvent> events = new ArrayList<>();
    for (String content : contents) {
      events.addAll(securityAssessmentService.scan(content, source));
    }
    persistEvents(requestId, events);
  }

  private void persistEvents(String requestId, List<SecurityEvent> events) {
    if (events.isEmpty() || requestId == null || requestId.isBlank()) {
      return;
    }
    try {
      UUID id = UUID.fromString(requestId);
      changeRequestRepository
          .findById(id)
          .ifPresent(request -> securityAssessmentService.persist(request, events));
    } catch (IllegalArgumentException e) {
      log.warn(
          "security_scan_invalid_request_id request_id={} trace_id={}",
          requestId,
          MDC.get("trace_id"));
    }
  }

  private SecurityAssessmentDto toSecurityAssessmentDto(List<SecurityEvent> events) {
    List<SecurityEventDto> dtos =
        events.stream()
            .map(
                event ->
                    new SecurityEventDto(
                        event.type(), event.source(), event.evidence(), event.action()))
            .toList();
    return new SecurityAssessmentDto(!dtos.isEmpty(), dtos);
  }

  private ImpactFinding toImpactFinding(ImpactFindingDto dto) {
    return new ImpactFinding(dto.component(), dto.description(), dto.severity());
  }

  private TestRecommendation toRecommendation(QaService.QaRecommendation recommendation) {
    return new TestRecommendation(
        recommendation.component(),
        recommendation.description(),
        recommendation.priority(),
        recommendation.priorityJustification(),
        recommendation.riskCategory(),
        recommendation.refined());
  }

  private QaFindingDto toQaFinding(com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewFindingDto finding) {
    return new QaFindingDto(
        finding.component(), finding.description(), finding.severity(), finding.source());
  }

  private RiskMatrixEntryDto toMatrixEntry(
      com.ai.change.request.analyzer.qa.RiskMatrixService.RiskCategoryAssessment assessment) {
    return new RiskMatrixEntryDto(
        assessment.category(),
        assessment.applicable(),
        assessment.impact() == null ? null : assessment.impact().name(),
        assessment.likelihood() == null ? null : assessment.likelihood().name(),
        assessment.priority() == null ? null : assessment.priority().name(),
        assessment.justification());
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

  private void recordCompleted(String node, long start, boolean degraded) {
    traceService.record(
        node,
        "completed",
        TraceService.elapsedMs(start),
        degraded ? "degraded" : "ok",
        null,
        null,
        null,
        null);
  }
}
