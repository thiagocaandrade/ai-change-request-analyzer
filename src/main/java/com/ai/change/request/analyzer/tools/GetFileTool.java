package com.ai.change.request.analyzer.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tool {@code get_file}: le um arquivo dentro do repositorio configurado. Rejeita path traversal,
 * absolutos fora da raiz e arquivos inexistentes com erro estruturado.
 */
@Component
public class GetFileTool implements AnalysisTool {

  public static final String NAME = "get_file";

  private static final int MAX_PATH_LENGTH = 500;

  private final RepoAccessPolicy policy;
  private final ObjectMapper objectMapper;
  private final long maxFileSize;

  public GetFileTool(
      RepoAccessPolicy policy,
      ObjectMapper objectMapper,
      @Value("${tools.max-file-size:102400}") long maxFileSize) {
    this.policy = policy;
    this.objectMapper = objectMapper;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(NAME)
        .description(
            "Le o conteudo de um arquivo do repositorio configurado, dado um caminho relativo. "
                + "Caminhos com path traversal ou fora da raiz sao rejeitados.")
        .inputSchema(
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "Caminho relativo do arquivo"}
              },
              "required": ["path"]
            }
            """)
        .build();
  }

  @Override
  public String call(String toolInput) {
    FileInput input;
    try {
      input = objectMapper.readValue(toolInput, FileInput.class);
    } catch (JsonProcessingException e) {
      return error("invalid_input", "argumentos invalidos: " + e.getMessage());
    }
    if (input.path() == null || input.path().isBlank()) {
      return error("invalid_input", "path nao pode ser vazio");
    }
    if (input.path().length() > MAX_PATH_LENGTH) {
      return error("invalid_input", "path excede " + MAX_PATH_LENGTH + " caracteres");
    }
    Path file;
    try {
      file = policy.resolveInside(input.path());
    } catch (ToolAccessException e) {
      return error(e.getCode(), e.getMessage());
    }
    if (!Files.exists(file) || !Files.isRegularFile(file)) {
      return error("file_not_found", "arquivo inexistente: " + input.path());
    }
    try {
      long size = Files.size(file);
      if (size > maxFileSize) {
        return error("file_too_large", "arquivo excede " + maxFileSize + " bytes");
      }
      String content = Files.readString(file, StandardCharsets.UTF_8);
      return objectMapper.writeValueAsString(Map.of("path", input.path(), "content", content));
    } catch (IOException e) {
      return error("file_read_failed", "falha ao ler arquivo: " + e.getClass().getSimpleName());
    }
  }

  String error(String code, String message) {
    try {
      return objectMapper.writeValueAsString(Map.of("error", code, "message", message));
    } catch (JsonProcessingException e) {
      return "{\"error\":\"" + code + "\",\"message\":\"tool error\"}";
    }
  }

  record FileInput(String path) {}
}
