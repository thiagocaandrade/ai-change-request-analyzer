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

  /**
   * Versao padrao do prompt versionado da etapa. A etapa de risco usa a v2 (refinada por evidencia
   * comparavel, ver docs/prompt-refinement.md); a v1 permanece carregavel para reproducao do
   * experimento.
   */
  public int defaultVersion() {
    return this == RISK_ANALYSIS ? 2 : 1;
  }
}
