package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ExecutionMode;
import com.mcfaas.common.model.FunctionSpec;
import com.mcfaas.common.model.InvocationRequest;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KubernetesJobBuilderTest {
    @Test
    void issue012_jobTemplateIncludesImageAndEnv() {
        KubernetesProperties properties = new KubernetesProperties("mcfaas", "http://control-plane/v1/internal/executions", 10);
        KubernetesJobBuilder builder = new KubernetesJobBuilder(properties);

        FunctionSpec spec = new FunctionSpec(
                "echo",
                "mcfaas/function-runtime:0.1.0",
                List.of("java", "-jar", "app.jar"),
                Map.of("FOO", "bar"),
                null,
                1000,
                1,
                10,
                3,
                null,
                ExecutionMode.REMOTE
        );

        InvocationTask task = new InvocationTask(
                "exec-123",
                "echo",
                spec,
                new InvocationRequest("hi", Map.of()),
                "idem",
                "trace",
                Instant.now(),
                1
        );

        Job job = builder.build(task);
        assertEquals("mcfaas/function-runtime:0.1.0", job.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertNotNull(job.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
    }
}
