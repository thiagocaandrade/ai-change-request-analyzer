package com.ai.change.request.analyzer.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

  private final Chunker chunker = new Chunker();

  @Test
  void chunksBySection() {
    String text =
        """
        # Titulo

        ## Secao A
        conteudo da secao A

        ## Secao B
        conteudo da secao B
        """;

    List<Chunker.Chunk> chunks = chunker.chunk("doc", text);

    assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    assertThat(chunks.get(0).chunkId()).isEqualTo("doc-0");
    assertThat(chunks).anyMatch(chunk -> chunk.content().contains("Secao A"));
    assertThat(chunks).anyMatch(chunk -> chunk.content().contains("Secao B"));
    assertThat(chunks).allMatch(chunk -> chunk.chunkId().startsWith("doc-"));
  }

  @Test
  void splitsLongSectionIntoBoundedChunks() {
    String longParagraph = "palavra ".repeat(900);
    String text = "## Secao Longa\n" + longParagraph + "\n\n" + longParagraph;

    List<Chunker.Chunk> chunks = chunker.chunk("doc", text);

    assertThat(chunks).hasSizeGreaterThan(2);
    assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 2400);
  }

  @Test
  void singleSectionDocumentProducesOneChunk() {
    List<Chunker.Chunk> chunks = chunker.chunk("doc", "texto simples sem secao");

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).chunkId()).isEqualTo("doc-0");
  }

  @Test
  void emptyDocumentProducesNoChunks() {
    List<Chunker.Chunk> chunks = chunker.chunk("doc", "   \n  ");

    assertThat(chunks).isEmpty();
  }
}
