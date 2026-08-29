package com.ai.change.request.analyzer.service;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskLevel;
import java.util.Map;
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
