# Image Guide

이미지 처리 및 최적화 가이드입니다.

## ImageProcessingService

Java AWT 기반의 이미지 처리 서비스입니다. 외부 이미지 라이브러리 없이 순수 Java로 구현되어 있습니다.

### 지원 형식

| 형식 | 읽기 | 쓰기 | 비고 |
|------|-----|-----|------|
| JPEG | ✔ | ✔ | 품질 조절 가능 |
| PNG | ✔ | ✔ | 투명도 지원 |
| GIF | ✔ | ✔ | 애니메이션 미지원 |
| BMP | ✔ | ✔ | - |
| WebP | ✔ | ✔ | webp-imageio 필요 |

---

## 이미지 설정

```yaml
simplix:
  file:
    image:
      default-max-width: 2048              # 기본 최대 너비
      default-max-height: 2048             # 기본 최대 높이
      default-quality: 85                  # JPEG 품질 (1-100)
      max-file-size: 10MB                  # 이미지 최대 크기
      allowed-formats:
        - image/jpeg
        - image/png
        - image/gif
        - image/webp
      thumbnail:
        default-sizes: [64, 128, 256]
        default-quality: 80
        max-dimension: 512
      optimization:
        enable-webp-conversion: true
        webp-quality: 80
        webp-lossless: false
        min-size-for-optimization: 10240   # 10KB
```

---

## 리사이징

### resizeIfExceeds()

최대 크기를 초과하는 경우에만 리사이징합니다.

```java
@Autowired
private ImageProcessingService imageService;

public ProcessedImage resizeImage(InputStream input) {
    // 2048x2048을 초과하면 리사이징, 아니면 원본 반환
    return imageService.resizeIfExceeds(input, 2048, 2048);
}
```

### resize()

지정된 크기로 리사이징합니다.

```java
// 비율 유지 리사이징 (기본)
ProcessedImage result = imageService.resize(input, 800, 600, true);

// 비율 무시 리사이징 (늘리기)
ProcessedImage stretched = imageService.resize(input, 800, 600, false);
```

### 비율 유지 동작

`preserveAspectRatio=true`인 경우:
- 원본 비율을 유지하면서 지정된 경계 내에 맞춤
- 가로 또는 세로 중 긴 쪽을 기준으로 축소

```
원본: 1920x1080 (16:9)
요청: 800x800

결과: 800x450 (16:9 유지, 가로 기준)
```

---

## 썸네일 생성

### generateThumbnail()

중앙 크롭 방식으로 썸네일을 생성합니다.

```java
// 128x128 정사각형 썸네일
ProcessedImage thumbnail = imageService.generateThumbnail(input, 128, 128);

// 직사각형 썸네일
ProcessedImage banner = imageService.generateThumbnail(input, 400, 300);
```

### 크롭 동작

중앙을 기준으로 크롭하여 지정된 비율에 맞춥니다:

```
원본: 1920x1080 (가로)
요청: 128x128 (정사각형)

1. 중앙 영역 크롭: 1080x1080
2. 리사이징: 128x128
```

### 썸네일 캐싱

`FileStorageService.getThumbnail()`을 사용하면 자동으로 캐싱됩니다:

```java
@Autowired
private FileStorageService storageService;

// 첫 호출: 썸네일 생성 후 캐시에 저장
Resource thumbnail1 = storageService.getThumbnail("path/to/image.jpg", 128, 128);

// 두 번째 호출: 캐시에서 반환
Resource thumbnail2 = storageService.getThumbnail("path/to/image.jpg", 128, 128);
```

**Local Storage 캐시 구조:**
```
.thumbnails/
└── 128x128/
    └── path/
        └── to/
            └── image.jpg
```

**S3 Storage 캐시 구조:**
```
thumbnails/128x128_path_to_image.jpg
```

### 썸네일 URL

```java
// 썸네일 URL 생성
String url = storageService.getThumbnailUrl("2024/12/15/image.jpg", 128, 128);

// Local: /files/thumbnails/128x128/2024/12/15/image.jpg
// S3: https://bucket.../thumbnails/128x128_2024_12_15_image.jpg
```

---

## WebP 변환

### WebP 장점

- JPEG 대비 25-34% 용량 감소 (동일 품질)
- PNG 대비 26% 용량 감소 (무손실)
- 투명도 지원
- 애니메이션 지원

### WebP 의존성

