package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangeRequestControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ChangeRequestRepository repository;

  @MockitoBean private AgentClient agentClient;

  @Test
  void happyPathPersistsCompletedRequest() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(
            new AgentResponse(
                "req-id", "completed", Map.of("processed_text", "Alterar desconto VIP")));

    var result =
        mockMvc
            .perform(
                post("/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Alterar desconto VIP de 10% para 15%\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(body.get("traceId").asText()).isNotBlank();
    assertThat(body.get("result").get("processed_text").asText()).isEqualTo("Alterar desconto VIP");

    UUID id = UUID.fromString(body.get("id").asText());
    ChangeRequest persisted = repository.findById(id).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(ChangeRequestStatus.COMPLETED);
    assertThat(persisted.getTraceId()).isEqualTo(body.get("traceId").asText());
  }

  @Test
  void agentUnavailableMarksRequestFailedWithCause() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenThrow(
            new AgentUnavailableException(
                "agente indisponivel apos 3 tentativas", new RuntimeException("timeout")));

    var result =
        mockMvc
            .perform(
                post("/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Alterar desconto VIP\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(503);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("status").asText()).isEqualTo("FAILED");
    assertThat(body.get("result").get("error").asText()).isEqualTo("agent_unavailable");

    UUID id = UUID.fromString(body.get("id").asText());
    assertThat(repository.findById(id).orElseThrow().getStatus())
        .isEqualTo(ChangeRequestStatus.FAILED);
  }

  @Test
  void unknownIdReturnsStructuredNotFound() throws Exception {
    var result = mockMvc.perform(get("/requests/" + UUID.randomUUID())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("not_found");
  }

  @Test
  void blankTextReturnsStructuredBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("invalid_request");
  }
}
