package com.ai.change.request.analyzer.qa;

import com.ai.change.request.analyzer.ai.dto.AiResults.RiskCategorySuggestionDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Matriz de priorizacao de testes 100% deterministica: combinacao fixa Impact x Likelihood →
 * prioridade, calculada SEMPRE pela aplicacao. O modelo apenas SUGERE impacto e probabilidade por
 * categoria; sugestoes fora de faixa sao normalizadas e a prioridade final nunca vem diretamente da
 * sugestao.
 */
@Service
public class RiskMatrixService {

  public enum Impact {
    LOW,
    MEDIUM,
    HIGH
  }

  public enum Likelihood {
    LOW,
    MEDIUM,
    HIGH
  }

  public enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

  public static final String CATEGORY_PROMPT_INJECTION = "prompt_injection";
  public static final String CATEGORY_UNAUTHORIZED_TOOL_ACCESS = "unauthorized_tool_access";
  public static final String CATEGORY_INCORRECT_HIGH_LOW_CLASSIFICATION =
      "incorrect_high_low_classification";
  public static final String CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION =
      "financial_business_rule_regression";

  /** Categorias obrigatorias avaliadas em toda analise (ordem canonica fixa). */
  public static final List<String> REQUIRED_CATEGORIES =
      List.of(
          CATEGORY_PROMPT_INJECTION,
          CATEGORY_UNAUTHORIZED_TOOL_ACCESS,
          CATEGORY_INCORRECT_HIGH_LOW_CLASSIFICATION,
          CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION);

  private static final Map<String, List<String>> KEYWORDS = keywords();

  /** Avaliacao deterministica de uma categoria de risco. */
  public record RiskCategoryAssessment(
      String category,
      boolean applicable,
      Impact impact,
      Likelihood likelihood,
      Priority priority,
      String justification) {}

  /** Tabela fixa da matriz (regra documentada, nunca alterada pelo modelo). */
  public Priority priority(Impact impact, Likelihood likelihood) {
    return switch (impact) {
      case HIGH ->
          switch (likelihood) {
            case HIGH, MEDIUM -> Priority.HIGH;
            case LOW -> Priority.MEDIUM;
          };
      case MEDIUM ->
          switch (likelihood) {
            case HIGH -> Priority.HIGH;
            case MEDIUM -> Priority.MEDIUM;
            case LOW -> Priority.LOW;
          };
      case LOW ->
          switch (likelihood) {
            case HIGH -> Priority.MEDIUM;
            case MEDIUM, LOW -> Priority.LOW;
          };
    };
  }

  /** Normaliza sugestao do modelo; valor fora de faixa cai no padrao determinístico. */
  public Impact normalizeImpact(String raw) {
    return parse(raw, Impact.MEDIUM);
  }

  public Likelihood normalizeLikelihood(String raw) {
    return parse(raw, Likelihood.MEDIUM);
  }

  public Priority normalizePriority(String raw) {
    return parse(raw, Priority.MEDIUM);
  }

