package com.ai.change.request.analyzer.qa;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewFindingDto;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestrador da etapa QA dentro do estagio de geracao de testes: code review → evidencia com os
 * findings de QA → geracao → refinamento limitado (max. 2 iteracoes) com feedback registrado.
 *
 * <p>O fluxo apenas PRODUZ recomendacoes estruturadas: nunca cria, altera ou executa arquivos do
 * repositorio. Cada iteracao de refinamento e registrada como trace event e contabilizada no
 * registro QA.
 */
@Service
public class QaService {

  private static final Logger log = LoggerFactory.getLogger(QaService.class);

  static final int MAX_REFINEMENT_ITERATIONS = 2;

  private static final Set<String> GENERIC_COMPONENTS =
      Set.of("unit", "integration", "e2e", "end-to-end", "regression", "qa");

  private static final int MAX_RESULT_JSON_LENGTH = 8000;

  private final QaCodeReviewService codeReviewService;
  private final AiAnalysisService aiAnalysisService;
  private final EvidenceRenderer evidenceRenderer;
  private final TraceService traceService;
  private final AnalysisMetrics metrics;
  private final ObjectMapper objectMapper;

  public QaService(
      QaCodeReviewService codeReviewService,
      AiAnalysisService aiAnalysisService,
      EvidenceRenderer evidenceRenderer,
      TraceService traceService,
      AnalysisMetrics metrics,
      ObjectMapper objectMapper) {
    this.codeReviewService = codeReviewService;
    this.aiAnalysisService = aiAnalysisService;
    this.evidenceRenderer = evidenceRenderer;
    this.traceService = traceService;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
  }

  /** Recomendacao de teste enriquecida pelo fluxo QA. */
  public record QaRecommendation(
      String component,
      String description,
      String priority,
      String priorityJustification,
      String riskCategory,
      boolean refined) {}

  /** Resultado completo da etapa QA. */
  public record QaOutcome(
      CodeReviewResult review,
      List<QaRecommendation> recommendations,
      List<Map<String, Object>> documents,
      int refinementIterations,
      boolean degraded,
      String reviewResultJson,
      String generationResultJson) {}

  /**
   * Executa a etapa QA do estagio de geracao de testes: revisao com IA, geracao com os findings de
   * QA como evidencia e refinamento limitado e registrado de recomendacoes incompletas.
   */
  public QaOutcome generateTestPlanWithQa(
      String changeText,
      String diff,
      Map<String, Object> risk,
      Map<String, Object> classification,
      List<Map<String, Object>> impactFindings) {
    long reviewStart = System.nanoTime();
    traceService.record("qa_review", "started");
    QaCodeReviewService.ReviewOutcome review = codeReviewService.review(changeText, diff);
    metrics.qaReview();
    traceService.record(
        "qa_review",
        "completed",
        TraceService.elapsedMs(reviewStart),
        review.degraded() ? "degraded" : "ok",
        null,
        null,
        null,
        null);

    String baseEvidence =
        evidenceRenderer.renderSections(
            Map.of(
                "RISCO", listOrEmpty(risk),
                "CLASSIFICACAO", listOrEmpty(classification),
                "IMPACTO", listOrEmpty(impactFindings),
                "FINDINGS QA", toFindingMaps(review.result())));

    TestPlanResult plan = aiAnalysisService.generateTestPlan(changeText, baseEvidence);
    List<TestRecommendationDto> items = plan.recommendations();
    int iterations = 0;
    Set<String> knownComponents = knownComponents(review.result());

    while (hasInvalid(items, knownComponents) && iterations < MAX_REFINEMENT_ITERATIONS) {
      iterations++;
      metrics.qaRefinement();
      traceService.record(
          "qa_refinement",
          "refining",
          null,
          "retrying",
          "recommendation_invalid",
          null,
          null,
          null,
          feedbackDetail(items, knownComponents));
      String feedback = feedbackSection(items, knownComponents);
      TestPlanResult refinedPlan =
          aiAnalysisService.generateTestPlan(changeText, baseEvidence + "\n" + feedback);
      items = refinedPlan.recommendations();
      traceService.record(
          "qa_refinement",
          "completed",
          null,
          hasInvalid(items, knownComponents) ? "still_invalid" : "ok",
          null,
          null,
          null,
          null);
      log.warn(
          "qa_refinement iteration={} invalid_remaining={}",
          iterations,
          countInvalid(items, knownComponents));
    }

    List<QaRecommendation> recommendations = new ArrayList<>();
    for (TestRecommendationDto item : items) {
      boolean refined = !isInvalid(item, knownComponents);
      if (!refined) {
        traceService.record(
            "qa_refinement",
            "limit_exhausted",
            null,
            "failed",
            "recommendation_not_refined",
            null,
            null,
            null);
      }
      recommendations.add(
          new QaRecommendation(
              item.component(),
              item.description(),
              item.priority(),
              refined
                  ? "recomendacao valida (componente consistente com os findings de QA)"
                  : "recomendacao mantida nao refinada apos o limite de iteracoes",
              null,
              refined));
    }

    boolean degraded = review.degraded() || plan.degraded();
    return new QaOutcome(
        review.result(),
        List.copyOf(recommendations),
        review.documents(),
        iterations,
        degraded,
        truncate(toJson(review.result())),
        truncate(toJson(items)));
  }

