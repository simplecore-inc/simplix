package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Creates the S3 client and presigner for one set of settings. Replaceable so that tests can supply
 * fakes and count rebuilds.
 */
interface S3ClientFactory {

    S3Client createClient(StorageProperties.S3StorageConfig config);

    S3Presigner createPresigner(StorageProperties.S3StorageConfig config);
}
