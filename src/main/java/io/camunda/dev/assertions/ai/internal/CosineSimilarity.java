package io.camunda.dev.assertions.ai.internal;

public final class CosineSimilarity {

  private CosineSimilarity() {}

  public static double cosine(float[] a, float[] b) {
    if (a == null || b == null) {
      throw new IllegalArgumentException("vectors must not be null");
    }
    if (a.length != b.length) {
      throw new IllegalArgumentException("vector dimensions differ: " + a.length + " vs " + b.length);
    }
    double dot = 0.0;
    double na = 0.0;
    double nb = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      na += (double) a[i] * a[i];
      nb += (double) b[i] * b[i];
    }
    if (na == 0.0 || nb == 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
