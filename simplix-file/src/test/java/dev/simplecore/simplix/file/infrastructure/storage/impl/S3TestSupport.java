package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.storage.StorageConfigurationSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Test doubles shared by the S3 storage tests.
 */
final class S3TestSupport {

    private S3TestSupport() {
    }

    static StorageProperties.S3StorageConfig completeConfig() {
        StorageProperties.S3StorageConfig config = new StorageProperties.S3StorageConfig();
        config.setEndpoint("http://localhost:9000");
        config.setBucket("test-bucket");
        config.setAccessKey("AKIATESTACCESSKEY00");
        config.setSecretKey("test-secret-key-value-0123456789abcdef");
        config.setRegion("us-east-1");
        return config;
    }

    /** A source whose settings and revision a test changes. */
    static final class MutableSource implements StorageConfigurationSource {
        private volatile StorageProperties.S3StorageConfig config;
        private final AtomicLong revision = new AtomicLong();
        volatile RuntimeException failure;

        MutableSource(StorageProperties.S3StorageConfig config) {
            this.config = config;
        }

        void replace(StorageProperties.S3StorageConfig next) {
            this.config = next;
            revision.incrementAndGet();
        }

        long bump() {
            return revision.incrementAndGet();
        }

        @Override
        public StorageProperties.S3StorageConfig s3() {
            if (failure != null) {
                throw failure;
            }
            return config;
        }

        @Override
        public long revision() {
            if (failure != null) {
                throw failure;
            }
            return revision.get();
        }
    }

    /** Hands out a new fake client per build and keeps them all. */
    static final class RecordingFactory implements S3ClientFactory {
        final List<FakeS3Client> clients = Collections.synchronizedList(new ArrayList<>());
        final List<S3Presigner> presigners = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger builds = new AtomicInteger();
        private final Supplier<S3Presigner> presignerSupplier;

        RecordingFactory(Supplier<S3Presigner> presignerSupplier) {
            this.presignerSupplier = presignerSupplier;
        }

        @Override
        public S3Client createClient(StorageProperties.S3StorageConfig config) {
            builds.incrementAndGet();
            FakeS3Client client = new FakeS3Client();
            clients.add(client);
            return client;
        }

        @Override
        public S3Presigner createPresigner(StorageProperties.S3StorageConfig config) {
            S3Presigner presigner = presignerSupplier.get();
            presigners.add(presigner);
            return presigner;
        }

        FakeS3Client latest() {
            return clients.get(clients.size() - 1);
        }
    }

    static S3FileStorageService service(StorageConfigurationSource source, S3ClientFactory factory) {
        return new S3FileStorageService(source, new FileProperties(), null, factory);
    }
}
