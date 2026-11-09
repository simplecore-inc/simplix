package dev.simplecore.simplix.file.infrastructure.storage;

import java.time.Instant;
import java.util.Map;

/**
 * A time-limited upload ticket that lets a client send a file straight to the storage backend.
 * <p>
 * The client issues one HTTP request with {@link #method()} to {@link #url()} and sends exactly the
 * headers in {@link #headers()} together with the file as the body. The backend refuses a body whose
 * length or content type differs from the ones the ticket was created for, so the ticket cannot be
 * used to store a different file than the one the server approved.
 *
 * @param storedPath path the object will have once the upload finishes, in the same form
 *                   {@link StoredFileInfo#storedPath()} has after {@code store}
 * @param url        absolute URL to send the request to
 * @param method     HTTP method of the request, {@code PUT} for S3 compatible storage
 * @param headers    headers the client has to send, keys in lower case. It never contains
 *                   {@code host}, which every HTTP client sets itself. A browser sets
 *                   {@code content-length} itself too and ignores an attempt to set it.
 * @param expiresAt  instant after which the backend refuses the request
 */
public record PresignedUpload(
    String storedPath,
    String url,
    String method,
    Map<String, String> headers,
    Instant expiresAt
) {

    public PresignedUpload {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
