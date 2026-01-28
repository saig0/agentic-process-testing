package io.camunda.dev.assertions.ai.internal;

import io.camunda.dev.assertions.ai.SemanticOptions;

public final class TextNormalizer {

  private TextNormalizer() {}

  public static String normalize(String in, SemanticOptions options) {
    if (in == null) {
      return null;
    }
    String out = in;
    if (options.normalizeWhitespace()) {
      out = out.trim().replaceAll("\\s+", " ");
    }
    if (options.normalizeToLowercase()) {
      out = out.toLowerCase();
    }
    return out;
  }
}
