package com.ai.change.request.analyzer.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiConfigTest {

  @Autowired private ApplicationContext context;

  @Test
  void contextStartsWithoutAnyAiEnvironmentVariables() {
    assertThat(context).isNotNull();
  }

  @Test
  void noAiModelBeansExistWhenCredentialsAreMissing() {
    assertThat(context.getBeansOfType(OpenAiChatModel.class)).isEmpty();
    assertThat(context.getBeansOfType(ChatClient.class)).isEmpty();
  }
}
