package com.ai.change.request.analyzer.devops;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Deteccao deterministica (sem LLM) de anomalia em metricas de execucao e de tendencia de falha em
 * execucoes de pipeline.
 *
 * <ul>
 *   <li><b>Anomalia por desvio:</b> baseline = media movel simples das ultimas N observacoes (N
 *       configuravel, default 5); desvio relativo = |obs - baseline| / baseline; severidade por
 *       limiares configuraveis (>= high: HIGH; >= medium: MEDIUM; abaixo: normal, sem anomalia).
 *   <li><b>Tendencia de falha:</b> taxa de falha em janela movel de 5 execucoes; quando a taxa da
 *       metade mais recente da janela supera a da metade mais antiga, a tendencia e registrada.
 * </ul>
 *
 * <p>Mesma entrada produz sempre a mesma saida (nenhum estado aleatorio, nenhum modelo de IA).
 */
@Service
public class AnomalyService {

  public record AnomalyResult(
      double baseline, double observed, double deviation, String severity, boolean anomaly) {}

  public record TrendResult(
      boolean trend, double failureRate, int windowSize, List<Double> rates) {}

  private final int windowSize;
  private final double highThreshold;
  private final double mediumThreshold;

  public AnomalyService(
      @Value("${devops.anomaly.window-size:5}") int windowSize,
      @Value("${devops.anomaly.high-threshold:0.5}") double highThreshold,
      @Value("${devops.anomaly.medium-threshold:0.2}") double mediumThreshold) {
    this.windowSize = windowSize;
    this.highThreshold = highThreshold;
    this.mediumThreshold = mediumThreshold;
  }

  /** Calcula desvio e severidade da observacao contra o baseline das observacoes anteriores. */
  public AnomalyResult analyze(List<Double> history, double observation) {
    List<Double> window = tail(history, windowSize);
    if (window.isEmpty()) {
      return new AnomalyResult(observation, observation, 0.0, null, false);
    }
    double baseline = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double deviation;
    if (baseline > 0) {
      deviation = Math.abs(observation - baseline) / baseline;
    } else {
      deviation = observation > 0 ? highThreshold : 0.0;
    }
    String severity = severityFor(deviation);
    return new AnomalyResult(baseline, observation, deviation, severity, severity != null);
  }

  /**
   * Calcula a tendencia de falha sobre as ultimas execucoes (maximo windowSize): tendencia
   * registrada quando a taxa de falha da metade mais recente da janela supera a da metade mais
   * antiga (a execucao do meio e descartada quando a janela e impar).
   */
  public TrendResult failureTrend(List<Boolean> results) {
    List<Boolean> window = tail(results, windowSize);
    if (window.size() < 2) {
      return new TrendResult(false, 0.0, window.size(), List.of());
    }
    int half = window.size() / 2;
    List<Boolean> older = window.subList(0, half);
    List<Boolean> recent = window.subList(window.size() - half, window.size());
    double olderRate = rate(older);
    double recentRate = rate(recent);
    double totalRate = rate(window);
    return new TrendResult(
        recentRate > olderRate, totalRate, window.size(), List.of(olderRate, recentRate));
  }

  private static double rate(List<Boolean> runs) {
    long failures = runs.stream().filter(success -> !Boolean.TRUE.equals(success)).count();
    return failures / (double) runs.size();
  }

  private String severityFor(double deviation) {
    if (deviation >= highThreshold) {
      return "HIGH";
    }
    if (deviation >= mediumThreshold) {
      return "MEDIUM";
    }
    return null;
  }

  private static <T> List<T> tail(List<T> values, int size) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    int from = Math.max(0, values.size() - size);
    return List.copyOf(values.subList(from, values.size()));
  }
}
