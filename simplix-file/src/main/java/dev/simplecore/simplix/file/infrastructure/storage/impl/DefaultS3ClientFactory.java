package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds real AWS SDK clients from the settings. Building does not contact the endpoint.
 */
final class DefaultS3ClientFactory implements S3ClientFactory {

    @Override
    public S3Client createClient(StorageProperties.S3StorageConfig config) {
        var builder = S3Client.builder()
            .credentialsProvider(credentials(config))
            .region(Region.of(config.getRegion()))
            .serviceConfiguration(serviceConfiguration(config));
        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        return builder.build();
    }

    @Override
    public S3Presigner createPresigner(StorageProperties.S3StorageConfig config) {
        var builder = S3Presigner.builder()
            .credentialsProvider(credentials(config))
            .region(Region.of(config.getRegion()))
            .serviceConfiguration(serviceConfiguration(config));
        if (StringUtils.hasText(config.getEndpoint())) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        return builder.build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties.S3StorageConfig config) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
    }

    private static S3Configuration serviceConfiguration(StorageProperties.S3StorageConfig config) {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(config.isPathStyleAccess())
            .build();
    }
}