```gradle
dependencies {
    // WebP 지원 (Apple Silicon arm64 포함)
    implementation 'org.sejda.imageio:webp-imageio:0.1.6'
}
```

### WebP 설정

```yaml
simplix:
  file:
    image:
      optimization:
        enable-webp-conversion: true       # WebP 변환 활성화
        webp-quality: 80                   # 품질 (1-100)
        webp-lossless: false               # 무손실 압축
        min-size-for-optimization: 10240   # 최소 크기 (bytes)
```

### convertToWebp()

이미지를 WebP 형식으로 변환합니다.

```java
// 손실 압축 (기본)
ProcessedImage webp = imageService.convertToWebp(input, 80, false);

// 무손실 압축
ProcessedImage webpLossless = imageService.convertToWebp(input, 100, true);
```

### optimizeAndConvertToWebp()

리사이징과 WebP 변환을 한 번에 수행합니다.

```java
// 최대 1920x1080, 품질 80으로 최적화
ProcessedImage optimized = imageService.optimizeAndConvertToWebp(
    input, 1920, 1080, 80
);
```

### 자동 WebP 변환

`FileProcessingService`는 조건에 따라 자동으로 WebP 변환을 수행합니다:

**변환 조건:**
1. `enable-webp-conversion: true`
2. WebP 라이터 사용 가능 (`isWebpSupported()`)
3. 변환 가능한 MIME 타입 (jpeg, png, bmp)
4. 파일 크기가 `min-size-for-optimization` 이상

```java
// 자동 변환 확인
if (fileProcessingService.shouldOptimizeToWebp(file)) {
    log.info("WebP optimization will be applied");
}

// 변환 후 결과 확인
ProcessedFileResult result = fileProcessingService.processAndStore(request);
if (result.wasOptimized()) {
    log.info("Converted from {} to {}",
        result.originalMimeType(), result.mimeType());
    // "Converted from image/jpeg to image/webp"
}
```

### WebP 지원 확인

```java
if (imageService.isWebpSupported()) {
    log.info("WebP encoding is available");
} else {
    log.warn("WebP encoding not available - webp-imageio library may be missing");
}
```

---

## 메타데이터 추출

### extractMetadata()

이미지의 메타데이터를 추출합니다.

```java
ImageMetadata metadata = imageService.extractMetadata(input);

log.info("Size: {}x{}", metadata.width(), metadata.height());
log.info("Format: {}", metadata.format());  // "JPEG", "PNG", etc.
log.info("Aspect ratio: {}", metadata.getAspectRatio());
```

### ImageMetadata

```java
public record ImageMetadata(
    int width,
    int height,
    String format
) {
    // 가로형 여부
    public boolean isLandscape() {
        return width > height;
    }

    // 세로형 여부
    public boolean isPortrait() {
        return height > width;
    }

    // 정사각형 여부
    public boolean isSquare() {
        return width == height;
    }

    // 가로세로 비율 (width/height)
    public double getAspectRatio() {
        return (double) width / height;
    }
}
```

---

## ProcessedImage

이미지 처리 결과를 담는 레코드입니다.

```java
public record ProcessedImage(
    byte[] data,       // 이미지 바이트 데이터
    String mimeType,   // MIME 타입 (image/webp 등)
    int width,         // 너비
    int height         // 높이
) {}
```

### 사용 예시

```java
ProcessedImage result = imageService.resize(input, 800, 600, true);

// 저장
Files.write(Path.of("output.jpg"), result.data());

// InputStream으로 변환
InputStream stream = new ByteArrayInputStream(result.data());

// MIME 타입 확인
String contentType = result.mimeType();  // "image/jpeg"
```

---

## 코드 예제

### 이미지 업로드 with 리사이징

```java
@PostMapping("/upload/image")
public ImageResponse uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
    // 이미지인지 확인
    if (!imageService.isImage(file.getContentType())) {
        throw new IllegalArgumentException("Not an image file");
    }

    // 지원 형식인지 확인
    if (!imageService.isSupported(file.getContentType())) {
        throw new IllegalArgumentException("Unsupported image format");
    }

    // 메타데이터 추출
    ImageMetadata metadata = imageService.extractMetadata(file.getInputStream());
    log.info("Original size: {}x{}", metadata.width(), metadata.height());

    // 리사이징 및 저장
    ProcessedFileResult result = fileProcessingService.processAndStoreImage(
        file, 1920, 1080
    );

    return ImageResponse.builder()
        .storedPath(result.getStoredPath())
        .originalSize(metadata.width() + "x" + metadata.height())
        .finalSize(result.width() + "x" + result.height())
        .mimeType(result.mimeType())
        .wasOptimized(result.wasOptimized())
        .build();
}
```

