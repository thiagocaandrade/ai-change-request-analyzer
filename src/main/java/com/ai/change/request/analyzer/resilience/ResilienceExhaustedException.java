package com.ai.change.request.analyzer.resilience;

/** Falha estruturada de integracao apos o limite de tentativas; nunca simula sucesso. */
public class ResilienceExhaustedException extends RuntimeException {

  public ResilienceExhaustedException(String node, String event, Throwable cause) {
    super("integracao esgotada apos tentativas: " + node + "/" + event, cause);
  }
}
