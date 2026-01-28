package io.camunda.dev.frauddetection;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

@SpringBootTest(properties = {"camunda.client.worker.defaults.enabled=false"})
@CamundaSpringProcessTest
public class FraudDetectionTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void detectsFraudOnExpertAnalysis_whenExpertDetectsFraud() {
    // given process definition is deployed
    //    processTestContext
    //        .mockJobWorker("io.camunda:http-json:1")
    //        .withHandler(
    //            (jobClient, job) -> {
    //              jobClient
    //                  .newCompleteCommand(job)
    //                  .variables(
    //                      Map.of(
    //                          "fraudDetected", "true",
    //                          "expertAnalysis",
    //                              "After analyzing the transactions, I found several indicators of
    // fraud."))
    //                  .send()
    //                  .join();
    //            });
    processTestContext
        .mockJobWorker("io.camunda.agenticai:aiagent-job-worker:1")
        .withHandler(
            (jobClient, job) -> {
              jobClient
                  .newCompleteCommand(job)
                  .withResult(
                      result ->
                          result
                              .forAdHocSubProcess()
                              .activateElement("CallOnExternalAdvisor")
                              .variables(
                                  Map.of(
                                      "taxSubmission",
                                          "There are a lot of unusual transactions this year.",
                                      "tendency", "I think this is fraudulent behavior.",
                                      "suspiciousParts", "A B C"))
                              .completionConditionFulfilled(false))
                  .send()
                  .join();
            });

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
            "fraudDetected", true, "expertAnalysis", "There is the best fraud we have ever seen!"));

    // then
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasCompletedElements(
            "CallOnExternalAdvisor", "Event_1sxkleb")
            .hasTerminatedElements("Event_0gnk722", "Fraud_Detection_Agent");

  }
}