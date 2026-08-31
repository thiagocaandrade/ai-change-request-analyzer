package com.ai.change.request.analyzer.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

  @Test
  void resolveTemperatureAppliesValidValue() {
    assertThat(AiConfig.resolveTemperature("0.3", "gpt-4o-mini")).isEqualTo(0.3);
    assertThat(AiConfig.resolveTemperature(" 0 ", "gpt-4o-mini")).isEqualTo(0.0);
  }

  @Test
  void resolveTemperatureAbsentOrBlankUsesProviderDefault() {
    assertThat(AiConfig.resolveTemperature(null, "gpt-4o-mini")).isNull();
    assertThat(AiConfig.resolveTemperature("", "gpt-4o-mini")).isNull();
    assertThat(AiConfig.resolveTemperature("   ", "gpt-4o-mini")).isNull();
  }

  @Test
  void resolveTemperatureInvalidUsesProviderDefaultWithWarning() {
    assertThat(AiConfig.resolveTemperature("abc", "gpt-4o-mini")).isNull();
    assertThat(AiConfig.resolveTemperature("-0.5", "gpt-4o-mini")).isNull();
    assertThat(AiConfig.resolveTemperature("NaN", "gpt-4o-mini")).isNull();
    assertThat(AiConfig.resolveTemperature("Infinity", "gpt-4o-mini")).isNull();
  }

  @Test
  void unknownProviderDisablesChatClientEvenWithApiKey() {
    new ApplicationContextRunner()
        .withUserConfiguration(AiConfig.class)
        .withBean(ToolRegistry.class, () -> Mockito.mock(ToolRegistry.class))
        .withPropertyValues("ai.chat.api-key=sk-test", "ai.provider=anthropic")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(ChatClient.class)).isEmpty();
            });
  }

  @Test
  void openaiProviderWithKeyCreatesChatClient() {
    new ApplicationContextRunner()
        .withUserConfiguration(AiConfig.class)
        .withBean(ToolRegistry.class, () -> Mockito.mock(ToolRegistry.class))
        .withPropertyValues(
            "ai.chat.api-key=sk-test", "ai.provider=openai", "ai.chat.model=gpt-test")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(ChatClient.class)).hasSize(1);
            });
  }
}
