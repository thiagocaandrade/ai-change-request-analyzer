package com.ai.change.request.analyzer.observability;

import com.ai.change.request.analyzer.web.GlobalExceptionHandler.TraceNotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reconstrucao de execucao por trace_id: segundo sinal observavel da aplicacao. */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

  private final TraceService traceService;

  public TraceController(TraceService traceService) {
    this.traceService = traceService;
  }

  @GetMapping("/{traceId}")
  public List<TraceEventDto> getTrace(@PathVariable String traceId) {
    List<TraceEvent> events = traceService.findByTraceId(traceId);
    if (events.isEmpty()) {
      throw new TraceNotFoundException(traceId);
    }
    return events.stream().map(TraceEventDto::from).toList();
  }
}
