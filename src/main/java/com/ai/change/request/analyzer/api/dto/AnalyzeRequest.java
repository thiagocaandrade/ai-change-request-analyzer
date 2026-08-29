package com.ai.change.request.analyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalyzeRequest(@JsonProperty("request_id") String requestId, String text) {}
