package dev.simplecore.simplix.auth.jwe.provider;

import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;
import dev.simplecore.simplix.auth.jwe.store.JweKeyData;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import dev.simplecore.simplix.encryption.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("DatabaseJweKeyProvider")
@ExtendWith(MockitoExtension.class)
class DatabaseJweKeyProviderTest {

    @Mock
    private JweKeyStore keyStore;

    @Mock
    private EncryptionService encryptionService;

    private DatabaseJweKeyProvider provider;
    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        testKeyPair = gen.generateKeyPair();

        provider = new DatabaseJweKeyProvider(keyStore, encryptionService);
    }

    private JweKeyData createKeyData(String version, boolean active) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                testKeyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(
                testKeyPair.getPrivate().getEncoded());

        return JweKeyData.builder()
                .version(version)
                .encryptedPublicKey("enc-pub-" + version)
                .encryptedPrivateKey("enc-priv-" + version)
                .active(active)
                .createdAt(Instant.now())
                .build();
    }

    private void stubDecryption(String version) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                testKeyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(
                testKeyPair.getPrivate().getEncoded());

        when(encryptionService.decrypt("enc-pub-" + version)).thenReturn(publicKeyBase64);
        when(encryptionService.decrypt("enc-priv-" + version)).thenReturn(privateKeyBase64);
    }

    @Nested
    @DisplayName("initialize")
    class Initialize {

        @Test
        @DisplayName("should load keys from store during initialization")
        void shouldLoadKeysFromStore() {
            JweKeyData keyData = createKeyData("v1", true);
            when(keyStore.findAll()).thenReturn(List.of(keyData));
            stubDecryption("v1");

            provider.initialize();

            assertThat(provider.isConfigured()).isTrue();
            assertThat(provider.getCurrentVersion()).isEqualTo("v1");
            assertThat(provider.getAvailableVersions()).containsExactly("v1");
        }

        @Test
        @DisplayName("should handle empty key store")
        void shouldHandleEmptyKeyStore() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            provider.initialize();

            assertThat(provider.isConfigured()).isFalse();
            assertThat(provider.getCurrentVersion()).isNull();
        }

        @Test
        @DisplayName("should load multiple keys and select active as current")
        void shouldLoadMultipleKeys() {
            JweKeyData key1 = createKeyData("v1", false);
            JweKeyData key2 = createKeyData("v2", true);
            when(keyStore.findAll()).thenReturn(List.of(key1, key2));
            stubDecryption("v1");
            stubDecryption("v2");

            provider.initialize();

            assertThat(provider.getCurrentVersion()).isEqualTo("v2");
            assertThat(provider.getAvailableVersions()).containsExactlyInAnyOrder("v1", "v2");
        }

        @Test
        @DisplayName("should fallback to first key when no active key exists")
        void shouldFallbackToFirstKey() {
            JweKeyData key1 = createKeyData("v1", false);
            when(keyStore.findAll()).thenReturn(List.of(key1));
            stubDecryption("v1");

            provider.initialize();

            assertThat(provider.getCurrentVersion()).isNotNull();
            assertThat(provider.isConfigured()).isTrue();
        }
    }

    @Nested
    @DisplayName("getCurrentKeyPair")
    class GetCurrentKeyPair {

        @Test
        @DisplayName("should throw when no active key configured")
        void shouldThrowWhenNoActiveKey() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();

            assertThatThrownBy(() -> provider.getCurrentKeyPair())
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("No active JWE key configured");
        }

        @Test
        @DisplayName("should return current key pair")
        void shouldReturnCurrentKeyPair() {
            JweKeyData keyData = createKeyData("v1", true);
            when(keyStore.findAll()).thenReturn(List.of(keyData));
            stubDecryption("v1");
            provider.initialize();

            KeyPair result = provider.getCurrentKeyPair();

            assertThat(result).isNotNull();
            assertThat(result.getPublic().getAlgorithm()).isEqualTo("RSA");
            assertThat(result.getPrivate().getAlgorithm()).isEqualTo("RSA");
        }
    }

    @Nested
    @DisplayName("getKeyPair by version")
    class GetKeyPairByVersion {

        @Test
        @DisplayName("should return cached key pair for known version")
        void shouldReturnCachedKeyPair() {
            JweKeyData keyData = createKeyData("v1", true);
            when(keyStore.findAll()).thenReturn(List.of(keyData));
            stubDecryption("v1");
            provider.initialize();

            KeyPair result = provider.getKeyPair("v1");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should load on demand for unknown version")
        void shouldLoadOnDemandForUnknownVersion() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();

            JweKeyData keyData = createKeyData("v2", false);
            when(keyStore.findByVersion("v2")).thenReturn(Optional.of(keyData));
            stubDecryption("v2");

            KeyPair result = provider.getKeyPair("v2");

            assertThat(result).isNotNull();
            verify(keyStore).findByVersion("v2");
        }

        @Test
        @DisplayName("should throw when version not found")
        void shouldThrowWhenVersionNotFound() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();
            when(keyStore.findByVersion("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provider.getKeyPair("unknown"))
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("JWE key version not found: unknown");
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should reload all keys from store")
        void shouldReloadAllKeys() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();
            assertThat(provider.isConfigured()).isFalse();

            JweKeyData keyData = createKeyData("v1", true);
            when(keyStore.findAll()).thenReturn(List.of(keyData));
            stubDecryption("v1");

            provider.refresh();

            assertThat(provider.isConfigured()).isTrue();
            assertThat(provider.getCurrentVersion()).isEqualTo("v1");
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should skip keys with decryption errors during initialization")
        void shouldSkipKeysWithDecryptionErrors() {
            JweKeyData goodKey = createKeyData("v1", true);
            JweKeyData badKey = createKeyData("v2", false);

            // Good key decrypts fine
            stubDecryption("v1");

            // Bad key decryption fails
            when(encryptionService.decrypt("enc-pub-v2"))
                    .thenThrow(new RuntimeException("Decryption failed"));

            when(keyStore.findAll()).thenReturn(List.of(goodKey, badKey));

            provider.initialize();

            assertThat(provider.isConfigured()).isTrue();
            assertThat(provider.getAvailableVersions()).containsExactly("v1");
        }

        @Test
        @DisplayName("should return null for on-demand load of non-existent key")
        void shouldReturnNullForNonExistentOnDemandKey() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();
            when(keyStore.findByVersion("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> provider.getKeyPair("unknown"))
                    .isInstanceOf(dev.simplecore.simplix.auth.jwe.exception.JweKeyException.class);
        }
    }

    @Nested
    @DisplayName("KeyCacheState")
    class KeyCacheStateTest {

        @Test
        @DisplayName("should handle null keys map defensively")
        void shouldHandleEmptyCacheState() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();

            assertThat(provider.getAvailableVersions()).isEmpty();
            assertThat(provider.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("concurrent refresh")
    class ConcurrentRefresh {

        @Test
        @DisplayName("should skip refresh when lock is held by another thread")
        void shouldSkipConcurrentRefresh() throws Exception {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());
            provider.initialize();

            // Get the internal lock
            java.lang.reflect.Field lockField = DatabaseJweKeyProvider.class.getDeclaredField("refreshLock");
            lockField.setAccessible(true);
            java.util.concurrent.locks.ReentrantLock lock =
                    (java.util.concurrent.locks.ReentrantLock) lockField.get(provider);

            // Reset invocations so we only count calls during the concurrent test
            reset(keyStore);

            // Hold lock from a different thread (simulating concurrent refresh)
            java.util.concurrent.CountDownLatch lockHeld = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch testDone = new java.util.concurrent.CountDownLatch(1);

            Thread lockingThread = new Thread(() -> {
                lock.lock();
                try {
                    lockHeld.countDown();
                    testDone.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            });
            lockingThread.start();

            // Wait for the lock to be held
            lockHeld.await();

            try {
                // Now try refresh from main thread - should skip
                provider.refresh();
                // findAll should NOT be called since refresh was skipped
                verify(keyStore, never()).findAll();
            } finally {
                testDone.countDown();
                lockingThread.join();
            }
        }
    }

    @Test
    @DisplayName("should return correct provider name")
    void shouldReturnCorrectName() {
        assertThat(provider.getName()).isEqualTo("DatabaseJweKeyProvider");
    }
}
