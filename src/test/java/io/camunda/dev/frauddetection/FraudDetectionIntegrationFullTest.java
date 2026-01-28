package io.camunda.dev.frauddetection;

import com.icegreen.greenmail.spring.GreenMailBean;
import com.icegreen.greenmail.util.GreenMailUtil;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ElementSelectors;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("integration-test")
@EnabledIfEnvironmentVariables(
    value = {
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".+"),
      @EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_SECRET_KEY", matches = ".+"),
    })
public class FraudDetectionIntegrationFullTest {

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

    assertThat(greenMailBean.isStarted()).isTrue();
  }

  @Test
  void executesFraudDetectionAgent() {
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
                        Map.entry("totalExpenses", 10000),
                        Map.entry("largePurchases", List.of("stocks")),
                        Map.entry("charitableDonations", "None"))))
            .send()
            .join();

    // case 1: request additional information via e-mail and receive user reply
    CamundaAssert.assertThat(processInstance)
        .hasCompletedElements(ElementSelectors.byName("Send inquiry e-mail"))
        .hasActiveElements(ElementSelectors.byName("User response received"));

    assertThat(Arrays.asList(greenMailBean.getReceivedMessages()))
        .hasSize(1)
        .first()
        .satisfies(
            message -> {
              assertThat(message.getSubject()).isEqualTo("Tax Return Inquiry");
              assertThat(message.getContentType()).startsWith("multipart/mixed;");
              MimeMultipart multipart = (MimeMultipart) message.getContent();
              // just a poor verification of the e-mail content
              assertThat((String) multipart.getBodyPart(0).getContent())
                  .containsAnyOf(
                      "additional clarification",
                      "additional information",
                      "request clarification",
                      "provide additional details");
            });

    MimeMessage receivedMessage = greenMailBean.getReceivedMessages()[0];
    sendReplyMessage(receivedMessage);

    CamundaAssert.assertThat(processInstance)
        .hasCompletedElements(ElementSelectors.byName("User response received"));

    // case 2: call on expert analysis and detect fraud
    assertThatUserTask(byTaskName("Call On External Advisor")).isCreated();

    processTestContext.completeUserTask(
        byTaskName("Call On External Advisor"),
        Map.of("expertAnalysis", "Don't bother me", "fraudDetected", true));

    // then: verify that fraud is detected
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements(ElementSelectors.byName("Fraud is detected"));
  }

  private void sendReplyMessage(MimeMessage receivedMessage) {
    try {
      MimeMessage message = new MimeMessage(greenMailBean.getGreenMail().getSmtp().createSession());
      message.addRecipients(Message.RecipientType.TO, receivedMessage.getFrom());
      message.addFrom(receivedMessage.getRecipients(Message.RecipientType.TO));
      message.setHeader("In-Reply-To", receivedMessage.getMessageID());
      message.setSubject("Re: " + receivedMessage.getSubject());
      message.setText("All good, trust me!");

      GreenMailUtil.sendMimeMessage(message);
    } catch (Exception e) {
      throw new RuntimeException("Failed to send reply message", e);
    }
  }
}
