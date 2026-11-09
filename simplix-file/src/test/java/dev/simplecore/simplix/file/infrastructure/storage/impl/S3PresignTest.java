package dev.simplecore.simplix.file.infrastructure.storage.impl;

import dev.simplecore.simplix.file.config.StorageProperties;
import dev.simplecore.simplix.file.infrastructure.storage.PresignedUpload;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.MutableSource;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3TestSupport.RecordingFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Presigning is computed locally with the real AWS SDK presigner and fixed fake credentials, so the
 * shape of every URL is checked without a network.
 */
@DisplayName("S3FileStorageService presigned URLs")
class S3PresignTest {

    private StorageProperties.S3StorageConfig config;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        config = S3TestSupport.completeConfig();
        service = newService(config);
    }

    private static S3FileStorageService newService(StorageProperties.S3StorageConfig config) {
        DefaultS3ClientFactory real = new DefaultS3ClientFactory();
        RecordingFactory factory = new RecordingFactory(() -> real.createPresigner(config));
        return S3TestSupport.service(new MutableSource(config), factory);
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = URI.create(url).getRawQuery();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            params.put(
                URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static List<String> signedHeaders(String url) {
        return Arrays.asList(query(url).get("X-Amz-SignedHeaders").split(";"));
    }

    @Nested
    @DisplayName("createDirectUpload()")
    class CreateDirectUpload {

        @Test
        @DisplayName("Should build a PUT URL on the bucket path with the lifetime and the key of a normal store")
        void shouldBuildPutUrl() {
            PresignedUpload upload = service.createDirectUpload(
                "releases", "Setup-1.2.0.exe", "application/vnd.microsoft.portable-executable",
                123_456_789L, Duration.ofMinutes(30));

            assertThat(service.supportsDirectUpload()).isTrue();
            assertThat(upload.method()).isEqualTo("PUT");
            assertThat(upload.storedPath())
                .matches("releases/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.exe");
            URI uri = URI.create(upload.url());
            assertThat(uri.getScheme()).isEqualTo("http");
            assertThat(uri.getHost()).isEqualTo("localhost");
            assertThat(uri.getPort()).isEqualTo(9000);
            assertThat(uri.getPath()).isEqualTo("/test-bucket/" + upload.storedPath());
            Map<String, String> params = query(upload.url());
            assertThat(params).containsEntry("X-Amz-Expires", "1800")
                .containsEntry("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            assertThat(upload.expiresAt())
                .isBetween(Instant.now().plusSeconds(1790), Instant.now().plusSeconds(1810));
        }

        @Test
        @DisplayName("Should sign content-length and content-type so the bucket refuses any other body")
        void shouldSignContentLengthAndType() {
            PresignedUpload upload = service.createDirectUpload(
                "releases", "model.bin", "application/octet-stream", 4096, Duration.ofMinutes(10));

            assertThat(signedHeaders(upload.url())).contains("host", "content-length", "content-type");
            assertThat(upload.headers())
                .containsEntry("content-type", "application/octet-stream")
                .containsEntry("content-length", "4096")
                .doesNotContainKey("host");
            assertThat(upload.headers().keySet()).allMatch(name -> name.equals(name.toLowerCase()));
            // Every signed header except host has to be one the client is told to send.
            for (String signed : signedHeaders(upload.url())) {
                if (!"host".equals(signed)) {
                    assertThat(upload.headers()).as("signed header %s is listed for the client", signed)
                        .containsKey(signed);
                }
            }
        }

        @Test
        @DisplayName("Should never put the secret key or the signing key material in the URL")
        void shouldNotLeakSecret() {
            PresignedUpload upload = service.createDirectUpload(
                "releases", "a.bin", "application/zip", 10, Duration.ofMinutes(5));

            assertThat(upload.url()).doesNotContain(config.getSecretKey());
            assertThat(upload.headers().toString()).doesNotContain(config.getSecretKey());
            assertThat(query(upload.url()).get("X-Amz-Credential")).startsWith(config.getAccessKey() + "/");
        }

        @Test
        @DisplayName("Should give two tickets two different keys and signatures")
        void shouldGiveDistinctKeys() {
            PresignedUpload a = service.createDirectUpload("r", "a.bin", null, 10, Duration.ofMinutes(5));
            PresignedUpload b = service.createDirectUpload("r", "a.bin", null, 10, Duration.ofMinutes(5));

            assertThat(a.storedPath()).isNotEqualTo(b.storedPath());
            assertThat(query(a.url()).get("X-Amz-Signature")).isNotEqualTo(query(b.url()).get("X-Amz-Signature"));
        }

        @Test
        @DisplayName("Should default the content type to application/octet-stream")
        void shouldDefaultContentType() {
            PresignedUpload upload = service.createDirectUpload("r", "a", null, 10, Duration.ofMinutes(5));

            assertThat(upload.headers()).containsEntry("content-type", "application/octet-stream");
            assertThat(upload.storedPath()).doesNotContain("..");
        }

        @Test
        @DisplayName("Should refuse arguments no valid ticket can have")
        void shouldRefuseBadArguments() {
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "x/y", -1, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "x/y", 6L * 1024 * 1024 * 1024, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "x/y", 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "x/y", 1, Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "x/y", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.createDirectUpload("r", "a", "text/plain\r\nX-Evil: 1", 1, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getDownloadUrl()")
    class GetDownloadUrl {

        @Test
        @DisplayName("Should sign a GET with the lifetime the caller chose")
        void shouldUseCallerLifetime() {
            String url = service.getDownloadUrl("releases/2026/11/09/a.exe", Duration.ofSeconds(90), null);

            assertThat(URI.create(url).getPath()).isEqualTo("/test-bucket/releases/2026/11/09/a.exe");
            assertThat(query(url)).containsEntry("X-Amz-Expires", "90")
                .doesNotContainKey("response-content-disposition");
        }

        @Test
        @DisplayName("Should use the configured minutes when the caller gives no lifetime")
        void shouldUseConfiguredDefault() {
            config.setPresignedUrlExpiration(15);
            S3FileStorageService fresh = newService(config);

            assertThat(query(fresh.getDownloadUrl("a/b.txt", null, null))).containsEntry("X-Amz-Expires", "900");
            assertThat(query(fresh.getPublicUrl("a/b.txt"))).containsEntry("X-Amz-Expires", "900");
        }

        @Test
        @DisplayName("Should ask the bucket to answer with a Content-Disposition for a plain name")
        void shouldAddDisposition() {
            String url = service.getDownloadUrl("a/b.exe", Duration.ofMinutes(5), "StageVoice Setup.exe");

            assertThat(query(url).get("response-content-disposition"))
                .isEqualTo("attachment; filename=\"StageVoice Setup.exe\"; filename*=UTF-8''StageVoice%20Setup.exe");
        }

        @Test
        @DisplayName("Should percent-encode a Korean name and give an ASCII fallback")
        void shouldEncodeNonAscii() {
            String name = "스테이지보이스 1.0.exe";
            String url = service.getDownloadUrl("a/b.exe", Duration.ofMinutes(5), name);

            String disposition = query(url).get("response-content-disposition");
            assertThat(disposition).startsWith("attachment; filename=\"")
                .contains("filename*=UTF-8''%EC%8A%A4%ED%85%8C%EC%9D%B4%EC%A7%80%EB%B3%B4%EC%9D%B4%EC%8A%A4%201.0.exe");
            assertThat(disposition.chars().allMatch(c -> c >= 0x20 && c < 0x7f)).isTrue();
        }

        @Test
        @DisplayName("Should neutralise quotes, line breaks and path separators in the name")
        void shouldNeutraliseHostileName() {
            String url = service.getDownloadUrl("a/b.exe", Duration.ofMinutes(5),
                "evil\"; filename=\"x.sh\r\nSet-Cookie: a=b/../c\\d.exe");

            String disposition = query(url).get("response-content-disposition");
            assertThat(disposition).doesNotContain("\r").doesNotContain("\n").doesNotContain("/").doesNotContain("\\");
            assertThat(disposition).startsWith("attachment; filename=\"");
            // The quoted part holds exactly one string: the only quotes left are the two that frame it.
            assertThat(disposition.chars().filter(c -> c == '"').count()).isEqualTo(2);
            assertThat(disposition).contains("filename*=UTF-8''evil%22%3B%20filename%3D%22x.sh");
            assertThat(disposition.indexOf("filename*=")).isEqualTo(disposition.lastIndexOf("filename*="));
        }

        @Test
        @DisplayName("Should return the unsigned prefix URL and ignore lifetime and name when a prefix is set")
        void shouldReturnPrefixUrlUnchanged() {
            config.setPublicUrlPrefix("https://cdn.example.com/files");
            S3FileStorageService fresh = newService(config);

            assertThat(fresh.getDownloadUrl("a/b.exe", Duration.ofMinutes(5), "x.exe"))
                .isEqualTo("https://cdn.example.com/files/a/b.exe");
            assertThat(fresh.getPublicUrl("a/b.exe")).isEqualTo("https://cdn.example.com/files/a/b.exe");
        }

        @Test
        @DisplayName("Should refuse a lifetime SigV4 cannot express")
        void shouldRefuseBadLifetime() {
            assertThatThrownBy(() -> service.getDownloadUrl("a", Duration.ofDays(8), null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.getDownloadUrl("a", Duration.ofSeconds(-1), null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
