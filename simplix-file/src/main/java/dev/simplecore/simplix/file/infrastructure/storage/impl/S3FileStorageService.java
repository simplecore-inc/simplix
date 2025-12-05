package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.image.ImageProcessingService;
import dev.simplecore.simplix.file.infrastructure.image.ProcessedImage;
import dev.simplecore.simplix.file.infrastructure.storage.FileStorageService;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * S3 compatible storage implementation of FileStorageService.
 * <p>
 * Supports AWS S3, MinIO, RustFS, and other S3-compatible storage systems.
 */
@Service
@ConditionalOnProperty(name = "simplix.file.storage.provider", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class S3FileStorageService implements FileStorageService {

    private final StorageProperties storageProperties;
    private final FileProperties fileProperties;
    private final ImageProcessingService imageProcessingService;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @PostConstruct
    public void init() {
        StorageProperties.S3StorageConfig s3Config = storageProperties.getS3();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            s3Config.getAccessKey(),
            s3Config.getSecretKey()
        );

        S3Configuration serviceConfig = S3Configuration.builder()
            .pathStyleAccessEnabled(s3Config.isPathStyleAccess())
            .build();

        var clientBuilder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.of(s3Config.getRegion()))
            .serviceConfiguration(serviceConfig);

        var presignerBuilder = S3Presigner.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.of(s3Config.getRegion()))
            .serviceConfiguration(serviceConfig);

        if (s3Config.getEndpoint() != null && !s3Config.getEndpoint().isEmpty()) {
            URI endpointUri = URI.create(s3Config.getEndpoint());
            clientBuilder.endpointOverride(endpointUri);
            presignerBuilder.endpointOverride(endpointUri);
        }

        this.s3Client = clientBuilder.build();
        this.s3Presigner = presignerBuilder.build();

        log.info("Initialized S3 storage with endpoint: {}, bucket: {}",
            s3Config.getEndpoint(), s3Config.getBucket());

        ensureBucketExists();
    }

    @PreDestroy
    public void cleanup() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String directory) {
        String mimeType = URLConnection.guessContentTypeFromName(originalName);
        return store(inputStream, originalName, mimeType, directory);
    }

    @Override
    public StoredFileInfo store(InputStream inputStream, String originalName, String mimeType, String directory) {
        try {
            String extension = StoredFileInfo.extractExtension(originalName);
            String storedName = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);

            String datePath = buildDatePath();
            String fullDirectory = directory != null ? directory + "/" + datePath : datePath;
            String storedPath = fullDirectory + "/" + storedName;

            byte[] fileBytes = inputStream.readAllBytes();
            String checksum = calculateChecksum(fileBytes);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(storedPath)
                .contentType(mimeType)
                .contentLength((long) fileBytes.length)
                .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes));

            log.debug("Stored file to S3: {} -> {}", originalName, storedPath);

            return new StoredFileInfo(
                originalName,
                storedName,
                storedPath,
                mimeType,
                (long) fileBytes.length,
                extension,
                checksum
            );
        } catch (IOException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.WRITE_FAILED,
                "Failed to store file: " + originalName,
                e
            );
        } catch (S3Exception e) {
            throw new StorageException(
                StorageException.StorageErrorCode.WRITE_FAILED,
                "S3 error storing file: " + originalName + " - " + e.awsErrorDetails().errorMessage(),
                e
            );
        }
    }

    @Override
    public Resource retrieve(String storedPath) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(storedPath)
                .build();

            byte[] data = s3Client.getObjectAsBytes(getRequest).asByteArray();
            return new ByteArrayResource(data);
        } catch (NoSuchKeyException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.PATH_NOT_FOUND,
                "File not found in S3: " + storedPath
            );
        } catch (S3Exception e) {
            throw new StorageException(
                StorageException.StorageErrorCode.READ_FAILED,
                "S3 error reading file: " + storedPath + " - " + e.awsErrorDetails().errorMessage(),
                e
            );
        }
    }

    @Override
    public boolean delete(String storedPath) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(storedPath)
                .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Deleted file from S3: {}", storedPath);
            return true;
        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {} - {}", storedPath, e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    @Override
    public boolean exists(String storedPath) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(storedPath)
                .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("Error checking existence in S3: {} - {}", storedPath, e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    @Override
    public String getPublicUrl(String storedPath) {
        String publicUrlPrefix = storageProperties.getS3().getPublicUrlPrefix();

        if (publicUrlPrefix != null && !publicUrlPrefix.isEmpty()) {
            if (!publicUrlPrefix.endsWith("/")) {
                publicUrlPrefix += "/";
            }
            return publicUrlPrefix + storedPath;
        }

        return generatePresignedUrl(storedPath);
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

            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(thumbnailKey)
                .contentType(thumbnail.mimeType())
                .contentLength((long) thumbnail.data().length)
                .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(thumbnail.data()));

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

    // ==================== Private Methods ====================

    private void ensureBucketExists() {
        String bucket = storageProperties.getS3().getBucket();
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(bucket)
                .build();
            s3Client.headBucket(headRequest);
            log.debug("Bucket exists: {}", bucket);
        } catch (NoSuchBucketException e) {
            log.info("Bucket does not exist, creating: {}", bucket);
            CreateBucketRequest createRequest = CreateBucketRequest.builder()
                .bucket(bucket)
                .build();
            s3Client.createBucket(createRequest);
            log.info("Created bucket: {}", bucket);
        } catch (S3Exception e) {
            log.warn("Could not verify bucket existence: {} - {}", bucket, e.awsErrorDetails().errorMessage());
        }
    }

    private String generatePresignedUrl(String storedPath) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(storageProperties.getS3().getBucket())
            .key(storedPath)
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(storageProperties.getS3().getPresignedUrlExpiration()))
            .getObjectRequest(getRequest)
            .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String buildDatePath() {
        LocalDate now = LocalDate.now();
        return String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    private String buildThumbnailKey(String storedPath, int width, int height) {
        String prefix = fileProperties.getThumbnail().getS3Prefix();
        return prefix + "/" + width + "x" + height + "/" + storedPath;
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException(
                StorageException.StorageErrorCode.CHECKSUM_CALCULATION_FAILED,
                "Failed to calculate checksum",
                e
            );
        }
    }
}