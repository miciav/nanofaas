package it.unimib.datai.nanofaas.modules.storagededuplicator;

import it.unimib.datai.nanofaas.common.model.FunctionSpec;
import it.unimib.datai.nanofaas.controlplane.registry.FunctionRegistrationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DeduplicationListener implements FunctionRegistrationListener {
    private static final Logger log = LoggerFactory.getLogger(DeduplicationListener.class);

    private final StorageDeduplicatorProperties properties;
    private final FileDeduplicator deduplicator;

    public DeduplicationListener(StorageDeduplicatorProperties properties, FileDeduplicator deduplicator) {
        this.properties = properties;
        this.deduplicator = deduplicator;
    }

    @Override
    public void onRegister(FunctionSpec spec) {
        if (!properties.enabled() || !properties.autoDeduplicateOnRegistration()) {
            return;
        }

        log.info("Triggering storage deduplication for function: {}", spec.name());
        
        // In a real implementation, we would determine the local path where the function's 
        // artifact is stored. For this demonstration, we assume it's under base-path/functions/name
        Path functionPath = Paths.get(properties.basePath(), "functions", spec.name());
        Path commonPath = Paths.get(properties.basePath(), "common");

        try {
            FileDeduplicator.DeduplicationResult result = deduplicator.deduplicate(functionPath, commonPath);
            log.info("Deduplication completed for {}: processed {} files, saved {} bytes", 
                    spec.name(), result.filesCount(), result.bytesSaved());
        } catch (IOException e) {
            log.error("Failed to deduplicate storage for function {}: {}", spec.name(), e.getMessage());
        }
    }

    @Override
    public void onRemove(String functionName) {
        // Optional: cleanup common storage if no other function uses the files.
        // For simplicity, we keep common files as they might be reused by new functions.
        log.debug("Function {} removed, common storage remains unchanged.", functionName);
    }
}
