package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.infrastructure.exception.StorageException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helper shared by the storage implementations.
 */
final class Digests {

    private Digests() {
    }

    /**
     * A new SHA-256 digest.
     *
     * @return a fresh digest
     * @throws StorageException when the platform has no SHA-256
     */
    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.CHECKSUM_CALCULATION_FAILED,
                "Failed to calculate checksum",
                e
            );
        }
    }
}
