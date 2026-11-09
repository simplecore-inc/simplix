package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.PropertiesStorageConfigurationSource;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import dev.simplecore.simplix.file.infrastructure.storage.DownloadFileNames;
import dev.simplecore.simplix.file.infrastructure.storage.FileStorageService;
import dev.simplecore.simplix.file.infrastructure.storage.PresignedUpload;
import dev.simplecore.simplix.file.infrastructure.storage.StorageConfigurationSource;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import dev.simplecore.simplix.file.infrastructure.storage.StoredObjectInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * S3 compatible storage implementation of FileStorageService.
 * <p>
 * Supports AWS S3, MinIO, RustFS, and other S3-compatible storage systems.
 * <p>
 * <b>Settings.</b> The S3 settings come from a {@link StorageConfigurationSource}. The service asks
 * for the source's revision on every call and builds new clients when it changed, so settings an
 * operator edits at runtime take effect on the next call without a restart. Clients replaced this
 * way are closed once the calls that were running on them have finished. When the bucket or a
 * credential is missing the service still starts, and every operation throws a
 * {@link StorageException} with code {@code NOT_CONFIGURED} that names the missing settings.
 * <p>
 * <b>Memory.</b> {@code store} reads the stream through one buffer of at most the configured
 * multipart part size, however large the file is. A stream shorter than one part is sent in a single
 * request, a longer one as a multipart upload that is aborted when anything fails.
 * {@code retrieve} still returns the whole object as a byte array.
 */
@Service
@ConditionalOnProperty(name = "simplix.file.storage.provider", havingValue = "s3")
@Slf4j
public class S3FileStorageService implements FileStorageService {

    /** Largest number of parts S3 accepts in one multipart upload. */
    static final int MAX_PARTS = 10_000;

    /** Longest lifetime of a presigned URL under Signature Version 4. */
    static final Duration MAX_SIGNED_LIFETIME = Duration.ofDays(7);

    /** Largest object a single PUT request can create. */
    static final long MAX_SINGLE_PUT_BYTES = 5L * 1024 * 1024 * 1024;

    private static final int FIRST_READ_BYTES = 256 * 1024;
    private static final int CHECKSUM_BUFFER_BYTES = 64 * 1024;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final StorageConfigurationSource configurationSource;
    private final FileProperties fileProperties;
    private final ImageProcessingService imageProcessingService;
    private final S3ClientFactory clientFactory;

    private final AtomicReference<S3ClientHolder> current = new AtomicReference<>();
    private final Object rebuildLock = new Object();

    @Autowired
    public S3FileStorageService(StorageConfigurationSource configurationSource,
                                FileProperties fileProperties,
                                ImageProcessingService imageProcessingService) {
        this(configurationSource, fileProperties, imageProcessingService, new DefaultS3ClientFactory());
    }

    /**
     * Settings read once from the bound properties, as before the source existed.
     */
    public S3FileStorageService(StorageProperties storageProperties,
                                FileProperties fileProperties,
                                ImageProcessingService imageProcessingService) {
        this(new PropertiesStorageConfigurationSource(storageProperties), fileProperties, imageProcessingService);
    }

    S3FileStorageService(StorageConfigurationSource configurationSource,
                         FileProperties fileProperties,
                         ImageProcessingService imageProcessingService,
                         S3ClientFactory clientFactory) {
        this.configurationSource = configurationSource;
        this.fileProperties = fileProperties;
        this.imageProcessingService = imageProcessingService;
        this.clientFactory = clientFactory;
    }

