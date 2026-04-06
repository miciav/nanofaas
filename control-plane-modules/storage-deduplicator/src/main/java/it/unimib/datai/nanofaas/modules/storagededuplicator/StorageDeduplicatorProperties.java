package it.unimib.datai.nanofaas.modules.storagededuplicator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nanofaas.storage.deduplication")
public record StorageDeduplicatorProperties(
    boolean enabled,
    String basePath,
    boolean autoDeduplicateOnRegistration
) {
    public StorageDeduplicatorProperties {
        if (basePath == null || basePath.isBlank()) {
            basePath = "/var/lib/nanofaas/storage";
        }
    }
}
