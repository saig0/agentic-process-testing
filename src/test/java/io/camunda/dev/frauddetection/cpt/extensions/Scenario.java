package io.camunda.dev.frauddetection.cpt.extensions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;
import org.awaitility.core.ThrowingRunnable;

public interface Scenario {

    ScenarioInstruction when(final ThrowingRunnable condition);

    interface ScenarioInstruction {
        void then(final ThrowingRunnable action);
    }

    static ThrowingConsumer<CamundaClient> hasActiveElement(String elementName) {
        return client -> {
            SearchResponse<ElementInstance> searchResponse = client.newElementInstanceSearchRequest().filter(filter -> filter
                            .state(ElementInstanceState.ACTIVE)
                            .elementName(elementName))
                    .send().join();
            Assertions.assertThat(searchResponse.items())
                    .withFailMessage("Expected element '%s' to be active.", elementName)
                    .isNotEmpty();
        };
    }

}
