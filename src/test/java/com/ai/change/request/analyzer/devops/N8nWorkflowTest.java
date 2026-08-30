package com.ai.change.request.analyzer.devops;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Teste estrutural do workflow n8n exportavel: JSON valido, nos e arestas presentes, condicao
 * referenciando o campo de risco do resultado e apenas nos de integracao/roteamento.
 */
class N8nWorkflowTest {

  private static final Set<String> ALLOWED_NODE_TYPES =
      Set.of(
          "n8n-nodes-base.webhook",
          "n8n-nodes-base.httpRequest",
          "n8n-nodes-base.if",
          "n8n-nodes-base.noOp");

  private JsonNode workflow() throws Exception {
    Path path = Path.of("n8n", "workflow.json");
    assertThat(Files.exists(path)).isTrue();
    return new ObjectMapper().readTree(Files.readString(path));
  }

  private JsonNode nodeNamed(JsonNode workflow, String name) {
    for (JsonNode node : workflow.get("nodes")) {
      if (node.get("name") != null && node.get("name").asText().equals(name)) {
        return node;
      }
    }
    return null;
  }

  private List<String> nodeTypes(JsonNode workflow) {
    var nodes = workflow.get("nodes");
    var types = new java.util.ArrayList<String>();
    for (JsonNode node : nodes) {
      types.add(node.get("type").asText());
    }
    return types;
  }

  @Test
  void workflowIsValidJsonWithExpectedNodes() throws Exception {
    JsonNode workflow = workflow();

    assertThat(workflow.has("nodes")).isTrue();
    assertThat(workflow.has("connections")).isTrue();
    assertThat(nodeNamed(workflow, "Webhook - Solicitação de mudança")).isNotNull();
    assertThat(nodeNamed(workflow, "HTTP Request - Analisar mudança")).isNotNull();
    assertThat(nodeNamed(workflow, "IF - risco HIGH?")).isNotNull();
    assertThat(nodeNamed(workflow, "Notificar (risco HIGH)")).isNotNull();
  }

  @Test
  void edgesConnectWebhookToHttpToIfToNotification() throws Exception {
    JsonNode workflow = workflow();
    JsonNode connections = workflow.get("connections");

    JsonNode webhookOut = connections.get("Webhook - Solicitação de mudança").get("main");
    assertThat(webhookOut.get(0).get(0).get("node").asText()).isEqualTo("HTTP Request - Analisar mudança");

    JsonNode httpOut = connections.get("HTTP Request - Analisar mudança").get("main");
    assertThat(httpOut.get(0).get(0).get("node").asText()).isEqualTo("IF - risco HIGH?");

    JsonNode ifOut = connections.get("IF - risco HIGH?").get("main");
    assertThat(ifOut.get(0).get(0).get("node").asText()).isEqualTo("Notificar (risco HIGH)");
    assertThat(ifOut.get(1).isEmpty()).isTrue();
  }

  @Test
  void conditionReferencesRiskFieldOfResult() throws Exception {
    JsonNode workflow = workflow();
    JsonNode ifNode = nodeNamed(workflow, "IF - risco HIGH?");
    JsonNode condition = ifNode.get("parameters").get("conditions").get("conditions").get(0);

    assertThat(condition.get("leftValue").asText()).contains("risk");
    assertThat(condition.get("leftValue").asText()).contains("$json");
    assertThat(condition.get("rightValue").asText()).isEqualTo("HIGH");
    assertThat(condition.get("operator").get("operation").asText()).isEqualTo("equals");
  }

  @Test
  void workflowContainsOnlyIntegrationAndRoutingNodes() throws Exception {
    JsonNode workflow = workflow();

    assertThat(nodeTypes(workflow)).allMatch(ALLOWED_NODE_TYPES::contains);
  }

  @Test
  void httpRequestCallsTheAnalyzeEndpoint() throws Exception {
    JsonNode workflow = workflow();
    JsonNode httpNode = nodeNamed(workflow, "HTTP Request - Analisar mudança");

    assertThat(httpNode.get("parameters").get("method").asText()).isEqualTo("POST");
    assertThat(httpNode.get("parameters").get("url").asText())
        .contains("/api/change-requests");
    assertThat(httpNode.get("parameters").get("jsonBody").asText()).contains("text");
  }
}
