package dev.simplecore.simplix.file.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    // Configuration properties are automatically bound
}
