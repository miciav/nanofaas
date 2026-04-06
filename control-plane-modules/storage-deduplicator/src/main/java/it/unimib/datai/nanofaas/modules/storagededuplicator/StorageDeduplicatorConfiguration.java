package it.unimib.datai.nanofaas.modules.storagededuplicator;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(StorageDeduplicatorProperties.class)
@Import({FileDeduplicator.class, DeduplicationListener.class})
public class StorageDeduplicatorConfiguration {
}
