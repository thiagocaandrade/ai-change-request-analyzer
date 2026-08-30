package com.ai.change.request.analyzer.tools;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Politica de acesso ao repositorio: toda leitura resolve para dentro da raiz configurada. Rejeita
 * path traversal, caminhos absolutos e entradas vazias.
 */
@Component
public class RepoAccessPolicy {

  public static final String ERROR_PATH_TRAVERSAL = "path_traversal_rejected";
  public static final String ERROR_OUTSIDE_ROOT = "path_outside_root_rejected";
  public static final String ERROR_EMPTY_PATH = "empty_path_rejected";

  private final Path root;

  public RepoAccessPolicy(@Value("${tools.repo-root:}") String configuredRoot) {
    if (configuredRoot == null || configuredRoot.isBlank()) {
      this.root = Path.of("").toAbsolutePath().normalize();
    } else {
      this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }
  }

  public Path root() {
    return root;
  }

  public Path resolveInside(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new ToolAccessException(ERROR_EMPTY_PATH, "caminho vazio rejeitado");
    }
    String sanitized = rawPath.replace('\\', '/');
    Path candidate = Path.of(rawPath);
    if (sanitized.startsWith("/") || candidate.isAbsolute()) {
      throw new ToolAccessException(ERROR_OUTSIDE_ROOT, "caminho absoluto rejeitado: " + rawPath);
    }
    Path resolved = root.resolve(sanitized).normalize();
    if (!resolved.startsWith(root)) {
      throw new ToolAccessException(ERROR_PATH_TRAVERSAL, "path traversal rejeitado: " + rawPath);
    }
    return resolved;
  }
}
