package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.config.TraceIdFilter;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/requests")
public class ChangeRequestController {

  private static final Logger log = LoggerFactory.getLogger(ChangeRequestController.class);

  private final ChangeRequestRepository repository;
  private final AgentClient agentClient;
  private final ObjectMapper objectMapper;

  public ChangeRequestController(
      ChangeRequestRepository repository, AgentClient agentClient, ObjectMapper objectMapper) {
    this.repository = repository;
    this.agentClient = agentClient;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public ResponseEntity<ChangeRequestResponse> create(
      @Valid @RequestBody CreateChangeRequestRequest body, HttpServletRequest httpRequest) {
    String traceId = (String) httpRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    ChangeRequest request = new ChangeRequest();
    request.setText(body.text());
    request.setTraceId(traceId);
    request.setStatus(ChangeRequestStatus.PENDING);
    request = repository.save(request);

    ChangeRequestResponse response;
    try {
      var agentResponse = agentClient.analyze(request.getId().toString(), body.text(), traceId);
      request.setStatus(ChangeRequestStatus.COMPLETED);
      request.setResult(writeJson(agentResponse.result()));
      response = toResponse(request);
    } catch (AgentUnavailableException e) {
      request.setStatus(ChangeRequestStatus.FAILED);
      request.setResult(writeJson(Map.of("error", "agent_unavailable", "detail", e.getMessage())));
      response = toResponse(request);
    }
    repository.save(request);
    log.info("request_persisted id={} status={}", request.getId(), request.getStatus());
    HttpStatus httpStatus =
        request.getStatus() == ChangeRequestStatus.COMPLETED
            ? HttpStatus.CREATED
            : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(httpStatus).body(response);
  }

  @GetMapping("/{id}")
  public ChangeRequestResponse get(@PathVariable UUID id) {
    ChangeRequest request =
        repository
            .findById(id)
            .orElseThrow(() -> new GlobalExceptionHandler.ChangeRequestNotFoundException(id));
    return toResponse(request);
  }

  private ChangeRequestResponse toResponse(ChangeRequest request) {
    return new ChangeRequestResponse(
        request.getId(),
        request.getText(),
        request.getStatus(),
        request.getTraceId(),
        readJson(request.getResult()));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("falha ao serializar resultado", e);
    }
  }

  private Map<String, Object> readJson(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
