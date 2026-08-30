package com.ai.change.request.analyzer.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migration idempotente da tabela de vetores (extensao pgvector). Roda antes da ingestao; comandos
 * {@code IF NOT EXISTS} garantem idempotencia em reinicializacoes.
 */
@Component
@ConditionalOnBean(VectorStore.class)
@Order(1)
public class PgVectorSchemaMigration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PgVectorSchemaMigration.class);

  private final JdbcTemplate jdbcTemplate;

  public PgVectorSchemaMigration(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
    jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
    jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS vector_store ("
            + "id uuid PRIMARY KEY, content text, metadata json, embedding vector(1536))");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS vector_store_embedding_idx "
            + "ON vector_store USING HNSW (embedding vector_cosine_ops)");
    log.info("pgvector_schema_migrated");
  }
}