### 커스텀 썸네일 생성

```java
@GetMapping("/thumbnail/{size}")
public ResponseEntity<byte[]> getThumbnail(
    @PathVariable int size,
    @RequestParam String path
) throws IOException {
    // 크기 제한
    if (size > 512) {
        throw new IllegalArgumentException("Max thumbnail size is 512");
    }

    Resource original = storageService.retrieve(path);
    ProcessedImage thumbnail = imageService.generateThumbnail(
        original.getInputStream(), size, size
    );

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(thumbnail.mimeType()))
        .body(thumbnail.data());
}
```

### WebP 변환 API

```java
@PostMapping("/convert/webp")
public ResponseEntity<byte[]> convertToWebp(
    @RequestParam("file") MultipartFile file,
    @RequestParam(defaultValue = "80") int quality
) throws IOException {
    if (!imageService.isWebpSupported()) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(null);
    }

    ProcessedImage webp = imageService.convertToWebp(
        file.getInputStream(), quality, false
    );

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("image/webp"))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + changeExtension(file.getOriginalFilename(), "webp") + "\"")
        .body(webp.data());
}
```

### 이미지 최적화 파이프라인

```java
@Service
@RequiredArgsConstructor
public class ImageOptimizationService {

    private final ImageProcessingService imageService;
    private final FileStorageService storageService;
    private final ImageProperties imageProperties;

    public OptimizedImage optimize(MultipartFile file) throws IOException {
        InputStream input = file.getInputStream();

        // 1. 메타데이터 추출
        ImageMetadata metadata = imageService.extractMetadata(input);
        input = file.getInputStream();  // 스트림 재생성

        // 2. 리사이징 필요 여부 확인
        boolean needsResize = imageProperties.needsResize(
            metadata.width(), metadata.height()
        );

        // 3. WebP 변환 여부 확인
        boolean shouldConvertToWebp = imageService.isWebpSupported()
            && imageProperties.getOptimization().canConvertToWebp(file.getContentType())
            && file.getSize() >= imageProperties.getOptimization().getMinSizeForOptimization();

        // 4. 최적화 수행
        ProcessedImage result;
        if (shouldConvertToWebp) {
            result = imageService.optimizeAndConvertToWebp(
                input,
                imageProperties.getDefaultMaxWidth(),
                imageProperties.getDefaultMaxHeight(),
                imageProperties.getOptimization().getWebpQuality()
            );
        } else if (needsResize) {
            result = imageService.resizeIfExceeds(
                input,
                imageProperties.getDefaultMaxWidth(),
                imageProperties.getDefaultMaxHeight()
            );
        } else {
            // 최적화 불필요
            return OptimizedImage.unchanged(file);
        }

        return OptimizedImage.builder()
            .data(result.data())
            .mimeType(result.mimeType())
            .width(result.width())
            .height(result.height())
            .originalWidth(metadata.width())
            .originalHeight(metadata.height())
            .originalMimeType(file.getContentType())
            .wasResized(needsResize)
            .wasConverted(shouldConvertToWebp)
            .build();
    }
}
```

---

## 성능 고려사항

### 메모리 사용

- 대용량 이미지는 메모리를 많이 사용합니다
- `max-file-size`를 적절히 설정하세요
- 썸네일 캐싱을 활성화하여 반복 생성을 방지하세요

### 처리 시간

| 작업 | 1000x1000 이미지 | 4000x4000 이미지 |
|------|----------------|-----------------|
| 리사이징 | ~50ms | ~200ms |
| 썸네일 생성 | ~30ms | ~150ms |
| WebP 변환 | ~100ms | ~400ms |

### 권장 사항

1. **비동기 처리**: 대용량 이미지는 비동기로 처리
2. **썸네일 캐싱**: 항상 활성화
3. **WebP 변환**: 적절한 `min-size-for-optimization` 설정
4. **크기 제한**: `default-max-width/height`로 상한선 설정

---

## Related Documents

- [Overview (아키텍처 상세)](./overview.md) - 모듈 구조 및 설정
- [Storage Guide (저장소 설정)](./storage-guide.md) - Local/S3 Provider 설정
- [Upload Guide (파일 업로드)](./upload-guide.md) - 파일 업로드 및 처리
