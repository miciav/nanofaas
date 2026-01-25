package com.mcfaas.runtime.api;

import com.mcfaas.common.model.InvocationRequest;
import com.mcfaas.common.model.InvocationResult;
import com.mcfaas.common.runtime.FunctionHandler;
import com.mcfaas.runtime.core.CallbackClient;
import com.mcfaas.runtime.core.HandlerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class InvokeController {
    private final CallbackClient callbackClient;
    private final HandlerRegistry handlerRegistry;

    public InvokeController(CallbackClient callbackClient, HandlerRegistry handlerRegistry) {
        this.callbackClient = callbackClient;
        this.handlerRegistry = handlerRegistry;
    }

    @PostMapping("/invoke")
    public ResponseEntity<Object> invoke(@RequestBody InvocationRequest request) {
        String executionId = System.getenv("EXECUTION_ID");
        try {
            FunctionHandler handler = handlerRegistry.resolve();
            Object output = handler.handle(request);
            callbackClient.sendResult(executionId, InvocationResult.success(output));
            return ResponseEntity.ok(output);
        } catch (Exception ex) {
            callbackClient.sendResult(executionId, InvocationResult.error("HANDLER_ERROR", ex.getMessage()));
            return ResponseEntity.status(500).body(Map.of("error", "HANDLER_ERROR"));
        }
    }
}
