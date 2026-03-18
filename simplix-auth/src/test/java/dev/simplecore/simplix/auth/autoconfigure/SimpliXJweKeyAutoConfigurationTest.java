package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.jwe.provider.DatabaseJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.provider.StaticJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.service.JweKeyRotationService;
import dev.simplecore.simplix.auth.jwe.store.JweKeyData;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
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
import static org.mockito.Mockito.*;

@DisplayName("SimpliXJweKeyAutoConfiguration")
@ExtendWith(MockitoExtension.class)
class SimpliXJweKeyAutoConfigurationTest {

    private final SimpliXJweKeyAutoConfiguration config = new SimpliXJweKeyAutoConfiguration();

    @Test
    @DisplayName("should create uninitialized StaticJweKeyProvider as fallback")
    void shouldCreateStaticJweKeyProvider() {
        StaticJweKeyProvider provider = config.staticJweKeyProvider();

        assertThat(provider).isNotNull();
        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.getName()).isEqualTo("StaticJweKeyProvider");
    }

    @Nested
    @DisplayName("databaseJweKeyProvider")
    class DatabaseJweKeyProviderBean {

        @Mock
        private JweKeyStore keyStore;

        @Mock
        private EncryptionService encryptionService;

        @Test
        @DisplayName("should create and initialize DatabaseJweKeyProvider")
        void shouldCreateDatabaseJweKeyProvider() {
            when(keyStore.findAll()).thenReturn(Collections.emptyList());

            DatabaseJweKeyProvider provider = config.databaseJweKeyProvider(keyStore, encryptionService);

            assertThat(provider).isNotNull();
            assertThat(provider.getName()).isEqualTo("DatabaseJweKeyProvider");
            verify(keyStore).findAll(); // initialize() was called
        }
    }

    @Nested
    @DisplayName("jweKeyRotationService")
    class JweKeyRotationServiceBean {

        @Mock
        private JweKeyStore keyStore;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private DatabaseJweKeyProvider keyProvider;

        @Test
        @DisplayName("should create JweKeyRotationService with properties")
        void shouldCreateKeyRotationService() {
            SimpliXAuthProperties properties = new SimpliXAuthProperties();
            properties.getJwe().getKeyRolling().setKeySize(4096);
            properties.getJwe().getKeyRolling().setAutoInitialize(false);
            properties.getJwe().getKeyRolling().getRetention().setBufferSeconds(172800);
            properties.getJwe().getKeyRolling().getRetention().setAutoCleanup(true);
            properties.getToken().setRefreshTokenLifetime(1209600); // 14 days

            JweKeyRotationService service = config.jweKeyRotationService(
                    keyStore, encryptionService, keyProvider, properties);

            assertThat(service).isNotNull();
            assertThat(service.getRsaKeySize()).isEqualTo(4096);
        }

        @Test
        @DisplayName("should auto-initialize when configured")
        void shouldAutoInitialize() {
            SimpliXAuthProperties properties = new SimpliXAuthProperties();
            properties.getJwe().getKeyRolling().setAutoInitialize(true);

            // Simulate keys already exist
            JweKeyData existingKey = JweKeyData.builder().version("v1").active(true).build();
            when(keyStore.findCurrent()).thenReturn(Optional.of(existingKey));

            JweKeyRotationService service = config.jweKeyRotationService(
                    keyStore, encryptionService, keyProvider, properties);

            assertThat(service).isNotNull();
        }
    }
}
