package com.ai.change.request.analyzer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ai.change.request.analyzer.observability.TraceEvent;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceViewTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private TraceEventRepository repository;

  @Test
  void tracePageRendersEventsInChronologicalOrderWithRecoveredDocuments() throws Exception {
    Instant base = Instant.now().minusSeconds(30);
    repository.save(
        new TraceEvent(
            "trace-web-1",
            "req-web-1",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            base));
    repository.save(
        new TraceEvent(
            "trace-web-1",
            "req-web-1",
            "retrieve_knowledge",
            "rag_search",
            null,
            null,
            null,
            null,
            null,
            null,
            "[{\"source\":\"discount-policy.md\",\"document_id\":\"doc-1\",\"score\":0.92},"
                + "{\"source\":\"business-rules.md\",\"document_id\":\"doc-2\",\"score\":0.81}]",
            base.plusSeconds(1)));
    repository.save(
        new TraceEvent(
            "trace-web-1",
            "req-web-1",
            "pipeline",
            "analysis_completed",
            150L,
            "ok",
            null,
            "HIGH",
            null,
            "gpt-x",
            base.plusSeconds(2)));

    var result = mockMvc.perform(get("/traces/trace-web-1")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    String eventsSection = content.substring(content.indexOf(">Eventos<"));
    int first = eventsSection.indexOf("analysis_started");
    int rag = eventsSection.indexOf("retrieve_knowledge");
    int last = eventsSection.indexOf("analysis_completed");
    assertThat(first).isGreaterThan(-1);
    assertThat(rag).isGreaterThan(first);
    assertThat(last).isGreaterThan(rag);
    assertThat(content).contains("150", "gpt-x");
    assertThat(content)
        .contains("discount-policy.md", "business-rules.md", "doc-1", "0.92", "0.81");
  }

  @Test
  void tracePageWithoutDocumentsIndicatesNoneRecovered() throws Exception {
    repository.save(
        new TraceEvent(
            "trace-web-2",
            "req-web-2",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            Instant.now()));

    var result = mockMvc.perform(get("/traces/trace-web-2")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString())
        .contains("Nenhum documento recuperado nesta execução.");
  }

  @Test
  void unknownTraceRendersFriendly404Page() throws Exception {
    var result = mockMvc.perform(get("/traces/inexistente")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).contains("text/html");
    assertThat(result.getResponse().getContentAsString()).contains("Trace não encontrado");
  }

  @Test
  void tracePageEscapesUntrustedEventFields() throws Exception {
    repository.save(
        new TraceEvent(
            "trace-web-3",
            "req-web-3",
            "search_code",
            "failed",
            12L,
            "failed",
            "<script>alert(1)</script>",
            null,
            "search_code",
            null,
            Instant.now()));

    var result = mockMvc.perform(get("/traces/trace-web-3")).andReturn();

    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(content).doesNotContain("<script>alert(1)");
  }

  @Test
  void tracePageRendersNoSecrets() throws Exception {
    repository.save(
        new TraceEvent(
            "trace-web-4",
            "req-web-4",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            Instant.now()));
    repository.save(
        new TraceEvent(
            "trace-web-4",
            "req-web-4",
            "retrieve_knowledge",
            "rag_search",
            null,
            null,
            null,
            null,
            null,
            null,
            "[{\"source\":\"security-policy.md\",\"document_id\":\"doc-9\",\"score\":0.77}]",
            Instant.now()));

    var result = mockMvc.perform(get("/traces/trace-web-4")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    String content = result.getResponse().getContentAsString();
    assertThat(content).doesNotContain("sk-", "password", "api_key", "token", "secret");
    assertThat(content).contains("security-policy.md");
  }

  @Test
  void traceLookupFormRedirectsToTracePage() throws Exception {
    repository.save(
        new TraceEvent(
            "trace-web-5",
            "req-web-5",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            Instant.now()));

    var result = mockMvc.perform(post("/traces").param("traceId", "trace-web-5")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(303);
    assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/traces/trace-web-5");

    var page = mockMvc.perform(get("/traces/trace-web-5")).andReturn();
    assertThat(page.getResponse().getContentAsString()).contains("analysis_started");
  }
}
