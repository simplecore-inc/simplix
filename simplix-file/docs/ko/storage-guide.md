# Storage Guide

파일 저장소 설정 및 사용 가이드입니다.

## Storage Provider 비교

| 특성 | Local | S3 |
|------|-------|-----|
| **용도** | 개발/소규모 | 운영 환경 |
| **확장성** | 제한적 | 무제한 |
| **비용** | 서버 저장소 | 사용량 기반 |
| **고가용성** | 단일 서버 | 내장 |
| **CDN 통합** | 수동 | 쉬움 |
| **설정 난이도** | 낮음 | 중간 |

---

## LocalFileStorageService

### 설정

```yaml
simplix:
  file:
    storage:
      provider: local
      local:
        base-path: ./uploads          # 저장 경로
        public-url-prefix: /files     # 공개 URL prefix
    thumbnail:
      cache-path: ./uploads/.thumbnails
      cache-enabled: true
```

### 파일 저장 구조

파일은 날짜 기반 디렉토리에 UUID 이름으로 저장됩니다:

```
uploads/
├── 2024/
│   └── 12/
│       └── 15/
│           ├── a1b2c3d4-5678-90ab-cdef-1234567890ab.jpg
│           └── b2c3d4e5-6789-01bc-defg-2345678901bc.pdf
├── images/
│   └── 2024/
│       └── 12/
│           └── c3d4e5f6-7890-12cd-efgh-3456789012cd.webp
└── .thumbnails/
    └── 128x128/
        └── a1b2c3d4-5678-90ab-cdef-1234567890ab.jpg
```

### 공개 URL 형식

```
/files/{storedPath}
예: /files/2024/12/15/a1b2c3d4-5678-90ab-cdef-1234567890ab.jpg
```

### 썸네일 URL 형식

```
/files/thumbnails/{width}x{height}/{storedPath}
예: /files/thumbnails/128x128/2024/12/15/a1b2c3d4.jpg
```

### 보안 고려사항

- 디렉토리 탐색 공격 방지를 위한 경로 정규화
- UUID 기반 파일명으로 예측 불가능
- 적절한 파일 시스템 권한 설정 권장

---

## S3FileStorageService

### 의존성 추가

```gradle
dependencies {
    implementation 'software.amazon.awssdk:s3:2.29.26'
}
```

### AWS S3 설정

```yaml
simplix:
  file:
    storage:
      provider: s3
      s3:
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}
        bucket: my-app-files
        region: ap-northeast-2
        path-style-access: false      # AWS S3는 virtual-hosted style
        public-url-prefix: https://my-app-files.s3.ap-northeast-2.amazonaws.com
```

### MinIO 설정

```yaml
simplix:
  file:
    storage:
      provider: s3
      s3:
        endpoint: http://localhost:9000
        access-key: minioadmin
        secret-key: minioadmin
        bucket: my-bucket
        region: us-east-1
        path-style-access: true       # MinIO는 path-style 필수
```

### RustFS 설정

```yaml
simplix:
  file:
    storage:
      provider: s3
      s3:
        endpoint: http://localhost:8333
        access-key: rustfs-access-key
        secret-key: rustfs-secret-key
        bucket: my-bucket
        region: us-east-1
        path-style-access: true       # RustFS는 path-style 필수
```

### Presigned URL 설정

공개 URL prefix가 설정되지 않으면 Presigned URL이 생성됩니다:

```yaml
simplix:
  file:
    storage:
      s3:
        # public-url-prefix 생략 시 presigned URL 사용
        presigned-url-expiration: 60  # 60분 후 만료
```

### 버킷 자동 생성

S3FileStorageService는 초기화 시 버킷이 없으면 자동으로 생성합니다.

```
INFO ℹ S3 bucket 'my-bucket' not found, creating...
INFO ✔ S3 bucket 'my-bucket' created successfully
```

---

## FileStorageService API

### store()

파일을 저장합니다.

```java
@Autowired
private FileStorageService storageService;

public void storeFile(MultipartFile file) throws IOException {
    // 기본 저장
    StoredFileInfo info = storageService.store(
        file.getInputStream(),
        file.getOriginalFilename(),
        "files"  // directory
    );

    log.info("Stored: {} -> {}", info.originalName(), info.storedPath());
    log.info("Checksum: {}", info.checksum());
}

public void storeWithMimeType(InputStream input, String filename, String mimeType) {
    // MIME 타입 직접 지정
    StoredFileInfo info = storageService.store(
        input,
        filename,
        mimeType,
        "documents"
    );
}
```