  private boolean hasInvalid(List<TestRecommendationDto> items, Set<String> knownComponents) {
    return items.stream().anyMatch(item -> isInvalid(item, knownComponents));
  }

  private long countInvalid(List<TestRecommendationDto> items, Set<String> knownComponents) {
    return items.stream().filter(item -> isInvalid(item, knownComponents)).count();
  }

  /**
   * Recomendacao invalida quando descricao/componente vazios ou, havendo findings de QA, o
   * componente nao referencia nenhum componente revisado (nem um nivel de teste generico).
   */
  private boolean isInvalid(TestRecommendationDto item, Set<String> knownComponents) {
    if (item == null
        || item.component() == null
        || item.component().isBlank()
        || item.description() == null
        || item.description().isBlank()) {
      return true;
    }
    if (knownComponents.isEmpty()) {
      return false;
    }
    String component = item.component().trim().toLowerCase(Locale.ROOT);
    return !knownComponents.contains(component) && !GENERIC_COMPONENTS.contains(component);
  }

  private Set<String> knownComponents(CodeReviewResult review) {
    return review.findings().stream()
        .map(CodeReviewFindingDto::component)
        .filter(component -> component != null && !component.isBlank())
        .map(component -> component.trim().toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private String feedbackSection(List<TestRecommendationDto> items, Set<String> knownComponents) {
    List<Map<String, Object>> invalid = new ArrayList<>();
    for (TestRecommendationDto item : items) {
      if (isInvalid(item, knownComponents)) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("component", item.component());
        entry.put("description", item.description());
        entry.put("priority", item.priority());
        invalid.add(entry);
      }
    }
    return "[FEEDBACK]\n"
        + "Os itens a seguir sao invalidos (descricao/componente vazios ou componente nao citado "
        + "nos findings de QA). Refine APENAS esses itens, mantendo o schema JSON esperado:\n"
        + toJson(invalid);
  }

  private String feedbackDetail(List<TestRecommendationDto> items, Set<String> knownComponents) {
    return "itens_invalidos=" + toJson(items.stream().filter(item -> isInvalid(item, knownComponents)).map(TestRecommendationDto::component).toList());
  }

  private List<Map<String, Object>> toFindingMaps(CodeReviewResult review) {
    List<Map<String, Object>> findings = new ArrayList<>();
    for (CodeReviewFindingDto finding : review.findings()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("component", finding.component());
      entry.put("description", finding.description());
      entry.put("severity", finding.severity());
      entry.put("source", finding.source());
      findings.add(entry);
    }
    return findings;
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

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }

  private String truncate(String json) {
    if (json == null) {
      return null;
    }
    return json.length() > MAX_RESULT_JSON_LENGTH ? json.substring(0, MAX_RESULT_JSON_LENGTH) : json;
  }
}
