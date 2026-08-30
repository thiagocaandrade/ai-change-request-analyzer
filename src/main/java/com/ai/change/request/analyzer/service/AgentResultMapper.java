package com.ai.change.request.analyzer.service;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskLevel;
import com.ai.change.request.analyzer.security.SecurityAssessmentService.SecurityEvent;
import com.ai.change.request.analyzer.web.AgentGatewayDtos;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaBlockDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaFindingDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.QaRecordDto;
import com.ai.change.request.analyzer.web.AgentGatewayDtos.RiskMatrixEntryDto;
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
    for (AgentGatewayDtos.TestRecommendation recommendation : toRecommendations(result)) {
      analysis.addRecommendation(
          new com.ai.change.request.analyzer.domain.TestRecommendation(
              recommendation.component(),
              recommendation.description(),
              recommendation.priority(),
              recommendation.priorityJustification(),
              recommendation.riskCategory(),
              recommendation.refined()));
    }
    return analysis;
  }

  /**
   * Recomendacoes do resultado: preferencia pelo bloco {@code qa.recommendations} (priorizadas com
   * justificativa); sem bloco QA, usa o {@code test_plan} plano (contrato antigo preservado).
   */
  public List<AgentGatewayDtos.TestRecommendation> toRecommendations(Map<String, Object> result) {
    if (result == null) {
      return List.of();
    }
    Object rawQa = result.get("qa");
    if (rawQa instanceof Map<?, ?> qa && qa.get("recommendations") instanceof List<?> rawItems) {
      List<AgentGatewayDtos.TestRecommendation> recommendations = new ArrayList<>();
      for (Object item : rawItems) {
        if (!(item instanceof Map<?, ?> map)) {
          continue;
        }
        String component = stringOf(map.get("component"));
        String description = stringOf(map.get("description"));
        if (component == null || description == null) {
          continue;
        }
        recommendations.add(
            new AgentGatewayDtos.TestRecommendation(
                component,
                description,
                stringOf(map.get("priority")),
                stringOf(map.get("priorityJustification")),
                stringOf(map.get("riskCategory")),
                refinedOf(map.get("refined"))));
      }
      return List.copyOf(recommendations);
    }
    if (result.get("test_plan") instanceof List<?> rawItems) {
      List<AgentGatewayDtos.TestRecommendation> recommendations = new ArrayList<>();
      for (Object item : rawItems) {
        if (!(item instanceof Map<?, ?> map)) {
          continue;
        }
        String component = stringOf(map.get("component"));
        String description = stringOf(map.get("description"));
        if (component == null || description == null) {
          continue;
        }
        recommendations.add(
            new AgentGatewayDtos.TestRecommendation(
                component, description, stringOf(map.get("priority")), null, null, null));
      }
      return List.copyOf(recommendations);
    }
    return List.of();
  }

  /** Bloco QA tipado do resultado do agente (campo opcional; ausente → null). */
  public QaBlockDto toQa(Map<String, Object> result) {
    if (result == null || !(result.get("qa") instanceof Map<?, ?> qa)) {
      return null;
    }
    List<QaFindingDto> findings = new ArrayList<>();
    if (qa.get("findings") instanceof List<?> rawFindings) {
      for (Object item : rawFindings) {
        if (!(item instanceof Map<?, ?> map)) {
          continue;
        }
        String component = stringOf(map.get("component"));
        String description = stringOf(map.get("description"));
        if (component == null || description == null) {
          continue;
        }
        findings.add(
            new QaFindingDto(
                component,
                description,
                stringOf(map.get("severity")),
                stringOf(map.get("source"))));
      }
    }
    List<RiskMatrixEntryDto> matrix = new ArrayList<>();
    if (qa.get("riskMatrix") instanceof List<?> rawMatrix) {
      for (Object item : rawMatrix) {
        if (!(item instanceof Map<?, ?> map)) {
          continue;
        }
        String category = stringOf(map.get("category"));
        if (category == null) {
          continue;
        }
        matrix.add(
            new RiskMatrixEntryDto(
                category,
                Boolean.TRUE.equals(map.get("applicable")),
                stringOf(map.get("impact")),
                stringOf(map.get("likelihood")),
                stringOf(map.get("priority")),
                stringOf(map.get("justification"))));
      }
    }
    QaRecordDto record = null;
    if (qa.get("record") instanceof Map<?, ?> rawRecord) {
      String stage = stringOf(rawRecord.get("stage"));
      String promptVersion = stringOf(rawRecord.get("promptVersion"));
      if (stage != null && promptVersion != null) {
        record =
            new QaRecordDto(
                stage,
                promptVersion,
                stringOf(rawRecord.get("resultJson")),
                Boolean.TRUE.equals(rawRecord.get("degraded")),
                intOf(rawRecord.get("iterations")),
                stringOf(rawRecord.get("traceId")));
      }
    }
    return new QaBlockDto(
        List.copyOf(findings),
        toRecommendations(result),
        List.copyOf(matrix),
        Boolean.TRUE.equals(qa.get("degraded")),
        record);
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

  private Boolean refinedOf(Object raw) {
    if (raw instanceof Boolean bool) {
      return bool;
    }
    return null;
  }

  private int intOf(Object raw) {
    if (raw instanceof Number number) {
      return number.intValue();
    }
    return 0;
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
