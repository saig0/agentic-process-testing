package io.camunda.dev.frauddetection;

import com.icegreen.greenmail.spring.GreenMailBean;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ElementSelectors;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.Testcontainers;

import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariables(
    value = {
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".+"),
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_SECRET_KEY", matches = ".+"),
    })
public class FraudDetectionIntegrationTest {

  @Autowired
  private GreenMailBean greenMailBean;

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setup() {
    Testcontainers.exposeHostPorts(3025, 3143);
  }

  @BeforeEach
  void setUp() {
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(1));

    Assertions.assertThat(greenMailBean.isStarted()).isTrue();
  }

  @Test
  void executesFraudDetectionAgent() {
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fraud-detection-process")
            .latestVersion()
            .variables(
                Map.of(
                    "taxSubmission",
                    Map.ofEntries(
                        Map.entry("fullName", "John Doe"),
                        Map.entry("dob", "1980-01-14"),
                        Map.entry("emailAddress", "demo@camunda.com"),
                        Map.entry("totalIncome", 100000),
                        Map.entry("totalExpenses", 10000),
                        Map.entry("largePurchases", List.of("stocks")),
                        Map.entry("charitableDonations", "None"))))
            .send()
            .join();

    CamundaAssert.assertThat(processInstance)
        .hasCompletedElements(ElementSelectors.byName("Send inquiry e-mail"));

    assertThatUserTask(byTaskName("User Task")).isCreated();

    processTestContext.completeUserTask(byTaskName("User Task"), Map.of(
        "userResponse", "Don't bother me"
    ));

    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements(ElementSelectors.byName("Fraud is detected"));
  }
}
