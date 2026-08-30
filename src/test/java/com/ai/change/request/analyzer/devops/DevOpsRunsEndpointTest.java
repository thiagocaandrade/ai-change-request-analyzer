package com.ai.change.request.analyzer.devops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoint de execucoes de pipeline: anomalia, tendencia de falha e trace events correlacionados. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevOpsRunsEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private AnomalyEventRepository anomalyEventRepository;

  @Autowired private TraceService traceService;

  @Test
  void anomalyDetectedAgainstBaselineAndEventPersistedWithTraceId() throws Exception {
    for (int i = 0; i < 5; i++) {
      postRun(400, true);
    }

    JsonNode report = postRun(2800, true);

    JsonNode anomaly = report.get("anomaly");
    assertThat(anomaly.get("detected").asBoolean()).isTrue();
    assertThat(anomaly.get("baseline").asDouble()).isEqualTo(400.0);
    assertThat(anomaly.get("observed").asDouble()).isEqualTo(2800.0);
    assertThat(anomaly.get("deviation").asDouble()).isEqualTo(6.0);
    assertThat(anomaly.get("severity").asText()).isEqualTo("HIGH");

    String traceId = report.get("traceId").asText();
    List<AnomalyEvent> events = anomalyEventRepository.findByTraceIdOrderByCreatedAtAsc(traceId);
    assertThat(events).hasSize(1);
    AnomalyEvent event = events.get(0);
    assertThat(event.getMetric()).isEqualTo("duration_ms");
    assertThat(event.getBaseline()).isEqualTo(400.0);
    assertThat(event.getObserved()).isEqualTo(2800.0);
    assertThat(event.getDeviation()).isEqualTo(6.0);
    assertThat(event.getSeverity()).isEqualTo("HIGH");
    assertThat(event.getTraceId()).isEqualTo(traceId);
  }

  @Test
  void increasingFailureRateDetectsTrend() throws Exception {
    postRun(100, true);
    postRun(100, true);
    postRun(100, false);
    postRun(100, false);
    JsonNode report = postRun(100, false);

    JsonNode trend = report.get("failureTrend");
    assertThat(trend.get("detected").asBoolean()).isTrue();
    assertThat(trend.get("failureRate").asDouble()).isEqualTo(0.6);
    assertThat(trend.get("windowSize").asInt()).isEqualTo(5);
  }

  @Test
  void decreasingFailureRateHasNoTrend() throws Exception {
    postRun(100, false);
    postRun(100, false);
    postRun(100, true);
    postRun(100, true);
    JsonNode report = postRun(100, true);

    JsonNode trend = report.get("failureTrend");
    assertThat(trend.get("detected").asBoolean()).isFalse();
    assertThat(trend.get("failureRate").asDouble()).isEqualTo(0.4);
  }

  @Test
  void traceEventsAreChronologicalAndCorrelatedByTraceId() throws Exception {
    for (int i = 0; i < 5; i++) {
      postRun(400, true);
    }
    JsonNode report = postRun(2800, true);
    String traceId = report.get("traceId").asText();

    List<TraceEvent> events = traceService.findByTraceId(traceId);
    List<String> nodeSequence = events.stream().map(TraceEvent::getNode).toList();

    assertThat(nodeSequence).containsExactly("anomaly_check", "failure_trend");
    assertThat(events.get(0).getEvent()).isEqualTo("anomaly_detected");
    assertThat(events.get(0).getStatus()).isEqualTo("detected");
    assertThat(events.get(0).getDetail()).contains("severity=HIGH", "baseline=400.0", "observed=2800.0");
    assertThat(events.get(1).getEvent()).isEqualTo("no_trend");
    assertThat(events.get(0).getTraceId()).isEqualTo(traceId);
    assertThat(events.get(1).getTraceId()).isEqualTo(traceId);
    assertThat(events.get(0).getCreatedAt()).isBeforeOrEqualTo(events.get(1).getCreatedAt());
  }

  private JsonNode postRun(long durationMs, boolean success) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/devops/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of("durationMs", durationMs, "success", success))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
