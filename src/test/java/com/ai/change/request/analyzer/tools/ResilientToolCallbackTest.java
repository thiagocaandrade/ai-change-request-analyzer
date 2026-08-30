package com.ai.change.request.analyzer.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceEventRepository;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class ResilientToolCallbackTest {

  private SimpleMeterRegistry meterRegistry;
  private AnalysisMetrics metrics;
  private ResilienceExecutor executor;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new AnalysisMetrics(meterRegistry);
    TraceService traceService = new TraceService(mock(TraceEventRepository.class));
    executor = new ResilienceExecutor(traceService, 0, 10);
  }

  @AfterEach
  void cleanup() {
    MDC.clear();
  }

  @Test
  void failureAfterRetriesIsRecordedAndAnalysisContinues() {
    AtomicInteger calls = new AtomicInteger();
    ToolCallback failing =
        new FailingToolCallback(
            "search_code",
            input -> {
              calls.incrementAndGet();
              throw new IllegalStateException("boom");
            });
    ResilientToolCallback callback = new ResilientToolCallback(failing, 2000, executor, metrics);

    String result = callback.call("{\"query\":\"x\"}");

    assertThat(calls.get()).isEqualTo(3);
    assertThat(result).contains("tool_failed_after_retries").contains("search_code");
    assertThat(meterRegistry.get(AnalysisMetrics.TOOL_CALLS).counter().count()).isEqualTo(3.0);
    assertThat(meterRegistry.get(AnalysisMetrics.TOOL_ERRORS).counter().count()).isEqualTo(3.0);
  }

  @Test
  void transientFailureRecoversWithinRetryLimit() {
    AtomicInteger calls = new AtomicInteger();
    ToolCallback flaky =
        new FailingToolCallback(
            "get_file",
            input -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("transiente");
              }
              return "{\"path\":\"ok\"}";
            });
    ResilientToolCallback callback = new ResilientToolCallback(flaky, 2000, executor, metrics);

    String result = callback.call("{\"path\":\"x\"}");

    assertThat(calls.get()).isEqualTo(2);
    assertThat(result).contains("ok");
    assertThat(meterRegistry.get(AnalysisMetrics.TOOL_ERRORS).counter().count()).isEqualTo(1.0);
  }

  @Test
  void timeoutAfterRetriesReturnsStructuredError() {
    ToolCallback slow =
        new FailingToolCallback(
            "search_code",
            input -> {
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "{\"results\":[]}";
            });
    ResilientToolCallback callback = new ResilientToolCallback(slow, 50, executor, metrics);

    String result = callback.call("{\"query\":\"x\"}");

    assertThat(result).contains("tool_failed_after_retries");
  }

  @Test
  void failureLogCarriesTraceId() {
    Logger logger = (Logger) LoggerFactory.getLogger(ResilienceExecutor.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    MDC.put("trace_id", "trace-tools-1");
    try {
      ToolCallback failing =
          new FailingToolCallback(
              "get_file",
              input -> {
                throw new IllegalStateException("boom");
              });
      new ResilientToolCallback(failing, 2000, executor, metrics).call("{\"path\":\"x\"}");
    } finally {
      logger.detachAppender(appender);
      MDC.clear();
    }

    assertThat(appender.list)
        .anyMatch(
            event ->
                event.getMessage().contains("resilience_fallback")
                    && "trace-tools-1".equals(event.getMDCPropertyMap().get("trace_id")));
  }

  static class FailingToolCallback implements ToolCallback {

    private final String name;
    private final ToolInvoker invoker;

    FailingToolCallback(String name, ToolInvoker invoker) {
      this.name = name;
      this.invoker = invoker;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder().name(name).description("test").inputSchema("{}").build();
    }

    @Override
    public String call(String toolInput) {
      return invoker.invoke(toolInput);
    }
  }

  interface ToolInvoker {
    String invoke(String input);
  }
}
