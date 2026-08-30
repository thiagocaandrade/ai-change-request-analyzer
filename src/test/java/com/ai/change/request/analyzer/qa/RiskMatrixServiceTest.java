package com.ai.change.request.analyzer.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.ai.dto.AiResults.RiskCategorySuggestionDto;
import com.ai.change.request.analyzer.qa.RiskMatrixService.Impact;
import com.ai.change.request.analyzer.qa.RiskMatrixService.Likelihood;
import com.ai.change.request.analyzer.qa.RiskMatrixService.Priority;
import com.ai.change.request.analyzer.qa.RiskMatrixService.RiskCategoryAssessment;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskMatrixServiceTest {

  private final RiskMatrixService service = new RiskMatrixService();

  @Test
  void fullCombinationTableIsDeterministic() {
    assertThat(service.priority(Impact.HIGH, Likelihood.HIGH)).isEqualTo(Priority.HIGH);
    assertThat(service.priority(Impact.HIGH, Likelihood.MEDIUM)).isEqualTo(Priority.HIGH);
    assertThat(service.priority(Impact.HIGH, Likelihood.LOW)).isEqualTo(Priority.MEDIUM);
    assertThat(service.priority(Impact.MEDIUM, Likelihood.HIGH)).isEqualTo(Priority.HIGH);
    assertThat(service.priority(Impact.MEDIUM, Likelihood.MEDIUM)).isEqualTo(Priority.MEDIUM);
    assertThat(service.priority(Impact.MEDIUM, Likelihood.LOW)).isEqualTo(Priority.LOW);
    assertThat(service.priority(Impact.LOW, Likelihood.HIGH)).isEqualTo(Priority.MEDIUM);
    assertThat(service.priority(Impact.LOW, Likelihood.MEDIUM)).isEqualTo(Priority.LOW);
    assertThat(service.priority(Impact.LOW, Likelihood.LOW)).isEqualTo(Priority.LOW);
  }

  @Test
  void outOfRangeSuggestionsAreNormalizedToDeterministicDefaults() {
    assertThat(service.normalizeImpact("CRITICAL")).isEqualTo(Impact.MEDIUM);
    assertThat(service.normalizeImpact("")).isEqualTo(Impact.MEDIUM);
    assertThat(service.normalizeImpact(null)).isEqualTo(Impact.MEDIUM);
    assertThat(service.normalizeImpact("high")).isEqualTo(Impact.HIGH);
    assertThat(service.normalizeLikelihood("rarissima")).isEqualTo(Likelihood.MEDIUM);
    assertThat(service.normalizePriority("urgente")).isEqualTo(Priority.MEDIUM);
  }

  @Test
  void everyRequiredCategoryIsEvaluatedAndApplicableOnesGetMatrixPriority() {
    List<RiskCategoryAssessment> assessments =
        service.evaluate(
            List.of(
                new RiskCategorySuggestionDto(
                    RiskMatrixService.CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION,
                    "HIGH",
                    "LOW"),
                new RiskCategorySuggestionDto(
                    RiskMatrixService.CATEGORY_PROMPT_INJECTION, "high", "baixa")),
            "Alterar o desconto de clientes VIP de 10% para 15%.");

    assertThat(assessments).hasSize(4);
    assertThat(assessments)
        .allMatch(
            assessment ->
                RiskMatrixService.REQUIRED_CATEGORIES.contains(assessment.category()));

    RiskCategoryAssessment financial =
        assessments.stream()
            .filter(
                assessment ->
                    assessment
                        .category()
                        .equals(RiskMatrixService.CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION))
            .findFirst()
            .orElseThrow();
    assertThat(financial.applicable()).isTrue();
    assertThat(financial.impact()).isEqualTo(Impact.HIGH);
    assertThat(financial.likelihood()).isEqualTo(Likelihood.LOW);
    assertThat(financial.priority()).isEqualTo(Priority.MEDIUM);
    assertThat(financial.justification()).contains("matriz deterministica");

    RiskCategoryAssessment injection =
        assessments.stream()
            .filter(
                assessment ->
                    assessment.category().equals(RiskMatrixService.CATEGORY_PROMPT_INJECTION))
            .findFirst()
            .orElseThrow();
    assertThat(injection.applicable()).isTrue();
    assertThat(injection.impact()).isEqualTo(Impact.HIGH);
    assertThat(injection.likelihood()).isEqualTo(Likelihood.MEDIUM);
    assertThat(injection.priority()).isEqualTo(Priority.HIGH);
  }

  @Test
  void keywordHintAppliesDeterministicDefaultsAndPriority() {
    List<RiskCategoryAssessment> assessments =
        service.evaluate(List.of(), "Alterar desconto VIP de 10% para 15%");

    RiskCategoryAssessment financial =
        assessments.stream()
            .filter(
                assessment ->
                    assessment
                        .category()
                        .equals(RiskMatrixService.CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION))
            .findFirst()
            .orElseThrow();
    assertThat(financial.applicable()).isTrue();
    assertThat(financial.impact()).isEqualTo(Impact.HIGH);
    assertThat(financial.likelihood()).isEqualTo(Likelihood.MEDIUM);
    assertThat(financial.priority()).isEqualTo(Priority.HIGH);
    assertThat(financial.justification()).contains("defaults deterministicos");
  }

  @Test
  void categoriesWithoutSuggestionOrHintAreRecordedAsNotApplicable() {
    List<RiskCategoryAssessment> assessments =
        service.evaluate(List.of(), "Refatorar o modulo de relatorios");

    RiskCategoryAssessment injection =
        assessments.stream()
            .filter(
                assessment ->
                    assessment.category().equals(RiskMatrixService.CATEGORY_PROMPT_INJECTION))
            .findFirst()
            .orElseThrow();
    assertThat(injection.applicable()).isFalse();
    assertThat(injection.priority()).isNull();
    assertThat(injection.justification()).contains("nao aplicavel");
  }

  @Test
  void categoryForAndTopPriorityFollowDeterministicRules() {
    assertThat(service.categoryFor("cobrir teste do desconto VIP")).isEqualTo(
        RiskMatrixService.CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION);
    assertThat(service.categoryFor("teste de acesso a ferramenta")).isEqualTo(
        RiskMatrixService.CATEGORY_UNAUTHORIZED_TOOL_ACCESS);
    assertThat(service.categoryFor("teste generico sem palavra chave")).isNull();

    List<RiskCategoryAssessment> assessments =
        service.evaluate(List.of(), "Alterar desconto VIP");
    RiskCategoryAssessment top = service.topPriority(assessments);
    assertThat(top).isNotNull();
    assertThat(top.priority()).isEqualTo(Priority.HIGH);
    assertThat(top.category()).isEqualTo(
        RiskMatrixService.CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION);
  }
}
