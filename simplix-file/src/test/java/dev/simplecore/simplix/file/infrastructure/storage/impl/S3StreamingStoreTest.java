package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.exception.StorageException;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.MutableSource;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.RecordingFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("S3FileStorageService streaming store")
class S3StreamingStoreTest {

    private static final int MB = 1024 * 1024;
    private static final int PART = 5 * MB;

    private RecordingFactory factory;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties.S3StorageConfig config = S3TestSupport.completeConfig();
        config.setMultipartPartSize(DataSize.ofBytes(PART));
        factory = new RecordingFactory(() -> mock(S3Presigner.class));
        service = S3TestSupport.service(new MutableSource(config), factory);
        service.init();
    }

    private FakeS3Client fake() {
        return factory.latest();
    }

    private StoredFileInfo store(GeneratedBytes source) {
        return service.store(source, "installer.bin", "application/octet-stream", "releases");
    }

    @Test
    @DisplayName("Should send a stream shorter than one part as a single request")
    void shouldSendShortStreamAsSingleRequest() {
        StoredFileInfo info = store(new GeneratedBytes(1000));

        assertThat(fake().calls).contains("putObject").doesNotContain("createMultipartUpload");
        assertThat(info.fileSize()).isEqualTo(1000);
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(1000));
        assertThat(fake().objects.get(info.storedPath()).data()).hasSize(1000);
    }

    @Test
    @DisplayName("Should send an empty stream as an empty object")
    void shouldSendEmptyStream() {
        StoredFileInfo info = store(new GeneratedBytes(0));

        assertThat(info.fileSize()).isZero();
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(0));
        assertThat(fake().objects.get(info.storedPath()).data()).isEmpty();
    }

    @Test
    @DisplayName("Should send a stream of exactly one part as a single request")
    void shouldSendExactlyOnePartAsSingleRequest() {
        StoredFileInfo info = store(new GeneratedBytes(PART));

        assertThat(fake().calls).contains("putObject").doesNotContain("createMultipartUpload");
        assertThat(info.fileSize()).isEqualTo(PART);
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(PART));
    }

    @Test
    @DisplayName("Should split a stream one byte over a part into a full part and a one byte part")
    void shouldSplitStreamOneByteOverPart() {
        StoredFileInfo info = store(new GeneratedBytes(PART + 1L));

        assertThat(fake().partLengths).containsExactly(PART, 1);
        assertThat(fake().calls).contains("completeMultipartUpload").doesNotContain("putObject");
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(PART + 1L));
    }

    @Test
    @DisplayName("Should not send an empty part when the stream ends on a part boundary")
    void shouldNotSendEmptyPartOnBoundary() {
        StoredFileInfo info = store(new GeneratedBytes(2L * PART));

        assertThat(fake().partLengths).containsExactly(PART, PART);
        assertThat(info.fileSize()).isEqualTo(2L * PART);
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(2L * PART));
    }

    @Test
    @DisplayName("Should keep what it holds below two parts and rebuild the exact bytes from 40 MB")
    void shouldBoundMemoryByPartSize() {
        long size = 40L * MB;
        GeneratedBytes source = new GeneratedBytes(size);
        // The fake reads the source's progress at every part.
        fake().sourceConsumed = source::consumed;

        StoredFileInfo info = store(source);

        assertThat(fake().partLengths).hasSize(8).allMatch(length -> length <= PART);
        // Bytes taken from the source but not yet sent, seen when each part goes out.
        assertThat(fake().outstandingAtPart).hasSize(8).allMatch(held -> held <= 2L * PART);
        assertThat(info.fileSize()).isEqualTo(size);
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(size));
        byte[] stored = fake().objects.get(info.storedPath()).data();
        assertThat(stored).hasSize((int) size);
        assertThat(stored[0]).isEqualTo(GeneratedBytes.byteAt(0));
        assertThat(stored[(int) size - 1]).isEqualTo(GeneratedBytes.byteAt(size - 1));
        assertThat(stored[PART + 17]).isEqualTo(GeneratedBytes.byteAt(PART + 17));
        assertThat(fake().openUploads()).isEmpty();
    }

    @Test
    @DisplayName("Should abort the multipart upload when a part fails and keep the original exception")
    void shouldAbortWhenPartFails() {
        SdkClientException partFailure = SdkClientException.create("part 3 refused");
        fake().beforePart = number -> {
            if (number == 3) {
                throw partFailure;
            }
        };

        assertThatThrownBy(() -> store(new GeneratedBytes(40L * MB)))
            .isInstanceOf(StorageException.class)
            .hasCause(partFailure)
            .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                .isEqualTo(StorageException.StorageErrorCode.WRITE_FAILED));

        assertThat(fake().aborted.get()).isEqualTo(1);
        assertThat(fake().openUploads()).isEmpty();
        assertThat(fake().objects).isEmpty();
        assertThat(fake().calls).doesNotContain("completeMultipartUpload");
    }

    @Test
    @DisplayName("Should not let a failed abort hide the original exception")
    void shouldKeepOriginalExceptionWhenAbortFails() {
        SdkClientException partFailure = SdkClientException.create("part 2 refused");
        SdkClientException abortFailure = SdkClientException.create("abort refused");
        fake().beforePart = number -> {
            if (number == 2) {
                throw partFailure;
            }
        };
        fake().beforeAbort = id -> {
            throw abortFailure;
        };

        assertThatThrownBy(() -> store(new GeneratedBytes(40L * MB)))
            .isInstanceOf(StorageException.class)
            .hasCause(partFailure);

        assertThat(partFailure.getSuppressed()).containsExactly(abortFailure);
    }

    @Test
    @DisplayName("Should abort when completing the upload fails")
    void shouldAbortWhenCompleteFails() {
        fake().beforeComplete = id -> {
            throw SdkClientException.create("complete refused");
        };

        assertThatThrownBy(() -> store(new GeneratedBytes(12L * MB)))
            .isInstanceOf(StorageException.class);

        assertThat(fake().aborted.get()).isEqualTo(1);
        assertThat(fake().objects).isEmpty();
    }

    @Test
    @DisplayName("Should abort when the source stream fails in the middle of a multipart upload")
    void shouldAbortWhenSourceFails() {
        assertThatThrownBy(() -> store(new GeneratedBytes(40L * MB, 12L * MB)))
            .isInstanceOf(StorageException.class)
            .hasCauseInstanceOf(IOException.class)
            .satisfies(e -> assertThat(((StorageException) e).getErrorCode())
                .isEqualTo(StorageException.StorageErrorCode.WRITE_FAILED));

        assertThat(fake().aborted.get()).isEqualTo(1);
        assertThat(fake().openUploads()).isEmpty();
        assertThat(fake().objects).isEmpty();
    }

    @Test
    @DisplayName("Should send the content type and the length on a single request")
    void shouldSendContentTypeOnSingleRequest() {
        StoredFileInfo info = service.store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "a.bin", "text/plain", null);

        assertThat(fake().objects.get(info.storedPath()).contentType()).isEqualTo("text/plain");
        assertThat(info.storedPath()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.bin");
    }

    @Test
    @DisplayName("Should move a part size under the S3 minimum up to it")
    void shouldClampPartSize() {
        StorageProperties.S3StorageConfig config = new StorageProperties.S3StorageConfig();
        config.setMultipartPartSize(DataSize.ofMegabytes(1));
        assertThat(config.effectivePartSizeBytes()).isEqualTo(5 * MB);

        config.setMultipartPartSize(DataSize.ofGigabytes(4));
        assertThat(config.effectivePartSizeBytes()).isEqualTo(512 * MB);

        config.setMultipartPartSize(DataSize.ofMegabytes(16));
        assertThat(config.effectivePartSizeBytes()).isEqualTo(16 * MB);
    }
}
