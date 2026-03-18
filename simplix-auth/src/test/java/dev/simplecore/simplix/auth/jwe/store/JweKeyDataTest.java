package dev.simplecore.simplix.auth.jwe.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JweKeyData")
class JweKeyDataTest {

    @Test
    @DisplayName("should build with all fields using builder")
    void shouldBuildWithAllFields() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);

        JweKeyData keyData = JweKeyData.builder()
                .version("jwe-v1702345678901")
                .encryptedPublicKey("enc-pub-key")
                .encryptedPrivateKey("enc-priv-key")
                .active(true)
                .createdAt(now)
                .expiresAt(expiresAt)
                .initializationMarker("INITIAL")
                .build();

        assertThat(keyData.getVersion()).isEqualTo("jwe-v1702345678901");
        assertThat(keyData.getEncryptedPublicKey()).isEqualTo("enc-pub-key");
        assertThat(keyData.getEncryptedPrivateKey()).isEqualTo("enc-priv-key");
        assertThat(keyData.isActive()).isTrue();
        assertThat(keyData.getCreatedAt()).isEqualTo(now);
        assertThat(keyData.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(keyData.getInitializationMarker()).isEqualTo("INITIAL");
    }

    @Test
    @DisplayName("should support setters")
    void shouldSupportSetters() {
        JweKeyData keyData = JweKeyData.builder().build();

        keyData.setVersion("v2");
        keyData.setActive(false);

        assertThat(keyData.getVersion()).isEqualTo("v2");
        assertThat(keyData.isActive()).isFalse();
    }

    @Test
    @DisplayName("INIT_MARKER constant should be INITIAL")
    void initMarkerConstant() {
        assertThat(JweKeyData.INIT_MARKER).isEqualTo("INITIAL");
    }

    @Test
    @DisplayName("should allow null optional fields")
    void shouldAllowNullOptionalFields() {
        JweKeyData keyData = JweKeyData.builder()
                .version("v1")
                .active(true)
                .build();

        assertThat(keyData.getExpiresAt()).isNull();
        assertThat(keyData.getInitializationMarker()).isNull();
        assertThat(keyData.getCreatedAt()).isNull();
    }
}
