package com.ai.change.request.analyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TraceEventRepository repository;

  @Autowired private TraceService traceService;

  @Test
  void traceWithoutEventsReturns404() throws Exception {
    var result = mockMvc.perform(get("/api/traces/inexistente")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("trace_not_found");
  }

  @Test
  void traceWithEventsReturnsOrderedEvents() throws Exception {
    Instant base = Instant.now().minusSeconds(10);
    repository.save(
        new TraceEvent(
            "trace-endpoint-1",
            "req-ep-1",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            base));
    repository.save(
        new TraceEvent(
            "trace-endpoint-1",
            "req-ep-1",
            "pipeline",
            "analysis_completed",
            120L,
            "ok",
            null,
            "HIGH",
            null,
            null,
            base.plusMillis(500)));

    var result = mockMvc.perform(get("/api/traces/trace-endpoint-1")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.isArray()).isTrue();
    assertThat(body.size()).isEqualTo(2);
    assertThat(body.get(0).get("event").asText()).isEqualTo("analysis_started");
    assertThat(body.get(1).get("event").asText()).isEqualTo("analysis_completed");
    assertThat(body.get(1).get("durationMs").asLong()).isEqualTo(120L);
    assertThat(body.get(1).get("risk").asText()).isEqualTo("HIGH");
    assertThat(body.get(1).get("createdAt").asText()).isNotBlank();
  }

  @Test
  void recordedEventsCarryMdcTraceIdAndNeverSecrets() throws Exception {
    MDC.put("trace_id", "trace-endpoint-2");
    MDC.put("request_id", "req-ep-2");
    try {
      traceService.record("search_code", "completed", 5L, "ok", null, null, "search_code", null);
    } finally {
      MDC.clear();
    }

    var result = mockMvc.perform(get("/api/traces/trace-endpoint-2")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.isArray()).isTrue();
    assertThat(body.size()).isEqualTo(1);
    assertThat(body.get(0).get("traceId").asText()).isEqualTo("trace-endpoint-2");
    assertThat(body.get(0).get("requestId").asText()).isEqualTo("req-ep-2");
    assertThat(body.get(0).get("tool").asText()).isEqualTo("search_code");
    String payload = body.toString();
    assertThat(payload).doesNotContain("sk-", "password", "api_key", "token", "secret");
  }
}
