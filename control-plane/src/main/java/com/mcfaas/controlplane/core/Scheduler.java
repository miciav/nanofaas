package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ExecutionMode;
import com.mcfaas.common.model.InvocationResult;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Scheduler implements SmartLifecycle {
    private final QueueManager queueManager;
    private final DispatcherRouter dispatcherRouter;
    private final ExecutionStore executionStore;
    private final InvocationService invocationService;
    private final Metrics metrics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long tickMs = 2;

    public Scheduler(QueueManager queueManager,
                     DispatcherRouter dispatcherRouter,
                     ExecutionStore executionStore,
                     InvocationService invocationService,
                     Metrics metrics) {
        this.queueManager = queueManager;
        this.dispatcherRouter = dispatcherRouter;
        this.executionStore = executionStore;
        this.invocationService = invocationService;
        this.metrics = metrics;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::loop);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void loop() {
        while (running.get()) {
            boolean didWork = false;
            for (FunctionQueueState state : queueManager.states()) {
                if (!state.canDispatch()) {
                    continue;
                }
                InvocationTask task = state.poll();
                if (task == null) {
                    continue;
                }
                didWork = true;
                dispatch(state, task);
            }
            if (!didWork) {
                sleep(tickMs);
            }
        }
    }

    private void dispatch(FunctionQueueState state, InvocationTask task) {
        ExecutionRecord record = executionStore.get(task.executionId()).orElse(null);
        if (record == null) {
            return;
        }
        state.incrementInFlight();
        record.state(ExecutionState.RUNNING);
        record.startedAt(Instant.now());
        metrics.dispatch(task.functionName());

        if (task.functionSpec().executionMode() == ExecutionMode.LOCAL) {
            dispatcherRouter.dispatchLocal(task).whenComplete((result, error) -> {
                if (error != null) {
                    invocationService.completeExecution(task.executionId(),
                            InvocationResult.error("LOCAL_ERROR", error.getMessage()));
                    return;
                }
                invocationService.completeExecution(task.executionId(), result);
            });
            return;
        }

        if (task.functionSpec().executionMode() == ExecutionMode.POOL) {
            dispatcherRouter.dispatchPool(task).whenComplete((result, error) -> {
                if (error != null) {
                    invocationService.completeExecution(task.executionId(),
                            InvocationResult.error("POOL_ERROR", error.getMessage()));
                    return;
                }
                invocationService.completeExecution(task.executionId(), result);
            });
            return;
        }

        dispatcherRouter.dispatchRemote(task).whenComplete((result, error) -> {
            if (error != null) {
                invocationService.completeExecution(task.executionId(),
                        InvocationResult.error("DISPATCH_ERROR", error.getMessage()));
            }
        });
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
