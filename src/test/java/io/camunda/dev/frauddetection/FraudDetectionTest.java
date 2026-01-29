package io.camunda.dev.frauddetection;

import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.dev.frauddetection.cpt.extensions.AiAgentProcessInstance;
import io.camunda.dev.frauddetection.cpt.extensions.AiAgentResultHandler;
import io.camunda.dev.frauddetection.cpt.extensions.FluentVariableYield;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.camunda.process.test.api.CptAssertHelper;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
public class FraudDetectionTest {

  private static final String PROCESS_DEFINITION_ID = "fraud-detection-process";
  private static final String EMAIL_MESSAGE_ID = "1";
  private static final String EMAIL_MESSAGE_NAME = "ba04712f-eae7-433a-9dd4-c56286e65940";

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void analyzesFraud_withMultipleAgentInteractions() throws Throwable {
    // given
    emailAsksForReasoningAboutTransactions();
    fraudDetectionDoesNotDetectFraud();
    judgeRequestsFurtherFeedbackTwoTimes();
    aiAgentTraversesToolsUntilFraudDetection();

    // when
    final ProcessInstanceEvent processInstance = createProcessInstance();

    assertExternalAdvisorIsConsulted(processInstance);
    userSubmitsNoFraudDetected();
    assertProcessIsWaitingForUserMessage(processInstance);

    // then
    whenUserMessageIsReceived(
        processInstance,
        pi ->
            CamundaAssert.assertThat(pi)
                .isCompleted()
                .hasCompletedElement(byName("Fraud Detection Agent"), 2)
                .hasCompletedElements(byName("Call On External Advisor"), byName("No Fraud!"))
                .hasTerminatedElement(byName("Fraud Detection Agent"), 1)
                .hasTerminatedElements(byName("Throw Fraud"))
                .hasCompletedElements(
                    byName("Report fraud detection"), byName("Fraud is detected")));
  }

  private void aiAgentTraversesToolsUntilFraudDetection() {
    AiAgentProcessInstance.mockAiAigentSubProcessType(processTestContext)
        .withHandler(
            AiAgentResultHandler.with(this::callExternalAdvisor)
                .then(this::finalize)
                .then(this::askUserInAnEmail)
                .then(this::finalize)
                .then(this::detectFraud)
                .wire());
  }

  private void judgeRequestsFurtherFeedbackTwoTimes() {
    AiAgentProcessInstance.mockAiAgentTask(processTestContext)
        .withHandler(FluentVariableYield.yieldingVariables("finalCheck", "no", "no", "yes"));
  }

  private void fraudDetectionDoesNotDetectFraud() {
    processTestContext
        .mockJobWorker("io.camunda:http-json:1")
        .thenComplete(
            Map.of(
                "fraudDetected",
                "false",
                "expertAnalysis",
                "After analyzing the transactions, I found several indicators of fraud."));
  }

  private void emailAsksForReasoningAboutTransactions() {
    processTestContext
        .mockJobWorker("io.camunda:email:1")
        .thenComplete(
            Map.of(
                "emailSent",
                Map.of(
                    "messageId",
                    "1",
                    "body",
                    "Dear user, we have detected fraud in your submission. Please state your opinion!")));
  }

  private static void assertExternalAdvisorIsConsulted(ProcessInstanceEvent processInstance) {
    CamundaAssert.assertThat(processInstance).hasActiveElement("CallOnExternalAdvisor", 1);
  }

  private ProcessInstanceEvent createProcessInstance() {
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fraud-detection-process")
            .latestVersion()
            .variables(Map.of("taxSubmission", "ABC"))
            .send()
            .join();
    return processInstance;
  }

  private void userSubmitsNoFraudDetected() {
    processTestContext.completeUserTask(
        byElementId("CallOnExternalAdvisor"),
        Map.of(
            "fraudDetected",
            false,
            "expertAnalysis",
            "There is the best fraud that we have ever seen!"));
  }

  private void publishUserMessage() {
    client
        .newPublishMessageCommand()
        .messageName("ba04712f-eae7-433a-9dd4-c56286e65940")
        .correlationKey("1")
        .variables(Map.of("plainTextBody", "I did not commit fraud!"))
        .send()
        .join();
  }

  private static void assertProcessIsWaitingForUserMessage(ProcessInstanceEvent processInstance) {
    await()
        .pollInSameThread()
        .untilAsserted(
            () -> {
              CamundaAssert.assertThat(processInstance).hasActiveElement("Event_1gwy74w", 1);
            });
  }

  private CompleteAdHocSubProcessResultStep1 callExternalAdvisor(
      CompleteJobCommandStep1.CompleteJobCommandJobResultStep resultStep) {
    return resultStep
        .forAdHocSubProcess()
        .activateElement("CallOnExternalAdvisor")
        .variables(
            Map.of(
                "taxSubmission",
                "There are a lot of unusual transactions this year.",
                "tendency",
                "I think this is fraudulent behavior.",
                "suspiciousParts",
                "A B C"))
        .completionConditionFulfilled(false);
  }

