package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.FunctionSpec;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QueueManager {
    private final Map<String, FunctionQueueState> queues = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public QueueManager(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public FunctionQueueState getOrCreate(FunctionSpec spec) {
        return queues.compute(spec.name(), (name, existing) -> {
            if (existing == null) {
                FunctionQueueState state = new FunctionQueueState(
                        name,
                        spec.queueSize(),
                        spec.concurrency()
                );
                Gauge.builder("function_queue_depth", state::queued)
                        .tag("function", name)
                        .register(meterRegistry);
                return state;
            }
            existing.concurrency(spec.concurrency());
            return existing;
        });
    }

    public void remove(String name) {
        queues.remove(name);
    }

    public Collection<FunctionQueueState> states() {
        return queues.values();
    }

    public boolean enqueue(InvocationTask task) {
        FunctionQueueState state = queues.get(task.functionName());
        if (state == null) {
            return false;
        }
        return state.offer(task);
    }

    public void incrementInFlight(String functionName) {
        FunctionQueueState state = queues.get(functionName);
        if (state != null) {
            state.incrementInFlight();
        }
    }

    public void decrementInFlight(String functionName) {
        FunctionQueueState state = queues.get(functionName);
        if (state != null) {
            state.decrementInFlight();
        }
    }
}
