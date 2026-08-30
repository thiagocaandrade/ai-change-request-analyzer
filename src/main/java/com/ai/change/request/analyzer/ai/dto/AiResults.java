package com.ai.change.request.analyzer.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Saidas estruturadas tipadas das etapas cognitivas. {@code degraded} marca o fallback
 * deterministico.
 */
public final class AiResults {

  private AiResults() {}

  public record ClassificationResult(
      @NotBlank @Size(max = 64) String category, @Size(max = 500) String notes, Boolean degraded) {}

  public record ImpactFindingDto(
      @NotBlank @Size(max = 200) String component,
      @NotBlank String description,
      @Size(max = 16) String severity) {}

  public record ImpactAnalysisResult(
      @NotNull List<@Valid ImpactFindingDto> findings, Boolean degraded) {}

  public record RiskAnalysisResult(
      @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String level,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
      @NotBlank String rationale,
      Boolean degraded) {}

  public record TestRecommendationDto(
      @NotBlank @Size(max = 200) String component,
      @NotBlank String description,
      @Size(max = 16) String priority) {}

  public record TestPlanResult(
      @NotNull @NotEmpty List<@Valid TestRecommendationDto> recommendations, Boolean degraded) {}

  public record SecurityFindingDto(
      @NotBlank @Size(max = 32) String type, @NotBlank @Size(max = 1000) String evidence) {}

  public record SecurityAnalysisResult(
      @NotNull List<@Valid SecurityFindingDto> findings, Boolean degraded) {}

  public record CodeReviewFindingDto(
      @NotBlank @Size(max = 200) String component,
      @NotBlank @Size(max = 4000) String description,
      @Size(max = 16) String severity,
      @Size(max = 200) String source) {}

  public record RiskCategorySuggestionDto(
      @NotBlank @Size(max = 64) String category,
      @Size(max = 16) String impact,
      @Size(max = 16) String likelihood) {}

  public record CodeReviewResult(
      @NotNull @Size(max = 8) List<@Valid CodeReviewFindingDto> findings,
      @NotNull List<@Valid RiskCategorySuggestionDto> riskCategories,
      Boolean degraded) {}
}
