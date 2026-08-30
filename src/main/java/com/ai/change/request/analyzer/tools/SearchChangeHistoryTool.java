package com.ai.change.request.analyzer.tools;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * Tool {@code search_change_history}: busca analises anteriores por termo no texto das solicitacoes
 * persistidas. Retorna identificador e resumo de cada resultado.
 */
@Component
public class SearchChangeHistoryTool implements AnalysisTool {

  public static final String NAME = "search_change_history";

  private static final int MAX_QUERY_LENGTH = 200;
  private static final int MAX_RESULTS = 10;

  private final ChangeRequestRepository changeRequestRepository;
  private final ObjectMapper objectMapper;

  public SearchChangeHistoryTool(
      ChangeRequestRepository changeRequestRepository, ObjectMapper objectMapper) {
    this.changeRequestRepository = changeRequestRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(NAME)
        .description(
            "Busca solicitacoes de mudanca anteriores por termo (ILIKE) e retorna "
                + "identificador e resumo de cada uma.")
        .inputSchema(
            """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "Termo a buscar no historico"}
              },
              "required": ["query"]
            }
            """)
        .build();
  }

  @Override
  public String call(String toolInput) {
    QueryInput input;
    try {
      input = objectMapper.readValue(toolInput, QueryInput.class);
    } catch (JsonProcessingException e) {
      return error("invalid_input", "argumentos invalidos: " + e.getMessage());
    }
    if (input.query() == null || input.query().isBlank()) {
      return error("invalid_input", "query nao pode ser vazia");
    }
    if (input.query().length() > MAX_QUERY_LENGTH) {
      return error("invalid_input", "query excede " + MAX_QUERY_LENGTH + " caracteres");
    }
    try {
      List<ChangeRequest> found =
          changeRequestRepository.findByTextContainingIgnoreCase(input.query());
      List<Map<String, String>> results =
          found.stream()
              .limit(MAX_RESULTS)
              .map(
                  request ->
                      Map.of(
                          "requestId",
                          request.getId().toString(),
                          "summary",
                          summarize(request.getText())))
              .toList();
      return objectMapper.writeValueAsString(Map.of("results", results, "count", results.size()));
    } catch (Exception e) {
      return error("search_failed", "falha na busca: " + e.getClass().getSimpleName());
    }
  }

  static String summarize(String text) {
    String trimmed = text == null ? "" : text.trim();
    return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
  }

  String error(String code, String message) {
    try {
      return objectMapper.writeValueAsString(Map.of("error", code, "message", message));
    } catch (JsonProcessingException e) {
      return "{\"error\":\"" + code + "\",\"message\":\"tool error\"}";
    }
  }

  record QueryInput(String query) {}
}
