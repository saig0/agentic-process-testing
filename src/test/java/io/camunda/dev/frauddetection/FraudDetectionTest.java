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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
public class FraudDetectionTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void detectsFraudOnExpertAnalysis_whenExpertDetectsFraud() {
    // given process definition is deployed
    processTestContext
        .mockJobWorker("io.camunda:http-json:1")
        .withHandler(
            (jobClient, job) -> {
              jobClient
                  .newCompleteCommand(job)
                  .variables(
                      Map.of(
                          "fraudDetected", "false",
                          "expertAnalysis",
                              "After analyzing the transactions, I found several indicators of fraud."))
                  .send()
                  .join();
            });

    processTestContext.mockJobWorker("io.camunda.agenticai:aiagent:1")
            .withHandler( (jobClient, job) -> jobClient.newCompleteCommand(job)
                    .variables(Map.of("finalCheck", "yes")).send().join());

    var aiAgentChain = AiAgentResultHandler.with(this::callExternalAdvisor)
                          .then(this::finalize)
            .build();
    processTestContext
        .mockJobWorker("io.camunda.agenticai:aiagent-job-worker:1")
        .withHandler(
            (jobClient, job) -> jobClient
                .newCompleteCommand(job)
                .withResult(aiAgentChain)
                .send()
                .join());

    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("fraud-detection-process")
            .latestVersion()
            .variables(Map.of("taxSubmission", "ABC"))
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).hasActiveElement("CallOnExternalAdvisor", 1);

    processTestContext.completeUserTask(
        byElementId("CallOnExternalAdvisor"),
        Map.of(
            "fraudDetected",
            false,
            "expertAnalysis",
            "There is the best fraud that we have ever seen!"));

    //    CamundaAssert.assertThat(processInstance).hasActiveElement("EmailInquiry", 1);

    //    processTestContext.completeUserTask(
    //        byElementId("CallOnExternalAdvisor"),
    //        Map.of(
    //            "fraudDetected", false, "expertAnalysis", "There is the best fraud we have ever
    // seen!"));

    // then
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements("CallOnExternalAdvisor", "Event_0ut60ps", "Fraud_Detection_Agent");
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

  private CompleteAdHocSubProcessResultStep1 finalize(
      CompleteJobCommandStep1.CompleteJobCommandJobResultStep resultStep) {
    return resultStep.forAdHocSubProcess().completionConditionFulfilled(true);
  }
}
