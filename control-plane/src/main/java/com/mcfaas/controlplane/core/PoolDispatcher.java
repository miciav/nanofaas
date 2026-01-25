package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.InvocationResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PoolDispatcher implements Dispatcher {
    private final WebClient webClient;

    public PoolDispatcher(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public CompletableFuture<InvocationResult> dispatch(InvocationTask task) {
        String endpoint = task.functionSpec().endpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return CompletableFuture.completedFuture(
                    InvocationResult.error("POOL_ENDPOINT_MISSING", "endpointUrl is required for POOL mode"));
        }

        WebClient.RequestBodySpec request = webClient.post()
                .uri(endpoint)
                .header("X-Execution-Id", task.executionId());

        if (task.traceId() != null) {
            request.header("X-Trace-Id", task.traceId());
        }
        if (task.idempotencyKey() != null) {
            request.header("Idempotency-Key", task.idempotencyKey());
        }

        return request.bodyValue(task.request())
                .retrieve()
                .bodyToMono(Object.class)
                .map(InvocationResult::success)
                .onErrorResume(ex -> reactor.core.publisher.Mono.just(
                        InvocationResult.error("POOL_ERROR", ex.getMessage())))
                .toFuture();
    }
}
