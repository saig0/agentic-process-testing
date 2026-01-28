package io.camunda.dev.assertions.ai;

import static io.camunda.dev.assertions.ai.CamundaAiAssertions.assertThat;
import static io.camunda.dev.assertions.ai.CamundaAiAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

@Disabled
class CamundaAiAssertionsTest {

  private EmbeddingModel embeddingModel;
  private ChatModel judgeModel;

  @BeforeEach
  void setUp() {
    final var openAiApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();

    embeddingModel =
        new OpenAiEmbeddingModel(
            openAiApi,
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build(),
            RetryUtils.DEFAULT_RETRY_TEMPLATE);

    var openAiChatOptions =
        OpenAiChatOptions.builder().model("gpt-4.1").temperature(0.4).maxTokens(200).build();

    judgeModel =
        OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
  }

  @Test
  void embeddings_passes_when_similarity_high() {
    assertThat("cat")
        .usingEmbeddingModel(embeddingModel)
        .usingOptions(SemanticOptions.defaults().withEmbeddingThreshold(0.80))
        .isSemanticallySimilarTo("dog");
  }

  @Test
  void embeddings_fails_when_similarity_low() {
    assertThatThrownBy(
            () ->
                assertThat("cat")
                    .usingEmbeddingModel(embeddingModel)
                    .usingOptions(SemanticOptions.defaults().withEmbeddingThreshold(0.80))
                    .isSemanticallySimilarTo("dog"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void judge_requires_model() {
    assertThatThrownBy(() -> assertThat("the sky is blue").matchesExpectationWithJudge("i'm hungry"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ChatModel is required");
  }

  @Test
  void judge_passes_when_fake_returns_pass() {
    assertThat("i'm hungry")
        .usingJudgeModel(judgeModel)
        .usingOptions(SemanticOptions.defaults().withJudgeMinScore(0.7))
        .matchesExpectationWithJudge("the sky is blue");
  }

  @Test
  void json_path_uses_same_semantics() {
    assertThatJson("{\n  \"status\": \"RESOLVED\"\n}")
        .usingEmbeddingModel(embeddingModel)
        .matchesDescription("status resolved");
  }

  // --- fakes ---

  static final class FakeEmbeddingModel implements EmbeddingModel {

    @Override
    public float[] embed(org.springframework.ai.document.Document document) {
      return embed(document.getText() == null ? "" : document.getText());
    }

    @Override
    public float[] embed(String text) {
      String t = text == null ? "" : text.toLowerCase();
      // Deterministic, clearly separable vectors for unit tests.
      if (t.contains("cat")) {
        return new float[] {1f, 0f};
      }
      if (t.contains("dog")) {
        return new float[] {0f, 1f};
      }
      return new float[] {1f, 1f};
    }

    // Unused by our code paths; keep minimal implementations.
    @Override
    public org.springframework.ai.embedding.EmbeddingResponse call(
        org.springframework.ai.embedding.EmbeddingRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  static final class FakeChatModel implements ChatModel {
    private final String response;

    FakeChatModel(String response) {
      this.response = response;
    }

    @Override
    public String call(String message) {
      return response;
    }

    @Override
    public org.springframework.ai.chat.model.ChatResponse call(
        org.springframework.ai.chat.prompt.Prompt prompt) {
      throw new UnsupportedOperationException();
    }
  }
}
