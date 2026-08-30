package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SearchChangeHistoryToolTest {

  @Autowired private ChangeRequestRepository repository;

  @Autowired private SearchChangeHistoryTool tool;

  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-history-1");
    repository.save(request);
  }

  @Test
  void termReturnsPreviousAnalysesWithIdAndSummary() throws Exception {
    String result = tool.call("{\"query\":\"desconto\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.has("error")).isFalse();
    assertThat(node.get("count").asInt()).isGreaterThanOrEqualTo(1);
    JsonNode first = node.get("results").get(0);
    assertThat(first.get("requestId").asText()).isNotBlank();
    assertThat(first.get("summary").asText()).contains("desconto");
  }

  @Test
  void noMatchReturnsEmptyList() throws Exception {
    String result = tool.call("{\"query\":\"termo-inexistente\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("results").isEmpty()).isTrue();
    assertThat(node.get("count").asInt()).isZero();
  }

  @Test
  void rejectsEmptyQuery() throws Exception {
    String result = tool.call("{\"query\":\"\"}");

    JsonNode node = objectMapper.readTree(result);
    assertThat(node.get("error").asText()).isEqualTo("invalid_input");
  }

  @Test
  void exposesToolDefinition() {
    assertThat(tool.getToolDefinition().name()).isEqualTo("search_change_history");
  }
}
