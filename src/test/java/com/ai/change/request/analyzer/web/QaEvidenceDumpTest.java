package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gera os HTMLs para a evidencia {@code docs/evidence/09-ai-code-review.png} (script {@code
 * .kilo/scripts/qa-evidence.ps1}): resultado com a secao QA (findings, matriz e recomendacoes
 * priorizadas) e trace com eventos {@code qa_review}/{@code qa_refinement}. Executa apenas com
 * {@code -Dqa.evidence.dump=true}; ignorado na suite normal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "qa.evidence.dump", matches = "true")
class QaEvidenceDumpTest {

  private static final String QA_ANALYSIS =
      """
      {
        "findings": [
          {"component": "discount-service", "description": "Desconto VIP alterado de 10% para 15%", "severity": "HIGH"},
          {"component": "checkout", "description": "Regra financeira afetada em tela de checkout", "severity": "MEDIUM"}
        ],
        "riskAssessment": {"level": "HIGH", "confidence": 0.95, "rationale": "Alteração de regra financeira com impacto em cobrança"},
        "testRecommendations": [
          {"component": "discount-service", "description": "Teste de regressão do desconto VIP de 15%", "priority": "HIGH",
           "priorityJustification": "categoria financial_business_rule_regression avaliada com sugestão do modelo normalizada: impacto=HIGH, probabilidade=MEDIUM -> combinação HIGH x MEDIUM = HIGH (matriz determinística)",
           "riskCategory": "financial_business_rule_regression", "refined": true},
          {"component": "checkout", "description": "E2E do fluxo de checkout com desconto VIP", "priority": "MEDIUM",
           "priorityJustification": "categoria incorrect_high_low_classification: impacto=MEDIUM, probabilidade=MEDIUM -> MEDIUM (matriz determinística)",
           "riskCategory": "incorrect_high_low_classification", "refined": true}
        ],
        "qa": {
          "findings": [
            {"component": "discount-service", "description": "teste de regressão da regra de desconto ausente", "severity": "HIGH", "source": "business-rules.md"},
            {"component": "checkout", "description": "validação de desconto não coberta por teste E2E", "severity": "MEDIUM", "source": "testing-guidelines.md"}
          ],
          "riskMatrix": [
            {"category": "financial_business_rule_regression", "applicable": true, "impact": "HIGH", "likelihood": "MEDIUM", "priority": "HIGH", "justification": "sugestão do modelo normalizada: HIGH x MEDIUM = HIGH"},
            {"category": "prompt_injection", "applicable": false, "impact": null, "likelihood": null, "priority": null, "justification": "categoria avaliada e não aplicável à alteração"}
          ],
          "degraded": false,
          "record": {"stage": "CODE_REVIEW", "promptVersion": "code-review-v1", "resultJson": "{\\"findings\\":[{\\"component\\":\\"discount-service\\"}]}", "degraded": false, "iterations": 0, "traceId": "trace-qa-evidence"}
        }
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private TraceEventRepository traceEventRepository;

  @Test
  void dumpQaRenderedPagesToTarget() throws Exception {
    Path out = Path.of("target", "qa-evidence");
    Files.createDirectories(out);

    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-qa-evidence");
    request = changeRequestRepository.save(request);

    var analysis =
        mockMvc
            .perform(
                post("/api/change-requests/" + request.getId() + "/analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(QA_ANALYSIS))
            .andReturn();
    assertThat(analysis.getResponse().getStatus()).isEqualTo(200);

    seedQaTrace(request.getTraceId());
    dumpPage("/requests/" + request.getId(), "result-qa.html", out);
    dumpPage("/traces/" + request.getTraceId(), "trace-qa.html", out);
  }

  private void seedQaTrace(String traceId) {
    Instant base = Instant.now().minusSeconds(30);
    traceEventRepository.save(
        new TraceEvent(
            traceId, null, "qa_review", "started", null, null, null, null, null, null, base));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            null,
            "retrieve_knowledge",
            "rag_search",
            38L,
            "ok",
            null,
            null,
            null,
            null,
            "[{\"source\":\"business-rules.md\",\"document_id\":\"business-rules\",\"score\":0.97},"
                + "{\"source\":\"coding-guidelines.md\",\"document_id\":\"coding-guidelines\",\"score\":0.88}]",
            base.plusSeconds(1)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            null,
            "qa_review",
            "completed",
            412L,
            "ok",
            null,
            null,
            null,
            null,
            base.plusSeconds(2)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            null,
            "qa_refinement",
            "refining",
            null,
            "retrying",
            "recommendation_invalid",
            null,
            null,
            null,
            "itens_invalidos=[\"checkout\"]",
            base.plusSeconds(3)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            null,
            "qa_refinement",
            "completed",
            298L,
            "ok",
            null,
            null,
            null,
            null,
            base.plusSeconds(4)));
    traceEventRepository.save(
        new TraceEvent(
            traceId,
            null,
            "pipeline",
            "analysis_persisted",
            null,
            "ok",
            null,
            "HIGH",
            null,
            null,
            base.plusSeconds(5)));
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
}
