package com.ai.change.request.analyzer.devops;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.devops.AnomalyService.AnomalyResult;
import com.ai.change.request.analyzer.devops.AnomalyService.TrendResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnomalyServiceTest {

  private final AnomalyService service = new AnomalyService(5, 0.5, 0.2);

  @Test
  void significantDeviationIsHigh() {
    AnomalyResult result = service.analyze(List.of(400.0, 400.0, 400.0, 400.0, 400.0), 2800.0);

    assertThat(result.anomaly()).isTrue();
    assertThat(result.baseline()).isEqualTo(400.0);
    assertThat(result.observed()).isEqualTo(2800.0);
    assertThat(result.deviation()).isEqualTo(6.0);
    assertThat(result.severity()).isEqualTo("HIGH");
  }

  @Test
  void deviationBelowThresholdIsNotAnomaly() {
    AnomalyResult result = service.analyze(List.of(400.0, 400.0, 400.0, 400.0, 400.0), 450.0);

    assertThat(result.anomaly()).isFalse();
    assertThat(result.severity()).isNull();
  }

  @Test
  void boundariesAreMediumAtMediumThresholdAndHighAtHighThreshold() {
    AnomalyResult medium = service.analyze(List.of(400.0, 400.0, 400.0, 400.0, 400.0), 480.0);
    assertThat(medium.deviation()).isEqualTo(0.2);
    assertThat(medium.severity()).isEqualTo("MEDIUM");

    AnomalyResult high = service.analyze(List.of(400.0, 400.0, 400.0, 400.0, 400.0), 600.0);
    assertThat(high.deviation()).isEqualTo(0.5);
    assertThat(high.severity()).isEqualTo("HIGH");
  }

  @Test
  void baselineUsesOnlyLastFiveObservations() {
    AnomalyResult result =
        service.analyze(List.of(100.0, 400.0, 400.0, 400.0, 400.0, 400.0), 2800.0);

    assertThat(result.baseline()).isEqualTo(400.0);
    assertThat(result.severity()).isEqualTo("HIGH");
  }

  @Test
  void emptyHistoryHasNoAnomaly() {
    AnomalyResult result = service.analyze(List.of(), 2800.0);

    assertThat(result.anomaly()).isFalse();
    assertThat(result.baseline()).isEqualTo(2800.0);
  }

  @Test
  void sameInputProducesSameOutput() {
    List<Double> history = List.of(400.0, 410.0, 390.0, 400.0, 405.0);

    AnomalyResult first = service.analyze(history, 2800.0);
    AnomalyResult second = service.analyze(history, 2800.0);

    assertThat(second).isEqualTo(first);
  }

  @Test
  void increasingFailureRateRegistersTrend() {
    TrendResult result = service.failureTrend(List.of(true, true, false, false, false));

    assertThat(result.trend()).isTrue();
    assertThat(result.windowSize()).isEqualTo(5);
    assertThat(result.failureRate()).isEqualTo(0.6);
    assertThat(result.rates()).containsExactly(0.0, 1.0);
  }

  @Test
  void decreasingFailureRateHasNoTrend() {
    TrendResult result = service.failureTrend(List.of(false, false, true, true, true));

    assertThat(result.trend()).isFalse();
    assertThat(result.failureRate()).isEqualTo(0.4);
    assertThat(result.rates()).containsExactly(1.0, 0.0);
  }

  @Test
  void nonMonotonicFailureRateHasNoTrend() {
    TrendResult result = service.failureTrend(List.of(true, false, true, false, true));

    assertThat(result.trend()).isFalse();
    assertThat(result.rates()).containsExactly(0.5, 0.5);
  }

  @Test
  void fewerThanTwoRunsHaveNoTrend() {
    TrendResult result = service.failureTrend(List.of(false));

    assertThat(result.trend()).isFalse();
  }

  @Test
  void trendUsesOnlyLastFiveRuns() {
    TrendResult result =
        service.failureTrend(
            List.of(false, false, false, false, false, true, true, false, false, false));

    assertThat(result.windowSize()).isEqualTo(5);
    assertThat(result.trend()).isTrue();
  }
}
