package dev.simplecore.simplix.auth.jwe.service;

import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;
import dev.simplecore.simplix.auth.jwe.provider.DatabaseJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.store.JweKeyData;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import dev.simplecore.simplix.encryption.service.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("JweKeyRotationService")
@ExtendWith(MockitoExtension.class)
class JweKeyRotationServiceTest {

    @Mock
    private JweKeyStore keyStore;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private DatabaseJweKeyProvider keyProvider;

    private JweKeyRotationService service;

    @BeforeEach
    void setUp() {
        service = new JweKeyRotationService(keyStore, encryptionService, keyProvider);
    }

    @Nested
    @DisplayName("rotateKey")
    class RotateKey {

        @Test
        @DisplayName("should generate new key pair and store it")
        void shouldGenerateAndStoreNewKey() {
            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted-data");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            String version = service.rotateKey();

            assertThat(version).isNotNull();
            assertThat(version).startsWith("jwe-v");

            ArgumentCaptor<JweKeyData> captor = ArgumentCaptor.forClass(JweKeyData.class);
            verify(keyStore).save(captor.capture());
            JweKeyData saved = captor.getValue();

            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getEncryptedPublicKey()).isEqualTo("encrypted-data");
            assertThat(saved.getEncryptedPrivateKey()).isEqualTo("encrypted-data");
            assertThat(saved.getExpiresAt()).isNotNull();

            verify(keyStore).deactivateAllExcept(version);
            verify(keyProvider).refresh();
        }

        @Test
        @DisplayName("should use rotation marker based on current active key")
        void shouldUseRotationMarkerBasedOnCurrentKey() {
            JweKeyData currentKey = JweKeyData.builder()
                    .version("old-v1")
                    .active(true)
                    .build();
            when(keyStore.findCurrent()).thenReturn(Optional.of(currentKey));

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            service.rotateKey();

            ArgumentCaptor<JweKeyData> captor = ArgumentCaptor.forClass(JweKeyData.class);
            verify(keyStore).save(captor.capture());
            assertThat(captor.getValue().getInitializationMarker()).isEqualTo("AFTER-old-v1");
        }

