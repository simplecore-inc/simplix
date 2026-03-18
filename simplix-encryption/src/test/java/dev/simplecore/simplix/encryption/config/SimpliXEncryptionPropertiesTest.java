package dev.simplecore.simplix.encryption.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXEncryptionProperties Tests")
class SimpliXEncryptionPropertiesTest {

    private SimpliXEncryptionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SimpliXEncryptionProperties();
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have enabled set to true by default")
        void shouldHaveEnabledTrue() {
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have provider set to 'simple' by default")
        void shouldHaveProviderSimple() {
            assertThat(properties.getProvider()).isEqualTo("simple");
        }

        @Test
        @DisplayName("Should have default static key")
        void shouldHaveDefaultStaticKey() {
            assertThat(properties.getStaticKey())
                .isEqualTo("dev-default-key-do-not-use-in-production");
        }

        @Test
        @DisplayName("Should have null master key by default")
        void shouldHaveNullMasterKey() {
            assertThat(properties.getMasterKey()).isNull();
        }

        @Test
        @DisplayName("Should have null salt by default")
        void shouldHaveNullSalt() {
            assertThat(properties.getSalt()).isNull();
        }

        @Test
        @DisplayName("Should have null key store path by default")
        void shouldHaveNullKeyStorePath() {
            assertThat(properties.getKeyStorePath()).isNull();
        }

        @Test
        @DisplayName("Should have auto rotation disabled by default")
        void shouldHaveAutoRotationDisabled() {
            assertThat(properties.isAutoRotation()).isFalse();
        }
    }

    @Nested
    @DisplayName("Simple nested config")
    class SimpleConfigTests {

        @Test
        @DisplayName("Should have allow rotation disabled by default")
        void shouldHaveAllowRotationDisabled() {
            assertThat(properties.getSimple()).isNotNull();
            assertThat(properties.getSimple().isAllowRotation()).isFalse();
        }

        @Test
        @DisplayName("Should allow setting allow rotation")
        void shouldAllowSettingAllowRotation() {
            properties.getSimple().setAllowRotation(true);

            assertThat(properties.getSimple().isAllowRotation()).isTrue();
        }
    }

    @Nested
    @DisplayName("Vault nested config")
    class VaultConfigTests {

        @Test
        @DisplayName("Should have vault disabled by default")
        void shouldHaveVaultDisabled() {
            assertThat(properties.getVault()).isNotNull();
            assertThat(properties.getVault().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have default vault path")
        void shouldHaveDefaultVaultPath() {
            assertThat(properties.getVault().getPath()).isEqualTo("secret/encryption");
        }

        @Test
        @DisplayName("Should have null namespace by default")
        void shouldHaveNullNamespace() {
            assertThat(properties.getVault().getNamespace()).isNull();
        }

        @Test
        @DisplayName("Should allow setting vault properties")
        void shouldAllowSettingVaultProperties() {
            properties.getVault().setEnabled(true);
            properties.getVault().setPath("custom/path");
            properties.getVault().setNamespace("my-ns");

            assertThat(properties.getVault().isEnabled()).isTrue();
            assertThat(properties.getVault().getPath()).isEqualTo("custom/path");
            assertThat(properties.getVault().getNamespace()).isEqualTo("my-ns");
        }
    }

    @Nested
    @DisplayName("Rotation nested config")
    class RotationConfigTests {

        @Test
        @DisplayName("Should have rotation disabled by default")
        void shouldHaveRotationDisabled() {
            assertThat(properties.getRotation()).isNotNull();
            assertThat(properties.getRotation().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should have default rotation days of 90")
        void shouldHaveDefaultRotationDays() {
            assertThat(properties.getRotation().getDays()).isEqualTo(90);
        }

        @Test
        @DisplayName("Should allow setting rotation properties")
        void shouldAllowSettingRotationProperties() {
            properties.getRotation().setEnabled(true);
            properties.getRotation().setDays(30);

            assertThat(properties.getRotation().isEnabled()).isTrue();
            assertThat(properties.getRotation().getDays()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Configurable nested config")
    class ConfigurableConfigTests {

        @Test
        @DisplayName("Should have null current version by default")
        void shouldHaveNullCurrentVersion() {
            assertThat(properties.getConfigurable()).isNotNull();
            assertThat(properties.getConfigurable().getCurrentVersion()).isNull();
        }

        @Test
        @DisplayName("Should have empty keys map by default")
        void shouldHaveEmptyKeysMap() {
            assertThat(properties.getConfigurable().getKeys()).isNotNull();
            assertThat(properties.getConfigurable().getKeys()).isEmpty();
        }

        @Test
        @DisplayName("Should allow setting configurable properties")
        void shouldAllowSettingConfigurableProperties() {
            properties.getConfigurable().setCurrentVersion("v1");

            Map<String, SimpliXEncryptionProperties.Configurable.KeyConfig> keys = new LinkedHashMap<>();
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            keyConfig.setKey("base64key");
            keyConfig.setDeprecated(false);
            keys.put("v1", keyConfig);
            properties.getConfigurable().setKeys(keys);

            assertThat(properties.getConfigurable().getCurrentVersion()).isEqualTo("v1");
            assertThat(properties.getConfigurable().getKeys()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Configurable.KeyConfig")
    class KeyConfigTests {

        @Test
        @DisplayName("Should have null key by default")
        void shouldHaveNullKey() {
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();

            assertThat(keyConfig.getKey()).isNull();
        }

        @Test
        @DisplayName("Should have deprecated false by default")
        void shouldHaveDeprecatedFalse() {
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();

            assertThat(keyConfig.isDeprecated()).isFalse();
        }

        @Test
        @DisplayName("Should allow setting key config properties")
        void shouldAllowSettingKeyConfig() {
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig =
                new SimpliXEncryptionProperties.Configurable.KeyConfig();
            keyConfig.setKey("test-key");
            keyConfig.setDeprecated(true);

            assertThat(keyConfig.getKey()).isEqualTo("test-key");
            assertThat(keyConfig.isDeprecated()).isTrue();
        }
    }

    @Nested
    @DisplayName("Setter/Getter")
    class SetterGetterTests {

        @Test
        @DisplayName("Should set and get all top-level properties")
        void shouldSetAndGetAllProperties() {
            properties.setEnabled(false);
            properties.setProvider("vault");
            properties.setStaticKey("my-key");
            properties.setMasterKey("master");
            properties.setSalt("salt-value");
            properties.setKeyStorePath("/path/to/store");
            properties.setAutoRotation(true);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getProvider()).isEqualTo("vault");
            assertThat(properties.getStaticKey()).isEqualTo("my-key");
            assertThat(properties.getMasterKey()).isEqualTo("master");
            assertThat(properties.getSalt()).isEqualTo("salt-value");
            assertThat(properties.getKeyStorePath()).isEqualTo("/path/to/store");
            assertThat(properties.isAutoRotation()).isTrue();
        }
    }
}
