package io.camunda.dev.frauddetection.cpt.extensions;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.worker.JobHandler;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class AiAgentResultHandler {

  public static AiAgentResultHandler with(
      Function<CompleteJobCommandStep1.CompleteJobCommandJobResultStep, CompleteJobResult>
          handler) {
    AiAgentResultHandler resultHandler = new AiAgentResultHandler();
    return resultHandler.then(handler);
  }

  private final LinkedList<
          Function<CompleteJobCommandStep1.CompleteJobCommandJobResultStep, CompleteJobResult>>
      resultHandlers = new LinkedList<>();

  public AiAgentResultHandler then(
      Function<CompleteJobCommandStep1.CompleteJobCommandJobResultStep, CompleteJobResult>
          handler) {
    resultHandlers.add(handler);
    return this;
  }

  public JobHandler wire() {
    var callCounter = new AtomicInteger(0);
    return (jobClient, job) -> {
      jobClient
          .newCompleteCommand(job)
          .withResult(result -> resultHandlers.get(callCounter.getAndIncrement()).apply(result))
          .send()
          .join();
    };
  }
}
