package com.ai.change.request.analyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Reconstrucao completa de uma execucao pelo trace_id (pipeline + gateways + IA + tools). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AgentClient agentClient;

  @Test
  void fullAnalysisIsReconstructibleByTraceId() throws Exception {
    String traceId = "trace-full-" + UUID.randomUUID();
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(
            new AgentResponse(
                "req-trace",
                "completed",
                Map.of("risk", "HIGH", "confidence", 0.95, "rationale", "regra financeira")));

    mockMvc
        .perform(
            post("/api/change-requests")
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Alterar desconto VIP de 10% para 15%\"}"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(201));

    mockMvc
        .perform(
            post("/api/agent/classify")
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeText\":\"Alterar desconto VIP de 10% para 15%\"}"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

    mockMvc
        .perform(
            post("/api/agent/analyze-code")
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeText\":\"Alterar desconto VIP de 10% para 15%\"}"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

    mockMvc
        .perform(
            post("/api/agent/retrieve-knowledge")
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeText\":\"Alterar desconto VIP de 10% para 15%\"}"))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

    var result = mockMvc.perform(get("/api/traces/" + traceId)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode events = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(events.isArray()).isTrue();
    assertThat(events.size()).isGreaterThanOrEqualTo(6);

    List<String> nodes = new ArrayList<>();
    List<String> eventNames = new ArrayList<>();
    events.forEach(e -> nodes.add(e.get("node").asText()));
    events.forEach(e -> eventNames.add(e.get("event").asText()));
    assertThat(nodes).contains("pipeline", "classify", "analyze-code", "retrieve_knowledge");
    assertThat(eventNames).contains("analysis_started", "analysis_completed");
    assertThat(eventNames).anyMatch(e -> e.startsWith("started"));

    events.forEach(
        e -> {
          assertThat(e.get("traceId").asText()).isEqualTo(traceId);
          assertThat(e.get("requestId").asText()).isNotBlank();
          assertThat(e.get("createdAt").asText()).isNotBlank();
        });

    JsonNode riskEvent =
        findFirst(events, e -> "analysis_completed".equals(e.get("event").asText()));
    assertThat(riskEvent).isNotNull();
    assertThat(riskEvent.get("risk").asText()).isEqualTo("HIGH");

    assertThat(eventsAreChronological(events)).isTrue();
  }

  @Test
  void unknownTraceReturns404() throws Exception {
    var result = mockMvc.perform(get("/api/traces/trace-inexistente")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("trace_not_found");
  }

  @Test
  void noTraceEventExposesSecrets() throws Exception {
    String traceId = "trace-safe-" + UUID.randomUUID();
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(
            new AgentResponse(
                "req-secrets", "completed", Map.of("risk", "LOW", "confidence", 0.5)));

    mockMvc
        .perform(
            post("/api/change-requests")
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Alterar desconto VIP de 10% para 15%\"}"))
        .andReturn();

    var result = mockMvc.perform(get("/api/traces/" + traceId)).andReturn();
    String payload = result.getResponse().getContentAsString();

    assertThat(payload).doesNotContain("sk-", "password", "api_key", "apiKey", "token", "secret");
  }

  private JsonNode findFirst(JsonNode events, java.util.function.Predicate<JsonNode> predicate) {
    for (JsonNode event : events) {
      if (predicate.test(event)) {
        return event;
      }
    }
    return null;
  }

  private boolean eventsAreChronological(JsonNode events) {
    java.time.Instant previous = java.time.Instant.MIN;
    for (JsonNode event : events) {
      java.time.Instant current = java.time.Instant.parse(event.get("createdAt").asText());
      if (current.isBefore(previous)) {
        return false;
      }
      previous = current;
    }
    return true;
  }
}
