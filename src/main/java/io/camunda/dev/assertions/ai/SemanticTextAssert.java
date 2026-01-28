package io.camunda.dev.assertions.ai;

import io.camunda.dev.assertions.ai.internal.CosineSimilarity;
import io.camunda.dev.assertions.ai.internal.TextNormalizer;
import org.assertj.core.api.AbstractAssert;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

public final class SemanticTextAssert extends AbstractAssert<SemanticTextAssert, String> {

  private final CamundaAiAssertionDefaults defaults;
  private EmbeddingModel embeddingModel;
  private ChatModel judgeModel;
  private SemanticOptions options;

  SemanticTextAssert(String actual, CamundaAiAssertionDefaults defaults) {
    super(actual, SemanticTextAssert.class);
    this.defaults = defaults;
    this.embeddingModel = defaults.embeddingModel();
    this.judgeModel = defaults.judgeModel();
    this.options = defaults.options();
  }

  public SemanticTextAssert usingEmbeddingModel(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
    return this;
  }

  public SemanticTextAssert usingJudgeModel(ChatModel judgeModel) {
    this.judgeModel = judgeModel;
    return this;
  }

  public SemanticTextAssert usingOptions(SemanticOptions options) {
    this.options = options;
    return this;
  }

  public SemanticTextAssert isSemanticallySimilarTo(String expectedTargetText) {
    isNotNull();
    if (embeddingModel == null) {
      failWithMessage("EmbeddingModel is required for embeddings-based assertions. Provide it via CamundaAiAssertions.configureDefaults(...) or usingEmbeddingModel(...)");
    }
    String a = TextNormalizer.normalize(actual, options);
    String b = TextNormalizer.normalize(expectedTargetText, options);

    float[] va = embeddingModel.embed(a);
    float[] vb = embeddingModel.embed(b);

    double similarity = CosineSimilarity.cosine(va, vb);
    if (similarity < options.embeddingThreshold()) {
      failWithMessage(
          "Expected text to be semantically similar (cosine >= %.2f) but was %.3f.%nExpected target: %s%nActual: %s",
          options.embeddingThreshold(), similarity, expectedTargetText, actual);
    }
    return this;
  }

  public SemanticTextAssert matchesExpectationWithJudge(String expectation) {
    isNotNull();
    if (judgeModel == null) {
      failWithMessage("ChatModel is required for judge-based assertions. Provide it via CamundaAiAssertions.configureDefaults(...) or usingJudgeModel(...)");
    }

    String prompt =
        "You are a strict test oracle. Decide if the ACTUAL output satisfies the EXPECTATION.\n"
            + "Return ONLY valid JSON of the form {\"pass\":true|false,\"score\":0..1,\"reason\":\"...\"}.\n\n"
            + "EXPECTATION:\n"
            + expectation
            + "\n\nACTUAL:\n"
            + actual;

    String response = judgeModel.call(prompt);
    JudgeResult result = JudgeResult.tryParse(response);
    if (result == null) {
      failWithMessage("Judge model returned unparsable result. Raw response: %s", response);
    }
    if (!result.pass || result.score < options.judgeMinScore()) {
      failWithMessage(
          "Expected judge to PASS (minScore=%.2f) but got pass=%s score=%.3f reason=%s.%nExpectation: %s%nActual: %s",
          options.judgeMinScore(), result.pass, result.score, result.reason, expectation, actual);
    }
    return this;
  }

  /** Minimal JSON parser for the expected judge output. */
  static final class JudgeResult {
    final boolean pass;
    final double score;
    final String reason;

    JudgeResult(boolean pass, double score, String reason) {
      this.pass = pass;
      this.score = score;
      this.reason = reason;
    }

    static JudgeResult tryParse(String json) {
      if (json == null) {
        return null;
      }
      String s = json.trim();
      // Very small/forgiving parser: expects keys pass/score/reason somewhere in the string.
      try {
        Boolean pass = extractBoolean(s, "pass");
        Double score = extractDouble(s, "score");
        String reason = extractString(s, "reason");
        if (pass == null || score == null) {
          return null;
        }
        return new JudgeResult(pass, score, reason == null ? "" : reason);
      } catch (Exception e) {
        return null;
      }
    }

    private static Boolean extractBoolean(String s, String key) {
      int i = s.indexOf('"' + key + '"');
      if (i < 0) return null;
      int colon = s.indexOf(':', i);
      if (colon < 0) return null;
      String tail = s.substring(colon + 1).trim();
      if (tail.startsWith("true")) return true;
      if (tail.startsWith("false")) return false;
      return null;
    }

    private static Double extractDouble(String s, String key) {
      int i = s.indexOf('"' + key + '"');
      if (i < 0) return null;
      int colon = s.indexOf(':', i);
      if (colon < 0) return null;
      int start = colon + 1;
      while (start < s.length() && Character.isWhitespace(s.charAt(start))) start++;
      int end = start;
      while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.' || s.charAt(end) == '-')) end++;
      if (end == start) return null;
      return Double.parseDouble(s.substring(start, end));
    }

    private static String extractString(String s, String key) {
      int i = s.indexOf('"' + key + '"');
      if (i < 0) return null;
      int colon = s.indexOf(':', i);
      if (colon < 0) return null;
      int q1 = s.indexOf('"', colon + 1);
      if (q1 < 0) return null;
      int q2 = s.indexOf('"', q1 + 1);
      if (q2 < 0) return null;
      return s.substring(q1 + 1, q2);
    }
  }
}
