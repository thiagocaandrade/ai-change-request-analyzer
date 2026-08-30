package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.dto.AiResults.ClassificationResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.RiskAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.memory.AnalysisMemoryService;
import com.ai.change.request.analyzer.rag.RagService;
import com.ai.change.request.analyzer.tools.CodeEvidenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ai.chat.api-key=sk-secreto-para-teste")
class AgentGatewayControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AiAnalysisService aiAnalysisService;

  @MockitoBean private CodeEvidenceService codeEvidenceService;

  @MockitoBean private RagService ragService;

  @MockitoBean private AnalysisMemoryService memoryService;

  private static final String TEXT = "{\"changeText\":\"Alterar desconto VIP\"}";

  @Test
  void classifyReturnsTypedResponse() throws Exception {
    when(aiAnalysisService.classify(anyString()))
        .thenReturn(new ClassificationResult("business_rule", "regra de desconto", false));

    var result =
        mockMvc
            .perform(
                post("/api/agent/classify").contentType(MediaType.APPLICATION_JSON).content(TEXT))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("category").asText()).isEqualTo("business_rule");
    assertThat(body.get("notes").asText()).isEqualTo("regra de desconto");
    assertThat(body.get("degraded").asBoolean()).isFalse();
  }

  @Test
  void analyzeImpactReturnsTypedFindings() throws Exception {
    when(aiAnalysisService.analyzeImpact(anyString(), anyString()))
        .thenReturn(
            new ImpactAnalysisResult(
                List.of(
                    new com.ai.change.request.analyzer.ai.dto.AiResults.ImpactFindingDto(
                        "discount-service", "desconto alterado", "HIGH")),
                false));

    String payload =
        """
        {"changeText":"Alterar desconto VIP","codeFindings":[],"retrievedDocuments":[],"historicalFindings":[]}
        """;
    var result =
        mockMvc
            .perform(
                post("/api/agent/analyze-impact")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("findings").get(0).get("component").asText()).isEqualTo("discount-service");
  }

  @Test
  void assessRiskReturnsTypedRisk() throws Exception {
    when(aiAnalysisService.assessRisk(anyString(), anyString()))
        .thenReturn(new RiskAnalysisResult("HIGH", 0.9, "regra financeira", false));

    String payload =
        "{\"changeText\":\"Alterar desconto VIP\",\"classification\":{},\"impactFindings\":[]}";
    var result =
        mockMvc
            .perform(
                post("/api/agent/assess-risk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("level").asText()).isEqualTo("HIGH");
    assertThat(body.get("confidence").asDouble()).isEqualTo(0.9);
  }

  @Test
  void generateTestPlanReturnsTypedRecommendations() throws Exception {
    when(aiAnalysisService.generateTestPlan(anyString(), anyString()))
        .thenReturn(
            new TestPlanResult(
                List.of(new TestRecommendationDto("unit", "cobrir desconto", "HIGH")), false));

    String payload =
        """
        {"changeText":"Alterar desconto VIP","risk":{},"classification":{},"impactFindings":[]}
        """;
    var result =
        mockMvc
            .perform(
                post("/api/agent/generate-test-plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("recommendations").get(0).get("component").asText()).isEqualTo("unit");
  }

  @Test
  void retrieveKnowledgeReturnsHitsWithSourceAndScore() throws Exception {
    when(ragService.search(anyString()))
        .thenReturn(
            new RagService.KnowledgeSearchResult(
                List.of(
                    new RagService.KnowledgeHit(
                        "discount-policy.md",
                        "discount-policy",
                        "discount-policy-0",
                        0.93,
                        "Clientes VIP recebem desconto de 10%")),
                false));

    var result =
        mockMvc
            .perform(
                post("/api/agent/retrieve-knowledge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TEXT))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode first = body.get("documents").get(0);
    assertThat(first.get("source").asText()).isEqualTo("discount-policy.md");
    assertThat(first.get("score").asDouble()).isEqualTo(0.93);
    assertThat(first.get("content").asText()).contains("desconto");
  }

  @Test
  void retrieveHistoryReturnsHitsWithRequestIdAndSummary() throws Exception {
    when(memoryService.searchByTerms(anyString()))
        .thenReturn(
            new AnalysisMemoryService.HistorySearchResult(
                List.of(
                    new AnalysisMemoryService.HistoryHit(
                        "00000000-0000-0000-0000-000000000001", "semelhante a CR anterior")),
                false));

    var result =
        mockMvc
            .perform(
                post("/api/agent/retrieve-history")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TEXT))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("findings").get(0).get("requestId").asText()).isNotBlank();
  }

  @Test
  void responsesNeverExposeSecrets() throws Exception {
    when(aiAnalysisService.classify(anyString()))
        .thenReturn(new ClassificationResult("general", "sem chave", true));
    when(ragService.search(anyString()))
        .thenReturn(
            new RagService.KnowledgeSearchResult(
                List.of(
                    new RagService.KnowledgeHit(
                        "security-policy.md",
                        "security-policy",
                        "security-policy-0",
                        0.8,
                        "Chaves de API nunca aparecem em logs")),
                false));

    String[] paths = {"/api/agent/classify", "/api/agent/retrieve-knowledge"};
    String secret = "sk-secreto-para-teste";
    for (String path : paths) {
      var result =
          mockMvc
              .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(TEXT))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain(secret);
      assertThat(body).doesNotContainIgnoringCase("api_key");
      assertThat(body).doesNotContainIgnoringCase("apiKey");
      assertThat(body).doesNotContainIgnoringCase("token");
    }
  }

  @Test
  void blankChangeTextReturnsStructuredBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/agent/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"changeText\":\"\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("error").asText()).isEqualTo("invalid_request");
  }
}
