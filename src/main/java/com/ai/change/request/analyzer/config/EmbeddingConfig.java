package com.ai.change.request.analyzer.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.HttpClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Modelo de embedding condicionado a chave em {@code ai.embedding.api-key}. Sem chave, nenhum bean
 * de embedding existe e o RAG fica desativado (analise degradada).
 */
@Configuration
public class EmbeddingConfig {

  @Bean
  @ConditionalOnExpression("'${ai.embedding.api-key:}'.trim().length() > 0")
  EmbeddingModel embeddingModel(
      @Value("${ai.embedding.api-key}") String apiKey,
      @Value("${ai.embedding.model:}") String model,
      @Value("${ai.embedding.base-url:}") String baseUrl) {
    HttpClient httpClient = SpringAiOpenAiHttpClient.builder().build();
    var optionsBuilder = ClientOptions.builder().apiKey(apiKey).httpClient(httpClient);
    if (baseUrl != null && !baseUrl.isBlank()) {
      optionsBuilder.baseUrl(baseUrl);
    }
    OpenAIClient openAiClient = new OpenAIClientImpl(optionsBuilder.build());
    return OpenAiEmbeddingModel.builder()
        .openAiClient(openAiClient)
        .options(OpenAiEmbeddingOptions.builder().model(model).build())
        .build();
  }
}
