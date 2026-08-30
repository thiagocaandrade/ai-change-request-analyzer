package com.ai.change.request.analyzer.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Servidor MCP da aplicacao: tools listadas e mesmas protecoes das implementacoes internas. */
@SpringBootTest
@ActiveProfiles("test")
class McpToolsTest {

  @Autowired private McpSyncServer mcpSyncServer;

  @Autowired private ToolCallbackProvider agentMcpTools;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void mcpServerListsSearchCodeAndGetFile() {
    List<String> names = mcpSyncServer.listTools().stream().map(tool -> tool.name()).toList();

    assertThat(names).containsExactlyInAnyOrder("search_code", "get_file");
  }

  @Test
  void mcpToolsAreTheSameCallbacksWithPathProtections() throws Exception {
    var callbacks = agentMcpTools.getToolCallbacks();
    var getFile =
        List.of(callbacks).stream()
            .filter(callback -> callback.getToolDefinition().name().equals("get_file"))
            .findFirst()
            .orElseThrow();

    String result = getFile.call("{\"path\":\"../fora-do-repo.txt\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("path_traversal_rejected");
  }

  @Test
  void mcpSearchCodeValidatesInput() throws Exception {
    var callbacks = agentMcpTools.getToolCallbacks();
    var searchCode =
        List.of(callbacks).stream()
            .filter(callback -> callback.getToolDefinition().name().equals("search_code"))
            .findFirst()
            .orElseThrow();

    String result = searchCode.call("{\"query\":\"\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }
}
