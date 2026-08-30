package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetRelatedTestsToolTest {

  @TempDir Path root;

  private GetRelatedTestsTool tool;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    Files.createDirectories(root.resolve("src/test/java"));
    Files.writeString(
        root.resolve("src/test/java/DiscountServiceTest.java"),
        "class DiscountServiceTest { // cobre desconto vip }\n");
    Files.writeString(
        root.resolve("src/test/java/UnrelatedTest.java"),
        "class UnrelatedTest { // cobre frete }\n");
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());
    objectMapper = new ObjectMapper();
    tool = new GetRelatedTestsTool(policy, objectMapper);
  }

  @Test
  void findsTestsByComponentName() throws Exception {
    String result = tool.call("{\"component\":\"discount\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.has("error")).isFalse();
    assertThat(node.get("count").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(node.get("results").get(0).get("file").asText())
        .contains("DiscountServiceTest.java");
  }

  @Test
  void findsTestsByContentMatch() throws Exception {
    String result = tool.call("{\"component\":\"vip\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("count").asInt()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void noMatchReturnsEmptyList() throws Exception {
    String result = tool.call("{\"component\":\"componente-inexistente\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("results").isEmpty()).isTrue();
    assertThat(node.get("count").asInt()).isZero();
  }

  @Test
  void rejectsEmptyComponent() throws Exception {
    String result = tool.call("{\"component\":\"\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void exposesToolDefinitionWithSchema() {
    assertThat(tool.getToolDefinition().name()).isEqualTo("get_related_tests");
    assertThat(tool.getToolDefinition().inputSchema()).contains("component");
  }
}
