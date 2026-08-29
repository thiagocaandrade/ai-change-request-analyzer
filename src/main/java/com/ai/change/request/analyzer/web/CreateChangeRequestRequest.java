package com.ai.change.request.analyzer.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChangeRequestRequest(@NotBlank @Size(max = 4000) String text) {}
