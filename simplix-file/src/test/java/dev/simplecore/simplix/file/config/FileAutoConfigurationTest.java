package dev.simplecore.simplix.file.config;

import dev.simplecore.simplix.file.infrastructure.storage.FileStorageService;
import dev.simplecore.simplix.file.infrastructure.storage.StorageConfigurationSource;
import dev.simplecore.simplix.file.infrastructure.storage.impl.S3FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileAutoConfiguration")
class FileAutoConfigurationTest {

    @Test
    @DisplayName("Should be annotated with @AutoConfiguration")
    void shouldBeAnnotatedWithAutoConfiguration() {
        assertThat(FileAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class)).isTrue();
    }

    @Test
    @DisplayName("Should be conditional on simplix.file.enabled property")
    void shouldBeConditionalOnSimplixFileEnabledProperty() {
        ConditionalOnProperty annotation =
            FileAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("simplix.file");
        assertThat(annotation.name()).containsExactly("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("Should enable configuration properties for all property classes")
    void shouldEnableConfigurationPropertiesForAllPropertyClasses() {
        EnableConfigurationProperties annotation =
            FileAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactlyInAnyOrder(
            StorageProperties.class,
            FileProperties.class,
            ImageProperties.class
        );
    }

    @Test
    @DisplayName("Should scan correct base packages")
    void shouldScanCorrectBasePackages() {
        ComponentScan annotation =
            FileAutoConfiguration.class.getAnnotation(ComponentScan.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackages()).containsExactlyInAnyOrder(
            "dev.simplecore.simplix.file.config",
            "dev.simplecore.simplix.file.infrastructure"
        );
    }

    @Test
    @DisplayName("Should be instantiable")
    void shouldBeInstantiable() {
        FileAutoConfiguration config = new FileAutoConfiguration();
        assertThat(config).isNotNull();
    }

    @Nested
    @DisplayName("StorageConfigurationSource bean")
    class SourceBean {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class));

        @Test
        @DisplayName("Should provide the properties backed source by default")
        void shouldProvideDefaultSource() {
            runner.withPropertyValues(
                "simplix.file.storage.s3.bucket=from-properties",
                "simplix.file.storage.s3.multipart-part-size=16MB"
            ).run(context -> {
                StorageConfigurationSource source = context.getBean(StorageConfigurationSource.class);

                assertThat(source).isInstanceOf(PropertiesStorageConfigurationSource.class);
                assertThat(source.s3().getBucket()).isEqualTo("from-properties");
                assertThat(source.s3().getMultipartPartSize()).isEqualTo(DataSize.ofMegabytes(16));
                assertThat(source.revision()).isEqualTo(source.revision());
            });
        }

        @Test
        @DisplayName("Should back off when the application declares its own source")
        void shouldBackOffForApplicationSource() {
            runner.withBean(StorageConfigurationSource.class, FileAutoConfigurationTest::ownSource).run(context -> {
                assertThat(context).hasSingleBean(StorageConfigurationSource.class);
                assertThat(context.getBean(StorageConfigurationSource.class).s3().getBucket())
                    .isEqualTo("from-database");
            });
        }

        @Test
        @DisplayName("Should start the S3 service with an application source and no S3 properties at all")
        void shouldStartUnconfiguredS3Service() {
            runner.withPropertyValues("simplix.file.storage.provider=s3").run(context -> {
                assertThat(context).hasNotFailed();
                FileStorageService storage = context.getBean(FileStorageService.class);

                assertThat(storage).isInstanceOf(S3FileStorageService.class);
                assertThat(storage.isConfigured()).isFalse();
            });
        }

        @Test
        @DisplayName("Should let the S3 service follow an application source once it is complete")
        void shouldFollowApplicationSource() {
            runner.withPropertyValues("simplix.file.storage.provider=s3")
                .withBean(StorageConfigurationSource.class, FileAutoConfigurationTest::ownSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStorageService.class).isConfigured()).isTrue();
                });
        }
    }

    private static StorageConfigurationSource ownSource() {
        StorageProperties.S3StorageConfig config = new StorageProperties.S3StorageConfig();
        config.setBucket("from-database");
        config.setAccessKey("AKIAFROMDATABASE000");
        config.setSecretKey("secret-from-database-0123456789");
        config.setEndpoint("http://localhost:9");
        return new StorageConfigurationSource() {
            @Override
            public StorageProperties.S3StorageConfig s3() {
                return config;
            }

            @Override
            public long revision() {
                return 42;
            }
        };
    }
}
