package com.ai.change.request.analyzer.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceIdLoggingTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void everyLogLineOfARequestCarriesTheSameTraceId() throws Exception {
    ListAppender<ILoggingEvent> appender = attachAppender();
    try {
      var result = mockMvc.perform(MockMvcRequestBuilders.get("/any-path")).andReturn();

      String responseTraceId = result.getResponse().getHeader("X-Trace-Id");
      assertThat(responseTraceId).isNotBlank();

      List<ILoggingEvent> events =
          appender.list.stream()
              .filter(e -> e.getMDCPropertyMap().get("trace_id") != null)
              .toList();
      assertThat(events).isNotEmpty();
      assertThat(events)
          .allMatch(e -> responseTraceId.equals(e.getMDCPropertyMap().get("trace_id")));
    } finally {
      detachAppender(appender);
    }
  }

  @Test
  void requestsWithoutHeaderGenerateDistinctTraceIds() throws Exception {
    var first =
        mockMvc
            .perform(MockMvcRequestBuilders.get("/any-path"))
            .andReturn()
            .getResponse()
            .getHeader("X-Trace-Id");
    var second =
        mockMvc
            .perform(MockMvcRequestBuilders.get("/any-path"))
            .andReturn()
            .getResponse()
            .getHeader("X-Trace-Id");
    assertThat(first).isNotBlank();
    assertThat(second).isNotBlank();
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void requestLogsCarryStandardizedFieldsAndRequestId() throws Exception {
    ListAppender<ILoggingEvent> appender = attachAppender();
    try {
      mockMvc.perform(MockMvcRequestBuilders.get("/any-path")).andReturn();

      assertThat(appender.list)
          .anyMatch(
              e ->
                  e.getFormattedMessage().contains("node=http")
                      && e.getFormattedMessage().contains("event=request_started")
                      && e.getMDCPropertyMap().get("request_id") != null
                      && e.getMDCPropertyMap().get("trace_id") != null);
      assertThat(appender.list)
          .anyMatch(
              e ->
                  e.getFormattedMessage().contains("event=request_finished")
                      && e.getFormattedMessage().contains("status=")
                      && e.getFormattedMessage().contains("duration_ms="));
    } finally {
      detachAppender(appender);
    }
  }

  private ListAppender<ILoggingEvent> attachAppender() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    return appender;
  }

  private void detachAppender(ListAppender<ILoggingEvent> appender) {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(appender);
    appender.stop();
  }
}
