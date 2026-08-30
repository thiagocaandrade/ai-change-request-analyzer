package com.ai.change.request.analyzer.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TraceEventRepositoryTest {

  @Autowired private TraceEventRepository repository;

  @Test
  void savesAndRecoversEventsByTraceIdInChronologicalOrder() {
    Instant base = Instant.now().minusSeconds(60);
    TraceEvent first =
        new TraceEvent(
            "trace-a",
            "req-1",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            base);
    TraceEvent second =
        new TraceEvent(
            "trace-a",
            "req-1",
            "classify",
            "completed",
            12L,
            "ok",
            null,
            null,
            null,
            "gpt-x",
            base.plusSeconds(1));
    TraceEvent third =
        new TraceEvent(
            "trace-a",
            "req-1",
            "assess-risk",
            "completed",
            30L,
            "ok",
            null,
            "HIGH",
            null,
            null,
            base.plusSeconds(2));
    TraceEvent otherTrace =
        new TraceEvent(
            "trace-b",
            "req-2",
            "pipeline",
            "analysis_started",
            null,
            "ok",
            null,
            null,
            null,
            null,
            base.plusSeconds(3));
    repository.saveAll(List.of(third, first, otherTrace, second));

    List<TraceEvent> events = repository.findByTraceIdOrderByCreatedAtAsc("trace-a");

    assertThat(events).hasSize(3);
    assertThat(events.get(0).getEvent()).isEqualTo("analysis_started");
    assertThat(events.get(1).getNode()).isEqualTo("classify");
    assertThat(events.get(1).getDurationMs()).isEqualTo(12L);
    assertThat(events.get(1).getModel()).isEqualTo("gpt-x");
    assertThat(events.get(2).getRisk()).isEqualTo("HIGH");
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.getTraceId()).isEqualTo("trace-a");
              assertThat(event.getRequestId()).isEqualTo("req-1");
              assertThat(event.getId()).isNotNull();
              assertThat(event.getCreatedAt()).isNotNull();
            });
    assertThat(repository.findByTraceIdOrderByCreatedAtAsc("trace-b")).hasSize(1);
  }
}
