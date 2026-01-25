package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ExecutionStatus;
import com.mcfaas.common.model.FunctionSpec;
import com.mcfaas.common.model.InvocationRequest;
import com.mcfaas.common.model.InvocationResponse;
import com.mcfaas.common.model.InvocationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class InvocationService {
    private final FunctionService functionService;
    private final QueueManager queueManager;
    private final ExecutionStore executionStore;
    private final IdempotencyStore idempotencyStore;
    private final RateLimiter rateLimiter;
    private final Metrics metrics;

    public InvocationService(FunctionService functionService,
                             QueueManager queueManager,
                             ExecutionStore executionStore,
                             IdempotencyStore idempotencyStore,
                             RateLimiter rateLimiter,
                             Metrics metrics) {
        this.functionService = functionService;
        this.queueManager = queueManager;
        this.executionStore = executionStore;
        this.idempotencyStore = idempotencyStore;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    public InvocationResponse invokeSync(String functionName,
                                         InvocationRequest request,
                                         String idempotencyKey,
                                         String traceId,
                                         Integer timeoutOverrideMs) throws InterruptedException {
        enforceRateLimit();

        FunctionSpec spec = functionService.get(functionName).orElseThrow(FunctionNotFoundException::new);
        ExecutionLookup lookup = createOrReuseExecution(functionName, spec, request, idempotencyKey, traceId);
        ExecutionRecord record = lookup.record();

        if (record.state() == ExecutionState.SUCCESS || record.state() == ExecutionState.ERROR) {
            InvocationResult result = record.lastError() == null
                    ? InvocationResult.success(record.output())
                    : new InvocationResult(false, null, record.lastError());
            return toResponse(record, result);
        }

        if (lookup.isNew()) {
            enqueueOrThrow(record);
        }

        int timeoutMs = timeoutOverrideMs == null ? spec.timeoutMs() : timeoutOverrideMs;
        try {
            InvocationResult result = record.completion().get(timeoutMs, TimeUnit.MILLISECONDS);
            return toResponse(record, result);
        } catch (Exception ex) {
            record.state(ExecutionState.TIMEOUT);
            metrics.timeout(functionName);
            return new InvocationResponse(record.executionId(), "timeout", null, null);
        }
    }

    public InvocationResponse invokeAsync(String functionName,
                                          InvocationRequest request,
                                          String idempotencyKey,
                                          String traceId) {
        enforceRateLimit();

        FunctionSpec spec = functionService.get(functionName).orElseThrow(FunctionNotFoundException::new);
        ExecutionLookup lookup = createOrReuseExecution(functionName, spec, request, idempotencyKey, traceId);
        ExecutionRecord record = lookup.record();

        if (lookup.isNew()) {
            enqueueOrThrow(record);
        }
        return new InvocationResponse(record.executionId(), "queued", null, null);
    }

    public Optional<ExecutionStatus> getStatus(String executionId) {
        return executionStore.get(executionId).map(this::toStatus);
    }

    public void completeExecution(String executionId, InvocationResult result) {
        ExecutionRecord record = executionStore.get(executionId).orElse(null);
        if (record == null) {
            return;
        }

        record.finishedAt(Instant.now());
        if (result.success()) {
            record.state(ExecutionState.SUCCESS);
            record.output(result.output());
            metrics.success(record.task().functionName());
        } else {
            record.state(ExecutionState.ERROR);
            record.lastError(result.error());
            metrics.error(record.task().functionName());
        }
        if (record.startedAt() != null && record.finishedAt() != null) {
            long durationMs = record.finishedAt().toEpochMilli() - record.startedAt().toEpochMilli();
            metrics.latency(record.task().functionName()).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        record.completion().complete(result);
        queueManager.decrementInFlight(record.task().functionName());

        if (!result.success() && record.task().attempt() < record.task().functionSpec().maxRetries()) {
            metrics.retry(record.task().functionName());
            InvocationTask retryTask = new InvocationTask(
                    record.executionId(),
                    record.task().functionName(),
                    record.task().functionSpec(),
                    record.task().request(),
                    record.task().idempotencyKey(),
                    record.task().traceId(),
                    Instant.now(),
                    record.task().attempt() + 1
            );
            ExecutionRecord retryRecord = new ExecutionRecord(record.executionId(), retryTask);
            executionStore.put(retryRecord);
            enqueueOrThrow(retryRecord);
        }
    }

    private void enforceRateLimit() {
        if (!rateLimiter.allow()) {
            throw new RateLimitException();
        }
    }

    private ExecutionLookup createOrReuseExecution(String functionName,
                                                   FunctionSpec spec,
                                                   InvocationRequest request,
                                                   String idempotencyKey,
                                                   String traceId) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> existingId = idempotencyStore.getExecutionId(functionName, idempotencyKey);
            if (existingId.isPresent()) {
                ExecutionRecord existing = executionStore.get(existingId.get()).orElse(null);
                if (existing != null) {
                    return new ExecutionLookup(existing, false);
                }
            }
        }

        String executionId = UUID.randomUUID().toString();
        InvocationTask task = new InvocationTask(
                executionId,
                functionName,
                spec,
                request,
                idempotencyKey,
                traceId,
                Instant.now(),
                1
        );
        ExecutionRecord record = new ExecutionRecord(executionId, task);
        executionStore.put(record);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(functionName, idempotencyKey, executionId);
        }
        return new ExecutionLookup(record, true);
    }

    private void enqueueOrThrow(ExecutionRecord record) {
        boolean enqueued = queueManager.enqueue(record.task());
        if (!enqueued) {
            metrics.queueRejected(record.task().functionName());
            throw new QueueFullException();
        }
        metrics.enqueue(record.task().functionName());
    }

    private InvocationResponse toResponse(ExecutionRecord record, InvocationResult result) {
        String status = result.success() ? "success" : "error";
        return new InvocationResponse(record.executionId(), status, result.output(), result.error());
    }

    private ExecutionStatus toStatus(ExecutionRecord record) {
        String status = record.state().name().toLowerCase();
        return new ExecutionStatus(
                record.executionId(),
                status,
                record.startedAt(),
                record.finishedAt(),
                record.output(),
                record.lastError()
        );
    }

    private record ExecutionLookup(ExecutionRecord record, boolean isNew) {
    }
}
