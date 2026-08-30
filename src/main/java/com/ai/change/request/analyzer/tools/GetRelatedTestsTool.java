package com.ai.change.request.analyzer.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * Tool {@code get_related_tests}: encontra arquivos de teste relacionados a um componente, por nome
 * de arquivo ou conteudo, dentro do repositorio configurado.
 */
@Component
public class GetRelatedTestsTool implements AnalysisTool {

  public static final String NAME = "get_related_tests";

  private static final int MAX_COMPONENT_LENGTH = 200;
  private static final int MAX_RESULTS = 20;
  private static final Set<String> TEXT_EXTENSIONS =
      Set.of(".java", ".py", ".md", ".yml", ".yaml", ".xml", ".json", ".txt", ".feature");
  private static final Set<String> IGNORED_DIRS =
      Set.of(".git", "target", "node_modules", ".venv", "__pycache__", ".mvn", ".idea", ".vscode");

  private final RepoAccessPolicy policy;
  private final ObjectMapper objectMapper;

  public GetRelatedTestsTool(RepoAccessPolicy policy, ObjectMapper objectMapper) {
    this.policy = policy;
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(NAME)
        .description(
            "Encontra arquivos de teste relacionados a um componente, por nome de arquivo ou "
                + "conteudo. Retorna lista vazia quando nada e encontrado.")
        .inputSchema(
            """
            {
              "type": "object",
              "properties": {
                "component": {"type": "string", "description": "Nome do componente afetado"}
              },
              "required": ["component"]
            }
            """)
        .build();
  }

  @Override
  public String call(String toolInput) {
    ComponentInput input;
    try {
      input = objectMapper.readValue(toolInput, ComponentInput.class);
    } catch (JsonProcessingException e) {
      return error("invalid_input", "argumentos invalidos: " + e.getMessage());
    }
    if (input.component() == null || input.component().isBlank()) {
      return error("invalid_input", "component nao pode ser vazio");
    }
    if (input.component().length() > MAX_COMPONENT_LENGTH) {
      return error("invalid_input", "component excede " + MAX_COMPONENT_LENGTH + " caracteres");
    }
    try {
      List<Map<String, String>> results = search(input.component());
      return objectMapper.writeValueAsString(Map.of("results", results, "count", results.size()));
    } catch (Exception e) {
      return error("search_failed", "falha na busca: " + e.getClass().getSimpleName());
    }
  }

  List<Map<String, String>> search(String component) {
    String needle = component.toLowerCase();
    List<Path> matches = new ArrayList<>();
    Path root = policy.root();
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .filter(this::isTestFile)
          .filter(this::notIgnoredDir)
          .filter(this::isTextFile)
          .sorted(Comparator.comparing(Path::toString))
          .forEach(
              path -> {
                if (matches.size() >= MAX_RESULTS) {
                  return;
                }
                if (path.getFileName().toString().toLowerCase().contains(needle)
                    || contentContains(path, needle)) {
                  matches.add(path);
                }
              });
    } catch (IOException e) {
      throw new IllegalStateException("falha ao percorrer repositorio", e);
    }
    return matches.stream()
        .map(
            path ->
                Map.of(
                    "file",
                    policy.root().relativize(path).toString().replace('\\', '/'),
                    "match",
                    component))
        .toList();
  }

  private boolean isTestFile(Path path) {
    return path.toString().replace('\\', '/').contains("/test");
  }

  private boolean isTextFile(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 && TEXT_EXTENSIONS.contains(name.substring(dot).toLowerCase());
  }

  private boolean notIgnoredDir(Path path) {
    return path.getParent() == null
        || !IGNORED_DIRS.contains(path.getParent().getFileName().toString());
  }

  private boolean contentContains(Path path, String needle) {
    try {
      return Files.lines(path, StandardCharsets.UTF_8)
          .anyMatch(line -> line.toLowerCase().contains(needle));
    } catch (IOException e) {
      return false;
    }
  }

  String error(String code, String message) {
    try {
      return objectMapper.writeValueAsString(Map.of("error", code, "message", message));
    } catch (JsonProcessingException e) {
      return "{\"error\":\"" + code + "\",\"message\":\"tool error\"}";
    }
  }

  record ComponentInput(String component) {}
}
