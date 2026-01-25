package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.InvocationResult;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class KubernetesDispatcher implements Dispatcher {
    private final KubernetesClient client;
    private final KubernetesProperties properties;

    public KubernetesDispatcher(KubernetesClient client, KubernetesProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public CompletableFuture<InvocationResult> dispatch(InvocationTask task) {
        return CompletableFuture.supplyAsync(() -> {
            KubernetesJobBuilder builder = new KubernetesJobBuilder(properties);
            Job job = builder.build(task);
            client.batch().v1().jobs()
                    .inNamespace(namespace())
                    .resource(job)
                    .create();
            return InvocationResult.success(null);
        });
    }

    private String namespace() {
        if (properties.namespace() != null && !properties.namespace().isBlank()) {
            return properties.namespace();
        }
        String env = System.getenv("POD_NAMESPACE");
        return env == null || env.isBlank() ? "default" : env;
    }
}