    @PostConstruct
    public void init() {
        try {
            S3ClientHolder holder = resolve();
            if (holder.isConfigured()) {
                log.info("Initialized S3 storage with endpoint: {}, bucket: {}",
                    holder.config().getEndpoint(), holder.config().getBucket());
            }
        } catch (StorageException e) {
            log.warn("S3 storage settings could not be read at startup: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        synchronized (rebuildLock) {
            S3ClientHolder old = current.getAndSet(null);
            if (old != null) {
                old.retire();
            }
        }
    }

    @Override
    public boolean isConfigured() {
        try {
            return resolve().isConfigured();
        } catch (StorageException e) {
            return false;
        }
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String directory) {
        String mimeType = URLConnection.guessContentTypeFromName(originalName);
        return store(inputStream, originalName, mimeType, directory);
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String mimeType, String directory) {
        StoredKey key = StoredKey.create(directory, originalName);
        MessageDigest digest = Digests.sha256();

        return withHolder(holder -> {
            try {
                long size = upload(holder, key.storedPath(), mimeType, inputStream, digest);
                log.debug("Stored file to S3: {} -> {}", originalName, key.storedPath());
                return new StoredFileInfo(
                    originalName,
                    key.storedName(),
                    key.storedPath(),
                    mimeType,
                    size,
                    key.extension(),
                    HexFormat.of().formatHex(digest.digest())
                );
            } catch (IOException e) {
                throw new StorageException(
                    StorageException.StorageErrorCode.WRITE_FAILED,
                    "Failed to store file: " + originalName,
                    e
                );
            } catch (SdkException e) {
                throw new StorageException(
                    StorageException.StorageErrorCode.WRITE_FAILED,
                    "S3 error storing file: " + originalName + " - " + detail(e),
                    e
                );
            }
        });
    }

    @Override
    public Resource retrieve(String storedPath) {
        return withHolder(holder -> {
            try {
                GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(holder.config().getBucket())
                    .key(storedPath)
                    .build();

                byte[] data = holder.client().getObjectAsBytes(getRequest).asByteArray();
                return new ByteArrayResource(data);
            } catch (NoSuchKeyException e) {
                throw new StorageException(
                    StorageException.StorageErrorCode.PATH_NOT_FOUND,
                    "File not found in S3: " + storedPath
                );
            } catch (S3Exception e) {
                throw new StorageException(
                    StorageException.StorageErrorCode.READ_FAILED,
                    "S3 error reading file: " + storedPath + " - " + detail(e),
                    e
                );
            }
        });
    }

    @Override
    public boolean delete(String storedPath) {
        return withHolder(holder -> {
            try {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(holder.config().getBucket())
                    .key(storedPath)
                    .build();

                holder.client().deleteObject(deleteRequest);
                log.debug("Deleted file from S3: {}", storedPath);
                return true;
            } catch (S3Exception e) {
                log.error("Failed to delete file from S3: {} - {}", storedPath, detail(e));
                return false;
            }
        });
    }

    @Override
    public boolean exists(String storedPath) {
        return withHolder(holder -> {
            try {
                HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(holder.config().getBucket())
                    .key(storedPath)
                    .build();

                holder.client().headObject(headRequest);
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            } catch (S3Exception e) {
                log.warn("Error checking existence in S3: {} - {}", storedPath, detail(e));
                return false;
            }
        });
    }

    @Override
    public String getPublicUrl(String storedPath) {
        return getDownloadUrl(storedPath, null, null);
    }

    @Override
    public String getDownloadUrl(String storedPath, Duration validFor, String downloadFileName) {
        if (validFor != null) {
            requireSignableLifetime(validFor);
        }
        return withHolder(holder -> {
            String publicUrlPrefix = holder.config().getPublicUrlPrefix();
            if (StringUtils.hasText(publicUrlPrefix)) {
                return publicUrlPrefix.endsWith("/") ? publicUrlPrefix + storedPath : publicUrlPrefix + "/" + storedPath;
            }

            Duration lifetime = validFor != null
                ? validFor
                : Duration.ofMinutes(holder.config().getPresignedUrlExpiration());
            requireSignableLifetime(lifetime);

            GetObjectRequest.Builder getRequest = GetObjectRequest.builder()
                .bucket(holder.config().getBucket())
                .key(storedPath);
            if (downloadFileName != null) {
                getRequest.responseContentDisposition(DownloadFileNames.attachment(downloadFileName));
            }

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(lifetime)
                .getObjectRequest(getRequest.build())
                .build();

            return holder.presigner().presignGetObject(presignRequest).url().toString();
        });
    }

    @Override
    public boolean supportsDirectUpload() {
        return true;
    }

    @Override
    public PresignedUpload createDirectUpload(
        String directory, String originalName, String mimeType, long contentLength, Duration validFor
    ) {
        if (contentLength < 0 || contentLength > MAX_SINGLE_PUT_BYTES) {
            throw new IllegalArgumentException(
                "contentLength must be between 0 and " + MAX_SINGLE_PUT_BYTES + " bytes, the largest a single upload request can carry");
        }
        if (validFor == null) {
            throw new IllegalArgumentException("validFor is required");
        }
        requireSignableLifetime(validFor);
        String contentType = mimeType == null || mimeType.isBlank() ? DEFAULT_CONTENT_TYPE : mimeType.strip();
        if (containsControlCharacter(contentType)) {
            throw new IllegalArgumentException("mimeType must not contain control characters");
        }

        StoredKey key = StoredKey.create(directory, originalName);

        return withHolder(holder -> {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(holder.config().getBucket())
                .key(key.storedPath())
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .putObjectRequest(putRequest)
                .build();

            PresignedPutObjectRequest presigned = holder.presigner().presignPutObject(presignRequest);

            Map<String, String> headers = new LinkedHashMap<>();
            presigned.signedHeaders().forEach((name, values) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!"host".equals(lower) && !values.isEmpty()) {
                    headers.put(lower, String.join(",", values));
                }
            });

            return new PresignedUpload(
                key.storedPath(),
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                headers,
                presigned.expiration()
            );
        });
    }

    @Override
    public StoredObjectInfo describe(String storedPath, boolean withChecksum) {
        if (!StringUtils.hasText(storedPath)) {
            throw new StorageException(StorageException.StorageErrorCode.INVALID_PATH, "Stored path is empty");
        }
        return withHolder(holder -> {
            try {
                return withChecksum
                    ? describeWithChecksum(holder, storedPath)
                    : describeFromHead(holder, storedPath);
            } catch (S3Exception e) {
                if (isNotFound(e)) {
                    throw new StorageException(
                        StorageException.StorageErrorCode.PATH_NOT_FOUND,
                        "File not found in S3: " + storedPath
                    );
                }
                throw new StorageException(
                    StorageException.StorageErrorCode.READ_FAILED,
                    "S3 error reading file: " + storedPath + " - " + detail(e),
                    e
                );
            } catch (SdkException | IOException e) {
                throw new StorageException(
                    StorageException.StorageErrorCode.READ_FAILED,
                    "Failed to read file: " + storedPath,
                    e
                );
            }
        });
    }

    @Override
    public Resource getThumbnail(String storedPath, int width, int height) {
        String thumbnailKey = buildThumbnailKey(storedPath, width, height);

        if (exists(thumbnailKey)) {
            return retrieve(thumbnailKey);
        }

        try {
            Resource original = retrieve(storedPath);
            ProcessedImage thumbnail = imageProcessingService.generateThumbnail(
                original.getInputStream(), width, height
            );

            withHolder(holder -> {
                PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(holder.config().getBucket())
                    .key(thumbnailKey)
                    .contentType(thumbnail.mimeType())
                    .contentLength((long) thumbnail.data().length)
                    .build();

                return holder.client().putObject(putRequest, RequestBody.fromBytes(thumbnail.data()));
            });

            return new ByteArrayResource(thumbnail.data());
        } catch (IOException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.READ_FAILED,
                "Failed to generate thumbnail for: " + storedPath,
                e
            );
        }
    }

    @Override
    public String getThumbnailUrl(String storedPath, int width, int height) {
        String thumbnailKey = buildThumbnailKey(storedPath, width, height);
        return getPublicUrl(thumbnailKey);
    }

    // ==================== Upload ====================

    /**
     * Send the stream to the bucket and return the number of bytes sent. The digest sees every byte
     * once while it streams.
     */
    private long upload(S3ClientHolder holder, String key, String mimeType, InputStream inputStream,
                        MessageDigest digest) throws IOException {
        String bucket = holder.config().getBucket();
        int partSize = holder.config().effectivePartSizeBytes();

        // One byte of look-ahead tells a stream of exactly one part from the start of a longer one.
        PushbackInputStream source = new PushbackInputStream(new DigestInputStream(inputStream, digest), 1);

        byte[] first = source.readNBytes(Math.min(partSize, FIRST_READ_BYTES));
        if (first.length < Math.min(partSize, FIRST_READ_BYTES)) {
            putSingle(holder, bucket, key, mimeType, first, first.length);
            return first.length;
        }

        byte[] buffer = new byte[partSize];
        System.arraycopy(first, 0, buffer, 0, first.length);
        int filled = first.length + source.readNBytes(buffer, first.length, partSize - first.length);
        if (filled < partSize || !hasMore(source)) {
            putSingle(holder, bucket, key, mimeType, buffer, filled);
            return filled;
        }

        return uploadMultipart(holder, bucket, key, mimeType, source, buffer, partSize);
    }

    private void putSingle(S3ClientHolder holder, String bucket, String key, String mimeType,
                           byte[] data, int length) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(mimeType)
            .contentLength((long) length)
            .build();

        holder.client().putObject(putRequest,
            RequestBody.fromInputStream(new ByteArrayInputStream(data, 0, length), length));
    }

    /**
     * Upload a stream whose first part is already in {@code buffer}. The buffer is reused for every
     * part, so memory does not grow with the file. Any failure aborts the upload; a failed abort is
     * attached to the original exception as suppressed and never replaces it.
     */
    private long uploadMultipart(S3ClientHolder holder, String bucket, String key, String mimeType,
                                 PushbackInputStream source, byte[] buffer, int partSize) throws IOException {
        S3Client client = holder.client();
        String uploadId = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(mimeType)
            .build()).uploadId();

        try {
            List<CompletedPart> completed = new ArrayList<>();
            long total = 0;
            int length = partSize;
            int partNumber = 1;
            while (true) {
                if (partNumber > MAX_PARTS) {
                    throw new StorageException(
                        StorageException.StorageErrorCode.WRITE_FAILED,
                        "File needs more than " + MAX_PARTS + " parts at a part size of " + partSize
                            + " bytes; raise simplix.file.storage.s3.multipart-part-size"
                    );
                }
                UploadPartResponse response = client.uploadPart(
                    UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) length)
                        .build(),
                    RequestBody.fromInputStream(new ByteArrayInputStream(buffer, 0, length), length));
                completed.add(CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build());
                total += length;
                partNumber++;

                if (length < partSize) {
                    break;
                }
                length = source.readNBytes(buffer, 0, partSize);
                if (length == 0) {
                    break;
                }
            }

            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build());
            return total;
        } catch (Throwable failure) {
            abortQuietly(client, bucket, key, uploadId, failure);
            throw failure;
        }
    }

    private void abortQuietly(S3Client client, String bucket, String key, String uploadId, Throwable failure) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .build());
        } catch (RuntimeException abortFailure) {
            log.warn("Failed to abort multipart upload {} of {}: {}", uploadId, key, abortFailure.toString());
            failure.addSuppressed(abortFailure);
        }
    }

    private static boolean hasMore(PushbackInputStream source) throws IOException {
        int next = source.read();
        if (next < 0) {
            return false;
        }
        source.unread(next);
        return true;
    }

    // ==================== Describe ====================

    private StoredObjectInfo describeFromHead(S3ClientHolder holder, String storedPath) {
        HeadObjectResponse head = holder.client().headObject(HeadObjectRequest.builder()
            .bucket(holder.config().getBucket())
            .key(storedPath)
            .build());
        return new StoredObjectInfo(
            storedPath,
            head.contentLength() == null ? 0L : head.contentLength(),
            head.contentType(),
            head.lastModified(),
            null
        );
    }

    /**
     * Read the object once through a fixed buffer. The size and checksum are those of the bytes read,
     * so they describe the same object even when it is replaced while the read runs.
     */
    private StoredObjectInfo describeWithChecksum(S3ClientHolder holder, String storedPath) throws IOException {
        MessageDigest digest = Digests.sha256();
        byte[] buffer = new byte[CHECKSUM_BUFFER_BYTES];
        long total = 0;

        try (var in = holder.client().getObject(GetObjectRequest.builder()
            .bucket(holder.config().getBucket())
            .key(storedPath)
            .build())) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                total += read;
            }
            GetObjectResponse meta = in.response();
            if (meta.contentLength() != null && meta.contentLength() != total) {
                throw new IOException("Object ended after " + total + " of " + meta.contentLength() + " bytes");
            }
            return new StoredObjectInfo(
                storedPath, total, meta.contentType(), meta.lastModified(), HexFormat.of().formatHex(digest.digest()));
        }
    }

    // ==================== Clients ====================

    /**
     * Run an operation on the clients of the current revision. The holder is leased for the whole
     * call, so a rebuild in the meantime cannot close the clients under it.
     */
    private <T> T withHolder(Function<S3ClientHolder, T> operation) {
        while (true) {
            S3ClientHolder holder = resolve();
            if (!holder.isConfigured()) {
                throw new StorageException(
                    StorageException.StorageErrorCode.NOT_CONFIGURED,
                    "S3 storage is not configured: " + String.join(", ", holder.problems())
                );
            }
            if (holder.tryAcquire()) {
                try {
                    return operation.apply(holder);
                } finally {
                    holder.release();
                }
            }
        }
    }

    /**
     * The holder for the source's current revision, built on first use and after every change.
     */
    S3ClientHolder resolve() {
        long revision = readRevision();
        S3ClientHolder holder = current.get();
        if (holder != null && holder.revision() == revision) {
            return holder;
        }
        synchronized (rebuildLock) {
            holder = current.get();
            if (holder != null && holder.revision() == revision) {
                return holder;
            }
            S3ClientHolder next = build(revision);
            S3ClientHolder old = current.getAndSet(next);
            if (old != null) {
                old.retire();
            }
            return next;
        }
    }

    private long readRevision() {
        try {
            return configurationSource.revision();
        } catch (RuntimeException e) {
            throw sourceFailure(e);
        }
    }

    private S3ClientHolder build(long revision) {
        StorageProperties.S3StorageConfig config;
        try {
            config = configurationSource.s3().copy();
        } catch (RuntimeException e) {
            throw sourceFailure(e);
        }

        List<String> missing = config.missingSettings();
        if (!missing.isEmpty()) {
            log.info("S3 storage is not configured yet, missing: {}", String.join(", ", missing));
            return S3ClientHolder.unconfigured(revision, config, missing);
        }

        S3Client client = null;
        try {
            client = clientFactory.createClient(config);
            S3Presigner presigner = clientFactory.createPresigner(config);
            ensureBucketExists(client, config.getBucket());
            log.info("Built S3 storage clients for revision {} with endpoint: {}, bucket: {}",
                revision, config.getEndpoint(), config.getBucket());
            return S3ClientHolder.configured(revision, config, client, presigner);
        } catch (IllegalArgumentException | SdkException e) {
            log.warn("S3 storage settings are unusable: {}", e.toString());
            if (client != null) {
                client.close();
            }
            return S3ClientHolder.unconfigured(revision, config, List.of("endpoint or region is invalid"));
        }
    }

    private static StorageException sourceFailure(RuntimeException cause) {
        return new StorageException(
            StorageException.StorageErrorCode.NOT_CONFIGURED,
            "S3 storage settings could not be read from the configuration source",
            cause
        );
    }

    // ==================== Private Methods ====================

    void ensureBucketExists(S3Client client, String bucket) {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(bucket)
                .build();
            client.headBucket(headRequest);
            log.debug("Bucket exists: {}", bucket);
        } catch (NoSuchBucketException e) {
            log.info("Bucket does not exist, creating: {}", bucket);
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created bucket: {}", bucket);
            } catch (SdkException createFailure) {
                log.warn("Could not create bucket: {} - {}", bucket, detail(createFailure));
            }
        } catch (SdkException e) {
            log.warn("Could not verify bucket existence: {} - {}", bucket, detail(e));
        }
    }

    private static void requireSignableLifetime(Duration validFor) {
        if (validFor.isZero() || validFor.isNegative() || validFor.compareTo(MAX_SIGNED_LIFETIME) > 0) {
            throw new IllegalArgumentException("A signed URL lives from one second to seven days, got " + validFor);
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean isNotFound(S3Exception e) {
        return e instanceof NoSuchKeyException || e.statusCode() == 404;
    }

    private static String detail(SdkException e) {
        if (e instanceof AwsServiceException service
            && service.awsErrorDetails() != null
            && service.awsErrorDetails().errorMessage() != null) {
            return service.awsErrorDetails().errorMessage();
        }
        return e.getMessage();
    }

    private String buildThumbnailKey(String storedPath, int width, int height) {
        String prefix = fileProperties.getThumbnail().getS3Prefix();
        return prefix + "/" + width + "x" + height + "/" + storedPath;
    }
}
