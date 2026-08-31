package com.ai.change.request.analyzer.config;

import com.ai.change.request.analyzer.tools.ToolRegistry;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

  private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

  /**
   * Cliente de chat existe apenas com chave de API e provider suportado (openai). Provider
   * desconhecido segue o mesmo caminho do "sem modelo": bean ausente e analise degradada marcada.
   */
  @Bean
  @ConditionalOnExpression(
      "'${ai.chat.api-key:}'.trim().length() > 0 && '${ai.provider:openai}' == 'openai'")
  ChatClient chatClient(
      @Value("${ai.chat.api-key}") String apiKey,
      @Value("${ai.chat.model:}") String model,
      @Value("${ai.chat.temperature:}") String temperature,
      @Value("${ai.chat.base-url:}") String baseUrl,
      ToolRegistry toolRegistry) {
    HttpClient httpClient = SpringAiOpenAiHttpClient.builder().build();
    var optionsBuilder = ClientOptions.builder().apiKey(apiKey).httpClient(httpClient);
    if (baseUrl != null && !baseUrl.isBlank()) {
      optionsBuilder.baseUrl(baseUrl);
    }
    ClientOptions clientOptions = optionsBuilder.build();
    OpenAIClient openAiClient = new OpenAIClientImpl(clientOptions);
    OpenAIClientAsync openAiClientAsync = new OpenAIClientAsyncImpl(clientOptions);
    var chatOptionsBuilder = OpenAiChatOptions.builder().model(model);
    Double resolvedTemperature = resolveTemperature(temperature, model);
    if (resolvedTemperature != null) {
      chatOptionsBuilder.temperature(resolvedTemperature);
    }
    var chatModel =
        OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .openAiClientAsync(openAiClientAsync)
            .options(chatOptionsBuilder.build())
            .build();
    DefaultToolCallingManager toolCallingManager =
        DefaultToolCallingManager.builder()
            .toolCallbackResolver(new StaticToolCallbackResolver(toolRegistry.callbacks()))
            .build();
    return ChatClient.builder(chatModel)
        .defaultAdvisors(
            ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager).build())
        .build();
  }

  /**
   * Resolve a temperatura configurada: ausente/vazia → null (default do provider); nao numerica,
   * nao finita ou negativa → tratada como ausente com warning estruturado.
   */
  static Double resolveTemperature(String raw, String model) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value) || value < 0.0) {
        log.warn(
            "node=ai_config event=invalid_temperature temperature={} model={} "
                + "action=provider_default",
            raw,
            model);
        return null;
      }
      return value;
    } catch (NumberFormatException e) {
      log.warn(
          "node=ai_config event=invalid_temperature temperature={} model={} "
              + "action=provider_default",
          raw,
          model);
      return null;
    }
  }
}
