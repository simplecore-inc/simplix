package dev.simplecore.simplix.auth.jwe.provider;

import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;

import java.security.KeyPair;
import java.util.Set;

/**
 * Interface for JWE RSA key pair management.
 * Implementations provide key pairs for JWE encryption/decryption operations.
 *
 * <p>This interface is separate from simplix-encryption's KeyProvider (AES symmetric keys)
 * as JWE uses RSA asymmetric key pairs for key encryption.</p>
 *
 * <h2>Key Versioning</h2>
 * <p>Key versions are used as 'kid' (Key ID) in JWE headers, enabling:</p>
 * <ul>
 *   <li>Multiple key versions for backward compatibility</li>
 *   <li>Graceful key rotation without invalidating existing tokens</li>
 *   <li>Key-specific decryption based on token's kid header</li>
 * </ul>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@code StaticJweKeyProvider} - Single key from configuration (legacy/development)</li>
 *   <li>{@code DatabaseJweKeyProvider} - DB-backed multi-key with rotation support</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.auth.jwe.store.JweKeyStore
 */
public interface JweKeyProvider {

    /**
     * Gets the current active key pair for encryption.
     * This key pair is used for creating new JWE tokens.
     *
     * @return Current KeyPair for encryption
     * @throws JweKeyException if no active key is configured
     */
    KeyPair getCurrentKeyPair();

    /**
     * Gets a specific key pair by version for decryption.
     * Used when parsing JWE tokens with a specific 'kid' header.
     *
     * @param version Key version identifier (kid)
     * @return KeyPair for the specified version
     * @throws JweKeyException if version not found
     */
    KeyPair getKeyPair(String version);

    /**
     * Gets the current key version identifier.
     * This version is included as 'kid' in new JWE token headers.
     *
     * @return Current key version string (used as 'kid')
     */
    String getCurrentVersion();

    /**
     * Gets all available key versions.
     * Useful for diagnostics and key management operations.
     *
     * @return Set of available key version identifiers
     */
    Set<String> getAvailableVersions();

    /**
     * Checks if the provider is properly configured and ready to provide keys.
     *
     * @return true if ready to provide keys
     */
    boolean isConfigured();

    /**
     * Gets the provider name for logging and diagnostics.
     *
     * @return Provider implementation name
     */
    String getName();
}
