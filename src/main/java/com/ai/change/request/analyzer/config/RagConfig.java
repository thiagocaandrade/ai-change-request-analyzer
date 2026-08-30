package com.ai.change.request.analyzer.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vector store pgvector condicionado a modelo de embedding presente. O schema da tabela e criado
 * por {@code PgVectorSchemaMigration} (migration idempotente), nunca via ddl-auto.
 */
@Configuration
public class RagConfig {

  @Bean
  @ConditionalOnBean(EmbeddingModel.class)
  @ConditionalOnProperty(name = "ai.rag.enabled", havingValue = "true", matchIfMissing = true)
  VectorStore vectorStore(
      JdbcTemplate jdbcTemplate,
      EmbeddingModel embeddingModel,
      @Value("${ai.rag.dimensions:1536}") int dimensions) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .vectorTableName("vector_store")
        .initializeSchema(false)
        .dimensions(dimensions)
        .build();
  }
}
