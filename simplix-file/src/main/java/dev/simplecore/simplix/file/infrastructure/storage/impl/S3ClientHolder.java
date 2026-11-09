package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The S3 client, presigner and settings built for one revision of the configuration.
 * <p>
 * Lifetime rule: every operation holds a lease on the holder it started with. When the
 * configuration changes the service installs a new holder and retires the old one; the old
 * clients are closed when the last lease on it is released, or at once when none is held. A call
 * that began before the change therefore finishes on the settings it started with, and no call
 * ever meets a closed client. A holder built for an unconfigured installation owns no clients and
 * carries the problems that make it unusable.
 */
@Slf4j
final class S3ClientHolder {

    private final long revision;
    private final StorageProperties.S3StorageConfig config;
    private final S3Client client;
    private final S3Presigner presigner;
    private final List<String> problems;

    private final AtomicInteger leases = new AtomicInteger();
    private volatile boolean retired;
    private final AtomicBoolean closed = new AtomicBoolean();

    private S3ClientHolder(long revision, StorageProperties.S3StorageConfig config,
                           S3Client client, S3Presigner presigner, List<String> problems) {
        this.revision = revision;
        this.config = config;
        this.client = client;
        this.presigner = presigner;
        this.problems = List.copyOf(problems);
    }

    static S3ClientHolder configured(long revision, StorageProperties.S3StorageConfig config,
                                     S3Client client, S3Presigner presigner) {
        return new S3ClientHolder(revision, config, client, presigner, List.of());
    }

    static S3ClientHolder unconfigured(long revision, StorageProperties.S3StorageConfig config,
                                       List<String> problems) {
        return new S3ClientHolder(revision, config, null, null, problems);
    }

    long revision() {
        return revision;
    }

    boolean isConfigured() {
        return problems.isEmpty();
    }

    List<String> problems() {
        return problems;
    }

    StorageProperties.S3StorageConfig config() {
        return config;
    }

    S3Client client() {
        return client;
    }

    S3Presigner presigner() {
        return presigner;
    }

    /**
     * Take a lease. Returns false when the holder was retired in the meantime; the caller then
     * resolves the current holder again.
     */
    boolean tryAcquire() {
        leases.incrementAndGet();
        if (retired) {
            release();
            return false;
        }
        return true;
    }

    void release() {
        if (leases.decrementAndGet() == 0 && retired) {
            closeResources();
        }
    }

    /**
     * Mark the holder as replaced. Its clients close when no lease is outstanding.
     */
    void retire() {
        retired = true;
        if (leases.get() == 0) {
            closeResources();
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    private void closeResources() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(client);
        closeQuietly(presigner);
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("Failed to close an S3 resource: {}", e.toString());
        }
    }
}
