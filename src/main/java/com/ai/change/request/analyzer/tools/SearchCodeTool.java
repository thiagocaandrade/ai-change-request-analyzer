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
 * Tool {@code search_code}: busca textual em arquivos texto dentro do repositorio configurado. Nao
 * executa shell; nao sai da raiz configurada.
 */
@Component
public class SearchCodeTool implements AnalysisTool {

  public static final String NAME = "search_code";

  private static final int MAX_QUERY_LENGTH = 200;
  private static final int MAX_RESULTS = 20;
  private static final Set<String> TEXT_EXTENSIONS =
      Set.of(
          ".java",
          ".py",
          ".md",
          ".yml",
          ".yaml",
          ".properties",
          ".xml",
          ".json",
          ".txt",
          ".html",
          ".kt",
          ".js",
          ".ts");
  private static final Set<String> IGNORED_DIRS =
      Set.of(".git", "target", "node_modules", ".venv", "__pycache__", ".mvn", ".idea", ".vscode");

  private final RepoAccessPolicy policy;
  private final ObjectMapper objectMapper;

  public SearchCodeTool(RepoAccessPolicy policy, ObjectMapper objectMapper) {
    this.policy = policy;
    this.objectMapper = objectMapper;
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return ToolDefinition.builder()
        .name(NAME)
        .description(
            "Busca textual (case-insensitive) em arquivos do repositorio configurado. "
                + "Retorna arquivo, linha e trecho para cada ocorrencia.")
        .inputSchema(
            """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "Termo a buscar no repositorio"}
              },
              "required": ["query"]
            }
            """)
        .build();
  }

  @Override
  public String call(String toolInput) {
    SearchInput input;
    try {
      input = objectMapper.readValue(toolInput, SearchInput.class);
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
      List<Match> matches = search(input.query());
      return objectMapper.writeValueAsString(Map.of("results", matches, "count", matches.size()));
    } catch (Exception e) {
      return error("search_failed", "falha na busca: " + e.getClass().getSimpleName());
    }
  }

  List<Match> search(String query) {
    String needle = query.toLowerCase();
    List<Match> matches = new ArrayList<>();
    Path root = policy.root();
    if (!Files.isDirectory(root)) {
      return matches;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .filter(this::isTextFile)
          .filter(this::notIgnoredDir)
          .sorted(Comparator.comparing(Path::toString))
          .forEach(
              path -> {
                if (matches.size() >= MAX_RESULTS) {
                  return;
                }
                searchInFile(path, needle, matches);
              });
    } catch (IOException e) {
      throw new IllegalStateException("falha ao percorrer repositorio", e);
    }
    return matches;
  }

  private void searchInFile(Path path, String needle, List<Match> matches) {
    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
      var lineNumber = new int[] {0};
      lines.forEach(
          line -> {
            lineNumber[0]++;
            if (matches.size() >= MAX_RESULTS || !line.toLowerCase().contains(needle)) {
              return;
            }
            matches.add(new Match(relative(path), lineNumber[0], snippet(line)));
          });
    } catch (IOException e) {
      // arquivo ilegivel: ignora e segue
    }
  }

  private String relative(Path path) {
    Path root = policy.root();
    return root.relativize(path).toString().replace('\\', '/');
  }

  private String snippet(String line) {
    String trimmed = line.trim();
    return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
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

  String error(String code, String message) {
    try {
      return objectMapper.writeValueAsString(Map.of("error", code, "message", message));
    } catch (JsonProcessingException e) {
      return "{\"error\":\"" + code + "\",\"message\":\"tool error\"}";
    }
  }

  record SearchInput(String query) {}

  record Match(String file, int line, String snippet) {}
}
