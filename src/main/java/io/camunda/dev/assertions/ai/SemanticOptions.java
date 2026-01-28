package io.camunda.dev.assertions.ai;

/** Options for semantic assertions. */
public final class SemanticOptions {

  private final double embeddingThreshold;
  private final double judgeMinScore;
  private final boolean normalizeWhitespace;
  private final boolean normalizeToLowercase;

  private SemanticOptions(Builder builder) {
    this.embeddingThreshold = builder.embeddingThreshold;
    this.judgeMinScore = builder.judgeMinScore;
    this.normalizeWhitespace = builder.normalizeWhitespace;
    this.normalizeToLowercase = builder.normalizeToLowercase;
  }

  public static SemanticOptions defaults() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public double embeddingThreshold() {
    return embeddingThreshold;
  }

  public double judgeMinScore() {
    return judgeMinScore;
  }

  public boolean normalizeWhitespace() {
    return normalizeWhitespace;
  }

  public boolean normalizeToLowercase() {
    return normalizeToLowercase;
  }

  public SemanticOptions withEmbeddingThreshold(double threshold) {
    return builderFromThis().embeddingThreshold(threshold).build();
  }

  public SemanticOptions withJudgeMinScore(double minScore) {
    return builderFromThis().judgeMinScore(minScore).build();
  }

  private Builder builderFromThis() {
    return builder()
        .embeddingThreshold(this.embeddingThreshold)
        .judgeMinScore(this.judgeMinScore)
        .normalizeWhitespace(this.normalizeWhitespace)
        .normalizeToLowercase(this.normalizeToLowercase);
  }

  public static final class Builder {
    private double embeddingThreshold = 0.80;
    private double judgeMinScore = 0.70;
    private boolean normalizeWhitespace = true;
    private boolean normalizeToLowercase = false;

    public Builder embeddingThreshold(double embeddingThreshold) {
      this.embeddingThreshold = embeddingThreshold;
      return this;
    }

    public Builder judgeMinScore(double judgeMinScore) {
      this.judgeMinScore = judgeMinScore;
      return this;
    }

    public Builder normalizeWhitespace(boolean normalizeWhitespace) {
      this.normalizeWhitespace = normalizeWhitespace;
      return this;
    }

    public Builder normalizeToLowercase(boolean normalizeToLowercase) {
      this.normalizeToLowercase = normalizeToLowercase;
      return this;
    }

    public SemanticOptions build() {
      if (embeddingThreshold < 0.0 || embeddingThreshold > 1.0) {
        throw new IllegalArgumentException("embeddingThreshold must be in [0,1]");
      }
      if (judgeMinScore < 0.0 || judgeMinScore > 1.0) {
        throw new IllegalArgumentException("judgeMinScore must be in [0,1]");
      }
      return new SemanticOptions(this);
    }
  }
}
