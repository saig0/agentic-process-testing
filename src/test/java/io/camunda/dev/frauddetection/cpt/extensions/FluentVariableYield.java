package io.camunda.dev.frauddetection.cpt.extensions;

import io.camunda.client.api.worker.JobHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FluentVariableYield {

  public static JobHandler yieldingVariables(String variableName, String... assignments) {
    var list = List.of(assignments);
    var counter = new AtomicInteger(0);
    return (client, job) -> {
      var nextValue = list.get(counter.getAndIncrement());
      client
          .newCompleteCommand(job.getKey())
          .variables(Map.of(variableName, nextValue))
          .send()
          .join();
    };
  }
}
