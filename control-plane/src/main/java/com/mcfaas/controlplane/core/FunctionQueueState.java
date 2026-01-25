package com.mcfaas.controlplane.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FunctionQueueState {
    private final String functionName;
    private final ArrayBlockingQueue<InvocationTask> queue;
    private final AtomicInteger inFlight;
    private volatile int concurrency;

    public FunctionQueueState(String functionName, int queueSize, int concurrency) {
        this.functionName = functionName;
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.inFlight = new AtomicInteger();
        this.concurrency = concurrency;
    }

    public String functionName() {
        return functionName;
    }

    public int queueSize() {
        return queue.remainingCapacity() + queue.size();
    }

    public int queued() {
        return queue.size();
    }

    public boolean offer(InvocationTask task) {
        return queue.offer(task);
    }

    public InvocationTask poll() {
        return queue.poll();
    }

    public int inFlight() {
        return inFlight.get();
    }

    public boolean canDispatch() {
        return inFlight.get() < concurrency;
    }

    public void incrementInFlight() {
        inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlight.decrementAndGet();
    }

    public void concurrency(int concurrency) {
        this.concurrency = concurrency;
    }
}
