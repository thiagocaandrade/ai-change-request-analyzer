package com.ai.change.request.analyzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.RiskLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResultMapperTest {

  private final AgentResultMapper mapper = new AgentResultMapper();

  private ChangeRequest request() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    return request;
  }

  @Test
  void mapsCompleteResultWithRisk() {
    ChangeAnalysis analysis =
        mapper.toAnalysis(
            request(),
            Map.of(
                "risk", "HIGH",
                "confidence", 0.9,
                "rationale", "regra financeira",
                "processed_text", "Alterar desconto VIP"));

    assertThat(analysis.getRiskAssessment()).isNotNull();
    assertThat(analysis.getRiskAssessment().getLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(analysis.getRiskAssessment().getConfidence()).isEqualTo(0.9);
    assertThat(analysis.getRiskAssessment().getRationale()).isEqualTo("regra financeira");
  }

  @Test
  void mapsPartialResultWithoutRiskToEmptyAnalysis() {
    ChangeAnalysis analysis =
        mapper.toAnalysis(request(), Map.of("processed_text", "Alterar desconto VIP"));

    assertThat(analysis).isNotNull();
    assertThat(analysis.getRiskAssessment()).isNull();
    assertThat(analysis.getFindings()).isEmpty();
  }

  @Test
  void nullResultNeverFails() {
    ChangeAnalysis analysis = mapper.toAnalysis(request(), null);

    assertThat(analysis).isNotNull();
    assertThat(analysis.getRiskAssessment()).isNull();
  }

  @Test
  void unknownRiskLevelIsIgnored() {
    ChangeAnalysis analysis =
        mapper.toAnalysis(request(), Map.of("risk", "CATASTROPHIC", "confidence", 0.9));

    assertThat(analysis.getRiskAssessment()).isNull();
  }

  @Test
  void invalidConfidenceIsIgnored() {
    ChangeAnalysis analysis =
        mapper.toAnalysis(request(), Map.of("risk", "HIGH", "confidence", 2.5));

    assertThat(analysis.getRiskAssessment()).isNull();
  }

  @Test
  void stringConfidenceIsParsedDefensively() {
    ChangeAnalysis analysis =
        mapper.toAnalysis(request(), Map.of("risk", "low", "confidence", "0.4"));

    assertThat(analysis.getRiskAssessment()).isNotNull();
    assertThat(analysis.getRiskAssessment().getLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(analysis.getRiskAssessment().getConfidence()).isEqualTo(0.4);
  }
}
