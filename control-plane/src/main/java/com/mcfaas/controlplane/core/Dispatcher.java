package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.InvocationResult;

import java.util.concurrent.CompletableFuture;

public interface Dispatcher {
    CompletableFuture<InvocationResult> dispatch(InvocationTask task);
}
