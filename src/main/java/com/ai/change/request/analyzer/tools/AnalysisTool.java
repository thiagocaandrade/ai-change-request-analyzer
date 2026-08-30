package com.ai.change.request.analyzer.tools;

import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Contrato local das tools da aplicacao. O registro no ChatClient e no MCP usa os mesmos callbacks
 * (Adaptados + ResilientToolCallback), nunca os beans crus.
 */
public interface AnalysisTool {

  ToolDefinition getToolDefinition();

  String call(String toolInput);
}
