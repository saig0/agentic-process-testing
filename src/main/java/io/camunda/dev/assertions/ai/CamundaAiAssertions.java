package io.camunda.dev.assertions.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Entry point for AI-related assertions.
 *
 * <p>Models must be provided explicitly (no Spring container magic). You can set global defaults via
 * {@link #configureDefaults(CamundaAiAssertionDefaults)} and override per assertion.
 */
public final class CamundaAiAssertions {

  private static volatile CamundaAiAssertionDefaults defaults = CamundaAiAssertionDefaults.builder().build();

  private CamundaAiAssertions() {}

  public static void configureDefaults(CamundaAiAssertionDefaults defaults) {
    if (defaults == null) {
      throw new IllegalArgumentException("defaults must not be null");
    }
    CamundaAiAssertions.defaults = defaults;
  }

  public static CamundaAiAssertionDefaults defaults() {
    return defaults;
  }

  public static SemanticTextAssert assertThat(String actualText) {
    return new SemanticTextAssert(actualText, defaults);
  }

  public static SemanticJsonAssert assertThatJson(String actualJson) {
    return new SemanticJsonAssert(actualJson, defaults);
  }

  // Convenience overloads to set a per-assert default model quickly
  public static SemanticTextAssert assertThat(String actualText, EmbeddingModel embeddingModel) {
    return assertThat(actualText).usingEmbeddingModel(embeddingModel);
  }

  public static SemanticTextAssert assertThat(String actualText, ChatModel judgeModel) {
    return assertThat(actualText).usingJudgeModel(judgeModel);
  }
}
