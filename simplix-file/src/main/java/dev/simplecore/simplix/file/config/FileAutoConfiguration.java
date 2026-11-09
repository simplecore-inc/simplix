package dev.simplecore.simplix.file.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import dev.simplecore.simplix.file.infrastructure.storage.StorageConfigurationSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the SimpliX file module.
 * <p>
 * Enables component scanning for infrastructure services and configuration properties.
 * This module provides pure file handling infrastructure without entity dependencies.
 * <p>
 * The module includes:
 * <ul>
 *   <li>File storage service (local filesystem or cloud storage)</li>
 *   <li>Image processing service (resize, thumbnail generation)</li>
 *   <li>File validation utilities</li>
 *   <li>FileCategory enum for file type classification</li>
 * </ul>
 * <p>
 * The S3 settings are read through a {@link StorageConfigurationSource}. The default bean reads
 * {@code simplix.file.storage.s3.*}; an application that keeps the settings elsewhere (a database
 * table an operator edits) declares its own bean of that type and the default backs off.
 * <p>
 * Entity and repository implementations should be in the domain module,
 * while services that use repositories should be in the application layer.
 * <p>
 * Configuration in application.yml:
 * <pre>{@code
 * simplix:
 *   file:
 *     enabled: true  # default: true
 *     default-max-size: 10MB
 *     storage:
 *       provider: local
 *       local:
 *         base-path: ./uploads
 *     image:
 *       default-max-width: 2048
 *       default-max-height: 2048
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
    "dev.simplecore.simplix.file.config",
    "dev.simplecore.simplix.file.infrastructure"
})
@EnableConfigurationProperties({
    StorageProperties.class,
    FileProperties.class,
    ImageProperties.class
})
public class FileAutoConfiguration {

    /**
     * Default settings source: the bound properties, with a revision that never changes.
     * Declared here rather than as a scanned component so that {@code @ConditionalOnMissingBean}
     * sees the application's own bean.
     */
    @Bean
    @ConditionalOnMissingBean(StorageConfigurationSource.class)
    public StorageConfigurationSource storageConfigurationSource(StorageProperties storageProperties) {
        return new PropertiesStorageConfigurationSource(storageProperties);
    }
}
