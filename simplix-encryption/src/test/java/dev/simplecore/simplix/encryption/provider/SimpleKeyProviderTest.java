package dev.simplecore.simplix.encryption.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpleKeyProvider Tests")
class SimpleKeyProviderTest {

    private SimpleKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SimpleKeyProvider();
        ReflectionTestUtils.setField(provider, "staticKey", "test-key-for-unit-testing");
        ReflectionTestUtils.setField(provider, "allowRotation", false);
        ReflectionTestUtils.setField(provider, "rotationEnabled", true);
        ReflectionTestUtils.setField(provider, "rotationDays", 90);
        ReflectionTestUtils.setField(provider, "autoRotation", true);
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("Should initialize with derived key from static key string")
        void shouldInitializeWithDerivedKey() {
            provider.initialize();

            assertThat(provider.getCurrentKey()).isNotNull();
            assertThat(provider.getCurrentVersion()).isEqualTo("static");
        }

        @Test
        @DisplayName("Should disable rotation when allowRotation is false")
        void shouldDisableRotationWhenNotAllowed() {
            provider.initialize();

            boolean rotationEnabled = (boolean) ReflectionTestUtils.getField(provider, "rotationEnabled");
            boolean autoRotation = (boolean) ReflectionTestUtils.getField(provider, "autoRotation");

            assertThat(rotationEnabled).isFalse();
            assertThat(autoRotation).isFalse();
        }

        @Test
        @DisplayName("Should keep rotation settings when allowRotation is true")
        void shouldKeepRotationWhenAllowed() {
            ReflectionTestUtils.setField(provider, "allowRotation", true);

            provider.initialize();

            boolean rotationEnabled = (boolean) ReflectionTestUtils.getField(provider, "rotationEnabled");
            assertThat(rotationEnabled).isTrue();
        }

        @Test
        @DisplayName("Should cache the key in permanentKeyCache")
        void shouldCacheKeyInPermanentKeyCache() {
            provider.initialize();

            @SuppressWarnings("unchecked")
            Map<String, SecretKey> cache = (Map<String, SecretKey>)
                ReflectionTestUtils.getField(provider, "permanentKeyCache");

            assertThat(cache).containsKey("static");
            assertThat(cache.get("static")).isEqualTo(provider.getCurrentKey());
        }

        @Test
        @DisplayName("Should derive same key for same input string")
        void shouldDeriveSameKeyForSameInput() {
            provider.initialize();
            SecretKey key1 = provider.getCurrentKey();

            SimpleKeyProvider provider2 = new SimpleKeyProvider();
            ReflectionTestUtils.setField(provider2, "staticKey", "test-key-for-unit-testing");
            ReflectionTestUtils.setField(provider2, "allowRotation", false);
            ReflectionTestUtils.setField(provider2, "rotationEnabled", true);
            ReflectionTestUtils.setField(provider2, "rotationDays", 90);
            ReflectionTestUtils.setField(provider2, "autoRotation", true);
            provider2.initialize();
            SecretKey key2 = provider2.getCurrentKey();

            assertThat(key1.getEncoded()).isEqualTo(key2.getEncoded());
        }

