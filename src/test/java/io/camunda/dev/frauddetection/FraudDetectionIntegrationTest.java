package io.camunda.dev.frauddetection;

import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.dev.assertions.ai.CamundaAiAssertionDefaults;
import io.camunda.dev.assertions.ai.CamundaAiAssertions;
import io.camunda.dev.assertions.ai.SemanticOptions;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.CptAssertHelper;
import io.camunda.process.test.api.assertions.ElementSelectors;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@SpringBootTest(
    properties = {
      "camunda.process-test.connectors-env-vars.CAMUNDA_CONNECTOR_POLLING_ENABLED=false",
      "camunda.process-test.connectors-env-vars.CONNECTOR_OUTBOUND_DISABLED=io.camunda:email:1"
    })
@CamundaSpringProcessTest
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariables(
    value = {
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".+"),
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_SECRET_KEY", matches = ".+"),
    })
public class FraudDetectionIntegrationTest {

  private static final String PROCESS_DEFINITION_ID = "fraud-detection-process";
  private static final String EMAIL_MESSAGE_ID = "1";
  private static final String EMAIL_MESSAGE_NAME = "ba04712f-eae7-433a-9dd4-c56286e65940";

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void setUp() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));

    CamundaAiAssertions.configureDefaults(
        CamundaAiAssertionDefaults.builder().judgeModel(createJudgeModel()).build());

    // mock mail interactions
    processTestContext
        .mockJobWorker("io.camunda:email:1")
        .thenComplete(
            Map.of(
                "emailSent",
                Map.ofEntries(
                    Map.entry("messageId", EMAIL_MESSAGE_ID),
                    Map.entry(
                        "body",
                        "Dear user, we have detected fraud in your submission. Please state your opinion!"))));

    CptAssertHelper.scenario(client)
        .when(
            () -> {
              CamundaAssert.assertThatProcessInstance(
                      ProcessInstanceSelectors.byProcessId(PROCESS_DEFINITION_ID))
                  .isWaitingForMessage(EMAIL_MESSAGE_NAME, EMAIL_MESSAGE_ID);
            })
        .then(
            () -> {
              client
                  .newPublishMessageCommand()
                  .messageName(EMAIL_MESSAGE_NAME)
                  .correlationKey(EMAIL_MESSAGE_ID)
                  .variables(Map.of("plainTextBody", "I did not commit fraud!"))
                  .send()
                  .join();
            });

    // mock call to external advisor
    CptAssertHelper.scenario(client)
        .when(
            () -> {
              assertThatUserTask(byTaskName("Call On External Advisor")).isCreated();
            })
        .then(
            () -> {
              processTestContext.completeUserTask(
                  byTaskName("Call On External Advisor"),
                  Map.ofEntries(
                      Map.entry("expertAnalysis", "Don't bother me"),
                      Map.entry("fraudDetected", true)));
            });
  }

  private ChatModel createJudgeModel() {
    return BedrockProxyChatModel.builder()
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    System.getenv("AWS_BEDROCK_ACCESS_KEY"),
                    System.getenv("AWS_BEDROCK_SECRET_KEY"))))
        .region(Region.EU_CENTRAL_1)
        .defaultOptions(
            BedrockChatOptions.builder()
                .model("eu.anthropic.claude-sonnet-4-5-20250929-v1:0")
                .temperature(0.4)
                .maxTokens(500)
                .build())
        .build();
  }

  @Test
  void executesFraudDetectionAgent() {
    // given: tax return submitted
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_DEFINITION_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "taxSubmission",
                    Map.ofEntries(
                        Map.entry("fullName", "John Doe"),
                        Map.entry("dob", "1980-01-14"),
                        Map.entry("emailAddress", "demo@camunda.com"),
                        Map.entry("totalIncome", 100000),
                        Map.entry("totalExpenses", 80000),
                        Map.entry("largePurchases", List.of("stocks")),
                        Map.entry("charitableDonations", "None"))))
            .send()
            .join();

    // then: verify that fraud is detected
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(2));

    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements(ElementSelectors.byName("Fraud is detected"));
  }

  @Test
  void verifyFraudDetectionAgentEmail() {
    // given: tax return submitted
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
                        Map.entry("totalExpenses", 80000),
                        Map.entry("largePurchases", List.of("stocks")),
                        Map.entry("charitableDonations", "None"))))
            .send()
            .join();

    // then: verify that fraud is detected
    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(2));

    CamundaAssert.assertThat(processInstance)
        .hasCompletedElements(ElementSelectors.byName("Send inquiry e-mail"));

    CamundaAssert.setAssertionTimeout(Duration.ofMinutes(2));

    CamundaAssert.assertThat(processInstance)
        .hasLocalVariableSatisfies(
            ElementSelectors.byId("Fraud_Detection_Agent#innerInstance"),
            "toolCall",
            MailToolCall.class,
            toolCall -> {
              assertThat(toolCall.emailBody).isNotEmpty();

              CamundaAiAssertions.assertThat(toolCall.emailBody)
                  .usingOptions(SemanticOptions.defaults().withJudgeMinScore(0.7))
                  .matchesExpectationWithJudge(
                      """
                                  An email text asking the user about at least one of the following:

                                  - the discrepancy between yearly income and expenses
                                  - clarification on the stock purchases
                                  """);
            });
  }

  public record MailToolCall(String emailBody) {}
}
