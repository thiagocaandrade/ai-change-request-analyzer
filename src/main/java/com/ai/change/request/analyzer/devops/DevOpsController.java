package com.ai.change.request.analyzer.devops;

import com.ai.change.request.analyzer.devops.DevOpsDtos.AnomalyDto;
import com.ai.change.request.analyzer.devops.DevOpsDtos.FailureTrendDto;
import com.ai.change.request.analyzer.devops.DevOpsDtos.LogAnalysisRequest;
import com.ai.change.request.analyzer.devops.DevOpsDtos.LogAnalysisResponse;
import com.ai.change.request.analyzer.devops.DevOpsDtos.RunRequest;
import com.ai.change.request.analyzer.devops.DevOpsDtos.RunResponse;
import com.ai.change.request.analyzer.devops.DevOpsDtos.SecurityEventDto;
import com.ai.change.request.analyzer.devops.PipelineRunService.RunReport;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de DevOps: analise assistida de logs de pipeline e execucoes de pipeline. */
@RestController
@RequestMapping("/api/devops")
public class DevOpsController {

  private final LogAnalysisService logAnalysisService;
  private final PipelineRunService pipelineRunService;

  public DevOpsController(
      LogAnalysisService logAnalysisService, PipelineRunService pipelineRunService) {
    this.logAnalysisService = logAnalysisService;
    this.pipelineRunService = pipelineRunService;
  }

  @PostMapping("/log-analysis")
  public LogAnalysisResponse analyzeLogs(@Valid @RequestBody LogAnalysisRequest body) {
    LogAnalysisService.LogOutcome outcome = logAnalysisService.analyze(body.log());
    return new LogAnalysisResponse(
        outcome.recordId(),
        outcome.result().summary(),
        outcome.result().failedStep(),
        outcome.result().probableCause(),
        outcome.result().evidence(),
        outcome.result().recommendedAction(),
        outcome.result().confidence(),
        Boolean.TRUE.equals(outcome.result().degraded()),
        outcome.promptVersion(),
        outcome.traceId(),
        outcome.securityEvents().stream()
            .map(
                event ->
                    new SecurityEventDto(
                        event.type(), event.source(), event.evidence(), event.action()))
            .toList());
  }

  @PostMapping("/runs")
  public RunResponse registerRun(@Valid @RequestBody RunRequest body) {
    RunReport report = pipelineRunService.register(body.durationMs(), body.success());
    return new RunResponse(
        report.runId(),
        report.traceId(),
        new AnomalyDto(
            report.anomaly().anomaly(),
            report.anomaly().baseline(),
            report.anomaly().observed(),
            report.anomaly().deviation(),
            report.anomaly().severity()),
        new FailureTrendDto(
            report.trend().trend(),
            report.trend().failureRate(),
            report.trend().windowSize(),
            report.trend().rates()));
  }
}
