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

## File Categories

| 카테고리 | 확장자 |
|----------|--------|
| IMAGE | jpg, png, gif, webp, svg, bmp |
| VIDEO | mp4, webm, mov, avi, mkv |
| AUDIO | mp3, wav, ogg, flac, aac |
| DOCUMENT | pdf, doc, docx, xls, xlsx, ppt, pptx |
| ARCHIVE | zip, rar, 7z, tar, gz |

## Documentation

- [Overview (아키텍처 상세)](overview.md)
- [Storage Guide (저장소 설정)](storage-guide.md)
- [Upload Guide (파일 업로드)](upload-guide.md)
- [Image Guide (이미지 처리)](image-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
