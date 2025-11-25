package dev.simplecore.simplix.encryption.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the SimpliX Encryption module.
 * <p>
 * This configuration is automatically loaded by Spring Boot when the encryption module
 * is on the classpath. It sets up encryption infrastructure components including:
 * - Key providers (Simple, Managed, Vault)
 * - Encryption service
 * - Key rotation management
 */
@AutoConfiguration
@ComponentScan(basePackages = "dev.simplecore.simplix.encryption")
@EnableConfigurationProperties(SimpliXEncryptionProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SimpliXEncryptionAutoConfiguration {

    private final SimpliXEncryptionProperties encryptionProperties;

    /**
     * Initialize encryption module
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("✔ SimpliX Encryption module initialized");
        log.debug("Encryption configuration: enabled={}, provider={}",
                encryptionProperties.isEnabled(),
                encryptionProperties.getProvider());
    }
}
