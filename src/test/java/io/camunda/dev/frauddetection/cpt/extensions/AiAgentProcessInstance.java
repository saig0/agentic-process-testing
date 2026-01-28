package io.camunda.dev.frauddetection.cpt.extensions;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;

public class AiAgentProcessInstance {

    public static JobWorkerMockBuilder mockAiAigentSubProcessType(CamundaProcessTestContext testContext) {
        return testContext.mockJobWorker("io.camunda.agenticai:aiagent-job-worker:1");
    }

    public static JobWorkerMockBuilder mockAiAgentTask(CamundaProcessTestContext testContext) {
        return testContext.mockJobWorker("io.camunda.agenticai:aiagent:1");
    }
}
