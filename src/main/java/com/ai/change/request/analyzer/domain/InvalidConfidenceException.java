package com.ai.change.request.analyzer.domain;

public class InvalidConfidenceException extends RuntimeException {

  public InvalidConfidenceException(Double confidence) {
    super("confidence fora do intervalo [0,1]: " + confidence);
  }
}
