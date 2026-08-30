package com.ai.change.request.analyzer.memory;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ImpactFinding;
import com.ai.change.request.analyzer.domain.ImpactFindingRepository;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskAssessmentRepository;
import com.ai.change.request.analyzer.domain.RiskLevel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoria persistente de analises anteriores: busca por termos, componente, regra de negocio e
 * classificacao. Falha de busca → lista vazia marcada (degradada), sem interromper a analise.
 */
@Service
public class AnalysisMemoryService {

  private static final Logger log = LoggerFactory.getLogger(AnalysisMemoryService.class);
  private static final int MAX_RESULTS = 10;

  public record HistoryHit(String requestId, String summary) {}

  public record HistorySearchResult(List<HistoryHit> hits, boolean degraded) {}

  private final ChangeRequestRepository changeRequestRepository;
  private final ImpactFindingRepository impactFindingRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;

  public AnalysisMemoryService(
      ChangeRequestRepository changeRequestRepository,
      ImpactFindingRepository impactFindingRepository,
      RiskAssessmentRepository riskAssessmentRepository) {
    this.changeRequestRepository = changeRequestRepository;
    this.impactFindingRepository = impactFindingRepository;
    this.riskAssessmentRepository = riskAssessmentRepository;
  }

  @Transactional(readOnly = true)
  public HistorySearchResult searchByTerms(String terms) {
    try {
      Map<String, HistoryHit> unique = new LinkedHashMap<>();
      for (String term : terms.split("\\s+")) {
        if (term.isBlank()) {
          continue;
        }
        for (ChangeRequest request : changeRequestRepository.findByTextContainingIgnoreCase(term)) {
          HistoryHit hit = toHit(request);
          unique.putIfAbsent(hit.requestId(), hit);
        }
      }
      return new HistorySearchResult(
          List.copyOf(unique.values().stream().limit(MAX_RESULTS).toList()), false);
    } catch (Exception e) {
      return degraded("searchByTerms", e);
    }
  }

  @Transactional(readOnly = true)
  public HistorySearchResult searchByComponent(String component) {
    try {
      Map<String, HistoryHit> unique = new LinkedHashMap<>();
      for (ImpactFinding finding :
          impactFindingRepository.findByComponentContainingIgnoreCase(component)) {
        HistoryHit hit = toHit(finding.getAnalysis().getChangeRequest());
        unique.putIfAbsent(hit.requestId(), hit);
      }
      return new HistorySearchResult(
          List.copyOf(unique.values().stream().limit(MAX_RESULTS).toList()), false);
    } catch (Exception e) {
      return degraded("searchByComponent", e);
    }
  }

  @Transactional(readOnly = true)
  public HistorySearchResult searchByBusinessRule(String rule) {
    try {
      Map<String, HistoryHit> unique = new LinkedHashMap<>();
      for (ChangeRequest request : changeRequestRepository.findByTextContainingIgnoreCase(rule)) {
        HistoryHit hit = toHit(request);
        unique.putIfAbsent(hit.requestId(), hit);
      }
      for (ImpactFinding finding :
          impactFindingRepository.findByDescriptionContainingIgnoreCase(rule)) {
        HistoryHit hit = toHit(finding.getAnalysis().getChangeRequest());
        unique.putIfAbsent(hit.requestId(), hit);
      }
      return new HistorySearchResult(
          List.copyOf(unique.values().stream().limit(MAX_RESULTS).toList()), false);
    } catch (Exception e) {
      return degraded("searchByBusinessRule", e);
    }
  }

  @Transactional(readOnly = true)
  public HistorySearchResult searchByClassification(String classification) {
    RiskLevel level;
    try {
      level = RiskLevel.valueOf(classification.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return new HistorySearchResult(List.of(), false);
    }
    try {
      Map<String, HistoryHit> unique = new LinkedHashMap<>();
      for (RiskAssessment assessment : riskAssessmentRepository.findByLevel(level)) {
        HistoryHit hit = toHit(assessment.getAnalysis().getChangeRequest());
        unique.putIfAbsent(hit.requestId(), hit);
      }
      return new HistorySearchResult(
          List.copyOf(unique.values().stream().limit(MAX_RESULTS).toList()), false);
    } catch (Exception e) {
      return degraded("searchByClassification", e);
    }
  }

  private HistoryHit toHit(ChangeRequest request) {
    return new HistoryHit(request.getId().toString(), summarize(request.getText()));
  }

  private String summarize(String text) {
    String trimmed = text == null ? "" : text.trim();
    return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
  }

  private HistorySearchResult degraded(String search, Exception e) {
    log.error(
        "memory_search_failed search={} error={} trace_id={}",
        search,
        e.getClass().getSimpleName(),
        MDC.get("trace_id"));
    return new HistorySearchResult(List.of(), true);
  }
}
