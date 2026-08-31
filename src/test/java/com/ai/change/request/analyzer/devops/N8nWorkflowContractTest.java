package com.ai.change.request.analyzer.devops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.api.AgentClient;
import com.ai.change.request.analyzer.api.dto.AgentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato do endpoint consumido pelo workflow n8n: o payload documentado em {@code n8n/README.md}
 * casa com {@code POST /api/change-requests} e a resposta contem o campo de risco usado pela
 * condicao do workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class N8nWorkflowContractTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AgentClient agentClient;

  @Test
  void documentedPayloadMatchesAnalyzeEndpointContract() throws Exception {
    when(agentClient.analyze(anyString(), anyString(), anyString()))
        .thenReturn(new AgentResponse("req-n8n", "completed", Map.of("risk", "HIGH")));

    var result =
        mockMvc
            .perform(
                post("/api/change-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("text", "Alterar o desconto de clientes VIP de 10% para 15%"))))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.has("analysis")).isTrue();
    assertThat(body.get("analysis").has("riskLevel")).isTrue();
    assertThat(body.has("traceId")).isTrue();
  }
}
