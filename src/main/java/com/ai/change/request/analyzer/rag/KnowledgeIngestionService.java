package com.ai.change.request.analyzer.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ingestao idempotente dos documentos de {@code knowledge/}: chunking por secao + embeddings +
 * gravacao no pgvector. Ingesta somente se a base estiver vazia — restart nao duplica chunks.
 */
@Component
@ConditionalOnBean(VectorStore.class)
@Order(2)
public class KnowledgeIngestionService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

  private final VectorStore vectorStore;
  private final Chunker chunker;
  private final JdbcTemplate jdbcTemplate;
  private final Path knowledgePath;

  public KnowledgeIngestionService(
      VectorStore vectorStore,
      Chunker chunker,
      JdbcTemplate jdbcTemplate,
      @Value("${ai.rag.knowledge-path:knowledge}") String knowledgePath) {
    this.vectorStore = vectorStore;
    this.chunker = chunker;
    this.jdbcTemplate = jdbcTemplate;
    this.knowledgePath = Path.of(knowledgePath).toAbsolutePath().normalize();
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      if (!isStoreEmpty()) {
        log.info("rag_ingestion_skipped reason=store_not_empty");
        return;
      }
      List<Document> documents = ingest();
      vectorStore.add(documents);
      log.info("rag_ingestion_completed chunks={}", documents.size());
    } catch (Exception e) {
      log.error(
          "rag_ingestion_failed error={} detail={} — analise segue degradada",
          e.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  boolean isStoreEmpty() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
    return count == null || count == 0;
  }

  List<Document> ingest() {
    if (!Files.isDirectory(knowledgePath)) {
      log.warn("rag_ingestion_skipped reason=knowledge_path_missing path={}", knowledgePath);
      return List.of();
    }
    List<Document> documents = new ArrayList<>();
    try (var files = Files.list(knowledgePath)) {
      files
          .filter(path -> path.getFileName().toString().endsWith(".md"))
          .sorted()
          .forEach(
              path -> {
                String fileName = path.getFileName().toString();
                String documentId = fileName.substring(0, fileName.length() - 3);
                try {
                  String content = Files.readString(path, StandardCharsets.UTF_8);
                  for (Chunker.Chunk chunk : chunker.chunk(documentId, content)) {
                    documents.add(
                        Document.builder()
                            .id(UUID.randomUUID().toString())
                            .text(chunk.content())
                            .metadata(
                                Map.of(
                                    "source", fileName,
                                    "document_id", documentId,
                                    "chunk_id", chunk.chunkId()))
                            .build());
                  }
                } catch (IOException e) {
                  log.warn(
                      "rag_ingestion_file_skipped file={} error={}",
                      fileName,
                      e.getClass().getSimpleName());
                }
              });
    } catch (IOException e) {
      log.error("rag_ingestion_failed error={}", e.getClass().getSimpleName());
    }
    return documents;
  }
}
