package io.camunda.dev.frauddetection;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariables(
    value = {
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".*"),
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_SECRET_KEY", matches = ".*")
    })
public class FraudDetectionIntegrationTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void executesFraudDetectionAgent() {
    // given process definition is deployed

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fraud-detection-process")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance).isActive();
  }
}
