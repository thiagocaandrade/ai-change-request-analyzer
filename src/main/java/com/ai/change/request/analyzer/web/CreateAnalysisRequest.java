package com.ai.change.request.analyzer.web;

import com.ai.change.request.analyzer.domain.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateAnalysisRequest(
    @NotNull List<@Valid FindingDto> findings,
    @NotNull @Valid RiskDto riskAssessment,
    @NotNull List<@Valid RecommendationDto> testRecommendations) {

  public record FindingDto(
      @NotBlank @Size(max = 200) String component,
      @NotBlank @Size(max = 4000) String description,
      @Size(max = 16) String severity) {}

  public record RiskDto(
      @NotNull RiskLevel level,
      @NotNull Double confidence,
      @Size(max = 4000) String rationale) {}

  public record RecommendationDto(
      @NotBlank @Size(max = 200) String component,
      @NotBlank @Size(max = 4000) String description,
      @Size(max = 16) String priority) {}
}
