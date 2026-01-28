package io.camunda.dev.assertions.ai;

import io.camunda.dev.assertions.ai.internal.JsonCanonicalizer;
import org.assertj.core.api.AbstractAssert;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

public final class SemanticJsonAssert extends AbstractAssert<SemanticJsonAssert, String> {

  private final CamundaAiAssertionDefaults defaults;
  private EmbeddingModel embeddingModel;
  private ChatModel judgeModel;
  private SemanticOptions options;

  SemanticJsonAssert(String actual, CamundaAiAssertionDefaults defaults) {
    super(actual, SemanticJsonAssert.class);
    this.defaults = defaults;
    this.embeddingModel = defaults.embeddingModel();
    this.judgeModel = defaults.judgeModel();
    this.options = defaults.options();
  }

  public SemanticJsonAssert usingEmbeddingModel(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
    return this;
  }

  public SemanticJsonAssert usingJudgeModel(ChatModel judgeModel) {
    this.judgeModel = judgeModel;
    return this;
  }

  public SemanticJsonAssert usingOptions(SemanticOptions options) {
    this.options = options;
    return this;
  }

  public SemanticJsonAssert matchesDescription(String freeformDescription) {
    isNotNull();
    if (embeddingModel == null) {
      failWithMessage("EmbeddingModel is required for embeddings-based assertions. Provide it via CamundaAiAssertions.configureDefaults(...) or usingEmbeddingModel(...)");
    }
    String canonical = JsonCanonicalizer.canonicalize(actual);
    new SemanticTextAssert(canonical, defaults)
        .usingEmbeddingModel(embeddingModel)
        .usingOptions(options)
        .isSemanticallySimilarTo(freeformDescription);
    return this;
  }

  public SemanticJsonAssert matchesDescriptionWithJudge(String expectation) {
    isNotNull();
    if (judgeModel == null) {
      failWithMessage("ChatModel is required for judge-based assertions. Provide it via CamundaAiAssertions.configureDefaults(...) or usingJudgeModel(...)");
    }
    String canonical = JsonCanonicalizer.canonicalize(actual);
    new SemanticTextAssert(canonical, defaults)
        .usingJudgeModel(judgeModel)
        .usingOptions(options)
        .matchesExpectationWithJudge(expectation);
    return this;
  }
}
