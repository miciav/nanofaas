package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.FunctionSpec;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KubernetesJobBuilder {
    private final KubernetesProperties properties;

    public KubernetesJobBuilder(KubernetesProperties properties) {
        this.properties = properties;
    }

    public Job build(InvocationTask task) {
        FunctionSpec spec = task.functionSpec();
        Map<String, String> env = spec.env();
        List<io.fabric8.kubernetes.api.model.EnvVar> envVars = new ArrayList<>();

        envVars.add(new EnvVarBuilder().withName("FUNCTION_NAME").withValue(task.functionName()).build());
        envVars.add(new EnvVarBuilder().withName("EXECUTION_ID").withValue(task.executionId()).build());
        if (task.traceId() != null) {
            envVars.add(new EnvVarBuilder().withName("TRACE_ID").withValue(task.traceId()).build());
        }
        envVars.add(new EnvVarBuilder().withName("INVOCATION_TIMEOUT_MS")
                .withValue(String.valueOf(spec.timeoutMs()))
                .build());
        String callbackUrl = properties.callbackUrl() == null ? "" : properties.callbackUrl();
        envVars.add(new EnvVarBuilder().withName("CALLBACK_URL")
                .withValue(callbackUrl)
                .build());

        if (env != null) {
            env.forEach((key, value) -> envVars.add(new EnvVarBuilder().withName(key).withValue(value).build()));
        }

        ResourceRequirementsBuilder resources = new ResourceRequirementsBuilder();
        if (spec.resources() != null) {
            if (spec.resources().cpu() != null) {
                resources.addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity(spec.resources().cpu()));
                resources.addToLimits("cpu", new io.fabric8.kubernetes.api.model.Quantity(spec.resources().cpu()));
            }
            if (spec.resources().memory() != null) {
                resources.addToRequests("memory", new io.fabric8.kubernetes.api.model.Quantity(spec.resources().memory()));
                resources.addToLimits("memory", new io.fabric8.kubernetes.api.model.Quantity(spec.resources().memory()));
            }
        }

        return new JobBuilder()
                .withNewMetadata()
                .withGenerateName("fn-" + task.functionName() + "-")
                .addToLabels("app", "mcfaas")
                .addToLabels("function", task.functionName())
                .addToLabels("executionId", task.executionId())
                .addToAnnotations("traceId", task.traceId() == null ? "" : task.traceId())
                .addToAnnotations("idempotencyKey", task.idempotencyKey() == null ? "" : task.idempotencyKey())
                .endMetadata()
                .withSpec(new io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder()
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withSpec(new PodSpecBuilder()
                                        .addNewContainer()
                                        .withName("function")
                                        .withImage(spec.image())
                                        .withCommand(spec.command() == null || spec.command().isEmpty() ? null : spec.command())
                                        .withEnv(envVars)
                                        .withResources(resources.build())
                                        .endContainer()
                                        .withRestartPolicy("Never")
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