### retrieve()

저장된 파일을 조회합니다.

```java
public Resource getFile(String storedPath) {
    return storageService.retrieve(storedPath);
}

// Spring MVC에서 파일 다운로드
@GetMapping("/download")
public ResponseEntity<Resource> download(@RequestParam String path) {
    Resource resource = storageService.retrieve(path);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + resource.getFilename() + "\"")
        .body(resource);
}
```

### delete()

파일을 삭제합니다. 연관된 썸네일도 함께 삭제됩니다.

```java
public void deleteFile(String storedPath) {
    boolean deleted = storageService.delete(storedPath);

    if (deleted) {
        log.info("File deleted: {}", storedPath);
    } else {
        log.warn("File not found: {}", storedPath);
    }
}
```

### exists()

파일 존재 여부를 확인합니다.

```java
public boolean fileExists(String storedPath) {
    return storageService.exists(storedPath);
}
```

### getPublicUrl()

공개 URL을 생성합니다.

```java
public String getFileUrl(String storedPath) {
    return storageService.getPublicUrl(storedPath);
}

// Local: /files/2024/12/15/uuid.jpg
// S3 (public): https://bucket.s3.region.amazonaws.com/2024/12/15/uuid.jpg
// S3 (presigned): https://bucket.s3.region.amazonaws.com/2024/12/15/uuid.jpg?X-Amz-...
```

### getThumbnail() / getThumbnailUrl()

썸네일을 조회하거나 URL을 생성합니다.

```java
// 썸네일 Resource 조회 (캐시에 없으면 자동 생성)
Resource thumbnail = storageService.getThumbnail(storedPath, 128, 128);

// 썸네일 URL 생성
String thumbnailUrl = storageService.getThumbnailUrl(storedPath, 128, 128);
// Local: /files/thumbnails/128x128/2024/12/15/uuid.jpg
// S3: https://bucket.../thumbnails/128x128_uuid.jpg
```

---

## StoredFileInfo 상세

`StoredFileInfo`는 저장된 파일의 정보를 담는 불변 레코드입니다.

```java
public record StoredFileInfo(
    String originalName,   // 원본 파일명: "profile.jpg"
    String storedName,     // 저장 파일명: "a1b2c3d4-5678-90ab-cdef.jpg"
    String storedPath,     // 전체 경로: "2024/12/15/a1b2c3d4-5678-90ab-cdef.jpg"
    String mimeType,       // MIME 타입: "image/jpeg"
    Long fileSize,         // 파일 크기: 102400 (bytes)
    String extension,      // 확장자: "jpg"
    String checksum        // SHA-256: "abc123..."
) {}
```

### 체크섬 활용

파일 무결성 검증에 체크섬을 활용할 수 있습니다:

```java
public void verifyIntegrity(String storedPath, String expectedChecksum) {
    Resource resource = storageService.retrieve(storedPath);

    try (InputStream is = resource.getInputStream()) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }

        String actualChecksum = bytesToHex(digest.digest());
        if (!actualChecksum.equals(expectedChecksum)) {
            throw new RuntimeException("Checksum mismatch!");
        }
    }
}
```

---

## 환경별 권장 설정

### 개발 환경

```yaml
simplix:
  file:
    storage:
      provider: local
      local:
        base-path: ./dev-uploads
        public-url-prefix: /files
```

### 스테이징 환경 (MinIO)

```yaml
simplix:
  file:
    storage:
      provider: s3
      s3:
        endpoint: http://minio.staging:9000
        access-key: ${MINIO_ACCESS_KEY}
        secret-key: ${MINIO_SECRET_KEY}
        bucket: staging-files
        path-style-access: true
```

### 운영 환경 (AWS S3)

```yaml
simplix:
  file:
    storage:
      provider: s3
      s3:
        bucket: production-files
        region: ap-northeast-2
        path-style-access: false
        public-url-prefix: https://cdn.example.com
```

---

## Related Documents

- [Overview (아키텍처 상세)](./overview.md) - 모듈 구조 및 설정
- [Upload Guide (파일 업로드)](./upload-guide.md) - 파일 업로드 및 처리
- [Image Guide (이미지 처리)](./image-guide.md) - 이미지 리사이징, 썸네일, WebP
