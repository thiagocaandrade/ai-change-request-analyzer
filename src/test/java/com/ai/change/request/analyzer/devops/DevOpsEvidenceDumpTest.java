package com.ai.change.request.analyzer.devops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.ai.AiAnalysisService;
import com.ai.change.request.analyzer.ai.dto.AiResults.LogAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Gera os HTMLs para as evidencias {@code docs/evidence/11-github-actions.png}, {@code
 * 12-anomaly.png} e {@code 13-n8n.png} (script {@code .kilo/scripts/devops-evidence.ps1}): pipeline
 * de CI, relatorio real de anomalia/tendencia dos endpoints e workflow n8n. Executa apenas com
 * {@code -Ddevops.evidence.dump=true}; ignorado na suite normal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "devops.evidence.dump", matches = "true")
class DevOpsEvidenceDumpTest {

  private static final String BUILD_LOG =
      "[INFO] Scanning for projects...\n"
          + "[ERROR] COMPILATION ERROR : DiscountService.java:[42,10] ';' expected\n"
          + "[INFO] BUILD FAILURE\n";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AiAnalysisService aiAnalysisService;

  @Test
  void dumpDevOpsEvidencePagesToTarget() throws Exception {
    Path out = Path.of("target", "devops-evidence");
    Files.createDirectories(out);
    Files.writeString(out.resolve("ci.html"), ciPage(), StandardCharsets.UTF_8);
    Files.writeString(out.resolve("anomaly.html"), anomalyPage(), StandardCharsets.UTF_8);
    Files.writeString(out.resolve("n8n.html"), n8nPage(), StandardCharsets.UTF_8);
  }

