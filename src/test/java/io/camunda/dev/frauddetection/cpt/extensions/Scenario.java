package io.camunda.dev.frauddetection.cpt.extensions;

import org.awaitility.core.ThrowingRunnable;

public interface Scenario {

    ScenarioInstruction when(final ThrowingRunnable condition);

    interface ScenarioInstruction {
        void then(final ThrowingRunnable action);
    }


}
