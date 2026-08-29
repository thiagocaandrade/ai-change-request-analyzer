package com.ai.change.request.analyzer.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.HttpClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

  @Bean
  @ConditionalOnExpression("'${ai.chat.api-key:}'.trim().length() > 0")
  ChatClient chatClient(
      @Value("${ai.chat.api-key}") String apiKey,
      @Value("${ai.chat.model:}") String model,
      @Value("${ai.chat.base-url:}") String baseUrl) {
    HttpClient httpClient = SpringAiOpenAiHttpClient.builder().build();
    var optionsBuilder = ClientOptions.builder().apiKey(apiKey).httpClient(httpClient);
    if (baseUrl != null && !baseUrl.isBlank()) {
      optionsBuilder.baseUrl(baseUrl);
    }
    OpenAIClient openAiClient = new OpenAIClientImpl(optionsBuilder.build());
    var chatModel =
        OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .options(OpenAiChatOptions.builder().model(model).build())
            .build();
    return ChatClient.builder(chatModel).build();
  }
}
