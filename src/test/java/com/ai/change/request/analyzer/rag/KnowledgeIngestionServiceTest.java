package com.ai.change.request.analyzer.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeIngestionServiceTest {

  @TempDir Path knowledgeDir;

  @Test
  void ingestsDocumentsWithMetadata() throws Exception {
    Files.writeString(
        knowledgeDir.resolve("discount-policy.md"), "## Desconto VIP\n10 por cento\n");
    Files.writeString(knowledgeDir.resolve("architecture.md"), "## Componentes\napp e agent\n");
    VectorStore store = mock(VectorStore.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    KnowledgeIngestionService service =
        new KnowledgeIngestionService(store, new Chunker(), jdbcTemplate, knowledgeDir.toString());

    List<Document> documents = service.ingest();

    assertThat(documents).hasSizeGreaterThanOrEqualTo(2);
    assertThat(documents)
        .allSatisfy(
            document -> {
              assertThat(document.getMetadata().get("source")).isNotNull();
              assertThat(document.getMetadata().get("document_id")).isNotNull();
              assertThat(document.getMetadata().get("chunk_id")).isNotNull();
              assertThat(document.getId()).isNotBlank();
            });
  }

  @Test
  void runIngestsOnlyWhenStoreIsEmpty() {
    VectorStore store = mock(VectorStore.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    KnowledgeIngestionService service =
        new KnowledgeIngestionService(store, new Chunker(), jdbcTemplate, knowledgeDir.toString());

    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0, 12);
    service.run(null);
    service.run(null);
    verify(store, times(1)).add(any());
  }

  @Test
  void runDoesNotDuplicateChunksOnRestart() {
    VectorStore store = mock(VectorStore.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    KnowledgeIngestionService service =
        new KnowledgeIngestionService(store, new Chunker(), jdbcTemplate, knowledgeDir.toString());

    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0, 5);
    service.run(null);
    service.run(null);

    ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
    verify(store, times(1)).add(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
  }
}
