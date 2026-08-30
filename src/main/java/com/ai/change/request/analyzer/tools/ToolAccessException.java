package com.ai.change.request.analyzer.tools;

/** Erro estruturado de acesso a arquivos, com codigo estavel para a resposta da tool. */
public class ToolAccessException extends RuntimeException {

  private final String code;

  public ToolAccessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