  private String ciPage() throws Exception {
    when(aiAnalysisService.analyzeLogs(anyString()))
        .thenReturn(
            new LogAnalysisResult(
                "falha na etapa de compilacao",
                "compile",
                "erro de sintaxe em DiscountService.java",
                "[ERROR] COMPILATION ERROR : DiscountService.java:[42,10]",
                "corrigir a sintaxe e reexecutar a compilacao",
                0.9,
                false));
    var logAnalysis =
        mockMvc
            .perform(
                post("/api/devops/log-analysis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("log", BUILD_LOG))))
            .andReturn();
    assertThat(logAnalysis.getResponse().getStatus()).isEqualTo(200);
    String diagnosis = pretty(logAnalysis.getResponse().getContentAsString());

    List<String> stages = ciStages();
    StringBuilder html = new StringBuilder();
    html.append(pageHead("CI pipeline - GitHub Actions", "11-github-actions"));
    html.append("<h2>Estagios do pipeline (compile - unit - integration - quality - Docker)</h2>");
    html.append("<table><tr><th>#</th><th>Estagio</th></tr>");
    for (int i = 0; i < stages.size(); i++) {
      html.append("<tr><td>")
          .append(i + 1)
          .append("</td><td><code>")
          .append(escape(stages.get(i)))
          .append("</code></td></tr>");
    }
    html.append("</table>");
    html.append("<p>build.log/test.log publicados via <code>actions/upload-artifact</code> com ")
        .append("<code>if: always()</code> apos redacao de padroes sensiveis ")
        .append("(<code>scripts/redact_logs.py</code>):</p>");
    html.append("<pre>").append(escape(redactScript())).append("</pre>");
    html.append("<h2>Analise de logs com IA (build.log simulado enviado a ")
        .append("<code>POST /api/devops/log-analysis</code>)</h2>");
    html.append("<pre>").append(escape(diagnosis)).append("</pre>");
    html.append(pageFoot());
    return html.toString();
  }

  private String anomalyPage() throws Exception {
    StringBuilder html = new StringBuilder();
    html.append(pageHead("Deteccao de anomalia e tendencia de falha", "12-anomaly"));
    html.append("<h2>Sequencia de execucoes em POST /api/devops/runs</h2>");
    List<String> reports = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      reports.add(runReport(400, true));
    }
    String anomalyReport = runReport(2800, true);
    reports.add(anomalyReport);

    html.append("<table><tr><th>#</th><th>duracao (ms)</th><th>resultado</th></tr>");
    for (int i = 0; i < 5; i++) {
      html.append("<tr><td>").append(i + 1).append("</td><td>400</td><td>sucesso</td></tr>");
    }
    html.append("<tr><td>6</td><td>2800</td><td>sucesso (anomalia)</td></tr></table>");
    html.append("<h2>Relatorio da 6a execucao (baseline 400ms, observado 2800ms)</h2>");
    html.append("<pre>").append(escape(pretty(anomalyReport))).append("</pre>");

    html.append("<h2>Tendencia de falha em janela de 5 execucoes</h2>");
    for (boolean success : new boolean[] {true, true, false, false, false}) {
      reports.add(runReport(100, success));
    }
    JsonNode trendNode = objectMapper.readTree(reports.get(reports.size() - 1)).get("failureTrend");
    html.append("<pre>").append(escape(trendNode.toPrettyString())).append("</pre>");
    html.append(pageFoot());
    return html.toString();
  }

  private String n8nPage() throws Exception {
    Path workflowPath = Path.of("n8n", "workflow.json");
    JsonNode workflow = objectMapper.readTree(Files.readString(workflowPath));

    StringBuilder html = new StringBuilder();
    html.append(pageHead("Workflow n8n - analise via webhook", "13-n8n"));
    html.append("<h2>Nos (somente integracao/roteamento)</h2>");
    html.append("<table><tr><th>No</th><th>Tipo</th><th>Detalhe</th></tr>");
    for (JsonNode node : workflow.get("nodes")) {
      html.append("<tr><td>")
          .append(escape(node.get("name").asText()))
          .append("</td><td><code>")
          .append(escape(node.get("type").asText()))
          .append("</code></td><td>")
          .append(escape(nodeDetail(node)))
          .append("</td></tr>");
    }
    html.append("</table>");
    html.append("<h2>Conexoes</h2>");
    StringBuilder flow = new StringBuilder();
    flow.append("Webhook - Solicitacao de mudanca");
    flow.append(" -> HTTP Request - Analisar mudanca");
    flow.append(" -> IF - risco HIGH?");
    flow.append(" -> [true] Notificar (risco HIGH)");
    flow.append(" / [false] fim (sem notificacao)");
    html.append("<p><code>").append(escape(flow.toString())).append("</code></p>");
    html.append("<h2>Condicao</h2>");
    for (JsonNode node : workflow.get("nodes")) {
      if (node.get("type").asText().equals("n8n-nodes-base.if")) {
        JsonNode condition = node.get("parameters").get("conditions").get("conditions").get(0);
        html.append("<pre>").append(escape(pretty(condition.toPrettyString()))).append("</pre>");
      }
    }
    html.append(
            "<p>Lógica de negocio: nenhuma - risco calculado no Spring Boot; o workflow apenas ")
        .append("repassa <code>analysis.riskLevel</code> e roteia.</p>");
    html.append(pageFoot());
    return html.toString();
  }

  private String runReport(long durationMs, boolean success) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/devops/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("durationMs", durationMs, "success", success))))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result.getResponse().getContentAsString();
  }

  private String pretty(String json) throws Exception {
    return objectMapper.readTree(json).toPrettyString();
  }

  private List<String> ciStages() throws Exception {
    List<String> stages = new ArrayList<>();
    for (String line : Files.readAllLines(Path.of(".github", "workflows", "ci.yml"))) {
      String trimmed = line.trim();
      if (trimmed.startsWith("- name:")) {
        stages.add(trimmed.substring("- name:".length()).trim());
      }
    }
    return stages;
  }

  private String redactScript() throws Exception {
    List<String> lines = Files.readAllLines(Path.of("scripts", "redact_logs.py"));
    return String.join("\n", lines.subList(0, Math.min(lines.size(), 16)));
  }

  private String nodeDetail(JsonNode node) {
    JsonNode parameters = node.get("parameters");
    if (parameters == null) {
      return "";
    }
    if (parameters.has("url")) {
      return "POST " + parameters.get("url").asText();
    }
    if (parameters.has("path")) {
      return parameters.get("httpMethod").asText() + " /" + parameters.get("path").asText();
    }
    return "";
  }

  private String pageHead(String title, String evidence) {
    return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
        + "<title>"
        + escape(title)
        + "</title><style>"
        + "body{font-family:'Segoe UI',Arial,sans-serif;background:#f8fafc;color:#1e293b;margin:0;padding:24px;}"
        + "h1{font-size:22px;border-bottom:2px solid #1e293b;padding-bottom:8px;}"
        + "h2{font-size:16px;margin-top:24px;}"
        + "table{border-collapse:collapse;width:100%;background:#fff;}"
        + "th,td{border:1px solid #cbd5e1;padding:6px 10px;text-align:left;font-size:13px;}"
        + "th{background:#e2e8f0;}"
        + "pre{background:#0f172a;color:#e2e8f0;padding:14px;border-radius:6px;font-size:12px;overflow-x:auto;white-space:pre-wrap;}"
        + "code{background:#e2e8f0;padding:1px 4px;border-radius:3px;font-size:12px;}"
        + ".badge{display:inline-block;background:#16a34a;color:#fff;padding:2px 8px;border-radius:10px;font-size:11px;}"
        + "</style></head><body><h1>"
        + escape(title)
        + " <span class=\"badge\">evidencia "
        + escape(evidence)
        + "</span></h1>";
  }

  private String pageFoot() {
    return "</body></html>";
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
