package dev.simplecore.simplix.file.infrastructure.storage;

import java.time.Instant;

/**
 * Facts about an object that is already in storage, read without handing its bytes to the caller.
 *
 * @param storedPath   path the object is stored under
 * @param size         size in bytes
 * @param contentType  MIME type the storage holds for the object, or null when it holds none
 * @param lastModified time of the last write, or null when the storage does not report one
 * @param checksum     SHA-256 of the content as lower-case hex, or null when the caller asked for
 *                     the metadata only
 */
public record StoredObjectInfo(
    String storedPath,
    long size,
    String contentType,
    Instant lastModified,
    String checksum
) {
}
