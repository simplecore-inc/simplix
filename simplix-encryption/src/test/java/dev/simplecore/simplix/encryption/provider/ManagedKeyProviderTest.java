package dev.simplecore.simplix.encryption.provider;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagedKeyProvider Tests")
class ManagedKeyProviderTest {

    @Mock
    private LockProvider lockProvider;

    @Mock
    private SimpleLock simpleLock;

    private ManagedKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ManagedKeyProvider();
        ReflectionTestUtils.setField(provider, "rotationEnabled", false);
        ReflectionTestUtils.setField(provider, "rotationDays", 90);
        ReflectionTestUtils.setField(provider, "autoRotation", false);
    }

    @Nested
    @DisplayName("initialize() - In-Memory Mode")
    class InitializeInMemoryTests {

        @Test
        @DisplayName("Should initialize with master key and salt")
        void shouldInitializeWithMasterKeyAndSalt() {
            provider.setMasterKey("a-very-long-master-key-for-testing-purposes-1234");
            provider.setSalt("a-long-enough-salt-string");

            provider.initialize();

            assertThat(provider.getCurrentKey()).isNotNull();
            assertThat(provider.getCurrentVersion()).isEqualTo("v0001");
            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should initialize with random key when no master key configured")
        void shouldInitializeWithRandomKey() {
            provider.initialize();

            assertThat(provider.getCurrentKey()).isNotNull();
            assertThat(provider.getCurrentVersion()).isEqualTo("v0001");
            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should derive same key from same master key and salt")
        void shouldDeriveSameKeyFromSameMasterKeyAndSalt() {
            provider.setMasterKey("a-very-long-master-key-for-testing-purposes-1234");
            provider.setSalt("a-long-enough-salt-string");
            provider.initialize();
            SecretKey key1 = provider.getCurrentKey();

            ManagedKeyProvider provider2 = new ManagedKeyProvider();
            ReflectionTestUtils.setField(provider2, "rotationEnabled", false);
            ReflectionTestUtils.setField(provider2, "rotationDays", 90);
            ReflectionTestUtils.setField(provider2, "autoRotation", false);
            provider2.setMasterKey("a-very-long-master-key-for-testing-purposes-1234");
            provider2.setSalt("a-long-enough-salt-string");
            provider2.initialize();
            SecretKey key2 = provider2.getCurrentKey();

            assertThat(key1.getEncoded()).isEqualTo(key2.getEncoded());
        }
    }

    @Nested
    @DisplayName("initialize() - File-Based Mode")
    class InitializeFileBasedTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Should initialize and create key store directory when it does not exist")
        void shouldCreateKeyStoreDirectory() {
            Path keyStorePath = tempDir.resolve("new-key-store");
            provider.setKeyStorePath(keyStorePath.toString());

            provider.initialize();

            assertThat(provider.getCurrentKey()).isNotNull();
            assertThat(provider.getCurrentVersion()).isEqualTo("v0001");
        }

        @Test
        @DisplayName("Should load existing keys from key store directory")
        void shouldLoadExistingKeys() throws Exception {
            // First initialize to create key files
            Path keyStorePath = tempDir.resolve("key-store");
            provider.setKeyStorePath(keyStorePath.toString());
            provider.initialize();

            String version = provider.getCurrentVersion();
            SecretKey originalKey = provider.getCurrentKey();

            // Create a new provider and load from same path
            ManagedKeyProvider provider2 = new ManagedKeyProvider();
            ReflectionTestUtils.setField(provider2, "rotationEnabled", false);
            ReflectionTestUtils.setField(provider2, "rotationDays", 90);
            ReflectionTestUtils.setField(provider2, "autoRotation", false);
            provider2.setKeyStorePath(keyStorePath.toString());
            provider2.initialize();

            assertThat(provider2.getCurrentKey().getEncoded()).isEqualTo(originalKey.getEncoded());
        }
    }

    @Nested
    @DisplayName("getCurrentKey()")
    class GetCurrentKeyTests {

        @Test
        @DisplayName("Should throw when not initialized")
        void shouldThrowWhenNotInitialized() {
            assertThatThrownBy(() -> provider.getCurrentKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No encryption key available");
        }

        @Test
        @DisplayName("Should return key after initialization")
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

        @Test
        @DisplayName("Should return key for valid version")
        void shouldReturnKeyForValidVersion() {
            provider.initialize();
            String version = provider.getCurrentVersion();

            SecretKey key = provider.getKey(version);

            assertThat(key).isNotNull();
        }

        @Test
        @DisplayName("Should throw for invalid version")
        void shouldThrowForInvalidVersion() {
            provider.initialize();

            assertThatThrownBy(() -> provider.getKey("non-existent-version"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key version not found");
        }
    }

    @Nested
    @DisplayName("rotateKey()")
    class RotateKeyTests {

        @Test
        @DisplayName("Should return current version when rotation is disabled")
        void shouldReturnCurrentVersionWhenRotationDisabled() {
            provider.initialize();
            String originalVersion = provider.getCurrentVersion();

            String result = provider.rotateKey();

            assertThat(result).isEqualTo(originalVersion);
        }

        @Test
        @DisplayName("Should rotate key without lock provider")
        void shouldRotateKeyWithoutLockProvider() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            provider.initialize();
            String originalVersion = provider.getCurrentVersion();

            String newVersion = provider.rotateKey();

            assertThat(newVersion).isNotEqualTo(originalVersion);
            assertThat(provider.getCurrentVersion()).isEqualTo(newVersion);
        }

        @Test
        @DisplayName("Should rotate key with lock provider when lock is acquired")
        void shouldRotateKeyWithLockWhenAcquired() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            provider.setLockProvider(lockProvider);
            when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));

            provider.initialize();
            String originalVersion = provider.getCurrentVersion();

            String newVersion = provider.rotateKey();

            assertThat(newVersion).isNotEqualTo(originalVersion);
            verify(simpleLock).unlock();
        }

        @Test
        @DisplayName("Should skip rotation when lock cannot be acquired")
        void shouldSkipRotationWhenLockCannotBeAcquired() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            provider.setLockProvider(lockProvider);
            when(lockProvider.lock(any())).thenReturn(Optional.empty());

            provider.initialize();
            String originalVersion = provider.getCurrentVersion();

            String result = provider.rotateKey();

            assertThat(result).isEqualTo(originalVersion);
        }

        @Test
        @DisplayName("Should preserve old keys after rotation for decryption")
        void shouldPreserveOldKeysAfterRotation() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            provider.initialize();
            String v1 = provider.getCurrentVersion();
            SecretKey keyV1 = provider.getCurrentKey();

            provider.rotateKey();
            String v2 = provider.getCurrentVersion();

            assertThat(v2).isNotEqualTo(v1);
            // Old key should still be accessible
            SecretKey retrievedOldKey = provider.getKey(v1);
            assertThat(retrievedOldKey.getEncoded()).isEqualTo(keyV1.getEncoded());
        }

        @Test
        @DisplayName("Should rotate key with file-based storage")
        void shouldRotateKeyWithFileBasedStorage(@TempDir Path tempDir) {
            Path keyStorePath = tempDir.resolve("key-store");
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            provider.setKeyStorePath(keyStorePath.toString());
            provider.initialize();

            String originalVersion = provider.getCurrentVersion();
            String newVersion = provider.rotateKey();

            assertThat(newVersion).isNotEqualTo(originalVersion);
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
        @DisplayName("Should return 'ManagedKeyProvider'")
        void shouldReturnManagedKeyProvider() {
            assertThat(provider.getName()).isEqualTo("ManagedKeyProvider");
        }
    }

    @Nested
    @DisplayName("getKeyStatistics()")
    class GetKeyStatisticsTests {

        @Test
        @DisplayName("Should include total keys in registry")
        void shouldIncludeTotalKeysInRegistry() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("totalKeysInRegistry");
            assertThat(((Number) stats.get("totalKeysInRegistry")).intValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should include active keys count")
        void shouldIncludeActiveKeysCount() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsKey("activeKeys");
            assertThat((long) stats.get("activeKeys")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should include key store path info")
        void shouldIncludeKeyStorePath() {
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("keyStorePath", "memory");
        }

        @Test
        @DisplayName("Should include key store path when configured")
        void shouldIncludeConfiguredKeyStorePath(@TempDir Path tempDir) {
            Path keyStorePath = tempDir.resolve("key-store");
            provider.setKeyStorePath(keyStorePath.toString());
            provider.initialize();

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats.get("keyStorePath")).isEqualTo(keyStorePath.toString());
        }
    }

    @Nested
    @DisplayName("shouldRotate() override")
    class ShouldRotateOverrideTests {

        @Test
        @DisplayName("Should return true when lastRotation is null (never rotated)")
        void shouldReturnTrueWhenNeverRotated() {
            ReflectionTestUtils.setField(provider, "lastRotation", null);

            boolean result = (boolean) ReflectionTestUtils.invokeMethod(provider, "shouldRotate");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("validateConfiguration()")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("Should return false when no master key, salt, or key store path")
        void shouldReturnFalseWhenNothingConfigured() {
            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return false when master key is too short")
        void shouldReturnFalseWhenMasterKeyTooShort() {
            provider.setMasterKey("short");
            provider.setSalt("a-long-enough-salt-string");

            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return false when salt is too short")
        void shouldReturnFalseWhenSaltTooShort() {
            provider.setMasterKey("a-very-long-master-key-for-testing-purposes-1234");
            provider.setSalt("short");

            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return true when key store path is configured without master key")
        void shouldReturnTrueWithKeyStorePath() {
            provider.setKeyStorePath("/some/path");

            assertThat(provider.validateConfiguration()).isTrue();
        }

        @Test
        @DisplayName("Should return true with valid master key and salt")
        void shouldReturnTrueWithValidMasterKeyAndSalt() {
            provider.setMasterKey("a-very-long-master-key-for-testing-purposes-1234");
            provider.setSalt("a-long-enough-salt-string");

            assertThat(provider.validateConfiguration()).isTrue();
        }

        @Test
        @DisplayName("Should return false when rotation enabled with invalid days")
        void shouldReturnFalseWithInvalidRotationDays() {
            provider.setKeyStorePath("/some/path");
            ReflectionTestUtils.setField(provider, "rotationEnabled", true);
            ReflectionTestUtils.setField(provider, "rotationDays", 0);

            assertThat(provider.validateConfiguration()).isFalse();
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            ManagedKeyProvider defaultProvider = new ManagedKeyProvider();

            assertThat(defaultProvider).isNotNull();
            assertThat(defaultProvider.getName()).isEqualTo("ManagedKeyProvider");
        }

        @Test
        @DisplayName("Should create with parameterized constructor")
        void shouldCreateWithParameterizedConstructor() {
            ManagedKeyProvider paramProvider = new ManagedKeyProvider(
                lockProvider, "/some/path", "master-key", "salt-value");

            assertThat(paramProvider).isNotNull();
        }
    }
}
