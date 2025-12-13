package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.jwe.provider.DatabaseJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.provider.JweKeyProvider;
import dev.simplecore.simplix.auth.jwe.provider.StaticJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.service.JweKeyRotationService;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.encryption.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for JWE key management.
 *
 * <p>This configuration provides two modes of operation:</p>
 *
 * <h2>1. Database-backed Key Rolling (Production)</h2>
 * <p>Enabled when:</p>
 * <ul>
 *   <li>{@code simplix.auth.jwe.key-rolling.enabled=true}</li>
 *   <li>{@link JweKeyStore} bean is provided by the application</li>
 *   <li>{@link EncryptionService} is available (from simplix-encryption)</li>
 * </ul>
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>{@link DatabaseJweKeyProvider} - Multi-key support with DB storage</li>
 *   <li>{@link JweKeyRotationService} - Key rotation helper</li>
 * </ul>
 *
 * <h2>2. Static Key (Development/Legacy)</h2>
 * <p>Enabled when key-rolling is disabled (default) or no JweKeyStore is available.</p>
 * <p>Provides {@link StaticJweKeyProvider} for single-key operation.</p>
 *
 * @see JweKeyProvider
 * @see JweKeyStore
 */
@AutoConfiguration
@EnableConfigurationProperties(SimpliXAuthProperties.class)
@Slf4j
public class SimpliXJweKeyAutoConfiguration {

    /**
     * Database-backed JWE key provider.
     * Activated when key-rolling is enabled and required beans are available.
     *
     * @param keyStore          Application-provided key storage implementation
     * @param encryptionService Encryption service for key material encryption
     * @return Initialized DatabaseJweKeyProvider
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "simplix.auth.jwe.key-rolling",
        name = "enabled",
        havingValue = "true"
    )
    @ConditionalOnBean({JweKeyStore.class, EncryptionService.class})
    public DatabaseJweKeyProvider databaseJweKeyProvider(
            JweKeyStore keyStore,
            EncryptionService encryptionService) {

        log.info("Configuring DatabaseJweKeyProvider with key-rolling enabled");

        DatabaseJweKeyProvider provider = new DatabaseJweKeyProvider(keyStore, encryptionService);
        provider.initialize();

        return provider;
    }

    /**
     * JWE key rotation service.
     * Only available when key-rolling is enabled.
     *
     * @param keyStore          Application-provided key storage implementation
     * @param encryptionService Encryption service for key material encryption
     * @param keyProvider       Database key provider to refresh after rotation
     * @param properties        Auth properties for configuration
     * @return Configured JweKeyRotationService
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "simplix.auth.jwe.key-rolling",
        name = "enabled",
        havingValue = "true"
    )
    @ConditionalOnBean(DatabaseJweKeyProvider.class)
    public JweKeyRotationService jweKeyRotationService(
            JweKeyStore keyStore,
            EncryptionService encryptionService,
            DatabaseJweKeyProvider keyProvider,
            SimpliXAuthProperties properties) {

        var keyRolling = properties.getJwe().getKeyRolling();
        var retention = keyRolling.getRetention();

        int keySize = keyRolling.getKeySize();
        int maxTokenLifetime = properties.getToken().getRefreshTokenLifetime();
        int bufferSeconds = retention.getBufferSeconds();
        boolean autoCleanup = retention.isAutoCleanup();

        JweKeyRotationService service = new JweKeyRotationService(
            keyStore, encryptionService, keyProvider,
            keySize, maxTokenLifetime, bufferSeconds, autoCleanup);

        // Auto-initialize if configured and no keys exist
        if (keyRolling.isAutoInitialize()) {
            service.initializeIfEmpty();
        }

        log.info("JweKeyRotationService configured - keySize: {} bits, retention: {} + {} seconds, autoCleanup: {}",
            keySize, maxTokenLifetime, bufferSeconds, autoCleanup);
        return service;
    }

    /**
     * Static JWE key provider (legacy/fallback).
     * Used when key-rolling is disabled or no JweKeyStore is available.
     *
     * <p>This provider is not immediately initialized here.
     * Initialization happens in SimpliXJweTokenProvider.init() which loads
     * the key from properties (encryptionKey or encryptionKeyLocation).</p>
     *
     * @return Uninitialized StaticJweKeyProvider
     */
    @Bean
    @ConditionalOnMissingBean(JweKeyProvider.class)
    public StaticJweKeyProvider staticJweKeyProvider() {
        log.info("Configuring StaticJweKeyProvider (key-rolling disabled or JweKeyStore not available)");
        return new StaticJweKeyProvider();
    }
}
