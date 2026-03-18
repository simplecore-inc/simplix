package dev.simplecore.simplix.encryption.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultKeyProvider Tests")
class VaultKeyProviderTest {

    private static final String KEY_PATH_PREFIX = "secret/encryption/keys";
    private static final String CURRENT_KEY_PATH = KEY_PATH_PREFIX + "/current";

    @Mock
    private VaultTemplate vaultTemplate;

    private VaultKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new VaultKeyProvider();
        provider.setVaultTemplate(vaultTemplate);
        provider.setVaultEnabled(true);
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
            VaultKeyProvider defaultProvider = new VaultKeyProvider();
            assertThat(defaultProvider).isNotNull();
        }

        @Test
        @DisplayName("Should create with parameterized constructor")
        void shouldCreateWithParameterizedConstructor() {
            VaultKeyProvider paramProvider = new VaultKeyProvider(vaultTemplate, true);
            assertThat(paramProvider).isNotNull();
        }
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("Should skip initialization when vault is disabled")
        void shouldSkipWhenVaultDisabled() {
            provider.setVaultEnabled(false);

            provider.initialize();

            verifyNoInteractions(vaultTemplate);
        }

        @Test
        @DisplayName("Should throw when VaultTemplate is null and vault is enabled")
        void shouldThrowWhenVaultTemplateNull() {
            provider.setVaultTemplate(null);

            assertThatThrownBy(() -> provider.initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VaultTemplate not configured");
        }

        @Test
        @DisplayName("Should load existing current key from Vault")
        void shouldLoadExistingCurrentKey() throws NoSuchAlgorithmException {
            String existingVersion = "v1234";
            String rotatedAt = Instant.now().toString();
            SecretKey testKey = generateTestKey();
            String encodedKey = Base64.getEncoder().encodeToString(testKey.getEncoded());

            // Mock current key path response
            VaultResponse currentResponse = new VaultResponse();
            Map<String, Object> currentData = new HashMap<>();
            currentData.put("version", existingVersion);
            currentData.put("rotatedAt", rotatedAt);
            currentResponse.setData(currentData);
            when(vaultTemplate.read(CURRENT_KEY_PATH)).thenReturn(currentResponse);

            // Mock key data response
            VaultResponse keyResponse = new VaultResponse();
            Map<String, Object> keyData = new HashMap<>();
            keyData.put("key", encodedKey);
            keyResponse.setData(keyData);
            when(vaultTemplate.read(KEY_PATH_PREFIX + "/" + existingVersion)).thenReturn(keyResponse);

            provider.initialize();

            assertThat(ReflectionTestUtils.getField(provider, "currentVersion")).isEqualTo(existingVersion);
        }

        @Test
        @DisplayName("Should create first key when no current key exists in Vault")
        void shouldCreateFirstKeyWhenNoneExists() {
            // Mock read to handle multiple paths
            when(vaultTemplate.read(anyString())).thenAnswer(invocation -> {
                String path = invocation.getArgument(0);
                if (CURRENT_KEY_PATH.equals(path)) {
                    // First call: no current key
                    VaultResponse currentResponse = new VaultResponse();
                    currentResponse.setData(null);
                    return currentResponse;
                }
                // For key paths (pre-loading after creation), the key is already cached
                // so this should not be called, but return a valid response just in case
                VaultResponse keyResp = new VaultResponse();
                Map<String, Object> data = new HashMap<>();
                byte[] keyBytes = new byte[32];
                new java.security.SecureRandom().nextBytes(keyBytes);
                data.put("key", Base64.getEncoder().encodeToString(keyBytes));
                keyResp.setData(data);
                return keyResp;
            });

            provider.initialize();

            // Verify key was written to Vault
            verify(vaultTemplate, atLeastOnce()).write(anyString(), any());
        }

        @Test
        @DisplayName("Should throw when Vault read fails")
        void shouldThrowWhenVaultReadFails() {
            when(vaultTemplate.read(CURRENT_KEY_PATH))
                .thenThrow(new RuntimeException("Vault connection failed"));

            assertThatThrownBy(() -> provider.initialize())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vault initialization failed");
        }
    }

    @Nested
    @DisplayName("getCurrentKey()")
    class GetCurrentKeyTests {

        @Test
        @DisplayName("Should refresh version from Vault and return key")
        void shouldRefreshAndReturnKey() throws NoSuchAlgorithmException {
            String version = "v1";
            SecretKey testKey = generateTestKey();

            // Set initial state
            ReflectionTestUtils.setField(provider, "currentVersion", version);
            provider.getClass(); // force class load

            // Pre-populate cache
            @SuppressWarnings("unchecked")
            Map<String, SecretKey> cache = (Map<String, SecretKey>)
                ReflectionTestUtils.getField(provider, "permanentKeyCache");
            cache.put(version, testKey);

            // Mock refreshCurrentVersion call
            VaultResponse response = new VaultResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("version", version);
            response.setData(data);
            when(vaultTemplate.read(CURRENT_KEY_PATH)).thenReturn(response);

            SecretKey result = provider.getCurrentKey();

            assertThat(result).isEqualTo(testKey);
        }
    }

    @Nested
    @DisplayName("getKey()")
    class GetKeyTests {

        @Test
        @DisplayName("Should return cached key if available")
        void shouldReturnCachedKey() throws NoSuchAlgorithmException {
            String version = "v1";
            SecretKey testKey = generateTestKey();

            @SuppressWarnings("unchecked")
            Map<String, SecretKey> cache = (Map<String, SecretKey>)
                ReflectionTestUtils.getField(provider, "permanentKeyCache");
            cache.put(version, testKey);

            SecretKey result = provider.getKey(version);

            assertThat(result).isEqualTo(testKey);
            verifyNoInteractions(vaultTemplate);
        }

        @Test
        @DisplayName("Should fetch key from Vault when not cached")
        void shouldFetchFromVaultWhenNotCached() throws NoSuchAlgorithmException {
            String version = "v1";
            SecretKey testKey = generateTestKey();
            String encodedKey = Base64.getEncoder().encodeToString(testKey.getEncoded());

            VaultResponse response = new VaultResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("key", encodedKey);
            response.setData(data);
            when(vaultTemplate.read(KEY_PATH_PREFIX + "/" + version)).thenReturn(response);

            SecretKey result = provider.getKey(version);

            assertThat(result.getEncoded()).isEqualTo(testKey.getEncoded());
            verify(vaultTemplate).read(KEY_PATH_PREFIX + "/" + version);
        }

        @Test
        @DisplayName("Should cache key after fetching from Vault")
        void shouldCacheKeyAfterFetch() throws NoSuchAlgorithmException {
            String version = "v1";
            SecretKey testKey = generateTestKey();
            String encodedKey = Base64.getEncoder().encodeToString(testKey.getEncoded());

            VaultResponse response = new VaultResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("key", encodedKey);
            response.setData(data);
            when(vaultTemplate.read(KEY_PATH_PREFIX + "/" + version)).thenReturn(response);

            // First call fetches from Vault
            provider.getKey(version);

            // Second call should use cache
            provider.getKey(version);

            verify(vaultTemplate, times(1)).read(KEY_PATH_PREFIX + "/" + version);
        }

        @Test
        @DisplayName("Should throw when key data is null in Vault response")
        void shouldThrowWhenKeyDataNull() {
            String version = "v1";
            VaultResponse response = new VaultResponse();
            response.setData(null);
            when(vaultTemplate.read(KEY_PATH_PREFIX + "/" + version)).thenReturn(response);

            assertThatThrownBy(() -> provider.getKey(version))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get key from Vault");
        }

        @Test
        @DisplayName("Should throw when key value is null in Vault data")
        void shouldThrowWhenKeyValueNull() {
            String version = "v1";
            VaultResponse response = new VaultResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("key", null);
            response.setData(data);
            when(vaultTemplate.read(KEY_PATH_PREFIX + "/" + version)).thenReturn(response);

            assertThatThrownBy(() -> provider.getKey(version))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get key from Vault");
        }

        @Test
        @DisplayName("Should throw when Vault read fails")
        void shouldThrowWhenVaultReadFails() {
            when(vaultTemplate.read(anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> provider.getKey("v1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get key from Vault");
        }
    }

    @Nested
    @DisplayName("getCurrentVersion()")
    class GetCurrentVersionTests {

        @Test
        @DisplayName("Should refresh from Vault and return current version")
        void shouldRefreshAndReturnVersion() {
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");

            VaultResponse response = new VaultResponse();
            Map<String, Object> data = new HashMap<>();
            data.put("version", "v2");
            data.put("rotatedAt", Instant.now().toString());
            response.setData(data);
            when(vaultTemplate.read(CURRENT_KEY_PATH)).thenReturn(response);

            String version = provider.getCurrentVersion();

            assertThat(version).isEqualTo("v2");
        }

        @Test
        @DisplayName("Should return cached version when Vault is unavailable")
        void shouldReturnCachedVersionWhenVaultUnavailable() {
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");

            when(vaultTemplate.read(CURRENT_KEY_PATH))
                .thenThrow(new RuntimeException("Vault unavailable"));

            String version = provider.getCurrentVersion();

            assertThat(version).isEqualTo("v1");
        }
    }

    @Nested
    @DisplayName("rotateKey()")
    class RotateKeyTests {

        @Test
        @DisplayName("Should return current version when rotation is disabled")
        void shouldReturnCurrentVersionWhenRotationDisabled() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", false);
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");

            String result = provider.rotateKey();

            assertThat(result).isEqualTo("v1");
        }

        @Test
        @DisplayName("Should create new key and update Vault on rotation")
        void shouldCreateNewKeyAndUpdateVault() {
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");

            String newVersion = provider.rotateKey();

            assertThat(newVersion).isNotNull();
            assertThat(newVersion).startsWith("v");
            // Verify key was written and current version was updated
            verify(vaultTemplate, atLeast(2)).write(anyString(), any());
        }

        @Test
        @DisplayName("Should throw when key rotation fails")
        void shouldThrowWhenRotationFails() {
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");
            doThrow(new RuntimeException("Write failed"))
                .when(vaultTemplate).write(anyString(), any());

            assertThatThrownBy(() -> provider.rotateKey())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to rotate key");
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("Should return false when VaultTemplate is null")
        void shouldReturnFalseWhenVaultTemplateNull() {
            provider.setVaultTemplate(null);

            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("Should return false when currentVersion is null")
        void shouldReturnFalseWhenVersionNull() {
            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("Should return true when properly configured")
        void shouldReturnTrueWhenConfigured() {
            ReflectionTestUtils.setField(provider, "currentVersion", "v1");

            assertThat(provider.isConfigured()).isTrue();
        }
    }

    @Nested
    @DisplayName("getName()")
    class GetNameTests {

        @Test
        @DisplayName("Should return 'VaultKeyProvider'")
        void shouldReturnName() {
            assertThat(provider.getName()).isEqualTo("VaultKeyProvider");
        }
    }

    private SecretKey generateTestKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }
}
