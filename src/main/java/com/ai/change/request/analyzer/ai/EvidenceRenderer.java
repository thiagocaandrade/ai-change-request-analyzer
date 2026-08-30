package com.ai.change.request.analyzer.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renderiza a evidencia recuperada como dado na secao delimitada "DADOS NAO CONFIÁVEIS" do user
 * message. O conteudo e sempre dado, nunca instrucao do sistema.
 */
@Component
public class EvidenceRenderer {

  private final ObjectMapper objectMapper;

  public EvidenceRenderer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String render(
      List<Map<String, Object>> codeFindings,
      List<Map<String, Object>> retrievedDocuments,
      List<Map<String, Object>> historicalFindings) {
    StringBuilder builder = new StringBuilder();
    section(builder, "CODIGO", codeFindings);
    section(builder, "DOCUMENTOS", retrievedDocuments);
    section(builder, "HISTORICO", historicalFindings);
    String result = builder.toString().trim();
    return result.isEmpty() ? "(sem evidencia disponivel)" : result;
  }

  public String renderSections(Map<String, List<Map<String, Object>>> sections) {
    StringBuilder builder = new StringBuilder();
    sections.forEach((label, items) -> section(builder, label, items == null ? List.of() : items));
    String result = builder.toString().trim();
    return result.isEmpty() ? "(sem evidencia disponivel)" : result;
  }

  private void section(StringBuilder builder, String label, List<Map<String, Object>> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    builder.append('[').append(label).append("]\n");
    for (Map<String, Object> item : items) {
      builder.append("- ").append(toJson(item)).append('\n');
    }
  }

  private String toJson(Map<String, Object> item) {
    try {
      return objectMapper.writeValueAsString(item);
    } catch (JsonProcessingException e) {
      return String.valueOf(item);
    }
  }
}
