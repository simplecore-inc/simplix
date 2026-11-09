package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.MutableSource;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.RecordingFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("S3FileStorageService runtime configuration")
class S3ConfigurationRebuildTest {

    private MutableSource source;
    private RecordingFactory factory;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        source = new MutableSource(S3TestSupport.completeConfig());
        factory = new RecordingFactory(() -> mock(S3Presigner.class));
        service = S3TestSupport.service(source, factory);
    }

    private static StorageProperties.S3StorageConfig configWithBucket(String bucket) {
        StorageProperties.S3StorageConfig config = S3TestSupport.completeConfig();
        config.setBucket(bucket);
        return config;
    }

    @Nested
    @DisplayName("revision")
    class Revision {

        @Test
        @DisplayName("Should build clients once while the revision stays the same")
        void shouldBuildOnceForOneRevision() {
            for (int i = 0; i < 5; i++) {
                service.exists("a/b.txt");
            }

            assertThat(factory.builds.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should rebuild on a new revision, close the old clients and use the new ones")
        void shouldRebuildOnNewRevision() {
            service.store(new ByteArrayInputStream(new byte[]{1}), "a.txt", "text/plain", "one");
            FakeS3Client first = factory.latest();

            source.replace(configWithBucket("other-bucket"));
            service.store(new ByteArrayInputStream(new byte[]{2}), "b.txt", "text/plain", "two");
            FakeS3Client second = factory.latest();

            assertThat(factory.builds.get()).isEqualTo(2);
            assertThat(second).isNotSameAs(first);
            assertThat(first.isClosed()).isTrue();
            assertThat(second.isClosed()).isFalse();
            assertThat(first.objects).hasSize(1);
            assertThat(second.objects).hasSize(1);
            assertThat(first.useAfterClose.get()).isZero();
        }

        @Test
        @DisplayName("Should read the bucket from the settings the client was built for")
        void shouldUseBucketOfCurrentSettings() {
            source.replace(configWithBucket("bucket-b"));

            assertThat(service.resolve().config().getBucket()).isEqualTo("bucket-b");
        }

        @Test
        @DisplayName("Should keep a call that started before the change on its own clients until it ends")
        void shouldFinishRunningCallOnOldClients() throws Exception {
            service.init();
            FakeS3Client old = factory.latest();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            InputStream blocking = new InputStream() {
                private int sent;

                @Override
                public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
                }

                @Override
                public int read(byte[] target, int offset, int len) throws IOException {
                    if (sent > 0) {
                        return -1;
                    }
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IOException("test timed out");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                    target[offset] = 7;
                    sent = 1;
                    return 1;
                }
            };

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<?> running = pool.submit(() -> service.store(blocking, "slow.bin", "text/plain", "slow"));
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

                source.replace(configWithBucket("other-bucket"));
                service.exists("anything");
                FakeS3Client replacement = factory.latest();

                assertThat(replacement).isNotSameAs(old);
                assertThat(old.isClosed()).as("old clients while a call still runs on them").isFalse();

                release.countDown();
                running.get(10, TimeUnit.SECONDS);

                assertThat(old.isClosed()).as("old clients after the call ended").isTrue();
                assertThat(old.objects).hasSize(1);
                assertThat(old.useAfterClose.get()).isZero();
                assertThat(replacement.isClosed()).isFalse();
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("Should never use a closed client while callers race a changing revision")
        void shouldNeverUseClosedClientUnderConcurrency() throws Exception {
            int callers = 8;
            int rounds = 400;
            ExecutorService pool = Executors.newFixedThreadPool(callers + 1);
            AtomicInteger failures = new AtomicInteger();
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int c = 0; c < callers; c++) {
                    futures.add(pool.submit(() -> {
                        go.await();
                        for (int i = 0; i < rounds; i++) {
                            try {
                                service.exists("k/" + i);
                                service.store(new ByteArrayInputStream(new byte[]{1, 2}), "x.bin", null, "c");
                            } catch (RuntimeException e) {
                                failures.incrementAndGet();
                            }
                        }
                        return null;
                    }));
                }
                futures.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < 100; i++) {
                        source.bump();
                        Thread.sleep(1);
                    }
                    return null;
                }));
                go.countDown();
                for (Future<?> future : futures) {
                    future.get(60, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(failures.get()).isZero();
            assertThat(factory.builds.get()).isGreaterThan(1);
            int useAfterClose = factory.clients.stream().mapToInt(c -> c.useAfterClose.get()).sum();
            assertThat(useAfterClose).isZero();

            service.cleanup();
            assertThat(factory.clients).allMatch(FakeS3Client::isClosed);
        }

        @Test
        @DisplayName("Should check the bucket when it builds clients and create it when missing")
        void shouldEnsureBucketOnBuild() {
            service.init();

            assertThat(factory.latest().calls).contains("headBucket");
        }
    }

    @Nested
    @DisplayName("not configured")
    class NotConfigured {

        @BeforeEach
        void emptySettings() {
            StorageProperties.S3StorageConfig empty = new StorageProperties.S3StorageConfig();
            empty.setSecretKey("SECRET-MUST-NOT-APPEAR");
            source = new MutableSource(empty);
            factory = new RecordingFactory(() -> mock(S3Presigner.class));
            service = S3TestSupport.service(source, factory);
        }

        @Test
        @DisplayName("Should start without throwing and build no client")
        void shouldStart() {
            assertThatCode(service::init).doesNotThrowAnyException();

            assertThat(service.isConfigured()).isFalse();
            assertThat(factory.builds.get()).isZero();
        }

        @Test
        @DisplayName("Should throw NOT_CONFIGURED naming what is missing and no secret from every operation")
        void shouldThrowFromEveryOperation() {
            List<Runnable> operations = List.of(
                () -> service.store(new ByteArrayInputStream(new byte[0]), "a.txt", "text/plain", "d"),
                () -> service.store(new ByteArrayInputStream(new byte[0]), "a.txt", "d"),
                () -> service.retrieve("a"),
                () -> service.delete("a"),
                () -> service.exists("a"),
                () -> service.getPublicUrl("a"),
                () -> service.getDownloadUrl("a", Duration.ofMinutes(5), "a.txt"),
                () -> service.createDirectUpload("d", "a.bin", "application/zip", 10, Duration.ofMinutes(5)),
                () -> service.describe("a"),
                () -> service.describe("a", false),
                () -> service.getThumbnail("a", 10, 10),
                () -> service.getThumbnailUrl("a", 10, 10)
            );

            for (Runnable operation : operations) {
                assertThatThrownBy(operation::run)
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("bucket")
                    .hasMessageContaining("access-key")
                    .hasMessageNotContaining("secret-key")
                    .hasMessageNotContaining("SECRET-MUST-NOT-APPEAR")
                    .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                        .isEqualTo(StorageException.StorageErrorCode.NOT_CONFIGURED));
            }
        }

        @Test
        @DisplayName("Should work after the settings are completed and the revision changes")
        void shouldWorkAfterConfiguration() {
            assertThat(service.isConfigured()).isFalse();

            source.replace(S3TestSupport.completeConfig());

            assertThat(service.isConfigured()).isTrue();
            service.store(new ByteArrayInputStream(new byte[]{1}), "a.txt", "text/plain", "d");
            assertThat(factory.latest().objects).hasSize(1);
        }

        @Test
        @DisplayName("Should go back to NOT_CONFIGURED when the settings are emptied again")
        void shouldBecomeUnconfiguredAgain() {
            source.replace(S3TestSupport.completeConfig());
            assertThat(service.isConfigured()).isTrue();
            FakeS3Client built = factory.latest();

            source.replace(new StorageProperties.S3StorageConfig());

            assertThat(service.isConfigured()).isFalse();
            assertThat(built.isClosed()).isTrue();
            assertThatThrownBy(() -> service.exists("a"))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.NOT_CONFIGURED));
        }

        @Test
        @DisplayName("Should report NOT_CONFIGURED, not crash, when the source itself fails")
        void shouldReportSourceFailure() {
            source.failure = new IllegalStateException("database down");

            assertThatCode(service::init).doesNotThrowAnyException();
            assertThat(service.isConfigured()).isFalse();
            assertThatThrownBy(() -> service.exists("a"))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.NOT_CONFIGURED));
        }

        @Test
        @DisplayName("Should treat a settings object without bucket, access key and secret as incomplete")
        void shouldListMissingSettings() {
            StorageProperties.S3StorageConfig config = new StorageProperties.S3StorageConfig();
            assertThat(config.missingSettings()).containsExactly("bucket", "access-key", "secret-key");

            config.setBucket("b");
            config.setAccessKey("  ");
            config.setSecretKey("s");
            assertThat(config.missingSettings()).containsExactly("access-key");

            config.setAccessKey("a");
            assertThat(config.missingSettings()).isEmpty();
        }
    }
}
