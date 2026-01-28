package io.camunda.dev.frauddetection;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.dev.frauddetection.cpt.AiAgentResultHandler;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
public class FraudDetectionTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void detectsFraudOnExpertAnalysis_whenExpertDetectsFraud() throws Throwable {
    // given
    mockEmailSending();
    mockFraudDetection();
    mockJudgeInteraction();
    mockAiAgentInteraction();

    // when
    final ProcessInstanceEvent processInstance = deployProcess();

    assertExternalAdvisorIsConsulted(processInstance);
    userSubmitsNoFraudDetected();
    assertProcessIsWaitingForUserMessage(processInstance);

    // then
    whenUserMessageIsReceived(processInstance, pi -> {
        assert pi != null;
        CamundaAssert.assertThat(pi)
              .isCompleted()
              .hasCompletedElement("Fraud_Detection_Agent", 2)
              .hasCompletedElements("CallOnExternalAdvisor", "Event_0ut60ps")
              .hasTerminatedElement("Fraud_Detection_Agent", 1)
              .hasTerminatedElements("Event_0gnk722")
              .hasCompletedElements("Activity_0o48wy2", "Event_1sxkleb");
    });
  }

  private void mockAiAgentInteraction() {
    var aiAgentChain =
        AiAgentResultHandler.with(this::callExternalAdvisor)
            .then(this::finalize)
            .then(this::generateEmailInquiry)
            .then(this::finalize)
            .then(this::detectFraud)
            .build();
    processTestContext
        .mockJobWorker("io.camunda.agenticai:aiagent-job-worker:1")
        .withHandler(
            (jobClient, job) ->
                jobClient.newCompleteCommand(job).withResult(aiAgentChain).send().join());
  }

  private void mockJudgeInteraction() {
    var judgeCall = new AtomicInteger(0);
    processTestContext
        .mockJobWorker("io.camunda.agenticai:aiagent:1")
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variables(
                        judgeCall.getAndIncrement() < 2
                            ? Map.of("finalCheck", "no")
                            : Map.of("finalCheck", "yes"))
                    .send()
                    .join());
  }

  private void mockFraudDetection() {
    processTestContext
        .mockJobWorker("io.camunda:http-json:1")
        .thenComplete(
            Map.of(
                "fraudDetected",
                "false",
                "expertAnalysis",
                "After analyzing the transactions, I found several indicators of fraud."));
  }

  private void mockEmailSending() {
    processTestContext
        .mockJobWorker("io.camunda:email:1")
            .thenComplete(Map.of(
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

  private ProcessInstanceEvent deployProcess() {
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

  private CompleteAdHocSubProcessResultStep1 generateEmailInquiry(
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

  private void whenUserMessageIsReceived(ProcessInstanceEvent event, ThrowingConsumer<ProcessInstanceEvent> assertions) throws Throwable {
    publishUserMessage();
    assertions.accept(event);
  }
}
