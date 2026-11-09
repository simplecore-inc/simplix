package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import dev.simplecore.simplix.file.infrastructure.storage.StoredObjectInfo;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.MutableSource;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.RecordingFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("S3FileStorageService describe()")
class S3DescribeTest {

    private RecordingFactory factory;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties.S3StorageConfig config = S3TestSupport.completeConfig();
        config.setMultipartPartSize(DataSize.ofMegabytes(5));
        factory = new RecordingFactory(() -> mock(S3Presigner.class));
        service = S3TestSupport.service(new MutableSource(config), factory);
        service.init();
    }

    @Test
    @DisplayName("Should report size, type, time and the SHA-256 of an object stored in several parts")
    void shouldDescribeWithChecksum() {
        long size = 12L * 1024 * 1024 + 3;
        StoredFileInfo stored = service.store(new GeneratedBytes(size), "a.bin", "application/zip", "releases");

        StoredObjectInfo info = service.describe(stored.storedPath());

        assertThat(info.storedPath()).isEqualTo(stored.storedPath());
        assertThat(info.size()).isEqualTo(size);
        assertThat(info.contentType()).isEqualTo("application/zip");
        assertThat(info.lastModified()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(size)).isEqualTo(stored.checksum());
        assertThat(info.checksum()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Should read only the metadata, with one HEAD and no transfer, when no checksum is asked for")
    void shouldDescribeWithoutChecksum() {
        StoredFileInfo stored = service.store(new GeneratedBytes(100), "a.bin", "text/plain", "r");
        factory.latest().calls.clear();

        StoredObjectInfo info = service.describe(stored.storedPath(), false);

        assertThat(info.size()).isEqualTo(100);
        assertThat(info.checksum()).isNull();
        assertThat(factory.latest().calls).containsExactly("headObject");
    }

    @Test
    @DisplayName("Should throw PATH_NOT_FOUND for a missing object, with and without checksum")
    void shouldThrowNotFound() {
        for (boolean withChecksum : new boolean[]{true, false}) {
            assertThatThrownBy(() -> service.describe("missing/key.bin", withChecksum))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                    .isEqualTo(StorageException.StorageErrorCode.PATH_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("Should refuse an empty path")
    void shouldRefuseEmptyPath() {
        assertThatThrownBy(() -> service.describe(" "))
            .isInstanceOf(StorageException.class)
            .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                .isEqualTo(StorageException.StorageErrorCode.INVALID_PATH));
    }
}
