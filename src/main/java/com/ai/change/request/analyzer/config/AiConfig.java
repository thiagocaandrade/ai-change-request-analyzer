package com.ai.change.request.analyzer.config;

import com.ai.change.request.analyzer.tools.ToolRegistry;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.http.HttpClient;
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

  @Bean
  @ConditionalOnExpression("'${ai.chat.api-key:}'.trim().length() > 0")
  ChatClient chatClient(
      @Value("${ai.chat.api-key}") String apiKey,
      @Value("${ai.chat.model:}") String model,
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
    var chatModel =
        OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .openAiClientAsync(openAiClientAsync)
            .options(OpenAiChatOptions.builder().model(model).build())
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
}
