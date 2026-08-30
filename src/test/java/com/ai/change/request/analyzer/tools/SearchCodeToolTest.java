package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchCodeToolTest {

  @TempDir Path root;

  private SearchCodeTool tool;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    Files.createDirectories(root.resolve("src/main/java"));
    Files.writeString(
        root.resolve("src/main/java/DiscountService.java"),
        "public class DiscountService {\n  double vipRate = 0.10; // desconto VIP\n}\n");
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());
    objectMapper = new ObjectMapper();
    tool = new SearchCodeTool(policy, objectMapper);
  }

  @Test
  void findsMatchesWithFileLineAndSnippet() throws Exception {
    String result = tool.call("{\"query\":\"desconto\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.has("error")).isFalse();
    assertThat(node.get("count").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode first = node.get("results").get(0);
    assertThat(first.get("file").asText()).contains("DiscountService.java");
    assertThat(first.get("line").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(first.get("snippet").asText()).contains("desconto");
  }

  @Test
  void rejectsEmptyQuery() throws Exception {
    String result = tool.call("{\"query\":\"\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void rejectsOversizedQuery() throws Exception {
    String result = tool.call("{\"query\":\"" + "a".repeat(201) + "\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void rejectsMalformedInput() throws Exception {
    String result = tool.call("nao-e-json");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void noMatchReturnsEmptyResults() throws Exception {
    String result = tool.call("{\"query\":\"inexistente-xyz\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("results").isEmpty()).isTrue();
    assertThat(node.get("count").asInt()).isZero();
  }

  @Test
  void exposesToolDefinitionWithSchema() {
    assertThat(tool.getToolDefinition().name()).isEqualTo("search_code");
    assertThat(tool.getToolDefinition().inputSchema()).contains("query");
  }
}
