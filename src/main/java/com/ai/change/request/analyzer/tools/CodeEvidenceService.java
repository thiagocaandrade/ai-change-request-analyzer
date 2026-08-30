package com.ai.change.request.analyzer.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Coleta evidencia de codigo e de testes via tools. Falha de tool (apos retries) e registrada e a
 * analise segue com as demais buscas.
 */
@Service
public class CodeEvidenceService {

  private static final Logger log = LoggerFactory.getLogger(CodeEvidenceService.class);

  private static final int MAX_KEYWORDS = 8;
  private static final int MAX_FINDINGS = 30;
  private static final Set<String> STOPWORDS =
      Set.of(
          "de", "da", "do", "dos", "das", "para", "com", "uma", "que", "ser", "em", "por", "the",
          "and", "for", "from");

  private final ToolRegistry toolRegistry;
  private final ObjectMapper objectMapper;

  public CodeEvidenceService(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
    this.toolRegistry = toolRegistry;
    this.objectMapper = objectMapper;
  }

  public record CodeFinding(
      String area, String description, String severity, String file, Integer line) {}

  public record CodeEvidence(List<CodeFinding> findings, boolean degraded) {}

  public CodeEvidence analyzeCode(String changeText) {
    List<String> keywords = extractKeywords(changeText);
    List<CodeFinding> findings = new ArrayList<>();
    int calls = 0;
    int failures = 0;

    for (String keyword : keywords) {
      if (findings.size() >= MAX_FINDINGS) {
        break;
      }
      String result = toolRegistry.byName(SearchCodeTool.NAME).call(json(Map.of("query", keyword)));
      calls++;
      if (parseError(result) != null) {
        failures++;
        continue;
      }
      findings.addAll(parseMatches(result, MAX_FINDINGS - findings.size()));
    }

    for (String keyword : keywords) {
      if (findings.size() >= MAX_FINDINGS) {
        break;
      }
      String result =
          toolRegistry.byName(GetRelatedTestsTool.NAME).call(json(Map.of("component", keyword)));
      calls++;
      if (parseError(result) != null) {
        failures++;
        continue;
      }
      findings.addAll(parseTests(result, keyword, MAX_FINDINGS - findings.size()));
    }

    boolean degraded = calls > 0 && failures == calls;
    if (degraded) {
      log.warn(
          "code_evidence_degraded failures={} calls={} trace_id={}",
          failures,
          calls,
          MDC.get("trace_id"));
    }
    return new CodeEvidence(List.copyOf(findings), degraded);
  }

  private String parseError(String toolResult) {
    try {
      JsonNode node = objectMapper.readTree(toolResult);
      return node.has("error") ? node.get("error").asText() : null;
    } catch (JsonProcessingException e) {
      return "invalid_tool_output";
    }
  }

  private List<CodeFinding> parseMatches(String toolResult, int limit) {
    List<CodeFinding> findings = new ArrayList<>();
    try {
      JsonNode node = objectMapper.readTree(toolResult);
      JsonNode results = node.get("results");
      if (results != null && results.isArray()) {
        for (JsonNode match : results) {
          if (findings.size() >= limit) {
            break;
          }
          String file = match.hasNonNull("file") ? match.get("file").asText() : null;
          Integer line = match.hasNonNull("line") ? match.get("line").asInt() : null;
          String snippet = match.hasNonNull("snippet") ? match.get("snippet").asText() : "";
          findings.add(
              new CodeFinding(
                  "code", "ocorrencia de termo em codigo: " + snippet, "INFO", file, line));
        }
      }
    } catch (JsonProcessingException e) {
      log.warn("tool_output_unreadable tool={}", SearchCodeTool.NAME);
    }
    return findings;
  }

  private List<CodeFinding> parseTests(String toolResult, String component, int limit) {
    List<CodeFinding> findings = new ArrayList<>();
    try {
      JsonNode node = objectMapper.readTree(toolResult);
      JsonNode results = node.get("results");
      if (results != null && results.isArray()) {
        for (JsonNode match : results) {
          if (findings.size() >= limit) {
            break;
          }
          String file = match.hasNonNull("file") ? match.get("file").asText() : null;
          findings.add(
              new CodeFinding(
                  "test",
                  "teste relacionado ao componente '" + component + "'",
                  "INFO",
                  file,
                  null));
        }
      }
    } catch (JsonProcessingException e) {
      log.warn("tool_output_unreadable tool={}", GetRelatedTestsTool.NAME);
    }
    return findings;
  }

  static List<String> extractKeywords(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    Set<String> keywords = new LinkedHashSet<>();
    for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
      if (token.length() < 3 || token.matches("[0-9]+") || STOPWORDS.contains(token)) {
        continue;
      }
      keywords.add(token);
      if (keywords.size() >= MAX_KEYWORDS) {
        break;
      }
    }
    return List.copyOf(keywords);
  }

  private String json(Map<String, String> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("falha ao serializar argumentos da tool", e);
    }
  }
}
