package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.domain.InvalidConfidenceException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

  @ExceptionHandler(AnalysisNotFoundException.class)
  ResponseEntity<ErrorResponse> handleAnalysisNotFound(AnalysisNotFoundException e) {
    return build(HttpStatus.NOT_FOUND, "analysis_not_found", e.getMessage());
  }

  @ExceptionHandler(ApprovalConflictException.class)
  ResponseEntity<ErrorResponse> handleApprovalConflict(ApprovalConflictException e) {
    return build(HttpStatus.CONFLICT, "approval_conflict", e.getMessage());
  }

  @ExceptionHandler(TraceNotFoundException.class)
  ResponseEntity<ErrorResponse> handleTraceNotFound(TraceNotFoundException e) {
    return build(HttpStatus.NOT_FOUND, "trace_not_found", e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .orElse("payload invalido");
    return build(HttpStatus.BAD_REQUEST, "invalid_request", detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
    return build(HttpStatus.BAD_REQUEST, "invalid_request", "payload invalido");
  }

  @ExceptionHandler(InvalidConfidenceException.class)
  ResponseEntity<ErrorResponse> handleInvalidConfidence(InvalidConfidenceException e) {
    return build(HttpStatus.BAD_REQUEST, "invalid_confidence", e.getMessage());
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

  public static class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(UUID id) {
      super("analise nao encontrada para a solicitacao: " + id);
    }
  }

  public static class ApprovalConflictException extends RuntimeException {
    public ApprovalConflictException(UUID id) {
      super("aprovacao nao esta PENDING ou nao e exigida para a solicitacao: " + id);
    }
  }

  public static class TraceNotFoundException extends RuntimeException {
    public TraceNotFoundException(String traceId) {
      super("nenhum evento registrado para o trace: " + traceId);
    }
  }
}
