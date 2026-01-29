package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.dev.frauddetection.cpt.extensions.Scenario;
import io.camunda.dev.frauddetection.cpt.extensions.ScenarioImpl;
import io.camunda.process.test.impl.assertions.CamundaDataSource;

import java.time.Duration;

public class CptAssertHelper {

  public static Scenario scenario(CamundaClient camundaClient) {
    CamundaDataSource camundaDataSource = new CamundaDataSource(camundaClient);
    CamundaAssert.initialize(camundaDataSource);

    return new ScenarioImpl(
        () -> {
          CamundaAssert.initialize(camundaDataSource);
          CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
        });
  }
}
