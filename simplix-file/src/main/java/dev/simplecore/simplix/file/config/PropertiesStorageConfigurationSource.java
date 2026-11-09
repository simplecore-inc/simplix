package dev.simplecore.simplix.file.config;

import dev.simplecore.simplix.file.infrastructure.storage.StorageConfigurationSource;

/**
 * Default {@link StorageConfigurationSource}: the settings bound from
 * {@code simplix.file.storage.s3.*}, with a revision that never changes.
 */
public class PropertiesStorageConfigurationSource implements StorageConfigurationSource {

    private final StorageProperties storageProperties;

    public PropertiesStorageConfigurationSource(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public StorageProperties.S3StorageConfig s3() {
        return storageProperties.getS3();
    }

    @Override
    public long revision() {
        return 0L;
    }
}
