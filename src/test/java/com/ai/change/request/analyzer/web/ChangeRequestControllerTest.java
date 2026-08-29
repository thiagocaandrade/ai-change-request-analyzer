package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.domain.ApprovalStatus;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.domain.RiskLevel;
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

  private static final String CREATE_REQUEST =
      "{\"text\":\"Alterar desconto VIP de 10% para 15%\"}";

  private static final String HIGH_ANALYSIS =
      """
      {
        "findings": [
          {"component": "discount-service", "description": "Desconto VIP alterado", "severity": "HIGH"}
        ],
        "riskAssessment": {"level": "HIGH", "confidence": 0.95, "rationale": "regra financeira"},
        "testRecommendations": [
          {"component": "discount-service", "description": "Cobrir desconto VIP de 15%", "priority": "HIGH"}
        ]
      }
      """;

  @Test
  void happyPathPersistsCompletedRequestWithTypedAnalysis() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(
            new AgentResponse(
                "req-id", "completed", Map.of("processed_text", "Alterar desconto VIP")));

    var result =
        mockMvc
            .perform(
                post("/api/change-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(body.get("traceId").asText()).isNotBlank();
    assertThat(body.get("failureReason").isNull()).isTrue();
    assertThat(body.get("analysis").isNull()).isFalse();
    assertThat(body.get("analysis").get("riskLevel").isNull()).isTrue();
    assertThat(body.get("analysis").get("approvalRequired").asBoolean()).isFalse();

    UUID id = UUID.fromString(body.get("id").asText());
    ChangeRequest persisted = repository.findById(id).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(ChangeRequestStatus.COMPLETED);
    assertThat(persisted.getTraceId()).isEqualTo(body.get("traceId").asText());
    assertThat(persisted.getAnalysis()).isNotNull();
    assertThat(persisted.getAnalysis().getRiskAssessment()).isNull();
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
                post("/api/change-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(503);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("status").asText()).isEqualTo("FAILED");
    assertThat(body.get("failureReason").asText()).contains("agent_unavailable");
    assertThat(body.get("analysis").isNull()).isTrue();

    UUID id = UUID.fromString(body.get("id").asText());
    assertThat(repository.findById(id).orElseThrow().getStatus())
        .isEqualTo(ChangeRequestStatus.FAILED);
  }

  @Test
  void unknownIdReturnsStructuredNotFound() throws Exception {
    var result = mockMvc.perform(get("/api/change-requests/" + UUID.randomUUID())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("not_found");
  }

  @Test
  void blankTextReturnsStructuredBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/change-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("invalid_request");
  }

  @Test
  void submitAnalysisHighRiskRequiresApproval() throws Exception {
    String id = createRequestId();

    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + id + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HIGH_ANALYSIS))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("analysis").get("riskLevel").asText()).isEqualTo("HIGH");
    assertThat(body.get("analysis").get("approvalRequired").asBoolean()).isTrue();
    assertThat(body.get("analysis").get("approvalStatus").asText()).isEqualTo("PENDING");
    assertThat(body.get("analysis").get("findingsCount").asInt()).isEqualTo(1);
    assertThat(body.get("analysis").get("recommendationsCount").asInt()).isEqualTo(1);
  }

  @Test
  void submitAnalysisLowRiskDoesNotRequireApproval() throws Exception {
    String id = createRequestId();
    String payload =
        """
        {
          "findings": [],
          "riskAssessment": {"level": "LOW", "confidence": 0.8},
          "testRecommendations": []
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + id + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("analysis").get("riskLevel").asText()).isEqualTo("LOW");
    assertThat(body.get("analysis").get("approvalRequired").asBoolean()).isFalse();
  }

  @Test
  void submitAnalysisWithInvalidConfidenceIsRejected() throws Exception {
    String id = createRequestId();
    String payload =
        """
        {
          "findings": [],
          "riskAssessment": {"level": "MEDIUM", "confidence": 1.5},
          "testRecommendations": []
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + id + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("invalid_confidence");
  }

  @Test
  void submitAnalysisWithoutRiskAssessmentIsRejected() throws Exception {
    String id = createRequestId();
    String payload = "{\"findings\":[],\"testRecommendations\":[]}";

    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + id + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("invalid_request");
  }

  @Test
  void submitAnalysisToUnknownRequestReturnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + UUID.randomUUID() + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(HIGH_ANALYSIS))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("not_found");
  }

  @Test
  void getAnalysisReturnsFullTypedAnalysis() throws Exception {
    String id = createRequestId();
    mockMvc
        .perform(
            post("/api/change-requests/" + id + "/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(HIGH_ANALYSIS))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

    var result = mockMvc.perform(get("/api/change-requests/" + id + "/analysis")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("requestId").asText()).isEqualTo(id);
    assertThat(body.get("riskLevel").asText()).isEqualTo("HIGH");
    assertThat(body.get("confidence").asDouble()).isEqualTo(0.95);
    assertThat(body.get("approvalRequired").asBoolean()).isTrue();
    assertThat(body.get("approvalStatus").asText()).isEqualTo(ApprovalStatus.PENDING.name());
    assertThat(body.get("findings")).hasSize(1);
    assertThat(body.get("testRecommendations")).hasSize(1);
  }

  @Test
  void getAnalysisWithoutAnalysisReturnsNotFound() throws Exception {
    ChangeRequest request = new ChangeRequest();
    request.setText("Sem analise");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-sem-analise");
    String id = repository.save(request).getId().toString();

    var result = mockMvc.perform(get("/api/change-requests/" + id + "/analysis")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("analysis_not_found");
  }

  @Test
  void getRequestExposesAnalysisSummary() throws Exception {
    String id = createRequestId();
    mockMvc
        .perform(
            post("/api/change-requests/" + id + "/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(HIGH_ANALYSIS))
        .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));

    var result = mockMvc.perform(get("/api/change-requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("analysis").get("riskLevel").asText()).isEqualTo("HIGH");
    assertThat(body.get("analysis").get("approvalRequired").asBoolean()).isTrue();
  }

  private String createRequestId() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(new AgentResponse("req-id", "completed", Map.of("processed_text", "x")));
    var result =
        mockMvc
            .perform(
                post("/api/change-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREATE_REQUEST))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("id").asText();
  }
}