  private CompleteAdHocSubProcessResultStep1 askUserInAnEmail(
      CompleteJobCommandStep1.CompleteJobCommandJobResultStep resultStep) {
    return resultStep
        .forAdHocSubProcess()
        .activateElement("Activity_1yge9uw")
        .variables(
            Map.of(
                "taxSubmission",
                "There are a lot of unusual transactions this year.",
                "whatToClarify",
                "I the submitter lying to us?"))
        .completionConditionFulfilled(false);
  }

  private CompleteAdHocSubProcessResultStep1 detectFraud(
      CompleteJobCommandStep1.CompleteJobCommandJobResultStep resultStep) {
    return resultStep
        .forAdHocSubProcess()
        .activateElement("FraudDetected")
        .completionConditionFulfilled(false);
  }

  private CompleteAdHocSubProcessResultStep1 finalize(
      CompleteJobCommandStep1.CompleteJobCommandJobResultStep resultStep) {
    return resultStep.forAdHocSubProcess().completionConditionFulfilled(true);
  }

  private void whenUserMessageIsReceived(
      @NonNull ProcessInstanceEvent event, ThrowingConsumer<ProcessInstanceEvent> assertions)
      throws Throwable {
    publishUserMessage();
    assertions.accept(event);
  }

  @Test
  void shouldDetectFraud_imperativeStyle() {
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

    // when
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult ->
            jobResult
                .activateElement("CallOnExternalAdvisor")
                .variables(
                    Map.ofEntries(
                        Map.entry(
                            "taxSubmission", "There are a lot of unusual transactions this year."),
                        Map.entry("tendency", "I think this is fraudulent behavior."),
                        Map.entry("suspiciousParts", "A B C"))));

    processTestContext.completeUserTask(
        byElementId("CallOnExternalAdvisor"),
        Map.ofEntries(
            Map.entry("fraudDetected", false),
            Map.entry("expertAnalysis", "There is the best fraud that we have ever seen!")));

    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.completionConditionFulfilled(true));

    processTestContext.completeJob(
        JobSelectors.byElementId("Judge_Agent"), Map.of("finalCheck", "no"));

    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult ->
            jobResult
                .activateElement("Activity_1yge9uw")
                .variables(
                    Map.ofEntries(
                        Map.entry(
                            "taxSubmission", "There are a lot of unusual transactions this year."),
                        Map.entry("whatToClarify", "I the submitter lying to us?"))));

    processTestContext.completeJob(
        "io.camunda:email:1",
        Map.of(
            "emailSent",
            Map.ofEntries(
                Map.entry("messageId", EMAIL_MESSAGE_ID),
                Map.entry(
                    "body",
                    "Dear user, we have detected fraud in your submission. Please state your opinion!"))));

    client
        .newPublishMessageCommand()
        .messageName(EMAIL_MESSAGE_NAME)
        .correlationKey(EMAIL_MESSAGE_ID)
        .variables(Map.of("plainTextBody", "I did not commit fraud!"))
        .send()
        .join();

    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.completionConditionFulfilled(true));

    processTestContext.completeJob(
        JobSelectors.byElementId("Judge_Agent"), Map.of("finalCheck", "no"));

    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.activateElement("FraudDetected"));

    // verify
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder(
            byName("Call On External Advisor"),
            byName("Send inquiry e-mail"),
            byName("Report fraud detection"),
            byName("Fraud is detected"));
  }

  @Test
  void shouldDetectFraud_scenarioStyle() {
    CptAssertHelper.scenario(client)
        .when(
            () -> {
              CamundaAssert.assertThatUserTask(byElementId("CallOnExternalAdvisor")).isCreated();
            })
        .then(
            () -> {
              processTestContext.completeUserTask(
                  byElementId("CallOnExternalAdvisor"),
                  Map.ofEntries(
                      Map.entry("fraudDetected", false),
                      Map.entry(
                          "expertAnalysis", "There is the best fraud that we have ever seen!")));
            });

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
                  .hasActiveElements(byName("User response received"));
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

    // when
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult ->
            jobResult
                .activateElement("CallOnExternalAdvisor")
                .variables(
                    Map.ofEntries(
                        Map.entry(
                            "taxSubmission", "There are a lot of unusual transactions this year."),
                        Map.entry("tendency", "I think this is fraudulent behavior."),
                        Map.entry("suspiciousParts", "A B C"))));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.completionConditionFulfilled(true));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJob(
        JobSelectors.byElementId("Judge_Agent"), Map.of("finalCheck", "no"));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult ->
            jobResult
                .activateElement("Activity_1yge9uw")
                .variables(
                    Map.ofEntries(
                        Map.entry(
                            "taxSubmission", "There are a lot of unusual transactions this year."),
                        Map.entry("whatToClarify", "I the submitter lying to us?"))));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.completionConditionFulfilled(true));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJob(
        JobSelectors.byElementId("Judge_Agent"), Map.of("finalCheck", "no"));

    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));
    processTestContext.completeJobOfAdHocSubProcess(
        JobSelectors.byElementId("Fraud_Detection_Agent"),
        jobResult -> jobResult.activateElement("FraudDetected"));

    // verify
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(10));

    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElementsInOrder(
            byName("Call On External Advisor"),
            byName("Send inquiry e-mail"),
            byName("Report fraud detection"),
            byName("Fraud is detected"));
  }
}
