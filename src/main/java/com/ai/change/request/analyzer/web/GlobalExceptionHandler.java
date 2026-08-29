package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.api.AgentUnavailableException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public record ErrorResponse(String error, String detail) {}

  @ExceptionHandler(ChangeRequestNotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(ChangeRequestNotFoundException e) {
    return build(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    return build(
        HttpStatus.BAD_REQUEST,
        "invalid_request",
        "texto e obrigatorio e deve ter ate 4000 caracteres");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return build(HttpStatus.BAD_REQUEST, "invalid_request", "identificador invalido");
  }

  @ExceptionHandler(AgentUnavailableException.class)
  ResponseEntity<ErrorResponse> handleAgentUnavailable(AgentUnavailableException e) {
    return build(HttpStatus.SERVICE_UNAVAILABLE, "agent_unavailable", e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("unexpected_error", e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "erro interno inesperado");
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String detail) {
    return ResponseEntity.status(status).body(new ErrorResponse(error, detail));
  }

  public static class ChangeRequestNotFoundException extends RuntimeException {
    public ChangeRequestNotFoundException(UUID id) {
      super("solicitacao nao encontrada: " + id);
    }
  }
}
