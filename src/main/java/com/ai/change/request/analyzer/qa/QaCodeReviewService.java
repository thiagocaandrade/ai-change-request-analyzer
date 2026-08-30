package com.ai.change.request.analyzer.qa;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.EvidenceRenderer;
import com.ai.change.request.analyzer.ai.dto.AiResults.CodeReviewResult;
import com.ai.change.request.analyzer.rag.RagService;
import com.ai.change.request.analyzer.rag.RagService.KnowledgeHit;
import com.ai.change.request.analyzer.rag.RagService.KnowledgeSearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Revisao de codigo assistida (etapa QA): recupera {@code coding-guidelines} e {@code
 * business-rules} via RAG, monta a evidencia delimitada como DADO NAO CONFIÁVEL e executa o estagio
 * {@code CODE_REVIEW}. A revisao apenas produz findings/recomendacoes; nunca altera o repositorio.
 */
@Service
public class QaCodeReviewService {

  private final RagService ragService;
  private final EvidenceRenderer evidenceRenderer;
  private final AiAnalysisService aiAnalysisService;

  public QaCodeReviewService(
      RagService ragService,
      EvidenceRenderer evidenceRenderer,
      AiAnalysisService aiAnalysisService) {
    this.ragService = ragService;
    this.evidenceRenderer = evidenceRenderer;
    this.aiAnalysisService = aiAnalysisService;
  }

  /** Resultado da revisao com os documentos recuperados usados como evidencia (dado). */
  public record ReviewOutcome(
      CodeReviewResult result, List<Map<String, Object>> documents, boolean degraded) {}

  /**
   * Revisa a alteracao: RAG (diretrizes de codigo + regras de negocio) → evidencia delimitada →
   * estagio CODE_REVIEW. Conteudo recuperado e sempre dado, nunca instrucao.
   */
  public ReviewOutcome review(String changeText, String diff) {
    KnowledgeSearchResult search = ragService.search(changeText);
    List<KnowledgeHit> relevant =
        search.hits().stream()
            .filter(
                hit ->
                    hit.source() != null
                        && (hit.source().contains("coding-guidelines")
                            || hit.source().contains("business-rules")))
            .toList();
    List<Map<String, Object>> documents = new ArrayList<>();
    for (KnowledgeHit hit : relevant) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("source", hit.source());
      entry.put("document_id", hit.documentId());
      entry.put("content", hit.content());
      documents.add(entry);
    }
    List<Map<String, Object>> diffSection = new ArrayList<>();
    if (diff != null && !diff.isBlank()) {
      diffSection.add(Map.of("diff", diff));
    }
    String evidence =
        evidenceRenderer.renderSections(Map.of("DOCUMENTOS", documents, "DIFF", diffSection));
    CodeReviewResult result = aiAnalysisService.reviewCode(changeText, evidence);
    return new ReviewOutcome(
        result, List.copyOf(documents), search.degraded() || result.degraded());
  }
}
