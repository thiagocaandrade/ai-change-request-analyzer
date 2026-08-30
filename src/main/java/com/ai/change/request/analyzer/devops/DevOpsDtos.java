package com.ai.change.request.analyzer.devops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** DTOs dos endpoints de DevOps (analise de logs e execucoes de pipeline). */
public final class DevOpsDtos {

  private DevOpsDtos() {}

  public record LogAnalysisRequest(@NotBlank @Size(max = 50000) String log) {}

  public record SecurityEventDto(String type, String source, String evidence, String action) {}

  public record LogAnalysisResponse(
      UUID recordId,
      String summary,
      String failedStep,
      String probableCause,
      String evidence,
      String recommendedAction,
      double confidence,
      boolean degraded,
      String promptVersion,
      String traceId,
      List<SecurityEventDto> securityEvents) {}

  public record RunRequest(@NotNull Long durationMs, @NotNull Boolean success) {}

  public record AnomalyDto(
      boolean detected, double baseline, double observed, double deviation, String severity) {}

  public record FailureTrendDto(
      boolean detected, double failureRate, int windowSize, List<Double> rates) {}

  public record RunResponse(
      UUID runId, String traceId, AnomalyDto anomaly, FailureTrendDto failureTrend) {}
}
