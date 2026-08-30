package com.ai.change.request.analyzer.tools;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registro unico das 4 tools, todas embrulhadas com timeout/retry/logs de trace_id. O ChatClient e
 * o servidor MCP usam estes mesmos callbacks.
 */
@Component
public class ToolRegistry {

  private final List<ToolCallback> callbacks;

  public ToolRegistry(
      SearchCodeTool searchCodeTool,
      GetFileTool getFileTool,
      SearchChangeHistoryTool searchChangeHistoryTool,
      GetRelatedTestsTool getRelatedTestsTool,
      @Value("${tools.timeout-ms:5000}") long timeoutMs) {
    this.callbacks =
        List.of(
            resilient(searchCodeTool, timeoutMs),
            resilient(getFileTool, timeoutMs),
            resilient(searchChangeHistoryTool, timeoutMs),
            resilient(getRelatedTestsTool, timeoutMs));
  }

  public List<ToolCallback> callbacks() {
    return callbacks;
  }

  public ToolCallback byName(String name) {
    return callbacks.stream()
        .filter(callback -> callback.getToolDefinition().name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("tool desconhecida: " + name));
  }

  private static ToolCallback resilient(AnalysisTool tool, long timeoutMs) {
    return new ResilientToolCallback(adapt(tool), timeoutMs);
  }

  private static ToolCallback adapt(AnalysisTool tool) {
    return new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return tool.getToolDefinition();
      }

      @Override
      public String call(String toolInput) {
        return tool.call(toolInput);
      }
    };
  }
}
