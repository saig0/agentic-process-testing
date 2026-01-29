package io.camunda.dev.frauddetection.cpt.extensions;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class ScenarioImpl implements Scenario {

  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private final Runnable beforeHook;

  public ScenarioImpl(Runnable beforeHook) {
    this.beforeHook = beforeHook;
  }

  @Override
  public ScenarioInstruction when(ThrowingRunnable condition) {
    return new ScenarioInstructionImpl(
        executorService,
        () -> {
          beforeHook.run();
          condition.run();
        });
  }

  private static class ScenarioInstructionImpl implements ScenarioInstruction {

    private final ExecutorService executorService;
    private final ThrowingRunnable condition;

    public ScenarioInstructionImpl(ExecutorService executorService, ThrowingRunnable condition) {
      this.executorService = executorService;
      this.condition = condition;
    }

    @Override
    public void then(ThrowingRunnable action) {
      executorService.execute(
          () -> {
            Awaitility.await("Wait until scenario condition is fulfilled")
                .pollInSameThread()
                .forever()
                .untilAsserted(condition);

            try {
              action.run();
            } catch (Throwable e) {
              throw new RuntimeException("Failed to execute scenario action", e);
            }
          });
    }
  }
}
