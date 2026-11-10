# SimpliX File Module

Spring Boot 애플리케이션을 위한 파일 업로드 및 이미지 처리 모듈입니다.

## Features

- ✔ **다중 Storage Provider** - Local filesystem, AWS S3, MinIO, RustFS
- ✔ **자동 이미지 최적화** - WebP 변환으로 용량 25-34% 감소
- ✔ **썸네일 자동 생성** - 중앙 크롭 방식, 캐싱 지원
- ✔ **파일 검증** - 크기, MIME 타입, 확장자 검증
- ✔ **SHA-256 체크섬** - 파일 무결성 검증
- ✔ **파일 카테고리 분류** - IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE
- ✔ **도메인 독립적** - 어떤 애플리케이션에서도 재사용 가능

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-file:${version}'

    // Optional: WebP 지원
    implementation 'org.sejda.imageio:webp-imageio:0.1.6'

    // Optional: S3 Storage
    implementation 'software.amazon.awssdk:s3:2.29.26'
}
```

### 2. Configuration

```yaml
simplix:
  file:
    enabled: true
    default-max-size: 10MB
    storage:
      provider: local
      local:
        base-path: ./uploads
        public-url-prefix: /files
    image:
      default-max-width: 2048
      default-max-height: 2048
      optimization:
        enable-webp-conversion: true
        webp-quality: 80
```

### 3. Usage

```java
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileProcessingService fileProcessingService;
    private final FileStorageService storageService;

    // 기본 파일 업로드
    public String uploadFile(MultipartFile file) {
        ProcessedFileResult result = fileProcessingService.processAndStore(
            FileProcessingRequest.of(file, "files")
        );

        return storageService.getPublicUrl(result.getStoredPath());
    }

    // 이미지 업로드 (리사이징 + WebP 최적화)
    public ImageInfo uploadImage(MultipartFile file) {
        ProcessedFileResult result = fileProcessingService.processAndStoreImage(
            file, 1920, 1080  // 최대 크기
        );

        return ImageInfo.builder()
            .url(storageService.getPublicUrl(result.getStoredPath()))
            .thumbnailUrl(storageService.getThumbnailUrl(result.getStoredPath(), 128, 128))
            .width(result.width())
            .height(result.height())
            .wasOptimized(result.wasOptimized())
            .build();
    }

    // 커스텀 옵션
    public String uploadWithOptions(MultipartFile file) {
        FileProcessingRequest request = FileProcessingRequest.builder(file)
            .directory("profiles")
            .maxFileSize(DataSize.ofMegabytes(5))
            .allowedMimeTypes(Set.of("image/jpeg", "image/png"))
            .maxWidth(512)
            .maxHeight(512)
            .enableWebpOptimization(true)
            .build();

        ProcessedFileResult result = fileProcessingService.processAndStore(request);
        return result.getStoredPath();
    }
}
```

## Storage Providers

| Provider | 용도 | 설정 |
|----------|------|------|
| Local | 개발/소규모 | `provider: local` |
| AWS S3 | Production | `provider: s3` |
| MinIO | Self-hosted S3 | `provider: s3` + `path-style-access: true` |
| RustFS | Self-hosted S3 | `provider: s3` + `path-style-access: true` |

## Configuration

```yaml
simplix:
  file:
    enabled: true
    default-max-size: 10MB
    max-files-per-request: 10
    checksum-algorithm: SHA-256
    allowed-mime-types:
      - image/jpeg
      - image/png
      - application/pdf
    storage:
      provider: local                      # local, s3
      local:
        base-path: ./uploads
        public-url-prefix: /files
      s3:
        endpoint: http://localhost:9000    # MinIO/RustFS
        access-key: ${S3_ACCESS_KEY}
        secret-key: ${S3_SECRET_KEY}
        bucket: my-bucket
        region: us-east-1
        path-style-access: true
        presigned-url-expiration: 60
        multipart-part-size: 8MB           # 5MB~512MB, 업로드 한 번에 메모리에 올리는 최대 크기
    image:
      default-max-width: 2048
      default-max-height: 2048
      default-quality: 85
      thumbnail:
        default-sizes: [64, 128, 256]
        default-quality: 80
      optimization:
        enable-webp-conversion: true
        webp-quality: 80
        min-size-for-optimization: 10240
    thumbnail:
      cache-path: ./uploads/.thumbnails
      cache-enabled: true
```

## S3 저장소 기능

S3 호환 저장소(`provider: s3`)는 아래 기능을 제공합니다. 자세한 설명과 예제는 [Storage Guide](ko/file/storage-guide.md)에 있습니다.

| 메서드 | 용도 |
|--------|------|
| `store(...)` | 스트림을 파트 크기 단위로 읽어 올립니다. 파일 전체를 메모리에 올리지 않습니다. |
| `supportsDirectUpload()` / `createDirectUpload(...)` | 브라우저가 서버를 거치지 않고 버킷에 직접 올리는 presigned PUT을 만듭니다. 길이와 Content-Type이 서명에 포함됩니다. |
| `describe(storedPath)` / `describe(storedPath, withChecksum)` | 객체의 크기, Content-Type, 수정 시각, SHA-256을 서버가 한 번 읽어 구합니다. |
| `getDownloadUrl(storedPath, validFor, downloadFileName)` | 호출마다 유효 시간과 저장될 파일 이름을 정한 다운로드 링크를 만듭니다. |
| `isConfigured()` | 저장소 설정이 갖춰졌는지 알려 줍니다. |

S3 설정은 `StorageConfigurationSource`가 제공합니다. 기본 구현은 `simplix.file.storage.s3.*`를 읽고 값이 바뀌지 않습니다. 운영자가 화면에서 설정을 바꾸는 애플리케이션은 이 인터페이스의 빈을 직접 등록해 기본 구현을 대체합니다.

```java
@Bean
StorageConfigurationSource storageConfigurationSource(StorageSettingsRepository settings) {
    return new StorageConfigurationSource() {
        @Override
        public StorageProperties.S3StorageConfig s3() {
            return settings.loadS3Config();      // 설정이 바뀐 뒤에만 호출됩니다
        }

        @Override
        public long revision() {
            return settings.version();           // 매 호출마다 불리므로 메모리 값이어야 합니다
        }
    };
}
```

`revision()` 값이 달라지면 다음 호출에서 S3 클라이언트를 다시 만듭니다. 진행 중이던 호출은 시작할 때의 설정으로 끝까지 실행되고, 그 호출이 모두 끝난 뒤에 이전 클라이언트를 닫습니다.

버킷이나 자격 증명이 비어 있어도 애플리케이션은 기동합니다. 이 상태에서 저장소를 쓰는 모든 메서드는 `StorageException`(`NOT_CONFIGURED`)을 던지고, 메시지에는 비어 있는 설정 이름(`bucket`, `access-key`, `secret-key`)만 들어갑니다. 값은 담기지 않습니다.

## File Categories

| 카테고리 | 확장자 |
|----------|--------|
| IMAGE | jpg, png, gif, webp, svg, bmp |
| VIDEO | mp4, webm, mov, avi, mkv |
| AUDIO | mp3, wav, ogg, flac, aac |
| DOCUMENT | pdf, doc, docx, xls, xlsx, ppt, pptx |
| ARCHIVE | zip, rar, 7z, tar, gz |

## Documentation

- [Overview (아키텍처 상세)](ko/file/overview.md)
- [Storage Guide (저장소 설정)](ko/file/storage-guide.md)
- [Upload Guide (파일 업로드)](ko/file/upload-guide.md)
- [Image Guide (이미지 처리)](ko/file/image-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
