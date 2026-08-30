package com.ai.change.request.analyzer.mcp;

import com.ai.change.request.analyzer.tools.GetFileTool;
import com.ai.change.request.analyzer.tools.SearchCodeTool;
import com.ai.change.request.analyzer.tools.ToolRegistry;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servidor MCP (Spring AI) expondo {@code search_code} e {@code get_file} com os mesmos
 * ToolCallbacks registrados no ChatClient — mesmas validacoes e protecoes de path.
 */
@Configuration
public class McpToolConfig {

  @Bean
  ToolCallbackProvider agentMcpTools(ToolRegistry toolRegistry) {
    return ToolCallbackProvider.from(
        toolRegistry.byName(SearchCodeTool.NAME), toolRegistry.byName(GetFileTool.NAME));
  }
}
