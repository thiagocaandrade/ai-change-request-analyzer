package com.ai.change.request.analyzer.devops;

import com.ai.change.request.analyzer.devops.AnomalyService.AnomalyResult;
import com.ai.change.request.analyzer.devops.AnomalyService.TrendResult;
import com.ai.change.request.analyzer.observability.TraceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Registro de execucoes de pipeline com deteccao deterministica de anomalia e tendencia de falha,
 * trace events correlacionados por trace_id e persistencia dos eventos.
 */
@Service
public class PipelineRunService {

  private static final Logger log = LoggerFactory.getLogger(PipelineRunService.class);

  public static final String METRIC_DURATION = "duration_ms";
  public static final String NODE_ANOMALY_CHECK = "anomaly_check";
  public static final String NODE_FAILURE_TREND = "failure_trend";

  public record RunReport(UUID runId, String traceId, AnomalyResult anomaly, TrendResult trend) {}

  private final PipelineRunRepository runRepository;
  private final AnomalyEventRepository anomalyEventRepository;
  private final AnomalyService anomalyService;
  private final TraceService traceService;

  public PipelineRunService(
      PipelineRunRepository runRepository,
      AnomalyEventRepository anomalyEventRepository,
      AnomalyService anomalyService,
      TraceService traceService) {
    this.runRepository = runRepository;
    this.anomalyEventRepository = anomalyEventRepository;
    this.anomalyService = anomalyService;
    this.traceService = traceService;
  }

  /** Registra a execucao e retorna o relatorio de anomalia/tendencia com trace events ordenados. */
  public RunReport register(long durationMs, boolean success) {
    String traceId = MDC.get("trace_id");
    List<PipelineRun> history = runRepository.findTop100ByOrderByCreatedAtAsc();
    List<Double> durations = history.stream().mapToDouble(PipelineRun::getDurationMs).boxed().toList();

    AnomalyResult anomaly = anomalyService.analyze(durations, (double) durationMs);
    traceService.record(
        NODE_ANOMALY_CHECK,
        anomaly.anomaly() ? "anomaly_detected" : "no_anomaly",
        null,
        anomaly.anomaly() ? "detected" : "normal",
        null,
        null,
        null,
        null,
        anomaly.anomaly()
            ? "metric="
                + METRIC_DURATION
                + " severity="
                + anomaly.severity()
                + " baseline="
                + anomaly.baseline()
                + " observed="
                + anomaly.observed()
            : null);
    if (anomaly.anomaly()) {
      log.warn(
          "anomaly_detected metric={} baseline={} observed={} deviation={} severity={} trace_id={}",
          METRIC_DURATION,
          anomaly.baseline(),
          anomaly.observed(),
          anomaly.deviation(),
          anomaly.severity(),
          traceId);
      try {
        anomalyEventRepository.save(
            new AnomalyEvent(
                traceId,
                METRIC_DURATION,
                anomaly.baseline(),
                anomaly.observed(),
                anomaly.deviation(),
                anomaly.severity(),
                Instant.now()));
      } catch (Exception e) {
        log.warn(
            "anomaly_event_persist_failed error={} trace_id={}",
            e.getClass().getSimpleName(),
            traceId);
      }
    }

    List<Boolean> results =
        history.stream().map(PipelineRun::isSuccess).collect(java.util.stream.Collectors.toList());
    results.add(success);
    TrendResult trend = anomalyService.failureTrend(results);
    traceService.record(
        NODE_FAILURE_TREND,
        trend.trend() ? "trend_detected" : "no_trend",
        null,
        trend.trend() ? "increasing" : "stable",
        null,
        null,
        null,
        null,
        trend.trend() ? "failure_rate=" + trend.failureRate() : null);
    if (trend.trend()) {
      log.warn(
          "failure_trend_detected failure_rate={} window_size={} trace_id={}",
          trend.failureRate(),
          trend.windowSize(),
          traceId);
    }

    PipelineRun run = new PipelineRun(durationMs, success, traceId, Instant.now());
    UUID runId = runRepository.save(run).getId();
    return new RunReport(runId, traceId, anomaly, trend);
  }
}
