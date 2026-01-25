package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.ExecutionMode;
import com.mcfaas.common.model.InvocationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Scheduler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final QueueManager queueManager;
    private final DispatcherRouter dispatcherRouter;
    private final ExecutionStore executionStore;
    private final InvocationService invocationService;
    private final Metrics metrics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcfaas-scheduler");
        t.setDaemon(false);
        return t;
    });
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
            log.info("Scheduler starting");
            executor.submit(this::loop);
        }
    }

    @Override
    public void stop() {
        log.info("Scheduler stopping...");
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Scheduler did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            log.warn("Scheduler shutdown interrupted");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Scheduler stopped");
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
        log.debug("Scheduler loop started");
        while (running.get()) {
            boolean didWork = false;
            for (FunctionQueueState state : queueManager.states()) {
                if (!running.get()) {
                    break;  // Exit early if stopping
                }
                // Atomically acquire a dispatch slot
                if (!state.tryAcquireSlot()) {
                    continue;
                }
                // Slot acquired - try to get a task
                InvocationTask task = state.poll();
                if (task == null) {
                    // No task in queue - release the slot
                    state.releaseSlot();
                    continue;
                }
                didWork = true;
                dispatch(state, task);
            }
            if (!didWork) {
                sleep(tickMs);
            }
        }
        log.debug("Scheduler loop exited");
    }

    private void dispatch(FunctionQueueState state, InvocationTask task) {
        ExecutionRecord record = executionStore.get(task.executionId()).orElse(null);
        if (record == null) {
            state.releaseSlot();  // Release slot if record not found
            return;
        }
        // Slot already acquired in loop() via tryAcquireSlot()
        // Mark running atomically (sets state and startedAt together)
        record.markRunning();
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

        // REMOTE dispatch: Job is created asynchronously in K8s.
        // Completion will happen via callback when the function pod finishes.
        // We only handle errors here - success is a no-op because the callback
        // from the function pod will call completeExecution().
        dispatcherRouter.dispatchRemote(task).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to dispatch REMOTE task {} for function {}: {}",
                        task.executionId(), task.functionName(), error.getMessage());
                invocationService.completeExecution(task.executionId(),
                        InvocationResult.error("DISPATCH_ERROR", error.getMessage()));
            } else {
                log.debug("K8s Job created for execution {}, function {}. Waiting for callback.",
                        task.executionId(), task.functionName());
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
