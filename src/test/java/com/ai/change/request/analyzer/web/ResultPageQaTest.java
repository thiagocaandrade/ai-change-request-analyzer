package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Pagina de resultado com a secao QA: completo, degradado e conteudo escapado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultPageQaTest {

  private static final String QA_ANALYSIS =
      """
      {
        "findings": [
          {"component": "discount-service", "description": "Desconto VIP alterado", "severity": "HIGH"}
        ],
        "riskAssessment": {"level": "HIGH", "confidence": 0.95, "rationale": "regra financeira"},
        "testRecommendations": [
          {"component": "discount-service", "description": "Cobrir desconto VIP de 15%", "priority": "HIGH",
           "priorityJustification": "categoria avaliada: matriz deterministica", "riskCategory": "financial_business_rule_regression", "refined": true}
        ],
        "qa": {
          "findings": [
            {"component": "discount-service", "description": "teste de regressao ausente", "severity": "HIGH", "source": "business-rules.md"}
          ],
          "riskMatrix": [
            {"category": "financial_business_rule_regression", "applicable": true, "impact": "HIGH", "likelihood": "MEDIUM", "priority": "HIGH", "justification": "matriz deterministica"}
          ],
          "degraded": false,
          "record": {"stage": "CODE_REVIEW", "promptVersion": "code-review-v1", "resultJson": "{}", "degraded": false, "iterations": 0, "traceId": "trace-qa-view"}
        }
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Autowired private ChangeRequestRepository repository;

  @Test
  void resultPageRendersQaFindingsAndPrioritizedRecommendations() throws Exception {
    String id = createRequest("trace-qa-complete");
    submitAnalysis(id, QA_ANALYSIS);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("QA com IA (code review)");
    assertThat(content).contains("teste de regressao ausente");
    assertThat(content).contains("business-rules.md");
    assertThat(content).contains("matriz deterministica");
    assertThat(content).contains("financial_business_rule_regression");
    assertThat(content).doesNotContain("QA degradado");
  }

  @Test
  void degradedQaRendersExplicitIndicationWithoutBreakingThePage() throws Exception {
    String id = createRequest("trace-qa-degraded");
    String payload =
        """
        {
          "findings": [],
          "riskAssessment": {"level": "LOW", "confidence": 0.8, "rationale": "cosmetica"},
          "testRecommendations": [
            {"component": "unit", "description": "teste unitario (degradado)", "priority": "MEDIUM",
             "priorityJustification": "fallback deterministico com matriz avaliada", "refined": true}
          ],
          "qa": {
            "findings": [],
            "riskMatrix": [],
            "degraded": true,
            "record": {"stage": "CODE_REVIEW", "promptVersion": "code-review-v1", "resultJson": "{}", "degraded": true, "iterations": 0, "traceId": "trace-qa-degraded"}
          }
        }
        """;
    submitAnalysis(id, payload);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("QA com IA (code review)");
    assertThat(content).contains("QA degradado");
    assertThat(content).contains("fallback deterministico com matriz avaliada");
  }

  @Test
  void qaContentWithScriptMarkupIsRenderedEscaped() throws Exception {
    String id = createRequest("trace-qa-escaped");
    String payload =
        """
        {
          "findings": [],
          "riskAssessment": {"level": "LOW", "confidence": 0.8, "rationale": "cosmetica"},
          "testRecommendations": [],
          "qa": {
            "findings": [
              {"component": "discount-service", "description": "<script>alert('xss')</script> teste ausente", "severity": "HIGH", "source": "code.md"}
            ],
            "riskMatrix": [],
            "degraded": false,
            "record": {"stage": "CODE_REVIEW", "promptVersion": "code-review-v1", "resultJson": "{}", "degraded": false, "iterations": 0, "traceId": "trace-qa-escaped"}
          }
        }
        """;
    submitAnalysis(id, payload);

    var result = mockMvc.perform(get("/requests/" + id)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    assertThat(content).doesNotContain("<script>alert");
  }

  private String createRequest(String traceId) {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.PENDING);
    request.setTraceId(traceId);
    return repository.save(request).getId().toString();
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
