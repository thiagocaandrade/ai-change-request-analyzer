package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.AgentUnavailableException;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebUiTest {

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

  private static final String LOW_ANALYSIS =
      """
      {
        "findings": [],
        "riskAssessment": {"level": "LOW", "confidence": 0.8, "rationale": "alteracao cosmética"},
        "testRecommendations": []
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Autowired private ChangeRequestRepository repository;

  @Autowired private SecurityAssessmentService securityAssessmentService;

  @MockitoBean private AgentClient agentClient;

  @Test
  void indexPageRendersFormWithCssLink() throws Exception {
    var result = mockMvc.perform(get("/")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("Nova solicitação de alteração", "/css/app.css");
    assertThat(content).contains("action=\"/change-requests\"", "name=\"text\"");
  }

  @Test
  void validFormTriggersAnalysisAndRedirectsToResultPage() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(new AgentResponse("req-id", "completed", Map.of("processed_text", "x")));

    var result =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", "Alterar o desconto de clientes VIP de 10% para 15%."))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(303);
    String location = result.getResponse().getRedirectedUrl();
    assertThat(location).startsWith("/requests/");
    String id = location.substring(location.lastIndexOf('/') + 1);

    ChangeRequest persisted = repository.findById(UUID.fromString(id)).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(ChangeRequestStatus.COMPLETED);
    assertThat(persisted.getText()).contains("desconto de clientes VIP");
    assertThat(persisted.getTraceId()).isNotBlank();
    verify(agentClient).analyze(anyString(), anyString(), anyString());
  }

  @Test
  void blankFormTextRendersFormWithValidationErrorWithoutCallingAgent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", "   "))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getModelAndView().getViewName()).isEqualTo("index");
    assertThat(result.getResponse().getContentAsString())
        .contains("Descreva a solicitação de alteração antes de enviar.");
    verify(agentClient, never()).analyze(anyString(), anyString(), anyString());
  }

  @Test
  void resultPageRendersRiskFindingsRecommendationsAndApproval() throws Exception {
    String id = createRequestViaForm();
    submitAnalysis(id, HIGH_ANALYSIS);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content)
        .contains(
            "HIGH",
            "0.95",
            "regra financeira",
            "discount-service",
            "Desconto VIP alterado",
            "Cobrir desconto VIP de 15%",
            "risk-high");
  }

  @Test
  void resultPageWithoutRiskAssessmentShowsUnavailable() throws Exception {
    String id = createRequestViaForm();

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("Risco não disponível.");
  }

  @Test
  void highRiskPendingShowsDecisionForm() throws Exception {
    String id = createRequestViaForm();
    submitAnalysis(id, HIGH_ANALYSIS);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("exigida", "Registrar decisão", "Aprovar", "Rejeitar");
    assertThat(content).contains("name=\"approver\"", "name=\"decision\"");
  }

  @Test
  void lowRiskShowsApprovalNotRequiredWithoutForm() throws Exception {
    String id = createRequestViaForm();
    submitAnalysis(id, LOW_ANALYSIS);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("não exigida");
    assertThat(content).doesNotContain("Registrar decisão", "name=\"approver\"");
  }

  @Test
  void submittedDecisionReflectsStatusAndApprover() throws Exception {
    String id = createRequestViaForm();
    submitAnalysis(id, HIGH_ANALYSIS);

    var approve =
        mockMvc
            .perform(
                post("/requests/" + id + "/approval")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("approver", "revisora")
                    .param("decision", "APPROVED"))
            .andReturn();

    assertThat(approve.getResponse().getStatus()).isEqualTo(303);
    assertThat(approve.getResponse().getRedirectedUrl()).isEqualTo("/requests/" + id);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("Aprovada", "revisora", "Decisão registrada");
    assertThat(content).doesNotContain("Registrar decisão");
  }

  @Test
  void failedAnalysisShowsStatusAndReasonOnPage() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenThrow(
            new AgentUnavailableException(
                "agente indisponivel apos 3 tentativas", new RuntimeException("timeout")));

    var submit =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", "Alterar desconto VIP"))
            .andReturn();

    assertThat(submit.getResponse().getStatus()).isEqualTo(303);
    String location = submit.getResponse().getRedirectedUrl();
    String id = location.substring(location.lastIndexOf('/') + 1);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("FAILED", "status-failed", "agent_unavailable");
  }

  @Test
  void unknownRequestRendersFriendly404Page() throws Exception {
    var result = mockMvc.perform(get("/requests/" + UUID.randomUUID())).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).contains("text/html");
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("Solicitação não encontrada.");
    assertThat(content).doesNotContain("\"error\"");
  }

  @Test
  void untrustedContentIsRenderedEscaped() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(new AgentResponse("req-id", "completed", Map.of()));
    var submit =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", "Alterar <script>alert(1)</script> desconto"))
            .andReturn();
    assertThat(submit.getResponse().getStatus()).isEqualTo(303);
    String id =
        submit
            .getResponse()
            .getRedirectedUrl()
            .substring(submit.getResponse().getRedirectedUrl().lastIndexOf('/') + 1);
    String payload =
        """
        {
          "findings": [
            {"component": "ui", "description": "<img src=x onerror=alert(2)>", "severity": "LOW"}
          ],
          "riskAssessment": {"level": "LOW", "confidence": 0.8},
          "testRecommendations": []
        }
        """;
    submitAnalysis(id, payload);
    ChangeRequest request = repository.findById(UUID.fromString(id)).orElseThrow();
    securityAssessmentService.persist(
        request,
        List.of(new SecurityEvent("prompt_injection", "code", "<b>ignore</b>", "IGNORED")));

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(content).contains("&lt;img src=x onerror=alert(2)&gt;");
    assertThat(content).contains("&lt;b&gt;ignore&lt;/b&gt;");
    assertThat(content).doesNotContain("<script>alert(1)", "<img src=x", "<b>ignore</b>");
  }

  private String createRequestViaForm() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(new AgentResponse("req-id", "completed", Map.of()));
    var result =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", "Alterar o desconto de clientes VIP de 10% para 15%."))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(303);
    String location = result.getResponse().getRedirectedUrl();
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private void submitAnalysis(String id, String payload) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/change-requests/" + id + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
  }
}
