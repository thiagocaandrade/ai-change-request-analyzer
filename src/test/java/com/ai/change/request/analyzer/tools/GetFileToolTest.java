package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GetFileToolTest {

  @TempDir Path root;

  private GetFileTool tool;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    Files.writeString(root.resolve("README.md"), "conteudo do arquivo\nsegunda linha\n");
    RepoAccessPolicy policy = new RepoAccessPolicy(root.toString());
    objectMapper = new ObjectMapper();
    tool = new GetFileTool(policy, objectMapper, 102400);
  }

  @Test
  void readsFileInsideRoot() throws Exception {
    String result = tool.call("{\"path\":\"README.md\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.has("error")).isFalse();
    assertThat(node.get("path").asText()).isEqualTo("README.md");
    assertThat(node.get("content").asText()).contains("conteudo do arquivo");
  }

  @Test
  void rejectsPathTraversalStructured() throws Exception {
    String result = tool.call("{\"path\":\"../secrets.txt\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("path_traversal_rejected");
    assertThat(node.get("message").asText()).contains("rejeitado");
  }

  @Test
  void rejectsAbsolutePathOutsideRootStructured() throws Exception {
    String result = tool.call("{\"path\":\"/etc/passwd\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("path_outside_root_rejected");
  }

  @Test
  void rejectsNonexistentFileStructured() throws Exception {
    String result = tool.call("{\"path\":\"nao-existe.txt\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("file_not_found");
  }

  @Test
  void rejectsEmptyPathStructured() throws Exception {
    String result = tool.call("{\"path\":\"\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void exposesToolDefinitionWithSchema() {
    assertThat(tool.getToolDefinition().name()).isEqualTo("get_file");
    assertThat(tool.getToolDefinition().inputSchema()).contains("path");
  }
}
