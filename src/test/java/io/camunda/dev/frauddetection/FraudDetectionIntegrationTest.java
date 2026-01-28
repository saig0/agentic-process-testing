package io.camunda.dev.frauddetection;

import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.spring.GreenMailBean;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.dev.assertions.ai.CamundaAiAssertionDefaults;
import io.camunda.dev.assertions.ai.CamundaAiAssertions;
import io.camunda.dev.assertions.ai.SemanticOptions;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ElementSelectors;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariables(
    value = {
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".+"),
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_SECRET_KEY", matches = ".+"),
    })
public class FraudDetectionIntegrationTest {

  @Autowired private GreenMailBean greenMailBean;

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setup() {
    Testcontainers.exposeHostPorts(3025, 3143);
  }

  @BeforeEach
  void setUp() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));

    CamundaAiAssertions.configureDefaults(
        CamundaAiAssertionDefaults.builder().judgeModel(createJudgeModel()).build());

    assertThat(greenMailBean.isStarted()).isTrue();
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

    AtomicReference<String> emailBody = new AtomicReference<>();
    CamundaAssert.assertThat(processInstance)
        .hasActiveElement(ElementSelectors.byId("Fraud_Detection_Agent#innerInstance"), 1)
        .hasLocalVariableSatisfies(
            ElementSelectors.byId("Fraud_Detection_Agent#innerInstance"),
            "toolCall",
            Map.class,
            toolCall -> {
              assertThat(toolCall).isNotEmpty().containsKey("emailBody");
              emailBody.set((String) toolCall.get("emailBody"));
            });

    CamundaAiAssertions.assertThat(emailBody.get())
        .usingOptions(SemanticOptions.defaults().withJudgeMinScore(0.7))
        .matchesExpectationWithJudge(
            """
                  An email text asking the user about at least one of the following:

                  - the discrepancy between yearly income and expenses
                  - clarification on the stock purchases
                  - OPTIONAL: a remark regarding the John Doe name being generic
                  """);

    assertThatUserTask(byTaskName("User Task")).isCreated();

    processTestContext.completeUserTask(
        byTaskName("User Task"), Map.of("userResponse", "Don't bother me"));

    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements(ElementSelectors.byName("Fraud is detected"));
  }
}
