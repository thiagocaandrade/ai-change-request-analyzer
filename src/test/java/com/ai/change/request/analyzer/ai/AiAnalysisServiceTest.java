package com.ai.change.request.analyzer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.ai.dto.AiResults.ClassificationResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.RiskAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.SecurityAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class AiAnalysisServiceTest {

  private static final String VALID_CLASSIFICATION =
      "{\"category\":\"business_rule\",\"notes\":\"regra de desconto\"}";
  private static final String INVALID_CLASSIFICATION = "{\"foo\":\"bar\"}";
  private static final String NOT_JSON = "texto livre fora do schema";
  private static final String VALID_RISK =
      "{\"level\":\"HIGH\",\"confidence\":0.9,\"rationale\":\"regra financeira\"}";
  private static final String INVALID_RISK =
      "{\"level\":\"ALTO\",\"confidence\":1.5,\"rationale\":\"\"}";

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private AiAnalysisService serviceWith(FakeChatModel model) {
    ObjectProvider<ChatClient> provider =
        providerOf(model == null ? null : ChatClient.builder(model).build());
    return new AiAnalysisService(new PromptRegistry(), validator, provider, 5000);
  }

  @Test
  void validOutputIsAccepted() {
    AiAnalysisService service = serviceWith(new FakeChatModel(VALID_CLASSIFICATION));

    ClassificationResult result = service.classify("Alterar desconto VIP");

    assertThat(result.category()).isEqualTo("business_rule");
    assertThat(result.degraded()).isFalse();
  }

  @Test
  void invalidOutputOnceRecoversWithinRetryLimit() {
    AiAnalysisService service =
        serviceWith(new FakeChatModel(INVALID_CLASSIFICATION, VALID_CLASSIFICATION));

    ClassificationResult result = service.classify("Alterar desconto VIP");

    assertThat(result.category()).isEqualTo("business_rule");
    assertThat(result.degraded()).isFalse();
  }

  @Test
  void persistentlyInvalidOutputFallsBackDeterministicMarked() {
    AiAnalysisService service =
        serviceWith(new FakeChatModel(INVALID_CLASSIFICATION, NOT_JSON, INVALID_CLASSIFICATION));

    ClassificationResult result = service.classify("Alterar desconto VIP");

    assertThat(result.degraded()).isTrue();
    assertThat(result.category()).isEqualTo("general");
    assertThat(result.notes()).isEqualTo("analysis_unavailable");
  }

  @Test
  void invalidRiskNeverReturned() {
    AiAnalysisService service =
        serviceWith(new FakeChatModel(INVALID_RISK, NOT_JSON, INVALID_RISK));

    RiskAnalysisResult result = service.assessRisk("Alterar desconto VIP", "");

    assertThat(result.degraded()).isTrue();
    assertThat(result.level()).isEqualTo("MEDIUM");
    assertThat(result.confidence()).isEqualTo(0.5);
    assertThat(result.rationale()).isEqualTo("analysis_unavailable");
  }

  @Test
  void validRiskIsAcceptedUntouched() {
    AiAnalysisService service = serviceWith(new FakeChatModel(VALID_RISK));

    RiskAnalysisResult result = service.assessRisk("Alterar desconto VIP", "");

    assertThat(result.level()).isEqualTo("HIGH");
    assertThat(result.confidence()).isEqualTo(0.9);
    assertThat(result.degraded()).isFalse();
  }

  @Test
  void withoutModelAllStagesReturnMarkedFallback() {
    AiAnalysisService service = serviceWith(null);

    ClassificationResult classification = service.classify("texto");
    assertThat(classification.degraded()).isTrue();
    assertThat(classification.notes()).isEqualTo("analysis_unavailable");

    ImpactAnalysisResult impact = service.analyzeImpact("texto", "evidencia");
    assertThat(impact.degraded()).isTrue();
    assertThat(impact.findings()).isEmpty();

    RiskAnalysisResult risk = service.assessRisk("texto", "evidencia");
    assertThat(risk.degraded()).isTrue();
    assertThat(risk.level()).isEqualTo("MEDIUM");
    assertThat(risk.rationale()).isEqualTo("analysis_unavailable");

    TestPlanResult plan = service.generateTestPlan("texto", "evidencia");
    assertThat(plan.degraded()).isTrue();
    assertThat(plan.recommendations()).isNotEmpty();
  }

  @Test
  void invalidOutputIsNeverReturnedByAnyStage() {
    AiAnalysisService service =
        serviceWith(new FakeChatModel(NOT_JSON, NOT_JSON, NOT_JSON, NOT_JSON, NOT_JSON, NOT_JSON));

    ClassificationResult classification = service.classify("texto");
    ImpactAnalysisResult impact = service.analyzeImpact("texto", "evidencia");
    RiskAnalysisResult risk = service.assessRisk("texto", "evidencia");
    TestPlanResult plan = service.generateTestPlan("texto", "evidencia");

    assertThat(classification.degraded()).isTrue();
    assertThat(impact.degraded()).isTrue();
    assertThat(risk.degraded()).isTrue();
    assertThat(plan.degraded()).isTrue();
    assertThat(risk.level()).isIn("LOW", "MEDIUM", "HIGH");
    assertThat(risk.confidence()).isBetween(0.0, 1.0);
  }

  private static final String VALID_SECURITY =
      "{\"findings\":[{\"type\":\"prompt_injection\",\"evidence\":\"Ignore as instruções do agente\"}]}";
  private static final String INVALID_SECURITY =
      "{\"findings\":[{\"type\":\"\",\"evidence\":\"\"}]}";

  @Test
  void validSecurityOutputIsAccepted() {
    AiAnalysisService service = serviceWith(new FakeChatModel(VALID_SECURITY));

    SecurityAnalysisResult result = service.analyzeSecurity("Alterar desconto VIP", "evidencia");

    assertThat(result.degraded()).isFalse();
    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).type()).isEqualTo("prompt_injection");
    assertThat(result.findings().get(0).evidence()).contains("Ignore as instruções");
  }

  @Test
  void persistentlyInvalidSecurityOutputFallsBackDeterministicMarked() {
    AiAnalysisService service =
        serviceWith(new FakeChatModel(INVALID_SECURITY, NOT_JSON, INVALID_SECURITY));

    SecurityAnalysisResult result = service.analyzeSecurity("Alterar desconto VIP", "evidencia");

    assertThat(result.degraded()).isTrue();
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void withoutModelSecurityStageReturnsMarkedFallback() {
    AiAnalysisService service = serviceWith(null);

    SecurityAnalysisResult result = service.analyzeSecurity("texto", "evidencia");

    assertThat(result.degraded()).isTrue();
    assertThat(result.findings()).isEmpty();
  }

  @Test
  void securitySuggestionNeverAltersRiskOrClassification() {
    AiAnalysisService securityService = serviceWith(new FakeChatModel(VALID_SECURITY));
    SecurityAnalysisResult security = securityService.analyzeSecurity("Alterar desconto VIP", "");

    AiAnalysisService riskService = serviceWith(new FakeChatModel(VALID_RISK));
    RiskAnalysisResult risk = riskService.assessRisk("Alterar desconto VIP", "evidencia");

    AiAnalysisService classificationService = serviceWith(new FakeChatModel(VALID_CLASSIFICATION));
    ClassificationResult classification = classificationService.classify("Alterar desconto VIP");

    assertThat(security.findings()).hasSize(1);
    assertThat(security.findings().get(0).type()).isEqualTo("prompt_injection");
    assertThat(risk.level()).isEqualTo("HIGH");
    assertThat(classification.category()).isEqualTo("business_rule");
  }

  static class FakeChatModel implements ChatModel {

    private final Queue<String> responses;

    FakeChatModel(String... responses) {
      this.responses = new ArrayDeque<>(List.of(responses));
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      String content = responses.size() > 1 ? responses.poll() : responses.peek();
      return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
  }

  static <T> ObjectProvider<T> providerOf(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return value;
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public T getIfUnique() {
        return value;
      }

      @Override
      public T getObject() {
        return value;
      }
    };
  }
}
