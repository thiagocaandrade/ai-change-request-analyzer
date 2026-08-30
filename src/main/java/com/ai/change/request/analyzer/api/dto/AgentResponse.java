package com.ai.change.request.analyzer.api.dto;

import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaBlockDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AgentResponse(
    @JsonProperty("request_id") String requestId,
    String status,
    Map<String, Object> result,
    @JsonProperty("qa") QaBlockDto qa) {

  public AgentResponse(String requestId, String status, Map<String, Object> result) {
    this(requestId, status, result, null);
  }
}
