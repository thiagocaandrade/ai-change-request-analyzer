package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import java.util.Map;
import java.util.UUID;

public record ChangeRequestResponse(
    UUID id, String text, ChangeRequestStatus status, String traceId, Map<String, Object> result) {}
