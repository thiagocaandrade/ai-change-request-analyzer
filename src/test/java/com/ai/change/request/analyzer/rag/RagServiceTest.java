package com.ai.change.request.analyzer.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

class RagServiceTest {

  private RagService serviceWith(VectorStore store) {
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
    return new RagService(provider, 4, 0.7);
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
  void failureReturnsEmptyMarkedResult() {
    VectorStore store = mock(VectorStore.class);
    when(store.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalStateException("pgvector fora"));
    RagService service = serviceWith(store);

    RagService.KnowledgeSearchResult result = service.search("query");

    assertThat(result.hits()).isEmpty();
    assertThat(result.degraded()).isTrue();
  }

  @Test
  void missingStoreReturnsEmptyMarkedResult() {
    RagService service = serviceWith(null);

    RagService.KnowledgeSearchResult result = service.search("query");

    assertThat(result.hits()).isEmpty();
    assertThat(result.degraded()).isTrue();
  }
}
