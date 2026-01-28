# agentic-process-testing

Camunda Hackathon 2026 project: Agentic process testing.

## CamundaAiAssertions (semantic assertions)

Notes:
- Models are **always provided explicitly** (no Spring container magic).
- You can set global defaults via `configureDefaults(...)` and override per assertion via `.usingEmbeddingModel(...)`, `.usingJudgeModel(...)`, and `.usingOptions(...)`.

Embeddings path (default methods):

```java
import static io.camunda.dev.assertions.ai.CamundaAiAssertions.assertThat;
import static io.camunda.dev.assertions.ai.CamundaAiAssertions.assertThatJson;

import io.camunda.dev.assertions.ai.CamundaAiAssertionDefaults;
import io.camunda.dev.assertions.ai.CamundaAiAssertions;
import io.camunda.dev.assertions.ai.SemanticOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

EmbeddingModel embeddingModel = /* provide explicitly */;
ChatModel judgeModel = /* provide explicitly */;

CamundaAiAssertions.configureDefaults(
    CamundaAiAssertionDefaults.builder()
        .embeddingModel(embeddingModel)
        .judgeModel(judgeModel)
        .options(SemanticOptions.defaults().withEmbeddingThreshold(0.80).withJudgeMinScore(0.70))
        .build());

assertThat("I refunded the customer and closed the ticket.")
    .isSemanticallySimilarTo("Refund issued and case closed");

assertThatJson("{\"status\":\"RESOLVED\",\"actions\":[\"refund\"]}")
    .matchesDescription("A resolved result that includes a refund action");
```

Judge-only path (explicit methods):

```java
assertThat("Steps: refunded; notified; closed case")
    .matchesExpectationWithJudge("Goal achieved: refund processed and ticket closed");

assertThatJson("{\"emailBody\":\"Hi John, your refund is approved.\"}")
    .matchesDescriptionWithJudge("Email is polite and does not ask for sensitive data");
```
