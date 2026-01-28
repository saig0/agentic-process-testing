package io.camunda.dev.assertions.ai.internal;

/** Minimal JSON canonicalizer.
 *
 * For v1 we keep this simple: trim and collapse whitespace.
 * (If needed we can add a real canonicalization based on Jackson later.)
 */
public final class JsonCanonicalizer {

  private JsonCanonicalizer() {}

  public static String canonicalize(String json) {
    if (json == null) {
      return null;
    }
    return json.trim().replaceAll("\\s+", " ");
  }
}
