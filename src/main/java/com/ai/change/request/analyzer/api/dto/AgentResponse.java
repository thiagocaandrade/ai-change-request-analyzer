package com.ai.change.request.analyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AgentResponse(
    @JsonProperty("request_id") String requestId, String status, Map<String, Object> result) {}
