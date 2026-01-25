package com.mcfaas.controlplane.core;

import com.mcfaas.common.model.FunctionSpec;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FunctionRegistry {
    private final Map<String, FunctionSpec> functions = new ConcurrentHashMap<>();

    public Collection<FunctionSpec> list() {
        return functions.values();
    }

    public Optional<FunctionSpec> get(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    public FunctionSpec put(FunctionSpec spec) {
        return functions.put(spec.name(), spec);
    }

    public FunctionSpec remove(String name) {
        return functions.remove(name);
    }
}
