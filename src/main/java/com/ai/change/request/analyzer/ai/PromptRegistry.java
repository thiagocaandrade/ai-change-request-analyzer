package com.ai.change.request.analyzer.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Carrega prompts versionados de {@code resources/prompts/<etapa>-v<N>.txt}.
 *
 * <p>Formato do arquivo: secoes {@code [SYSTEM]} e {@code [USER]} com os placeholders {@code
 * {change_text}}, {@code {evidence}} e {@code {format}}. A evidencia recuperada e renderizada
 * apenas na secao de usuario, nunca no system prompt.
 */
@Component
public class PromptRegistry {

  public record PromptTemplate(String systemTemplate, String userTemplate) {

    public String renderSystem(Map<String, String> variables) {
      return render(systemTemplate, variables);
    }

    public String renderUser(Map<String, String> variables) {
      return render(userTemplate, variables);
    }

    private static String render(String template, Map<String, String> variables) {
      String result = template;
      for (Map.Entry<String, String> entry : variables.entrySet()) {
        result =
            result.replace(
                "{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
      }
      return result;
    }
  }

  private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

  public PromptTemplate load(String stage, int version) {
    return cache.computeIfAbsent(stage + "-v" + version, PromptRegistry::doLoad);
  }

  private static PromptTemplate doLoad(String id) {
    String path = "prompts/" + id + ".txt";
    ClassPathResource resource = new ClassPathResource(path);
    if (!resource.exists()) {
      throw new IllegalArgumentException("prompt nao encontrado: " + path);
    }
    String content;
    try (InputStream input = resource.getInputStream()) {
      content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("falha ao ler prompt " + path, e);
    }
    String system = section(content, "[SYSTEM]");
    String user = section(content, "[USER]");
    if (system == null || user == null) {
      throw new IllegalStateException("prompt malformado (esperado [SYSTEM] e [USER]): " + path);
    }
    return new PromptTemplate(system, user);
  }

  private static String section(String content, String marker) {
    int start = content.indexOf(marker);
    if (start < 0) {
      return null;
    }
    start += marker.length();
    int nextMarker = content.indexOf('[', start);
    int end = nextMarker > start ? nextMarker : content.length();
    return content.substring(start, end).trim();
  }
}
