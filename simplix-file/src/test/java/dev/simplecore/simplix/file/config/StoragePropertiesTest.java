package dev.simplecore.simplix.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StorageProperties")
class StoragePropertiesTest {

    @Test
    @DisplayName("Should have correct default provider")
    void shouldHaveCorrectDefaultProvider() {
        StorageProperties properties = new StorageProperties();

        assertThat(properties.getProvider()).isEqualTo("local");
    }

    @Test
    @DisplayName("Should have default local storage config")
    void shouldHaveDefaultLocalStorageConfig() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.LocalStorageConfig local = properties.getLocal();

        assertThat(local).isNotNull();
        assertThat(local.getBasePath()).isEqualTo("./uploads");
        assertThat(local.getPublicUrlPrefix()).isEqualTo("/files");
    }

    @Test
    @DisplayName("Should have default S3 storage config")
    void shouldHaveDefaultS3StorageConfig() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.S3StorageConfig s3 = properties.getS3();

        assertThat(s3).isNotNull();
        assertThat(s3.getRegion()).isEqualTo("us-east-1");
        assertThat(s3.isPathStyleAccess()).isTrue();
        assertThat(s3.getPresignedUrlExpiration()).isEqualTo(60);
        assertThat(s3.getEndpoint()).isNull();
        assertThat(s3.getAccessKey()).isNull();
        assertThat(s3.getSecretKey()).isNull();
        assertThat(s3.getBucket()).isNull();
        assertThat(s3.getPublicUrlPrefix()).isNull();
    }

    @Test
    @DisplayName("Should allow setting local config values")
    void shouldAllowSettingLocalConfigValues() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setBasePath("/data/uploads");
        properties.getLocal().setPublicUrlPrefix("https://cdn.example.com");

        assertThat(properties.getLocal().getBasePath()).isEqualTo("/data/uploads");
        assertThat(properties.getLocal().getPublicUrlPrefix()).isEqualTo("https://cdn.example.com");
    }

    @Test
    @DisplayName("Should allow setting S3 config values")
    void shouldAllowSettingS3ConfigValues() {
        StorageProperties properties = new StorageProperties();
        StorageProperties.S3StorageConfig s3 = properties.getS3();
        s3.setEndpoint("http://minio:9000");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setBucket("my-bucket");
        s3.setRegion("ap-northeast-2");
        s3.setPathStyleAccess(false);
        s3.setPublicUrlPrefix("https://cdn.example.com");
        s3.setPresignedUrlExpiration(120);

        assertThat(s3.getEndpoint()).isEqualTo("http://minio:9000");
        assertThat(s3.getAccessKey()).isEqualTo("access-key");
        assertThat(s3.getSecretKey()).isEqualTo("secret-key");
        assertThat(s3.getBucket()).isEqualTo("my-bucket");
        assertThat(s3.getRegion()).isEqualTo("ap-northeast-2");
        assertThat(s3.isPathStyleAccess()).isFalse();
        assertThat(s3.getPublicUrlPrefix()).isEqualTo("https://cdn.example.com");
        assertThat(s3.getPresignedUrlExpiration()).isEqualTo(120);
    }

    @Test
    @DisplayName("Should allow setting provider")
    void shouldAllowSettingProvider() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("s3");

        assertThat(properties.getProvider()).isEqualTo("s3");
    }

    @Test
    @DisplayName("Should default the multipart part size to 8 MB")
    void shouldDefaultMultipartPartSize() {
        StorageProperties.S3StorageConfig s3 = new StorageProperties().getS3();

        assertThat(s3.getMultipartPartSize()).isEqualTo(DataSize.ofMegabytes(8));
        assertThat(s3.effectivePartSizeBytes()).isEqualTo(8 * 1024 * 1024);
    }

    @Test
    @DisplayName("Should copy every S3 setting")
    void shouldCopyEverySetting() {
        StorageProperties.S3StorageConfig s3 = new StorageProperties.S3StorageConfig();
        s3.setEndpoint("http://e");
        s3.setAccessKey("a");
        s3.setSecretKey("s");
        s3.setBucket("b");
        s3.setRegion("r");
        s3.setPathStyleAccess(false);
        s3.setPublicUrlPrefix("p");
        s3.setPresignedUrlExpiration(7);
        s3.setMultipartPartSize(DataSize.ofMegabytes(9));

        StorageProperties.S3StorageConfig copy = s3.copy();
        s3.setBucket("changed");

        assertThat(copy.getBucket()).isEqualTo("b");
        assertThat(copy.getEndpoint()).isEqualTo("http://e");
        assertThat(copy.getAccessKey()).isEqualTo("a");
        assertThat(copy.getSecretKey()).isEqualTo("s");
        assertThat(copy.getRegion()).isEqualTo("r");
        assertThat(copy.isPathStyleAccess()).isFalse();
        assertThat(copy.getPublicUrlPrefix()).isEqualTo("p");
        assertThat(copy.getPresignedUrlExpiration()).isEqualTo(7);
        assertThat(copy.getMultipartPartSize()).isEqualTo(DataSize.ofMegabytes(9));
    }
}
