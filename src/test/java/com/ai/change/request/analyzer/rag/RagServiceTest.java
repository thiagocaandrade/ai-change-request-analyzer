package com.ai.change.request.analyzer.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

class RagServiceTest {

  private RagService serviceWith(VectorStore store) {
    return serviceWith(store, 5000);
  }

  private RagService serviceWith(VectorStore store, long timeoutMs) {
    ObjectProvider<VectorStore> provider =
        new ObjectProvider<>() {
          @Override
          public VectorStore getObject(Object... args) {
            return store;
          }

          @Override
          public VectorStore getIfAvailable() {
            return store;
          }

          @Override
          public VectorStore getIfUnique() {
            return store;
          }

          @Override
          public VectorStore getObject() {
            return store;
          }
        };
    TraceService traceService = new TraceService(Mockito.mock(TraceEventRepository.class));
    ResilienceExecutor executor = new ResilienceExecutor(traceService, 0, 10);
    return new RagService(provider, 4, 0.7, executor, traceService, timeoutMs);
  }

  private Document document(String id, double score, String source) {
    return Document.builder()
        .id(id)
        .text("conteudo de " + source)
        .score(score)
        .metadata(
            Map.of(
                "source", source,
                "document_id", source.substring(0, source.length() - 3),
                "chunk_id", id))
        .build();
  }

  @Test
  void searchRespectsTopKAndThresholdAndSortsDescending() {
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                document("d-0", 0.92, "discount-policy.md"),
                document("d-1", 0.98, "business-rules.md"),
                document("d-2", 0.71, "architecture.md")));
    RagService service = serviceWith(store);

    RagService.KnowledgeSearchResult result = service.search("desconto VIP", 3, 0.7);

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(store).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(3);
    assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.7);
    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).hasSize(3);
    assertThat(result.hits().get(0).score()).isEqualTo(0.98);
    assertThat(result.hits().get(1).score()).isEqualTo(0.92);
    assertThat(result.hits().get(2).score()).isEqualTo(0.71);
    assertThat(result.hits())
        .allSatisfy(
            hit -> {
              assertThat(hit.source()).isNotBlank();
              assertThat(hit.documentId()).isNotBlank();
              assertThat(hit.chunkId()).isNotBlank();
            });
  }

  @Test
  void defaultsToConfiguredTopKAndThreshold() {
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    RagService service = serviceWith(store);

    service.search("query");

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(store).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(4);
    assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.7);
  }

  @Test
  void missingStoreReturnsEmptyMarkedResult() {
    RagService service = serviceWith(null);

    RagService.KnowledgeSearchResult result = service.search("query");

    assertThat(result.hits()).isEmpty();
    assertThat(result.degraded()).isTrue();
  }

  @Test
  void transientFailureRecoversWithinRetryLimit() {
    VectorStore store = mock(VectorStore.class);
    AtomicInteger calls = new AtomicInteger();
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenAnswer(
            invocation -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("pgvector transiente");
              }
              return List.of(document("d-0", 0.92, "discount-policy.md"));
            });
    RagService service = serviceWith(store);

    RagService.KnowledgeSearchResult result = service.search("desconto VIP");

    assertThat(calls.get()).isEqualTo(2);
    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).hasSize(1);
  }

  @Test
  void timeoutExhaustsRetriesAndReturnsEmptyMarkedResult() {
    VectorStore store = mock(VectorStore.class);
    AtomicInteger calls = new AtomicInteger();
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenAnswer(
            invocation -> {
              calls.incrementAndGet();
              try {
                Thread.sleep(300);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.of(document("d-0", 0.92, "discount-policy.md"));
            });
    RagService service = serviceWith(store, 80);

    RagService.KnowledgeSearchResult result = service.search("desconto VIP");

    assertThat(calls.get()).isEqualTo(3);
    assertThat(result.degraded()).isTrue();
    assertThat(result.hits()).isEmpty();
  }

  @Test
  void persistentFailureUsesMarkedFallbackWithoutInterruptingAnalysis() {
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalStateException("pgvector fora"));
    RagService service = serviceWith(store);

    RagService.KnowledgeSearchResult result = service.search("query");

    assertThat(result.hits()).isEmpty();
    assertThat(result.degraded()).isTrue();
  }
}
