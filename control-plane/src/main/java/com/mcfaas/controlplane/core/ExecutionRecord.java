package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ErrorInfo;
import com.mcfaas.common.model.InvocationResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class ExecutionRecord {
    private final String executionId;
    private final InvocationTask task;
    private final CompletableFuture<InvocationResult> completion;
    private volatile ExecutionState state;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile ErrorInfo lastError;
    private volatile Object output;

    public ExecutionRecord(String executionId, InvocationTask task) {
        this.executionId = executionId;
        this.task = task;
        this.completion = new CompletableFuture<>();
        this.state = ExecutionState.QUEUED;
    }

    public String executionId() {
        return executionId;
    }

    public InvocationTask task() {
        return task;
    }

    public CompletableFuture<InvocationResult> completion() {
        return completion;
    }

    public ExecutionState state() {
        return state;
    }

    public void state(ExecutionState state) {
        this.state = state;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void startedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public void finishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public ErrorInfo lastError() {
        return lastError;
    }

    public void lastError(ErrorInfo lastError) {
        this.lastError = lastError;
    }

    public Object output() {
        return output;
    }

    public void output(Object output) {
        this.output = output;
    }
}