        @Test
        @DisplayName("should return null when unique constraint violation occurs")
        void shouldReturnNullOnUniqueConstraintViolation() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);
            doThrow(new RuntimeException("unique constraint violation"))
                    .when(keyStore).save(any());

            String result = service.rotateKey();

            assertThat(result).isNull();
            verify(keyProvider).refresh();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredKeys")
    class CleanupExpiredKeys {

        @Test
        @DisplayName("should delete expired keys and refresh provider")
        void shouldDeleteExpiredKeys() {
            when(keyStore.deleteExpired()).thenReturn(3);

            int deleted = service.cleanupExpiredKeys();

            assertThat(deleted).isEqualTo(3);
            verify(keyProvider).refresh();
        }

        @Test
        @DisplayName("should not refresh provider when no keys deleted")
        void shouldNotRefreshWhenNoKeysDeleted() {
            when(keyStore.deleteExpired()).thenReturn(0);

            int deleted = service.cleanupExpiredKeys();

            assertThat(deleted).isZero();
            verify(keyProvider, never()).refresh();
        }

        @Test
        @DisplayName("should handle cleanup exception gracefully")
        void shouldHandleCleanupException() {
            when(keyStore.deleteExpired()).thenThrow(new RuntimeException("DB error"));

            int deleted = service.cleanupExpiredKeys();

            assertThat(deleted).isZero();
        }
    }

    @Nested
    @DisplayName("initializeIfEmpty")
    class InitializeIfEmpty {

        @Test
        @DisplayName("should create initial key when store is empty")
        void shouldCreateInitialKeyWhenEmpty() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            boolean result = service.initializeIfEmpty();

            assertThat(result).isTrue();
            verify(keyStore).save(any());
        }

        @Test
        @DisplayName("should skip when keys already exist")
        void shouldSkipWhenKeysExist() {
            JweKeyData existing = JweKeyData.builder().version("v1").active(true).build();
            when(keyStore.findCurrent()).thenReturn(Optional.of(existing));

            boolean result = service.initializeIfEmpty();

            assertThat(result).isFalse();
            verify(keyStore, never()).save(any());
        }

        @Test
        @DisplayName("should return false when another server already initialized")
        void shouldReturnFalseWhenRaceConditionLost() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);
            doThrow(new RuntimeException("unique constraint violation"))
                    .when(keyStore).save(any());

            boolean result = service.initializeIfEmpty();

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("with auto cleanup enabled")
    class WithAutoCleanup {

        @BeforeEach
        void setUp() {
            service = new JweKeyRotationService(
                    keyStore, encryptionService, keyProvider,
                    2048, 604800, 86400, true);
        }

        @Test
        @DisplayName("should cleanup expired keys before rotation")
        void shouldCleanupBeforeRotation() {
            when(keyStore.deleteExpired()).thenReturn(2);
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            service.rotateKey();

            verify(keyStore).deleteExpired();
        }
    }

    @Nested
    @DisplayName("rotateKey - fallback marker")
    class RotateKeyFallbackMarker {

        @Test
        @DisplayName("should use latest key version as marker when no active key")
        void shouldUseLatestKeyVersionAsMarker() {
            JweKeyData oldKey = JweKeyData.builder()
                    .version("old-v1")
                    .active(false)
                    .createdAt(java.time.Instant.now().minusSeconds(3600))
                    .build();
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(List.of(oldKey));

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            service.rotateKey();

            ArgumentCaptor<JweKeyData> captor = ArgumentCaptor.forClass(JweKeyData.class);
            verify(keyStore).save(captor.capture());
            assertThat(captor.getValue().getInitializationMarker()).isEqualTo("AFTER-old-v1");
        }

        @Test
        @DisplayName("should use latest key version as marker when multiple inactive keys")
        void shouldUseLatestWhenMultipleInactiveKeys() {
            JweKeyData oldKey1 = JweKeyData.builder()
                    .version("old-v1").active(false)
                    .createdAt(java.time.Instant.now().minusSeconds(7200))
                    .build();
            JweKeyData oldKey2 = JweKeyData.builder()
                    .version("old-v2").active(false)
                    .createdAt(java.time.Instant.now().minusSeconds(3600))
                    .build();
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(List.of(oldKey1, oldKey2));

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            service.rotateKey();

            ArgumentCaptor<JweKeyData> captor = ArgumentCaptor.forClass(JweKeyData.class);
            verify(keyStore).save(captor.capture());
            assertThat(captor.getValue().getInitializationMarker()).isEqualTo("AFTER-old-v2");
        }

        @Test
        @DisplayName("should throw JweKeyException for non-unique-constraint failures")
        void shouldThrowForNonUniqueErrors() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);
            doThrow(new RuntimeException("connection refused"))
                    .when(keyStore).save(any());

            assertThatThrownBy(() -> service.rotateKey())
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("Failed to rotate JWE key");
        }

        @Test
        @DisplayName("should handle SQL unique constraint via SQLState")
        void shouldHandleSqlUniqueConstraintViaSqlState() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            java.sql.SQLException sqlEx = new java.sql.SQLException("dup", "23505");
            doThrow(new RuntimeException("wrap", sqlEx))
                    .when(keyStore).save(any());

            String result = service.rotateKey();
            assertThat(result).isNull();
            verify(keyProvider).refresh();
        }

        @Test
        @DisplayName("should handle duplicate key message")
        void shouldHandleDuplicateKeyMessage() {
            when(keyStore.findCurrent()).thenReturn(Optional.empty());
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);
            doThrow(new RuntimeException("duplicate key value violates unique constraint"))
                    .when(keyStore).save(any());

            String result = service.rotateKey();
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("initializeIfEmpty - edge cases")
    class InitializeIfEmptyEdgeCases {

        @Test
        @DisplayName("should return false when another server already initialized during race")
        void shouldReturnFalseWhenAnotherServerInitializedDuringRace() {
            when(keyStore.findCurrent())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(JweKeyData.builder().version("v1").active(true).build()));
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            EncryptionService.EncryptedData encResult = mock(EncryptionService.EncryptedData.class);
            when(encResult.getData()).thenReturn("encrypted");
            when(encryptionService.encrypt(anyString())).thenReturn(encResult);

            // Simulate JweKeyException (not unique constraint - general failure)
            doThrow(new RuntimeException("connection error"))
                    .when(keyStore).save(any());

            boolean result = service.initializeIfEmpty();

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("should return configured RSA key size")
    void shouldReturnConfiguredKeySize() {
        assertThat(service.getRsaKeySize()).isEqualTo(2048);

        JweKeyRotationService customService = new JweKeyRotationService(
                keyStore, encryptionService, keyProvider, 4096);
        assertThat(customService.getRsaKeySize()).isEqualTo(4096);
    }
}
