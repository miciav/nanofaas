package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.FunctionSpec;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class FunctionService {
    private final FunctionRegistry registry;
    private final QueueManager queueManager;
    private final FunctionSpecResolver resolver;

    public FunctionService(FunctionRegistry registry, QueueManager queueManager, FunctionDefaults defaults) {
        this.registry = registry;
        this.queueManager = queueManager;
        this.resolver = new FunctionSpecResolver(defaults);
    }

    public Collection<FunctionSpec> list() {
        return registry.list();
    }

    public Optional<FunctionSpec> get(String name) {
        return registry.get(name);
    }

    public Optional<FunctionSpec> register(FunctionSpec spec) {
        if (registry.get(spec.name()).isPresent()) {
            return Optional.empty();
        }
        FunctionSpec resolved = resolver.resolve(spec);
        registry.put(resolved);
        queueManager.getOrCreate(resolved);
        return Optional.of(resolved);
    }

    public Optional<FunctionSpec> remove(String name) {
        FunctionSpec removed = registry.remove(name);
        if (removed != null) {
            queueManager.remove(name);
            return Optional.of(removed);
        }
        return Optional.empty();
    }
}
