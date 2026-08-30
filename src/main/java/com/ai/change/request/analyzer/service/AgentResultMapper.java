package com.ai.change.request.analyzer.service;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskLevel;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentResultMapper {

  public ChangeAnalysis toAnalysis(ChangeRequest request, Map<String, Object> result) {
    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);
    if (result == null) {
      return analysis;
    }
    RiskLevel level = parseLevel(result.get("risk"));
    Double confidence = parseConfidence(result.get("confidence"));
    if (level != null && confidence != null && confidence >= 0.0 && confidence <= 1.0) {
      analysis.setRiskAssessment(new RiskAssessment(level, confidence, parseRationale(result)));
    }
    return analysis;
  }

  /**
   * Mapeia {@code final_result.security_assessment} para eventos de seguranca tipados, com dedupe
   * por (type, source, evidence); eventos malformados sao descartados.
   */
  public List<SecurityEvent> toSecurityEvents(Map<String, Object> result) {
    if (result == null || !(result.get("security_assessment") instanceof Map<?, ?> assessment)) {
      return List.of();
    }
    if (!(assessment.get("events") instanceof List<?> rawEvents)) {
      return List.of();
    }
    List<SecurityEvent> events = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Object item : rawEvents) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      String type = stringOf(map.get("type"));
      String source = stringOf(map.get("source"));
      String evidence = stringOf(map.get("evidence"));
      String action = stringOf(map.get("action"));
      if (type == null || source == null || evidence == null) {
        continue;
      }
      if (!seen.add(type + "|" + source + "|" + evidence)) {
        continue;
      }
      events.add(new SecurityEvent(type, source, evidence, action));
    }
    return List.copyOf(events);
  }

  private String stringOf(Object raw) {
    if (raw instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  private RiskLevel parseLevel(Object raw) {
    if (!(raw instanceof String text)) {
      return null;
    }
    try {
      return RiskLevel.valueOf(text.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Double parseConfidence(Object raw) {
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    if (raw instanceof String text) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private String parseRationale(Map<String, Object> result) {
    Object rationale = result.get("rationale");
    return rationale instanceof String text ? text : null;
  }
}
