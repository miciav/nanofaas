package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ExecutionMode;
import com.mcfaas.common.model.FunctionSpec;
import com.mcfaas.common.model.InvocationRequest;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KubernetesDispatcherTest {
    private KubernetesServer server;

    @BeforeEach
    void setUp() {
        server = new KubernetesServer(true, true);
        server.before();
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    @Test
    void issue011_dispatchCreatesJob() {
        KubernetesClient client = server.getClient();
        KubernetesProperties properties = new KubernetesProperties("default", "http://control-plane/v1/internal/executions", 10);
        KubernetesDispatcher dispatcher = new KubernetesDispatcher(client, properties);

        FunctionSpec spec = new FunctionSpec(
                "echo",
                "mcfaas/function-runtime:0.1.0",
                List.of(),
                Map.of(),
                null,
                1000,
                1,
                10,
                3,
                null,
                ExecutionMode.REMOTE
        );
        InvocationTask task = new InvocationTask(
                "exec-1",
                "echo",
                spec,
                new InvocationRequest("payload", Map.of()),
                null,
                null,
                Instant.now(),
                1
        );

        dispatcher.dispatch(task).join();

        int jobCount = client.batch().v1().jobs().inNamespace("default").list().getItems().size();
        assertEquals(1, jobCount);
    }
}
