package dev.simplecore.simplix.encryption.provider;

import dev.simplecore.simplix.encryption.config.SimpliXEncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigurableKeyProvider Tests")
class ConfigurableKeyProviderTest {

    private ConfigurableKeyProvider provider;
    private SimpliXEncryptionProperties.Configurable config;

    private String validBase64Key1;
    private String validBase64Key2;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        provider = new ConfigurableKeyProvider();

        // Generate valid AES-256 keys and encode to Base64
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        validBase64Key1 = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        validBase64Key2 = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());

        config = new SimpliXEncryptionProperties.Configurable();
        config.setCurrentVersion("v1");

        Map<String, SimpliXEncryptionProperties.Configurable.KeyConfig> keys = new LinkedHashMap<>();

        SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig1 =
            new SimpliXEncryptionProperties.Configurable.KeyConfig();
        keyConfig1.setKey(validBase64Key1);
        keyConfig1.setDeprecated(false);
        keys.put("v1", keyConfig1);

        SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig2 =
            new SimpliXEncryptionProperties.Configurable.KeyConfig();
        keyConfig2.setKey(validBase64Key2);
        keyConfig2.setDeprecated(true);
        keys.put("v0", keyConfig2);

        config.setKeys(keys);

        provider.setConfig(config);
        ReflectionTestUtils.setField(provider, "rotationEnabled", true);
        ReflectionTestUtils.setField(provider, "rotationDays", 90);
        ReflectionTestUtils.setField(provider, "autoRotation", true);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            ConfigurableKeyProvider defaultProvider = new ConfigurableKeyProvider();
            assertThat(defaultProvider).isNotNull();
        }

        @Test
        @DisplayName("Should create with config constructor")
        void shouldCreateWithConfigConstructor() {
            ConfigurableKeyProvider configProvider = new ConfigurableKeyProvider(config);
            assertThat(configProvider).isNotNull();
        }
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("Should initialize with valid configuration")
        void shouldInitializeWithValidConfig() {
            provider.initialize();

            assertThat(provider.getCurrentVersion()).isEqualTo("v1");
            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should load all configured keys")
        void shouldLoadAllConfiguredKeys() {
            provider.initialize();

            Set<String> versions = provider.getAvailableVersions();

            assertThat(versions).containsExactlyInAnyOrder("v1", "v0");
        }

        @Test
        @DisplayName("Should mark deprecated keys correctly")
        void shouldMarkDeprecatedKeysCorrectly() {
            provider.initialize();

            assertThat(provider.isDeprecated("v0")).isTrue();
            assertThat(provider.isDeprecated("v1")).isFalse();
        }

        @Test
        @DisplayName("Should disable rotation")
        void shouldDisableRotation() {
            provider.initialize();

            boolean rotationEnabled = (boolean)
                ReflectionTestUtils.getField(provider, "rotationEnabled");
            boolean autoRotation = (boolean)
                ReflectionTestUtils.getField(provider, "autoRotation");

            assertThat(rotationEnabled).isFalse();
            assertThat(autoRotation).isFalse();
        }
    }

    @Nested
    @DisplayName("getCurrentKey()")
    class GetCurrentKeyTests {

        @Test
        @DisplayName("Should return the current version key")
        void shouldReturnCurrentVersionKey() {
            provider.initialize();

            SecretKey key = provider.getCurrentKey();

            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("AES");
            assertThat(key.getEncoded()).isEqualTo(Base64.getDecoder().decode(validBase64Key1));
        }

        @Test
        @DisplayName("Should throw when currentVersion is null")
        void shouldThrowWhenCurrentVersionNull() {
            provider.initialize();
            ReflectionTestUtils.setField(provider, "currentVersion", null);

            assertThatThrownBy(() -> provider.getCurrentKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No current key version configured");
        }

        @Test
        @DisplayName("Should throw when current key not found in cache")
        void shouldThrowWhenCurrentKeyNotInCache() {
            provider.initialize();
            ReflectionTestUtils.setField(provider, "currentVersion", "non-existent");

            assertThatThrownBy(() -> provider.getCurrentKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Current key not found");
        }
    }

    @Nested
    @DisplayName("getKey()")
    class GetKeyTests {

        @Test
        @DisplayName("Should return key for valid version")
        void shouldReturnKeyForValidVersion() {
            provider.initialize();

            SecretKey key = provider.getKey("v1");

            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("Should return deprecated key for decryption")
        void shouldReturnDeprecatedKeyForDecryption() {
            provider.initialize();

            SecretKey key = provider.getKey("v0");

            assertThat(key).isNotNull();
            assertThat(key.getEncoded()).isEqualTo(Base64.getDecoder().decode(validBase64Key2));
        }

        @Test
        @DisplayName("Should throw for non-existent version")
        void shouldThrowForNonExistentVersion() {
            provider.initialize();

            assertThatThrownBy(() -> provider.getKey("v999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key version not found");
        }
    }

    @Nested
    @DisplayName("rotateKey()")
    class RotateKeyTests {

        @Test
        @DisplayName("Should return current version (rotation not supported)")
        void shouldReturnCurrentVersion() {
            provider.initialize();

            String result = provider.rotateKey();

            assertThat(result).isEqualTo("v1");
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("Should return true when fully configured")
        void shouldReturnTrueWhenConfigured() {
            provider.initialize();

            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should return false before initialization")
        void shouldReturnFalseBeforeInit() {
            assertThat(provider.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("getName()")
    class GetNameTests {

        @Test
        @DisplayName("Should return 'ConfigurableKeyProvider'")
        void shouldReturnName() {
            assertThat(provider.getName()).isEqualTo("ConfigurableKeyProvider");
        }
    }

    @Nested
    @DisplayName("getKeyStatistics()")
    class GetKeyStatisticsTests {

        @Test
        @DisplayName("Should include configurable-specific statistics")
        void shouldIncludeConfigurableStats() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("totalConfiguredKeys");
            assertThat(stats).containsKey("deprecatedKeys");
            assertThat(stats).containsKey("activeKeys");
            assertThat(stats).containsKey("deprecatedVersions");
            assertThat((int) stats.get("totalConfiguredKeys")).isEqualTo(2);
            assertThat((int) stats.get("deprecatedKeys")).isEqualTo(1);
            assertThat((int) stats.get("activeKeys")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("validateConfiguration()")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("Should throw when config is null")
        void shouldThrowWhenConfigNull() {
            ConfigurableKeyProvider emptyProvider = new ConfigurableKeyProvider();
            ReflectionTestUtils.setField(emptyProvider, "rotationEnabled", true);
            ReflectionTestUtils.setField(emptyProvider, "rotationDays", 90);
            ReflectionTestUtils.setField(emptyProvider, "autoRotation", true);

            assertThatThrownBy(() -> emptyProvider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuration is not set");
        }

        @Test
        @DisplayName("Should throw when currentVersion is null")
        void shouldThrowWhenCurrentVersionNull() {
            config.setCurrentVersion(null);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-version must be specified");
        }

        @Test
        @DisplayName("Should throw when currentVersion is blank")
        void shouldThrowWhenCurrentVersionBlank() {
            config.setCurrentVersion("  ");

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-version must be specified");
        }

        @Test
        @DisplayName("Should throw when keys map is null")
        void shouldThrowWhenKeysNull() {
            config.setKeys(null);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one key must be configured");
        }

        @Test
        @DisplayName("Should throw when keys map is empty")
        void shouldThrowWhenKeysEmpty() {
            config.setKeys(new LinkedHashMap<>());

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one key must be configured");
        }

        @Test
        @DisplayName("Should throw when currentVersion not in keys map")
        void shouldThrowWhenCurrentVersionNotInKeys() {
            config.setCurrentVersion("v99");

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist in keys");
        }

        @Test
        @DisplayName("Should throw when currentVersion is deprecated")
        void shouldThrowWhenCurrentVersionDeprecated() {
            config.setCurrentVersion("v0");

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be deprecated");
        }

        @Test
        @DisplayName("Should throw when key value is null")
        void shouldThrowWhenKeyValueNull() {
            SimpliXEncryptionProperties.Configurable.KeyConfig badKeyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            badKeyConfig.setKey(null);
            config.getKeys().put("v1", badKeyConfig);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is empty");
        }

        @Test
        @DisplayName("Should throw when key value is blank")
        void shouldThrowWhenKeyValueBlank() {
            SimpliXEncryptionProperties.Configurable.KeyConfig badKeyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            badKeyConfig.setKey("  ");
            config.getKeys().put("v1", badKeyConfig);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is empty");
        }

        @Test
        @DisplayName("Should throw when key is not valid Base64")
        void shouldThrowWhenKeyNotValidBase64() {
            SimpliXEncryptionProperties.Configurable.KeyConfig badKeyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            badKeyConfig.setKey("not-valid-base64!!@@##");
            config.getKeys().put("v1", badKeyConfig);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
        }

        @Test
        @DisplayName("Should throw when key is wrong size")
        void shouldThrowWhenKeyWrongSize() {
            // 16 bytes (AES-128) instead of 32 bytes (AES-256)
            byte[] shortKey = new byte[16];
            SimpliXEncryptionProperties.Configurable.KeyConfig badKeyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            badKeyConfig.setKey(Base64.getEncoder().encodeToString(shortKey));
            config.getKeys().put("v1", badKeyConfig);

            assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be 32 bytes");
        }

        @Test
        @DisplayName("Should return true for valid configuration")
        void shouldReturnTrueForValidConfig() {
            assertThat(provider.validateConfiguration()).isTrue();
        }
    }

    @Nested
    @DisplayName("isDeprecated()")
    class IsDeprecatedTests {

        @Test
        @DisplayName("Should return true for deprecated version")
        void shouldReturnTrueForDeprecated() {
            provider.initialize();

            assertThat(provider.isDeprecated("v0")).isTrue();
        }

        @Test
        @DisplayName("Should return false for active version")
        void shouldReturnFalseForActive() {
            provider.initialize();

            assertThat(provider.isDeprecated("v1")).isFalse();
        }

        @Test
        @DisplayName("Should return false for non-existent version")
        void shouldReturnFalseForNonExistent() {
            provider.initialize();

            assertThat(provider.isDeprecated("v999")).isFalse();
        }
    }

    @Nested
    @DisplayName("getAvailableVersions()")
    class GetAvailableVersionsTests {

        @Test
        @DisplayName("Should return all loaded versions")
        void shouldReturnAllVersions() {
            provider.initialize();

            Set<String> versions = provider.getAvailableVersions();

            assertThat(versions).containsExactlyInAnyOrder("v1", "v0");
        }

        @Test
        @DisplayName("Should be empty before initialization")
        void shouldBeEmptyBeforeInit() {
            Set<String> versions = provider.getAvailableVersions();

            assertThat(versions).isEmpty();
        }
    }
}
