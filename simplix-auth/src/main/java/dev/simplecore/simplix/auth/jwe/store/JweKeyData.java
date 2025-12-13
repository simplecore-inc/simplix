package dev.simplecore.simplix.auth.jwe.store;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Data transfer object for JWE key storage.
 * Contains encrypted key material and metadata.
 *
 * <p>Key material (public/private keys) should be encrypted using
 * simplix-encryption before storage to ensure keys are protected at rest.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * JweKeyData keyData = JweKeyData.builder()
 *     .version("jwe-v1702345678901")
 *     .encryptedPublicKey(encryptionService.encrypt(publicKeyBase64).getData())
 *     .encryptedPrivateKey(encryptionService.encrypt(privateKeyBase64).getData())
 *     .active(true)
 *     .createdAt(Instant.now())
 *     .build();
 * }</pre>
 */
@Data
@Builder
public class JweKeyData {

    /**
     * Key version identifier.
     * Used as 'kid' (Key ID) in JWE header for multi-key decryption support.
     * Format: "jwe-v{timestamp}" (e.g., "jwe-v1702345678901")
     */
    private String version;

    /**
     * Encrypted public key.
     * Base64-encoded RSA public key, encrypted via simplix-encryption.
     * Format: "{encryptionVersion}:{iv}:{ciphertext}"
     */
    private String encryptedPublicKey;

    /**
     * Encrypted private key.
     * Base64-encoded RSA private key (PKCS#8), encrypted via simplix-encryption.
     * Format: "{encryptionVersion}:{iv}:{ciphertext}"
     */
    private String encryptedPrivateKey;

    /**
     * Whether this is the current active key for encryption.
     * Only one key should be active at a time.
     * Inactive keys are retained for decrypting existing tokens.
     */
    private boolean active;

    /**
     * Key creation timestamp.
     */
    private Instant createdAt;

    /**
     * Key expiration timestamp (optional).
     * Can be used for cleanup of old keys that are no longer needed.
     * Tokens encrypted with expired keys may still need decryption
     * until they naturally expire.
     */
    private Instant expiresAt;

    /**
     * Initialization marker for distributed environment safety.
     * <p>
     * When set to "INITIAL", this key was created during auto-initialization.
     * Applications should use a unique constraint on this field to prevent
     * multiple servers from creating initial keys simultaneously.
     * <p>
     * Only one key can have a non-null marker value (enforced by DB constraint).
     * Regular rotation keys should have this field set to null.
     */
    private String initializationMarker;

    /**
     * Constant for initialization marker value.
     */
    public static final String INIT_MARKER = "INITIAL";
}
