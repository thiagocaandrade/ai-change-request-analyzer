package com.ai.change.request.analyzer.ai;

import com.ai.change.request.analyzer.ai.dto.AiResults.ClassificationResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.ImpactAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.RiskAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.SecurityAnalysisResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestPlanResult;
import com.ai.change.request.analyzer.ai.dto.AiResults.TestRecommendationDto;
import com.ai.change.request.analyzer.observability.AnalysisMetrics;
import com.ai.change.request.analyzer.observability.TraceService;
import com.ai.change.request.analyzer.resilience.ResilienceExecutor;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Executa as etapas cognitivas via modelo de IA com structured output validado e retry limitado com
 * backoff (via {@link ResilienceExecutor}).
 *
 * <p>Sem modelo configurado, toda etapa retorna fallback deterministico marcado ({@code
 * degraded=true}); saida invalida nunca e retornada nem persistida.
 */
@Service
public class AiAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

  private final PromptRegistry promptRegistry;
  private final Validator validator;
  private final ObjectProvider<ChatClient> chatClientProvider;
  private final long chatTimeoutMs;
  private final ResilienceExecutor resilienceExecutor;
  private final AnalysisMetrics metrics;
  private final TraceService traceService;
  private final String modelName;

  public AiAnalysisService(
      PromptRegistry promptRegistry,
      Validator validator,
      ObjectProvider<ChatClient> chatClientProvider,
      @Value("${ai.chat.timeout-ms:30000}") long chatTimeoutMs,
      ResilienceExecutor resilienceExecutor,
      AnalysisMetrics metrics,
      TraceService traceService,
      @Value("${ai.chat.model:}") String modelName) {
    this.promptRegistry = promptRegistry;
    this.validator = validator;
    this.chatClientProvider = chatClientProvider;
    this.chatTimeoutMs = chatTimeoutMs;
    this.resilienceExecutor = resilienceExecutor;
    this.metrics = metrics;
    this.traceService = traceService;
    this.modelName = modelName;
  }

  public ClassificationResult classify(String changeText) {
    return normalizeClassification(
        generate(
            AnalysisStage.CLASSIFICATION,
            changeText,
            "",
            ClassificationResult.class,
            () -> new ClassificationResult("general", "analysis_unavailable", true)));
  }

  public ImpactAnalysisResult analyzeImpact(String changeText, String evidence) {
    return normalizeImpact(
        generate(
            AnalysisStage.IMPACT_ANALYSIS,
            changeText,
            evidence,
            ImpactAnalysisResult.class,
            () -> new ImpactAnalysisResult(List.of(), true)));
  }

  public RiskAnalysisResult assessRisk(String changeText, String evidence) {
    return normalizeRisk(
        generate(
            AnalysisStage.RISK_ANALYSIS,
            changeText,
            evidence,
            RiskAnalysisResult.class,
            () -> new RiskAnalysisResult("MEDIUM", 0.5, "analysis_unavailable", true)));
  }

  public TestPlanResult generateTestPlan(String changeText, String evidence) {
    return normalizePlan(
        generate(
            AnalysisStage.TEST_GENERATION,
            changeText,
            evidence,
            TestPlanResult.class,
            () -> new TestPlanResult(defaultFallbackPlan(), true)));
  }

  /**
   * Etapa assistida de analise de seguranca: a saida do modelo e apenas SUGESTAO validada; a
   * decisao final de deteccao, o registro do evento e a acao sao aplicados deterministicamente pela
   * aplicacao. A sugestao nunca altera risco ou classificacao.
   */
  public SecurityAnalysisResult analyzeSecurity(String changeText, String evidence) {
    return normalizeSecurity(
        generate(
            AnalysisStage.SECURITY_ANALYSIS,
            changeText,
            evidence,
            SecurityAnalysisResult.class,
            () -> new SecurityAnalysisResult(List.of(), true)));
  }

  private static ClassificationResult normalizeClassification(ClassificationResult result) {
    return new ClassificationResult(
        result.category(), result.notes(), Boolean.TRUE.equals(result.degraded()));
  }

  private static ImpactAnalysisResult normalizeImpact(ImpactAnalysisResult result) {
    return new ImpactAnalysisResult(result.findings(), Boolean.TRUE.equals(result.degraded()));
  }

  private static RiskAnalysisResult normalizeRisk(RiskAnalysisResult result) {
    return new RiskAnalysisResult(
        result.level(),
        result.confidence(),
        result.rationale(),
        Boolean.TRUE.equals(result.degraded()));
  }

  private static TestPlanResult normalizePlan(TestPlanResult result) {
    return new TestPlanResult(result.recommendations(), Boolean.TRUE.equals(result.degraded()));
  }

  private static SecurityAnalysisResult normalizeSecurity(SecurityAnalysisResult result) {
    return new SecurityAnalysisResult(result.findings(), Boolean.TRUE.equals(result.degraded()));
  }

  private <T> T generate(
      AnalysisStage stage,
      String changeText,
      String evidence,
      Class<T> outputType,
      Supplier<T> fallback) {
    String traceId = MDC.get("trace_id");
    String model = modelName == null || modelName.isBlank() ? "unconfigured" : modelName;
    ChatClient chatClient = chatClientProvider.getIfAvailable();
    if (chatClient == null) {
      traceService.record(stage.id(), "ai_unavailable", null, "degraded", null, null, null, model);
      log.warn("ai_unavailable stage={} model={} trace_id={}", stage.id(), model, traceId);
      return fallback.get();
    }

    var converter = new BeanOutputConverter<>(outputType);
    PromptRegistry.PromptTemplate template = promptRegistry.load(stage.id(), 1);
    String system = template.renderSystem(Map.of("format", converter.getFormat()));
    String user =
        template.renderUser(
            Map.of(
                "change_text", changeText == null ? "" : changeText,
                "evidence", evidence == null ? "" : evidence,
                "format", converter.getFormat()));

    return resilienceExecutor.execute(
        stage.id(),
        "llm_call",
        () -> {
          metrics.llmCall();
          try {
            String content = chatClient.prompt().system(system).user(user).call().content();
            T result = converter.convert(content);
            validate(result);
            return result;
          } catch (IllegalArgumentException e) {
            metrics.validationFailure();
            throw e;
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException("falha na chamada ao modelo", e);
          }
        },
        chatTimeoutMs,
        fallback,
        null,
        model);
  }

  private <T> void validate(T result) {
    var violations = validator.validate(result);
    if (!violations.isEmpty()) {
      String detail = violations.iterator().next().getMessage();
      throw new IllegalArgumentException("saida invalida: " + detail);
    }
  }

  private List<TestRecommendationDto> defaultFallbackPlan() {
    return List.of(
        new TestRecommendationDto(
            "unit", "teste unitario da regra afetada (degradado: analysis_unavailable)", "MEDIUM"),
        new TestRecommendationDto(
            "integration",
            "teste de integracao do fluxo afetado (degradado: analysis_unavailable)",
            "MEDIUM"));
  }
}
