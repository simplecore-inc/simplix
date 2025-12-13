package dev.simplecore.simplix.auth.jwe.store;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for JWE keys.
 * Applications must implement this interface to provide persistence.
 *
 * <p>This interface defines the contract for storing and retrieving JWE key data.
 * The library provides the key management logic while applications control
 * where and how keys are persisted.</p>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Keys are stored encrypted (using simplix-encryption) - this interface
 *       receives already-encrypted key material</li>
 *   <li>Only one key should be active at a time</li>
 *   <li>Old keys must be retained for decryption of existing tokens</li>
 *   <li>Thread-safety should be considered for concurrent access</li>
 * </ul>
 *
 * <h2>Example JPA Implementation</h2>
 * <pre>{@code
 * @Repository
 * public class JpaJweKeyStore implements JweKeyStore {
 *     private final JweKeyRepository repository;
 *
 *     @Override
 *     public JweKeyData save(JweKeyData keyData) {
 *         JweKeyEntity entity = toEntity(keyData);
 *         return toData(repository.save(entity));
 *     }
 *
 *     @Override
 *     @Transactional
 *     public void deactivateAllExcept(String exceptVersion) {
 *         repository.deactivateAllExcept(exceptVersion);
 *     }
 *     // ... other methods
 * }
 * }</pre>
 *
 * @see JweKeyData
 */
public interface JweKeyStore {

    /**
     * Saves a new key entry to storage.
     *
     * @param keyData Key data to save (with encrypted key material)
     * @return Saved key data with any generated IDs or timestamps
     */
    JweKeyData save(JweKeyData keyData);

    /**
     * Finds a key by its version identifier.
     *
     * @param version Key version (kid)
     * @return Optional containing key data if found, empty otherwise
     */
    Optional<JweKeyData> findByVersion(String version);

    /**
     * Finds the current active key.
     * There should be at most one active key at any time.
     *
     * @return Optional containing active key data if exists, empty otherwise
     */
    Optional<JweKeyData> findCurrent();

    /**
     * Finds all stored keys.
     * Includes both active and inactive keys.
     *
     * @return List of all key data entries, may be empty
     */
    List<JweKeyData> findAll();

    /**
     * Deactivates all keys except the specified version.
     * Called during key rotation to ensure only one key is active.
     *
     * <p>Implementation should set {@code active = false} for all keys
     * where version != exceptVersion.</p>
     *
     * @param exceptVersion Version to keep active
     */
    void deactivateAllExcept(String exceptVersion);

    /**
     * Finds all expired keys.
     * Keys are considered expired when their expiresAt is before the current time.
     *
     * <p>Default implementation returns empty list for backward compatibility.</p>
     *
     * @return List of expired key data entries
     */
    default List<JweKeyData> findExpired() {
        return List.of();
    }

    /**
     * Deletes a key by its version.
     * Use with caution - deleting a key makes tokens encrypted with it undecryptable.
     *
     * <p>Default implementation does nothing for backward compatibility.</p>
     *
     * @param version Key version to delete
     * @return true if key was deleted, false if not found
     */
    default boolean deleteByVersion(String version) {
        return false;
    }

    /**
     * Deletes all expired keys.
     * Convenience method that combines findExpired() and deleteByVersion().
     *
     * <p>Default implementation uses findExpired() and deleteByVersion().</p>
     *
     * @return Number of keys deleted
     */
    default int deleteExpired() {
        List<JweKeyData> expired = findExpired();
        int deleted = 0;
        for (JweKeyData key : expired) {
            if (deleteByVersion(key.getVersion())) {
                deleted++;
            }
        }
        return deleted;
    }
}
