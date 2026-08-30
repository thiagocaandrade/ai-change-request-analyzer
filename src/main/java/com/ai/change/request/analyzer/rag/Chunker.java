package com.ai.change.request.analyzer.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Chunking por secao/paragrafo (~400-600 tokens, sem overlap). */
@Component
public class Chunker {

  private static final int MAX_CHUNK_CHARS = 2400;

  public record Chunk(String chunkId, String content) {}

  public List<Chunk> chunk(String documentId, String text) {
    List<Chunk> chunks = new ArrayList<>();
    for (String section : splitSections(text)) {
      for (String part : splitLong(section)) {
        chunks.add(new Chunk(documentId + "-" + chunks.size(), part));
      }
    }
    return chunks;
  }

  List<String> splitSections(String text) {
    List<String> sections = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : text.split("\n", -1)) {
      if (line.startsWith("## ")) {
        if (!current.toString().isBlank()) {
          sections.add(current.toString().trim());
        }
        current = new StringBuilder(line);
      } else {
        current.append('\n').append(line);
      }
    }
    if (!current.toString().isBlank()) {
      sections.add(current.toString().trim());
    }
    if (sections.isEmpty() && text != null && !text.trim().isEmpty()) {
      sections.add(text.trim());
    }
    return sections;
  }

  List<String> splitLong(String section) {
    if (section.length() <= MAX_CHUNK_CHARS) {
      return List.of(section);
    }
    List<String> parts = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    for (String paragraph : section.split("\n\n")) {
      if (paragraph.isBlank()) {
        continue;
      }
      if (buffer.length() + paragraph.length() + 2 > MAX_CHUNK_CHARS && buffer.length() > 0) {
        parts.add(buffer.toString().trim());
        buffer = new StringBuilder();
      }
      if (paragraph.length() > MAX_CHUNK_CHARS) {
        if (buffer.length() > 0) {
          parts.add(buffer.toString().trim());
          buffer = new StringBuilder();
        }
        for (String hard : hardSplit(paragraph)) {
          parts.add(hard);
        }
      } else {
        buffer.append(paragraph).append("\n\n");
      }
    }
    if (buffer.length() > 0) {
      parts.add(buffer.toString().trim());
    }
    return parts;
  }

  private List<String> hardSplit(String paragraph) {
    List<String> parts = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();
    for (String word : paragraph.split("\\s+")) {
      if (buffer.length() + word.length() + 1 > MAX_CHUNK_CHARS && buffer.length() > 0) {
        parts.add(buffer.toString().trim());
        buffer = new StringBuilder();
      }
      buffer.append(word).append(' ');
    }
    if (buffer.length() > 0) {
      parts.add(buffer.toString().trim());
    }
    return parts;
  }
}