        @Test
        @DisplayName("Should derive different keys for different input strings")
        void shouldDeriveDifferentKeysForDifferentInput() {
            provider.initialize();
            SecretKey key1 = provider.getCurrentKey();

            SimpleKeyProvider provider2 = new SimpleKeyProvider();
            ReflectionTestUtils.setField(provider2, "staticKey", "different-key-string");
            ReflectionTestUtils.setField(provider2, "allowRotation", false);
            ReflectionTestUtils.setField(provider2, "rotationEnabled", true);
            ReflectionTestUtils.setField(provider2, "rotationDays", 90);
            ReflectionTestUtils.setField(provider2, "autoRotation", true);
            provider2.initialize();
            SecretKey key2 = provider2.getCurrentKey();

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }
    }

    @Nested
    @DisplayName("getCurrentKey()")
    class GetCurrentKeyTests {

        @Test
        @DisplayName("Should throw IllegalStateException when not initialized")
        void shouldThrowWhenNotInitialized() {
            assertThatThrownBy(() -> provider.getCurrentKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SimpleKeyProvider not initialized");
        }

        @Test
        @DisplayName("Should return the derived key after initialization")
        void shouldReturnKeyAfterInitialization() {
            provider.initialize();

            SecretKey key = provider.getCurrentKey();

            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("AES");
        }
    }

    @Nested
    @DisplayName("getKey()")
    class GetKeyTests {

        @BeforeEach
        void initProvider() {
            provider.initialize();
        }

        @Test
        @DisplayName("Should return current key for 'static' version")
        void shouldReturnCurrentKeyForStaticVersion() {
            SecretKey key = provider.getKey("static");

            assertThat(key).isEqualTo(provider.getCurrentKey());
        }

        @Test
        @DisplayName("Should return current key for null version")
        void shouldReturnCurrentKeyForNullVersion() {
            SecretKey key = provider.getKey(null);

            assertThat(key).isEqualTo(provider.getCurrentKey());
        }

        @Test
        @DisplayName("Should return current key for unknown version with warning")
        void shouldReturnCurrentKeyForUnknownVersion() {
            SecretKey key = provider.getKey("unknown-version");

            assertThat(key).isEqualTo(provider.getCurrentKey());
        }

        @Test
        @DisplayName("Should return cached key if version exists in cache")
        void shouldReturnCachedKeyIfExists() {
            // "static" version should be in cache from initialization
            SecretKey key = provider.getKey("static");

            assertThat(key).isNotNull();
        }
    }

    @Nested
    @DisplayName("getCurrentVersion()")
    class GetCurrentVersionTests {

        @Test
        @DisplayName("Should return null before initialization")
        void shouldReturnNullBeforeInitialization() {
            assertThat(provider.getCurrentVersion()).isNull();
        }

        @Test
        @DisplayName("Should return 'static' after initialization")
        void shouldReturnStaticAfterInitialization() {
            provider.initialize();

            assertThat(provider.getCurrentVersion()).isEqualTo("static");
        }
    }

    @Nested
    @DisplayName("rotateKey()")
    class RotateKeyTests {

        @Test
        @DisplayName("Should return current version when rotation is not allowed")
        void shouldReturnCurrentVersionWhenRotationNotAllowed() {
            provider.initialize();

            String result = provider.rotateKey();

            assertThat(result).isEqualTo("static");
        }

        @Test
        @DisplayName("Should return current version even when rotation is allowed")
        void shouldReturnCurrentVersionWhenRotationAllowed() {
            ReflectionTestUtils.setField(provider, "allowRotation", true);
            provider.initialize();

            String result = provider.rotateKey();

            // SimpleKeyProvider doesn't actually support rotation in practice
            assertThat(result).isEqualTo("static");
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("Should return false before initialization")
        void shouldReturnFalseBeforeInitialization() {
            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("Should return true after initialization")
        void shouldReturnTrueAfterInitialization() {
            provider.initialize();

            assertThat(provider.isConfigured()).isTrue();
        }
    }

    @Nested
    @DisplayName("getName()")
    class GetNameTests {

        @Test
        @DisplayName("Should return 'SimpleKeyProvider'")
        void shouldReturnSimpleKeyProvider() {
            assertThat(provider.getName()).isEqualTo("SimpleKeyProvider");
        }
    }

    @Nested
    @DisplayName("getKeyStatistics()")
    class GetKeyStatisticsTests {

        @Test
        @DisplayName("Should include warning about development only")
        void shouldIncludeDevWarning() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("warning");
            assertThat(stats.get("warning").toString()).contains("Development only");
        }

        @Test
        @DisplayName("Should mask static key showing only last 4 characters")
        void shouldMaskStaticKey() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("staticKey");
            String maskedKey = (String) stats.get("staticKey");
            assertThat(maskedKey).startsWith("****");
            assertThat(maskedKey).endsWith("ting");
        }

        @Test
        @DisplayName("Should include base statistics")
        void shouldIncludeBaseStatistics() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("provider");
            assertThat(stats.get("provider")).isEqualTo("SimpleKeyProvider");
        }
    }

    @Nested
    @DisplayName("validateConfiguration()")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("Should return false when static key is null")
        void shouldReturnFalseForNullStaticKey() {
            ReflectionTestUtils.setField(provider, "staticKey", (String) null);

            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return false when static key is empty")
        void shouldReturnFalseForEmptyStaticKey() {
            ReflectionTestUtils.setField(provider, "staticKey", "");

            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return true for valid static key")
        void shouldReturnTrueForValidStaticKey() {
            assertThat(provider.validateConfiguration()).isTrue();
        }

        @Test
        @DisplayName("Should return true even for default development key")
        void shouldReturnTrueForDefaultDevKey() {
            ReflectionTestUtils.setField(provider, "staticKey", "dev-default-key-do-not-use-in-production");

            assertThat(provider.validateConfiguration()).isTrue();
        }

        @Test
        @DisplayName("Should return true for short key with warning")
        void shouldReturnTrueForShortKey() {
            ReflectionTestUtils.setField(provider, "staticKey", "short");

            assertThat(provider.validateConfiguration()).isTrue();
        }
    }
}
