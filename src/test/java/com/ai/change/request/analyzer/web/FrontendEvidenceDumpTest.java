package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.security.SecurityAssessmentService;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gera os HTMLs renderizados das tres telas (formulario, resultado HIGH e trace) em {@code
 * target/frontend-evidence/} para a captura da evidencia {@code docs/evidence/08-frontend.png}
 * (script {@code .kilo/scripts/frontend-evidence.ps1}). Executa apenas com {@code
 * -Dfrontend.evidence.dump=true}; ignorado na suite normal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "frontend.evidence.dump", matches = "true")
class FrontendEvidenceDumpTest {

  private static final String HIGH_ANALYSIS =
      """
      {
        "findings": [
          {"component": "discount-service", "description": "Desconto VIP alterado de 10% para 15%", "severity": "HIGH"},
          {"component": "checkout", "description": "Regra financeira afetada em tela de checkout", "severity": "MEDIUM"}
        ],
        "riskAssessment": {"level": "HIGH", "confidence": 0.95, "rationale": "Alteração de regra financeira com impacto em cobrança"},
        "testRecommendations": [
          {"component": "discount-service", "description": "Teste unitário do desconto VIP de 15%", "priority": "HIGH"},
          {"component": "checkout", "description": "E2E do fluxo de checkout com desconto VIP", "priority": "HIGH"}
        ]
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private TraceEventRepository traceEventRepository;

  @Autowired private SecurityAssessmentService securityAssessmentService;

  @MockitoBean private AgentClient agentClient;

  @Test
  void dumpRenderedPagesToTarget() throws Exception {
    Path out = Path.of("target", "frontend-evidence");
    Files.createDirectories(out);

    dumpPage("/", "form.html", out);

    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(
            new AgentResponse("req-evid", "completed", Map.of("risk", "HIGH", "confidence", 0.95)));
    String id = submitForm("Alterar o desconto de clientes VIP de 10% para 15%.");
    submitAnalysis(id, HIGH_ANALYSIS);
    ChangeRequest request = changeRequestRepository.findById(UUID.fromString(id)).orElseThrow();
    securityAssessmentService.persist(
        request,
        List.of(
            new SecurityEvent(
                "prompt_injection", "request_text", "Ignore as instruções", "IGNORED")));
    dumpPage("/requests/" + id, "result-high.html", out);

    seedTrace(request.getTraceId());
    dumpPage("/traces/" + request.getTraceId(), "trace.html", out);
  }

  private void dumpPage(String path, String fileName, Path out) throws Exception {
    var result = mockMvc.perform(get(path)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String html = result.getResponse().getContentAsString();
    html =
        html.replace(
            "<link rel=\"stylesheet\" href=\"/css/app.css\">", "<style>" + css() + "</style>");
    Files.writeString(out.resolve(fileName), html, StandardCharsets.UTF_8);
  }

  private String css() throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/static/css/app.css")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private void seedTrace(String traceId) {
    Instant base = Instant.now().minusSeconds(20);
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            "req-evid",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            base));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            "req-evid",
            "retrieve_knowledge",
            "rag_search",
            42L,
            "ok",
            null,
            null,
            null,
            null,
            "[{\"source\":\"business-rules.md\",\"document_id\":\"doc-2\",\"score\":0.98},"
                + "{\"source\":\"discount-policy.md\",\"document_id\":\"doc-1\",\"score\":0.92}]",
            base.plusSeconds(1)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            "req-evid",
            "search_code",
            "completed",
            31L,
            "ok",
            null,
            null,
            "search_code",
            null,
            base.plusSeconds(2)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            "req-evid",
            "pipeline",
            "analysis_persisted",
            null,
            "ok",
            null,
            "HIGH",
            null,
            null,
            base.plusSeconds(3)));
  }

  private String submitForm(String text) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/change-requests")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("text", text))
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
