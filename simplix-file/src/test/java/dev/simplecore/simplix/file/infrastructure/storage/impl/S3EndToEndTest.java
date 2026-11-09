package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.FileProperties;
import dev.simplecore.simplix.file.config.PropertiesStorageConfigurationSource;
import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.storage.PresignedUpload;
import dev.simplecore.simplix.file.infrastructure.storage.StoredFileInfo;
import dev.simplecore.simplix.file.infrastructure.storage.StoredObjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the S3 operations against a real S3 compatible server. It is skipped unless
 * {@code SIMPLIX_S3_E2E_ENDPOINT} is set, together with {@code SIMPLIX_S3_E2E_ACCESS_KEY} and
 * {@code SIMPLIX_S3_E2E_SECRET_KEY}. The bucket is created by the service and named per run.
 * <p>
 * Example with RustFS:
 * <pre>
 * docker run -d -p 127.0.0.1:19000:9000 -e RUSTFS_ACCESS_KEY=e2eaccesskey \
 *   -e RUSTFS_SECRET_KEY=e2esecretkey1234 -v /some/dir:/data rustfs/rustfs
 * SIMPLIX_S3_E2E_ENDPOINT=http://127.0.0.1:19000 SIMPLIX_S3_E2E_ACCESS_KEY=e2eaccesskey \
 *   SIMPLIX_S3_E2E_SECRET_KEY=e2esecretkey1234 ./gradlew :simplix-file:test --tests '*S3EndToEndTest*'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SIMPLIX_S3_E2E_ENDPOINT", matches = ".+")
@DisplayName("S3FileStorageService against a real S3 compatible server")
class S3EndToEndTest {

    private static final int MB = 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.S3StorageConfig s3 = properties.getS3();
        s3.setEndpoint(System.getenv("SIMPLIX_S3_E2E_ENDPOINT"));
        s3.setAccessKey(System.getenv("SIMPLIX_S3_E2E_ACCESS_KEY"));
        s3.setSecretKey(System.getenv("SIMPLIX_S3_E2E_SECRET_KEY"));
        s3.setBucket("e2e-" + UUID.randomUUID().toString().substring(0, 8));
        s3.setMultipartPartSize(DataSize.ofMegabytes(5));
        service = new S3FileStorageService(
            new PropertiesStorageConfigurationSource(properties), new FileProperties(), null, new DefaultS3ClientFactory());
        service.init();
    }

    @Test
    @DisplayName("Should store a multipart file and describe it with the same SHA-256")
    void shouldStoreMultipartAndDescribe() {
        long size = 12L * MB + 7;

        StoredFileInfo stored = service.store(new GeneratedBytes(size), "big.bin", "application/octet-stream", "e2e");
        StoredObjectInfo info = service.describe(stored.storedPath());

        assertThat(stored.checksum()).isEqualTo(GeneratedBytes.sha256Hex(size));
        assertThat(info.size()).isEqualTo(size);
        assertThat(info.checksum()).isEqualTo(stored.checksum());
        assertThat(service.describe(stored.storedPath(), false).size()).isEqualTo(size);
        assertThat(service.delete(stored.storedPath())).isTrue();
        assertThat(service.exists(stored.storedPath())).isFalse();
    }

    @Test
    @DisplayName("Should accept a presigned PUT of the signed length and refuse any other body")
    void shouldHonourPresignedPut() throws Exception {
        byte[] body = new byte[3 * MB + 5];
        for (int i = 0; i < body.length; i++) {
            body[i] = GeneratedBytes.byteAt(i);
        }
        PresignedUpload ticket = service.createDirectUpload(
            "e2e", "model.bin", "application/octet-stream", body.length, Duration.ofMinutes(5));

        HttpResponse<String> ok = put(ticket, body);

        assertThat(ok.statusCode()).as("PUT with the signed body: %s", ok.body()).isEqualTo(200);
        StoredObjectInfo info = service.describe(ticket.storedPath());
        assertThat(info.size()).isEqualTo(body.length);
        assertThat(info.checksum()).isEqualTo(GeneratedBytes.sha256Hex(body.length));

        PresignedUpload second = service.createDirectUpload(
            "e2e", "model.bin", "application/octet-stream", body.length, Duration.ofMinutes(5));
        HttpResponse<String> shorter = put(second, new byte[body.length - 1]);
        assertThat(shorter.statusCode()).as("a body of another length").isGreaterThanOrEqualTo(400);
        assertThat(service.exists(second.storedPath())).isFalse();

        PresignedUpload third = service.createDirectUpload(
            "e2e", "model.bin", "application/octet-stream", body.length, Duration.ofMinutes(5));
        HttpResponse<String> otherType = putWithHeader(third, body, "content-type", "text/plain");
        assertThat(otherType.statusCode()).as("a body of another content type").isGreaterThanOrEqualTo(400);
        assertThat(service.exists(third.storedPath())).isFalse();
    }

    @Test
    @DisplayName("Should answer a download URL with the chosen file name")
    void shouldDownloadUnderChosenName() throws Exception {
        StoredFileInfo stored = service.store(
            new ByteArrayInputStream("hello".getBytes()), "a.txt", "text/plain", "e2e");

        String url = service.getDownloadUrl(stored.storedPath(), Duration.ofMinutes(2),
            "\uC2A4\uD14C\uC774\uC9C0 \"v1\".txt");
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello");
        assertThat(response.headers().firstValue("content-disposition")).hasValueSatisfying(value ->
            assertThat(value).startsWith("attachment;")
                .contains("filename*=UTF-8''%EC%8A%A4%ED%85%8C%EC%9D%B4%EC%A7%80%20%22v1%22.txt"));
    }

    private HttpResponse<String> put(PresignedUpload ticket, byte[] body) throws Exception {
        return putWithHeader(ticket, body, null, null);
    }

    private HttpResponse<String> putWithHeader(PresignedUpload ticket, byte[] body, String overrideName,
                                               String overrideValue) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ticket.url()))
            .method(ticket.method(), HttpRequest.BodyPublishers.ofByteArray(body));
        ticket.headers().forEach((name, value) -> {
            // The HTTP client sets content-length from the body itself.
            if (!"content-length".equals(name) && !name.equals(overrideName)) {
                request.header(name, value);
            }
        });
        if (overrideName != null) {
            request.header(overrideName, overrideValue);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
