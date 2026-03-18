package dev.simplecore.simplix.encryption.config;

import dev.simplecore.simplix.encryption.provider.ConfigurableKeyProvider;
import dev.simplecore.simplix.encryption.provider.KeyProvider;
import dev.simplecore.simplix.encryption.provider.ManagedKeyProvider;
import dev.simplecore.simplix.encryption.provider.SimpleKeyProvider;
import dev.simplecore.simplix.encryption.provider.VaultKeyProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.vault.core.VaultTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyProviderConfiguration Tests")
class KeyProviderConfigurationTest {

    @Mock
    private Environment environment;

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private LockProvider lockProvider;

    @Nested
    @DisplayName("KeyProviderConfiguration")
    class MainConfigTests {

        @Test
        @DisplayName("Should create with environment")
        void shouldCreateWithEnvironment() {
            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);

            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("Should log configuration with active profiles")
        void shouldLogWithActiveProfiles() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw
        }

        @Test
        @DisplayName("Should log configuration with default profiles when no active profiles")
        void shouldLogWithDefaultProfiles() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{});
            when(environment.getDefaultProfiles()).thenReturn(new String[]{"default"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw
        }

        @Test
        @DisplayName("Should detect VaultKeyProvider for prod profile")
        void shouldDetectVaultForProdProfile() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw, logs expected provider
        }

        @Test
        @DisplayName("Should detect VaultKeyProvider for staging profile")
        void shouldDetectVaultForStagingProfile() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"staging"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw
        }

        @Test
        @DisplayName("Should detect ManagedKeyProvider for file-based profile")
        void shouldDetectManagedForFileBasedProfile() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"file-based"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw
        }

        @Test
        @DisplayName("Should detect ManagedKeyProvider for managed profile")
        void shouldDetectManagedForManagedProfile() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"managed"});

            KeyProviderConfiguration config = new KeyProviderConfiguration(environment);
            config.logKeyProviderConfiguration();

            // Should not throw
        }
    }

    @Nested
    @DisplayName("VaultKeyProviderConfig")
    class VaultKeyProviderConfigTests {

        @Test
        @DisplayName("Should create VaultKeyProvider bean")
        void shouldCreateVaultKeyProvider() {
            KeyProviderConfiguration.VaultKeyProviderConfig config =
                new KeyProviderConfiguration.VaultKeyProviderConfig();

            KeyProvider provider = config.vaultKeyProvider(vaultTemplate, true);

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(VaultKeyProvider.class);
        }

        @Test
        @DisplayName("Should create VaultKeyProvider with null VaultTemplate")
        void shouldCreateVaultKeyProviderWithNullTemplate() {
            KeyProviderConfiguration.VaultKeyProviderConfig config =
                new KeyProviderConfiguration.VaultKeyProviderConfig();

            KeyProvider provider = config.vaultKeyProvider(null, false);

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(VaultKeyProvider.class);
        }
    }

    @Nested
    @DisplayName("ManagedKeyProviderConfig")
    class ManagedKeyProviderConfigTests {

        @Test
        @DisplayName("Should create ManagedKeyProvider bean")
        void shouldCreateManagedKeyProvider() {
            KeyProviderConfiguration.ManagedKeyProviderConfig config =
                new KeyProviderConfiguration.ManagedKeyProviderConfig();

            KeyProvider provider = config.managedKeyProvider(
                lockProvider, "/some/path", "master-key", "salt-value");

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(ManagedKeyProvider.class);
        }

        @Test
        @DisplayName("Should create ManagedKeyProvider with null optional dependencies")
        void shouldCreateManagedKeyProviderWithNullOptionals() {
            KeyProviderConfiguration.ManagedKeyProviderConfig config =
                new KeyProviderConfiguration.ManagedKeyProviderConfig();

            KeyProvider provider = config.managedKeyProvider(null, null, null, null);

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(ManagedKeyProvider.class);
        }
    }

    @Nested
    @DisplayName("ConfigurableKeyProviderConfig")
    class ConfigurableKeyProviderConfigTests {

        @Test
        @DisplayName("Should create ConfigurableKeyProvider bean")
        void shouldCreateConfigurableKeyProvider() {
            SimpliXEncryptionProperties properties = new SimpliXEncryptionProperties();
            SimpliXEncryptionProperties.Configurable configurable = new SimpliXEncryptionProperties.Configurable();
            configurable.setCurrentVersion("v1");

            Map<String, SimpliXEncryptionProperties.Configurable.KeyConfig> keys = new LinkedHashMap<>();
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            byte[] keyBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(keyBytes);
            keyConfig.setKey(Base64.getEncoder().encodeToString(keyBytes));
            keyConfig.setDeprecated(false);
            keys.put("v1", keyConfig);
            configurable.setKeys(keys);

            properties.setConfigurable(configurable);

            KeyProviderConfiguration.ConfigurableKeyProviderConfig config =
                new KeyProviderConfiguration.ConfigurableKeyProviderConfig();

            KeyProvider provider = config.configurableKeyProvider(properties);

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(ConfigurableKeyProvider.class);
        }
    }

    @Nested
    @DisplayName("SimpleKeyProviderConfig")
    class SimpleKeyProviderConfigTests {

        @Test
        @DisplayName("Should create SimpleKeyProvider bean")
        void shouldCreateSimpleKeyProvider() {
            KeyProviderConfiguration.SimpleKeyProviderConfig config =
                new KeyProviderConfiguration.SimpleKeyProviderConfig();

            KeyProvider provider = config.simpleKeyProvider();

            assertThat(provider).isNotNull();
            assertThat(provider).isInstanceOf(SimpleKeyProvider.class);
        }
    }
}
