package io.camunda.dev.assertions.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

/** Global defaults for CamundaAiAssertions. */
public final class CamundaAiAssertionDefaults {

  private final EmbeddingModel embeddingModel;
  private final ChatModel judgeModel;
  private final SemanticOptions options;

  private CamundaAiAssertionDefaults(Builder builder) {
    this.embeddingModel = builder.embeddingModel;
    this.judgeModel = builder.judgeModel;
    this.options = builder.options == null ? SemanticOptions.defaults() : builder.options;
  }

  public static Builder builder() {
    return new Builder();
  }

  public EmbeddingModel embeddingModel() {
    return embeddingModel;
  }

  public ChatModel judgeModel() {
    return judgeModel;
  }

  public SemanticOptions options() {
    return options;
  }

  public static final class Builder {
    private EmbeddingModel embeddingModel;
    private ChatModel judgeModel;
    private SemanticOptions options;

    public Builder embeddingModel(EmbeddingModel embeddingModel) {
      this.embeddingModel = embeddingModel;
      return this;
    }

    public Builder judgeModel(ChatModel judgeModel) {
      this.judgeModel = judgeModel;
      return this;
    }

    public Builder options(SemanticOptions options) {
      this.options = options;
      return this;
    }

    public CamundaAiAssertionDefaults build() {
      return new CamundaAiAssertionDefaults(this);
    }
  }
}
