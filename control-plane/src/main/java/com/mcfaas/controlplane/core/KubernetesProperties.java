package com.mcfaas.controlplane.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcfaas.k8s")
public record KubernetesProperties(
        String namespace,
        String callbackUrl
) {
}
