package dev.simplecore.simplix.encryption.config;

import dev.simplecore.simplix.encryption.provider.KeyProvider;
import dev.simplecore.simplix.encryption.provider.ManagedKeyProvider;
import dev.simplecore.simplix.encryption.provider.SimpleKeyProvider;
import dev.simplecore.simplix.encryption.provider.VaultKeyProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.vault.core.VaultTemplate;

/**
 * Configuration for KeyProvider beans.
 * Ensures only one KeyProvider is active at a time based on profiles and properties.
 * <p>
 * Priority order:
 * 1. VaultKeyProvider (prod, staging profiles)
 * 2. ManagedKeyProvider (file-based profile)
 * 3. SimpleKeyProvider (default fallback for dev)
 */
@Configuration
@Slf4j
public class KeyProviderConfiguration {

    private final Environment environment;

    public KeyProviderConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void logKeyProviderConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }

        log.info("✔ KeyProvider configuration initialized");
        log.info("  Active profiles: {}", String.join(", ", activeProfiles));

        // Determine which provider will be active
        String expectedProvider = "SimpleKeyProvider (default)";
        for (String profile : activeProfiles) {
            if ("prod".equals(profile) || "staging".equals(profile) || "vault".equals(profile)) {
                expectedProvider = "VaultKeyProvider";
                break;
            } else if ("file-based".equals(profile) || "managed".equals(profile)) {
                expectedProvider = "ManagedKeyProvider";
                break;
            }
        }

        log.info("  Expected KeyProvider: {}", expectedProvider);
    }

    /**
     * Ensures that only the VaultKeyProvider bean is created when appropriate profiles are active.
     * This prevents multiple KeyProvider beans from being registered.
     */
    @Configuration
    @Profile({"prod", "staging", "vault"})
    public static class VaultKeyProviderConfig {

        @Bean
        @Primary
        @ConditionalOnProperty(
            prefix = "simplix.encryption",
            name = "provider",
            havingValue = "vault",
            matchIfMissing = true  // Default for prod/staging
        )
        public KeyProvider vaultKeyProvider(
                @Autowired(required = false) VaultTemplate vaultTemplate,
                @Value("${simplix.encryption.vault.enabled:true}") boolean vaultEnabled) {
            log.info("✔ Creating VaultKeyProvider bean (prod/staging/vault profile active)");
            VaultKeyProvider provider = new VaultKeyProvider();
            provider.setVaultTemplate(vaultTemplate);
            provider.setVaultEnabled(vaultEnabled);
            return provider;
        }
    }

    /**
     * ManagedKeyProvider configuration for file-based environments.
     * Only created if VaultKeyProvider is not active.
     */
    @Configuration
    @Profile({"file-based", "managed", "staging"})
    public static class ManagedKeyProviderConfig {

        @Bean
        @Primary
        @ConditionalOnMissingBean(KeyProvider.class)
        @ConditionalOnProperty(
            prefix = "simplix.encryption",
            name = "provider",
            havingValue = "managed",
            matchIfMissing = false
        )
        public KeyProvider managedKeyProvider(
                @Autowired(required = false) LockProvider lockProvider,
                @Value("${simplix.encryption.key-store-path:#{null}}") String keyStorePath,
                @Value("${simplix.encryption.master-key:#{null}}") String masterKey,
                @Value("${simplix.encryption.salt:#{null}}") String salt) {
            log.info("✔ Creating ManagedKeyProvider bean (file-based/managed profile active)");
            ManagedKeyProvider provider = new ManagedKeyProvider();
            provider.setLockProvider(lockProvider);
            provider.setKeyStorePath(keyStorePath);
            provider.setMasterKey(masterKey);
            provider.setSalt(salt);
            return provider;
        }
    }

    /**
     * SimpleKeyProvider as fallback for development environments.
     * Only created if no other KeyProvider is active.
     */
    @Configuration
    @Profile({"dev", "test", "local", "default"})
    public static class SimpleKeyProviderConfig {

        @Bean
        @Primary
        @ConditionalOnMissingBean(KeyProvider.class)
        public KeyProvider simpleKeyProvider() {
            log.info("✔ Creating SimpleKeyProvider bean (dev/test/default profile active)");
            return new SimpleKeyProvider();
        }
    }
}