package com.ai.change.request.analyzer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String TRACE_ID_ATTRIBUTE = "traceId";

  private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = request.getHeader(TRACE_ID_HEADER);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
    }
    String requestId = UUID.randomUUID().toString();
    request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
    MDC.put("trace_id", traceId);
    MDC.put("request_id", requestId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    long start = System.nanoTime();
    log.info(
        "http_request_started node=http event=request_started method={} path={}",
        request.getMethod(),
        request.getRequestURI());
    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "http_request_finished node=http event=request_finished status={} duration_ms={}",
          response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("trace_id");
      MDC.remove("request_id");
    }
  }
}
