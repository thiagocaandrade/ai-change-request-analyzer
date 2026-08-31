package com.ai.change.request.analyzer.ai;

/** Etapas cognitivas da analise com prompt versionado proprio. */
public enum AnalysisStage {
  CLASSIFICATION("classification"),
  IMPACT_ANALYSIS("impact-analysis"),
  RISK_ANALYSIS("risk-analysis"),
  TEST_GENERATION("test-generation"),
  SECURITY_ANALYSIS("security-analysis"),
  CODE_REVIEW("code-review"),
  LOG_ANALYSIS("log-analysis");

  private final String id;

  AnalysisStage(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
