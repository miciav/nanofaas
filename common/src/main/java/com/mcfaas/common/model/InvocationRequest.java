package com.mcfaas.common.model;

import java.util.Map;

public record InvocationRequest(
        Object input,
        Map<String, String> metadata
) {
}
