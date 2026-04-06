package it.unimib.datai.nanofaas.modules.storagededuplicator;

import it.unimib.datai.nanofaas.common.controlplane.ControlPlaneModule;
import java.util.Set;

public class StorageDeduplicatorModule implements ControlPlaneModule {
    @Override
    public Set<Class<?>> configurationClasses() {
        return Set.of(StorageDeduplicatorConfiguration.class);
    }
}
