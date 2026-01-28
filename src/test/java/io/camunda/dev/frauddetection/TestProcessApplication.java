package io.camunda.dev.frauddetection;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Deployment(
    resources = {
      "classpath:/fraud-detection/**/*.bpmn",
      "classpath:/fraud-detection/**/*.form"
    })
public class TestProcessApplication {}
