package com.ai.change.request.analyzer.rag;

import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Busca semantica nos documentos de conhecimento: top-k configurável, threshold de score, metadata
 * (source, document_id, chunk_id, score) e ordenacao decrescente. Execucao com timeout, retry
 * limitado e backoff (via {@link ResilienceExecutor}); falha ou base indisponível → lista vazia
 * marcada (degradada), sem erro fatal. Busca bem-sucedida registra as fontes dos documentos
 * recuperados (apenas metadados, nunca o conteudo) no evento de auditoria.
 */
@Service
public class RagService {

  private static final Logger log = LoggerFactory.getLogger(RagService.class);

  private static final int MAX_SOURCES_DETAIL_LENGTH = 1024;

  public record KnowledgeHit(
      String source, String documentId, String chunkId, Double score, String content) {}

  public record KnowledgeSearchResult(List<KnowledgeHit> hits, boolean degraded) {}

  private final ObjectProvider<VectorStore> vectorStoreProvider;
  private final int defaultTopK;
  private final double defaultThreshold;
  private final ResilienceExecutor resilienceExecutor;
  private final TraceService traceService;
  private final ObjectMapper objectMapper;
  private final long timeoutMs;

  public RagService(
      ObjectProvider<VectorStore> vectorStoreProvider,
      @Value("${ai.rag.top-k:4}") int defaultTopK,
      @Value("${ai.rag.similarity-threshold:0.7}") double defaultThreshold,
      ResilienceExecutor resilienceExecutor,
      TraceService traceService,
      ObjectMapper objectMapper,
      @Value("${ai.rag.timeout-ms:5000}") long timeoutMs) {
    this.vectorStoreProvider = vectorStoreProvider;
    this.defaultTopK = defaultTopK;
    this.defaultThreshold = defaultThreshold;
    this.resilienceExecutor = resilienceExecutor;
    this.traceService = traceService;
    this.objectMapper = objectMapper;
    this.timeoutMs = timeoutMs;
  }

  public KnowledgeSearchResult search(String query) {
    return search(query, defaultTopK, defaultThreshold);
  }

  public KnowledgeSearchResult search(String query, int topK, double threshold) {
    String traceId = MDC.get("trace_id");
    VectorStore store = vectorStoreProvider.getIfAvailable();
    if (store == null) {
      traceService.record(
          "retrieve_knowledge",
          "rag_unavailable",
          null,
          "degraded",
          "no_vector_store",
          null,
          null,
          null);
      log.warn("rag_unavailable reason=no_vector_store trace_id={}", traceId);
      return new KnowledgeSearchResult(List.of(), true);
    }
    KnowledgeSearchResult result =
        resilienceExecutor.execute(
            "retrieve_knowledge",
            "rag_search",
            () -> {
              SearchRequest request =
                  SearchRequest.builder()
                      .query(query)
                      .topK(topK)
                      .similarityThreshold(threshold)
                      .build();
              List<Document> documents = store.similaritySearch(request);
              List<KnowledgeHit> hits =
                  documents.stream()
                      .map(this::toHit)
                      .sorted(
                          Comparator.comparingDouble(
                                  (KnowledgeHit hit) -> hit.score() == null ? -1.0 : hit.score())
                              .reversed())
                      .toList();
              return new KnowledgeSearchResult(hits, false);
            },
            timeoutMs,
            () -> {
              log.error("rag_search_degraded reason=retries_exhausted trace_id={}", traceId);
              return new KnowledgeSearchResult(List.of(), true);
            });
    recordSources(result);
    return result;
  }

  /** Registra as fontes dos documentos recuperados na reconstrucao da execucao (trace). */
  void recordSources(KnowledgeSearchResult result) {
    if (result.degraded()) {
      return;
    }
    traceService.record(
        "retrieve_knowledge",
        "rag_search",
        null,
        null,
        null,
        null,
        null,
        null,
        toSourcesDetail(result.hits()));
  }

  private String toSourcesDetail(List<KnowledgeHit> hits) {
    List<Map<String, Object>> sources = new ArrayList<>();
    for (KnowledgeHit hit : hits) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("source", hit.source());
      entry.put("document_id", hit.documentId());
      entry.put("score", hit.score());
      sources.add(entry);
    }
    try {
      String json = objectMapper.writeValueAsString(sources);
      while (json.length() > MAX_SOURCES_DETAIL_LENGTH && sources.size() > 1) {
        sources.remove(sources.size() - 1);
        json = objectMapper.writeValueAsString(sources);
      }
      if (json.length() > MAX_SOURCES_DETAIL_LENGTH) {
        json = json.substring(0, MAX_SOURCES_DETAIL_LENGTH);
      }
      return json;
    } catch (JsonProcessingException e) {
      log.warn("rag_sources_detail_serialization_failed error={}", e.getClass().getSimpleName());
      return null;
    }
  }

  private KnowledgeHit toHit(Document document) {
    Map<String, Object> metadata = document.getMetadata();
    return new KnowledgeHit(
        string(metadata.get("source")),
        string(metadata.get("document_id")),
        string(metadata.get("chunk_id")),
        document.getScore(),
        document.getText());
  }

  private String string(Object value) {
    return value == null ? null : value.toString();
  }
}