  private <T extends Enum<T>> T parse(String raw, T fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  /**
   * Avalia as 4 categorias obrigatorias: aplicavel quando o modelo sugeriu a categoria ou o texto
   * da alteracao indica a categoria (palavras-chave determinísticas). Sem sugestao, aplicam-se
   * defaults deterministicos (financeiro: impacto HIGH; demais: MEDIUM x MEDIUM).
   */
  public List<RiskCategoryAssessment> evaluate(
      List<RiskCategorySuggestionDto> suggestions, String changeText) {
    Map<String, RiskCategorySuggestionDto> byCategory = new LinkedHashMap<>();
    for (RiskCategorySuggestionDto suggestion :
        suggestions == null ? List.<RiskCategorySuggestionDto>of() : suggestions) {
      if (suggestion == null || suggestion.category() == null) {
        continue;
      }
      byCategory.putIfAbsent(suggestion.category().trim(), suggestion);
    }
    String text = changeText == null ? "" : changeText.toLowerCase(Locale.ROOT);
    List<RiskCategoryAssessment> assessments = new ArrayList<>();
    for (String category : REQUIRED_CATEGORIES) {
      RiskCategorySuggestionDto suggestion = byCategory.get(category);
      boolean keyword = matchesCategory(category, text);
      if (suggestion == null && !keyword) {
        assessments.add(
            new RiskCategoryAssessment(
                category,
                false,
                null,
                null,
                null,
                "categoria avaliada e nao aplicavel a alteracao (sem sugestao do modelo e sem indicio no texto)"));
        continue;
      }
      Impact impact =
          suggestion != null ? normalizeImpact(suggestion.impact()) : defaultImpact(category);
      Likelihood likelihood =
          suggestion != null ? normalizeLikelihood(suggestion.likelihood()) : defaultLikelihood();
      Priority priority = priority(impact, likelihood);
      assessments.add(
          new RiskCategoryAssessment(
              category,
              true,
              impact,
              likelihood,
              priority,
              justification(category, impact, likelihood, priority, suggestion == null)));
    }
    return List.copyOf(assessments);
  }

  /** Categoria deterministica que melhor descreve um texto (recomendacao/finding). */
  public String categoryFor(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lowered = text.toLowerCase(Locale.ROOT);
    for (String category : REQUIRED_CATEGORIES) {
      if (matchesCategory(category, lowered)) {
        return category;
      }
    }
    return null;
  }

  /** Categoria aplicavel de maior prioridade (desempate pela ordem canonica). */
  public RiskCategoryAssessment topPriority(List<RiskCategoryAssessment> assessments) {
    RiskCategoryAssessment top = null;
    for (RiskCategoryAssessment assessment : assessments) {
      if (!assessment.applicable() || assessment.priority() == null) {
        continue;
      }
      if (top == null
          || assessment.priority().ordinal() < top.priority().ordinal()
          || (assessment.priority() == top.priority()
              && REQUIRED_CATEGORIES.indexOf(assessment.category())
                  < REQUIRED_CATEGORIES.indexOf(top.category()))) {
        top = assessment;
      }
    }
    return top;
  }

  private Impact defaultImpact(String category) {
    return CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION.equals(category)
        ? Impact.HIGH
        : Impact.MEDIUM;
  }

  private Likelihood defaultLikelihood() {
    return Likelihood.MEDIUM;
  }

  private boolean matchesCategory(String category, String loweredText) {
    for (String keyword : KEYWORDS.getOrDefault(category, List.of())) {
      if (loweredText.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private String justification(
      String category,
      Impact impact,
      Likelihood likelihood,
      Priority priority,
      boolean deterministicDefault) {
    String origin =
        deterministicDefault
            ? "defaults deterministicos (indicio no texto da alteracao)"
            : "sugestao do modelo normalizada";
    return String.format(
        "categoria %s avaliada com %s: impacto=%s, probabilidade=%s -> combinacao %s x %s = %s (matriz deterministica)",
        category, origin, impact, likelihood, impact, likelihood, priority);
  }

  private static Map<String, List<String>> keywords() {
    Map<String, List<String>> map = new LinkedHashMap<>();
    map.put(
        CATEGORY_PROMPT_INJECTION,
        List.of("ignore", "instruç", "instruc", "classifique", "inject", "prompt", "adversar"));
    map.put(
        CATEGORY_UNAUTHORIZED_TOOL_ACCESS,
        List.of("tool", "ferramenta", "shell", "acesso", "comando"));
    map.put(
        CATEGORY_INCORRECT_HIGH_LOW_CLASSIFICATION,
        List.of("risco", "risk", "high", "low", "classifica", "classify"));
    map.put(
        CATEGORY_FINANCIAL_BUSINESS_RULE_REGRESSION,
        List.of(
            "desconto",
            "discount",
            "preço",
            "preco",
            "price",
            "vip",
            "financeir",
            "tarifa",
            "comissão",
            "comissao",
            "cobrança",
            "cobranca"));
    return Map.copyOf(map);
  }
}
