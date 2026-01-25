package com.mcfaas.runtime.core;

import com.mcfaas.common.model.InvocationResult;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CallbackClient {
    private final RestClient restClient = RestClient.create();

    public void sendResult(String executionId, InvocationResult result) {
        String baseUrl = System.getenv("CALLBACK_URL");
        if (baseUrl == null || baseUrl.isBlank() || executionId == null || executionId.isBlank()) {
            return;
        }
        String traceId = System.getenv("TRACE_ID");
        String url = baseUrl.endsWith(":complete")
                ? baseUrl
                : baseUrl + "/" + executionId + ":complete";
        RestClient.RequestBodySpec request = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (traceId != null && !traceId.isBlank()) {
            request.header("X-Trace-Id", traceId);
        }
        request.body(result)
                .retrieve()
                .toBodilessEntity();
    }
}
