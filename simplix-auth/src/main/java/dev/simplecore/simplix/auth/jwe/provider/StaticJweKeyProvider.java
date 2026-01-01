package dev.simplecore.simplix.auth.jwe.provider;

import com.nimbusds.jose.jwk.RSAKey;
import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;
import java.text.ParseException;
import java.util.Collections;
import java.util.Set;

/**
 * Static JWE key provider for backward compatibility.
 * Uses a single RSA key loaded from configuration (existing behavior).
 *
 * <p>Use Cases:</p>
 * <ul>
 *   <li>Development and testing environments</li>
 *   <li>Migration from legacy single-key setup</li>
 *   <li>Simple deployments without key rotation requirements</li>
 * </ul>
 *
 * <p>This provider does not support key rotation. For production environments
 * with key rotation needs, use {@link DatabaseJweKeyProvider} instead.</p>
 *
 * @see DatabaseJweKeyProvider
 */
@Slf4j
public class StaticJweKeyProvider implements JweKeyProvider {

    /**
     * Fixed version identifier for static keys.
     * Used as 'kid' in JWE headers when tokens are created with this provider.
     */
    public static final String STATIC_VERSION = "static";

    private KeyPair keyPair;
    private boolean configured = false;

    /**
     * Initializes the provider with an RSA key from JWK JSON string.
     *
     * @param jwkJson JWK JSON string containing RSA key pair
     * @throws JweKeyException if key parsing fails
     */
    public void initialize(String jwkJson) {
        if (jwkJson == null || jwkJson.isBlank()) {
            throw new JweKeyException("JWK JSON string is null or empty");
        }

        try {
            RSAKey rsaKey = RSAKey.parse(jwkJson);
            this.keyPair = new KeyPair(rsaKey.toPublicKey(), rsaKey.toPrivateKey());
            this.configured = true;
            log.info("StaticJweKeyProvider initialized successfully");
        } catch (ParseException e) {
            throw new JweKeyException("Failed to parse JWK JSON", e);
        } catch (Exception e) {
            throw new JweKeyException("Failed to initialize static JWE key", e);
        }
    }

    /**
     * Initializes the provider with an existing KeyPair.
     * Useful for testing or programmatic key provision.
     *
     * @param keyPair RSA key pair to use
     */
    public void initialize(KeyPair keyPair) {
        if (keyPair == null) {
            throw new JweKeyException("KeyPair is null");
        }
        this.keyPair = keyPair;
        this.configured = true;
        log.info("StaticJweKeyProvider initialized with KeyPair");
    }

    @Override
    public KeyPair getCurrentKeyPair() {
        ensureConfigured();
        return keyPair;
    }

    @Override
    public KeyPair getKeyPair(String version) {
        ensureConfigured();

        // Static provider only has one key, return it regardless of version
        // This allows backward compatibility with tokens created before versioning
        if (!STATIC_VERSION.equals(version) && version != null) {
            log.trace("StaticJweKeyProvider requested version '{}', returning static key", version);
        }
        return keyPair;
    }

    @Override
    public String getCurrentVersion() {
        return STATIC_VERSION;
    }

    @Override
    public Set<String> getAvailableVersions() {
        return configured ? Collections.singleton(STATIC_VERSION) : Collections.emptySet();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String getName() {
        return "StaticJweKeyProvider";
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new JweKeyException("StaticJweKeyProvider is not initialized. " +
                "Call initialize() with a valid JWK JSON or KeyPair.");
        }
    }
}
