package dev.simplecore.simplix.license.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.accesscore.license.sdk.model.LicenseModel.KeyMaterial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The keys a build carries, read the way the deployment reads them.
 *
 * <p>The embedded resource is the one thing about verification a product supplies itself, and
 * nothing about it is checkable from a screen: a resource that failed to parse, or that named a
 * key twice, would surface as every licence being unverifiable with no line saying why. So the
 * shipped file is loaded here through the same two steps the auto-configuration takes, and the
 * refusals are exercised alongside it.
 */
@DisplayName("Verification keys - what a build carries")
class VerificationKeysTest {

    private static final String PEM = "-----BEGIN PUBLIC KEY-----\nkey\n-----END PUBLIC KEY-----";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should read the embedded resource into the keys the SDK takes")
    void shouldReadEmbeddedResource() throws IOException {
        VerificationKeys keys = new VerificationKeys(readResource());

        List<KeyMaterial> materials = keys.materials();
        assertThat(materials).hasSize(1);
        assertThat(materials.get(0).kid()).isEqualTo("simplix-2026-01");
        assertThat(materials.get(0).publicKey()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(keys.carriesCompromisedKey()).isFalse();
        assertThat(keys.isCompromised("simplix-2026-01")).isFalse();
    }

    @Test
    @DisplayName("should report a key the resource marks compromised")
    void shouldReportCompromisedKey() {
        VerificationKeys keys = new VerificationKeys(List.of(
                new VerificationKey("current", PEM, false),
                new VerificationKey("leaked", PEM, true)));

        assertThat(keys.carriesCompromisedKey()).isTrue();
        assertThat(keys.isCompromised("leaked")).isTrue();
        assertThat(keys.isCompromised("current")).isFalse();
        assertThat(keys.isCompromised(null)).isFalse();
    }

    @Test
    @DisplayName("should refuse a build carrying no key at all")
    void shouldRefuseNoKeys() {
        assertThatThrownBy(() -> new VerificationKeys(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carries no verification key");
    }

    @Test
    @DisplayName("should refuse a key with no name")
    void shouldRefuseUnnamedKey() {
        assertThatThrownBy(() -> new VerificationKeys(List.of(new VerificationKey(" ", PEM, false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carries no name");
    }

    @Test
    @DisplayName("should refuse a key with no material")
    void shouldRefuseKeyWithoutMaterial() {
        assertThatThrownBy(() -> new VerificationKeys(List.of(new VerificationKey("current", "", false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carries no key material");
    }

    @Test
    @DisplayName("should refuse the same name declared twice")
    void shouldRefuseDuplicateName() {
        assertThatThrownBy(() -> new VerificationKeys(List.of(
                new VerificationKey("current", PEM, false),
                new VerificationKey("current", PEM, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declared twice");
    }

    /**
     * @return the resource's contents, read exactly as the auto-configuration reads them
     * @throws IOException when the embedded resource cannot be read
     */
    private List<VerificationKey> readResource() throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        try (InputStream stream = resourceLoader
                .getResource(LicenseProperties.VERIFICATION_KEYS_LOCATION).getInputStream()) {
            return objectMapper.readValue(stream.readAllBytes(),
                    new TypeReference<List<VerificationKey>>() {
                    });
        }
    }
}
