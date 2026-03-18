package dev.simplecore.simplix.auth.jwe.provider;

import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StaticJweKeyProvider")
class StaticJweKeyProviderTest {

    private StaticJweKeyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StaticJweKeyProvider();
    }

    @Nested
    @DisplayName("before initialization")
    class BeforeInitialization {

        @Test
        @DisplayName("should report not configured")
        void shouldReportNotConfigured() {
            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("should return empty available versions")
        void shouldReturnEmptyVersions() {
            assertThat(provider.getAvailableVersions()).isEmpty();
        }

        @Test
        @DisplayName("should return static as current version")
        void shouldReturnStaticVersion() {
            assertThat(provider.getCurrentVersion()).isEqualTo("static");
        }

        @Test
        @DisplayName("should throw on getCurrentKeyPair when not initialized")
        void shouldThrowOnGetCurrentKeyPair() {
            assertThatThrownBy(() -> provider.getCurrentKeyPair())
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("not initialized");
        }

        @Test
        @DisplayName("should throw on getKeyPair when not initialized")
        void shouldThrowOnGetKeyPair() {
            assertThatThrownBy(() -> provider.getKeyPair("any"))
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("not initialized");
        }
    }

    @Nested
    @DisplayName("initialization with KeyPair")
    class InitializationWithKeyPair {

        private KeyPair keyPair;

        @BeforeEach
        void setUp() throws NoSuchAlgorithmException {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            keyPair = gen.generateKeyPair();
        }

        @Test
        @DisplayName("should initialize successfully with valid KeyPair")
        void shouldInitializeSuccessfully() {
            provider.initialize(keyPair);

            assertThat(provider.isConfigured()).isTrue();
            assertThat(provider.getCurrentKeyPair()).isEqualTo(keyPair);
        }

        @Test
        @DisplayName("should throw on null KeyPair")
        void shouldThrowOnNullKeyPair() {
            assertThatThrownBy(() -> provider.initialize((KeyPair) null))
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("KeyPair is null");
        }

        @Test
        @DisplayName("should return singleton set of static version after init")
        void shouldReturnStaticVersionSet() {
            provider.initialize(keyPair);

            Set<String> versions = provider.getAvailableVersions();
            assertThat(versions).containsExactly("static");
        }

        @Test
        @DisplayName("should return key for any version request")
        void shouldReturnKeyForAnyVersion() {
            provider.initialize(keyPair);

            assertThat(provider.getKeyPair("static")).isEqualTo(keyPair);
            assertThat(provider.getKeyPair("v1")).isEqualTo(keyPair);
            assertThat(provider.getKeyPair(null)).isEqualTo(keyPair);
        }
    }

    @Nested
    @DisplayName("initialization with JWK JSON")
    class InitializationWithJwkJson {

        @Test
        @DisplayName("should throw on null JWK JSON")
        void shouldThrowOnNullJwkJson() {
            assertThatThrownBy(() -> provider.initialize((String) null))
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw on blank JWK JSON")
        void shouldThrowOnBlankJwkJson() {
            assertThatThrownBy(() -> provider.initialize("   "))
                    .isInstanceOf(JweKeyException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw on invalid JWK JSON")
        void shouldThrowOnInvalidJwkJson() {
            assertThatThrownBy(() -> provider.initialize("not-a-valid-jwk"))
                    .isInstanceOf(JweKeyException.class);
        }
    }

    @Test
    @DisplayName("should return correct provider name")
    void shouldReturnCorrectName() {
        assertThat(provider.getName()).isEqualTo("StaticJweKeyProvider");
    }

    @Test
    @DisplayName("STATIC_VERSION constant should be 'static'")
    void staticVersionConstant() {
        assertThat(StaticJweKeyProvider.STATIC_VERSION).isEqualTo("static");
    }
}
