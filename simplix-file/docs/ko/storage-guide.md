# Storage Guide

파일 저장소 설정 및 사용 가이드입니다.

## 목차

- [Storage Provider 비교](#storage-provider-비교)
- [LocalFileStorageService](#localfilestorageservice)
- [S3FileStorageService](#s3filestorageservice)
- [S3 설정 소스와 런타임 변경](#s3-설정-소스와-런타임-변경)
- [대용량 업로드와 메모리](#대용량-업로드와-메모리)
- [브라우저 직접 업로드](#브라우저-직접-업로드)
- [다운로드 링크](#다운로드-링크)
- [객체 정보 읽기](#객체-정보-읽기)
- [FileStorageService API](#filestorageservice-api)
- [StoredFileInfo 상세](#storedfileinfo-상세)
- [환경별 권장 설정](#환경별-권장-설정)
- [Related Documents](#related-documents)

---

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
    implementation 'software.amazon.awssdk:s3'
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

S3 클라이언트를 만들 때(기동 시, 그리고 설정이 바뀌어 다시 만들 때) 버킷이 없으면 자동으로 생성합니다. 설정이 비어 있으면 클라이언트를 만들지 않으므로 버킷 확인도 하지 않습니다. 확인이나 생성에 실패해도 경고만 남기고 계속합니다.

```
INFO Bucket does not exist, creating: my-bucket
INFO Created bucket: my-bucket
```

### 인증 방식

액세스 키와 시크릿 키를 설정해야 합니다. 인스턴스 역할 같은 기본 자격 증명 체인은 사용하지 않습니다. 저장소 종류(`provider: s3`)는 프로퍼티로 정하고, 런타임에 바꿀 수 있는 것은 S3 연결 설정(엔드포인트, 키, 버킷, 리전 등)입니다.

---

## S3 설정 소스와 런타임 변경

S3 설정은 `StorageConfigurationSource`를 통해 읽습니다.

```java
public interface StorageConfigurationSource {
    StorageProperties.S3StorageConfig s3();   // 현재 설정. 비어 있어도 됩니다.
    long revision();                           // 설정이 달라지면 달라지는 값
}
```

기본 구현(`PropertiesStorageConfigurationSource`)은 `simplix.file.storage.s3.*`를 읽고 `revision()`이 항상 같습니다. 애플리케이션이 같은 타입의 빈을 등록하면 기본 구현은 물러납니다.

```java
@Bean
StorageConfigurationSource storageConfigurationSource(StorageSettingsRepository settings) {
    return new StorageConfigurationSource() {
        @Override
        public StorageProperties.S3StorageConfig s3() {
            StorageProperties.S3StorageConfig config = new StorageProperties.S3StorageConfig();
            config.setEndpoint(settings.endpoint());
            config.setBucket(settings.bucket());
            config.setAccessKey(settings.accessKey());   // 복호화는 소스가 맡습니다
            config.setSecretKey(settings.secretKey());
            return config;
        }

        @Override
        public long revision() {
            return settings.version();
        }
    };
}
```

규칙은 다음과 같습니다.

- `revision()`은 저장소 메서드를 호출할 때마다 불립니다. 쿼리를 하지 말고 메모리에 둔 카운터나 시각을 돌려줍니다.
- `s3()`는 `revision()`이 이전과 달라졌을 때만 불립니다. 값이 바뀌었는데 `revision()`이 그대로면 변경을 알아채지 못합니다.
- `s3()`가 돌려준 객체는 복사해서 쓰므로 소스가 자기 객체를 계속 수정해도 됩니다.
- 소스 구현은 스레드에 안전해야 합니다.

### 클라이언트 교체 규칙

`revision()`이 달라지면 다음 호출이 새 S3 클라이언트와 presigner를 만듭니다. 각 호출은 시작할 때 클라이언트를 하나 잡고 끝날 때 놓습니다. 교체된 이전 클라이언트는 그것을 잡고 있는 호출이 모두 끝나면 닫히고, 잡고 있는 호출이 없으면 바로 닫힙니다. 따라서 오래 걸리는 업로드는 시작할 때의 설정으로 끝까지 실행되며, 닫힌 클라이언트를 쓰는 호출은 없습니다.

### 설정이 비어 있는 상태

버킷, 액세스 키, 시크릿 키 중 하나라도 비어 있으면 아직 설정되지 않은 것으로 봅니다. 이때도 애플리케이션은 기동하고, 저장소를 쓰는 모든 메서드가 `StorageException`을 던집니다.

```java
try {
    storageService.exists(path);
} catch (StorageException e) {
    if (e.getErrorCode() == StorageException.StorageErrorCode.NOT_CONFIGURED) {
        // 메시지: "S3 storage is not configured: bucket, access-key"
    }
}

boolean ready = storageService.isConfigured();
```

메시지에는 비어 있는 설정 이름만 들어가고 값(시크릿 포함)은 들어가지 않습니다. 소스가 값을 읽다가 예외를 던져도 같은 `NOT_CONFIGURED`로 보고하며 원인 예외를 붙입니다. 설정이 채워지고 `revision()`이 바뀌면 다음 호출부터 정상 동작합니다.

---

## 대용량 업로드와 메모리

`store()`는 입력 스트림을 파트 하나 크기의 버퍼로 읽으며 올립니다. 파일 전체를 메모리에 올리지 않으므로 512MB 설치 파일도 메모리 사용량은 파트 크기를 넘지 않습니다.

```yaml
simplix:
  file:
    storage:
      s3:
        multipart-part-size: 8MB   # 기본값 8MB, 범위 5MB~512MB
```

- 스트림이 파트 하나보다 짧거나 정확히 한 파트면 요청 한 번(`putObject`)으로 올립니다.
- 더 길면 멀티파트 업로드로 파트마다 올리고 마지막에 `completeMultipartUpload`를 호출합니다.
- SHA-256은 올리면서 같이 계산하므로 `StoredFileInfo.checksum()`을 위해 파일을 다시 읽지 않습니다.
- 어느 단계에서든 실패하면 멀티파트 업로드를 취소(`abortMultipartUpload`)합니다. 취소가 실패해도 원래 예외는 가려지지 않고, 취소 실패는 원래 예외에 suppressed로 붙습니다.
- S3는 마지막 파트를 제외한 파트가 5MB 이상이어야 하므로 5MB보다 작게 설정하면 5MB로, 512MB보다 크게 설정하면 512MB로 조정합니다.
- 멀티파트 업로드는 최대 10,000파트입니다. 기본 8MB 파트로 약 80GB까지 올릴 수 있고, 더 큰 파일은 파트 크기를 키워야 합니다.

`retrieve()`는 아직 객체 전체를 바이트 배열로 읽어 돌려줍니다. 큰 파일을 내려받게 할 때는 아래 다운로드 링크를 사용합니다.

---

## 브라우저 직접 업로드

파일 바이트가 서버를 지나지 않도록 브라우저가 버킷에 직접 올리는 presigned PUT을 만듭니다.

```java
if (storageService.supportsDirectUpload()) {
    PresignedUpload ticket = storageService.createDirectUpload(
        "releases",                  // directory
        "Setup-1.2.0.exe",           // 확장자를 가져오는 원본 이름
        "application/octet-stream",  // 클라이언트가 보낼 Content-Type
        installerSize,               // 클라이언트가 보낼 정확한 바이트 수
        Duration.ofMinutes(30)       // 티켓 유효 시간, 최대 7일
    );

    // ticket.storedPath()  업로드가 끝난 뒤의 저장 경로
    // ticket.url()         요청을 보낼 주소
    // ticket.method()      "PUT"
    // ticket.headers()     클라이언트가 그대로 보내야 하는 헤더 (키는 소문자)
    // ticket.expiresAt()   티켓 만료 시각
}
```

- 키는 `store()`와 같은 형식(`{directory}/{yyyy/MM/dd}/{uuid}.{ext}`)으로 만듭니다.
- `content-type`과 `content-length`가 서명에 포함되므로 다른 길이나 다른 타입의 본문은 버킷이 거부합니다.
- `headers()`에는 `host`가 들어 있지 않습니다. 브라우저는 `content-length`도 스스로 정하므로 `content-length`를 설정하지 못해도 정상입니다. 나머지 헤더(`content-type`)는 반드시 같은 값으로 보냅니다.
- 버킷에 CORS 규칙(허용 메서드 `PUT`, 허용 헤더 `content-type`)이 있어야 브라우저가 올릴 수 있습니다.
- 한 번의 PUT으로 올리므로 5GB까지입니다.
- 업로드가 끝난 객체의 크기와 SHA-256은 `describe()`로 확인합니다.
- S3가 아닌 구현은 `supportsDirectUpload()`가 `false`이고, `createDirectUpload()`는 `UNSUPPORTED_OPERATION`을 던집니다.

---

## 다운로드 링크

```java
String url = storageService.getDownloadUrl(
    storedPath,
    Duration.ofMinutes(10),   // 이 링크의 유효 시간 (null이면 presigned-url-expiration 분)
    "StageVoice Setup.exe"    // 브라우저가 저장할 파일 이름 (null이면 응답 헤더를 바꾸지 않음)
);
```

- `public-url-prefix`가 없으면 유효 시간이 호출마다 정해지는 presigned GET 링크입니다. 파일 이름을 주면 응답에 `Content-Disposition: attachment; filename="..."; filename*=UTF-8''...`가 붙습니다.
- 파일 이름은 신뢰할 수 없는 입력으로 다룹니다. 제어 문자(줄바꿈 포함)는 제거하고, `/`와 `\`는 `_`로 바꾸며, 따옴표는 퍼센트 인코딩하거나 대체합니다. 한글 같은 비ASCII 문자는 `filename*`에 UTF-8 퍼센트 인코딩으로 들어가고 `filename`에는 ASCII 대체 이름이 들어갑니다.
- 유효 시간은 1초 이상 7일 이하입니다. 범위를 벗어나면 `IllegalArgumentException`입니다.
- `public-url-prefix`가 있으면 `prefix/storedPath`를 그대로 돌려줍니다. 이 링크는 서명되지 않으므로 유효 시간과 파일 이름이 적용되지 않습니다.
- 로컬 저장소는 공개 URL(`getPublicUrl`)을 돌려주며 같은 이유로 두 값이 적용되지 않습니다.
- `getPublicUrl(storedPath)`는 `getDownloadUrl(storedPath, null, null)`과 같습니다.
- `Content-Disposition` 값만 필요하면 `DownloadFileNames.attachment(name)`을 사용합니다.

---

## 객체 정보 읽기

```java
StoredObjectInfo info = storageService.describe(storedPath);
// info.size(), info.contentType(), info.lastModified(), info.checksum() (소문자 16진수 SHA-256)

StoredObjectInfo head = storageService.describe(storedPath, false);   // 메타데이터만, checksum은 null
```

- 체크섬을 요청하면 서버가 객체를 한 번 스트리밍으로 읽습니다. 버퍼 크기가 고정이므로 객체 크기와 무관하게 메모리를 거의 쓰지 않습니다. 크기와 체크섬은 실제로 읽은 바이트 기준이라 서로 같은 객체를 설명합니다.
- `false`를 주면 요청 한 번(HEAD)만 보내고 내용을 내려받지 않습니다.
- 객체가 없으면 `StorageException`(`PATH_NOT_FOUND`)입니다.
- 직접 업로드가 끝난 뒤 크기와 체크섬을 확인하는 용도로 씁니다.

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
) {
    // 파일명에서 확장자 추출 (점 제외, 소문자)
    public static String extractExtension(String filename) {
        // "image.jpg" -> "jpg"
        // "FILE.PNG" -> "png"
        // "noext" -> ""
    }
}
```

### 유틸리티 메서드

```java
// 파일명에서 확장자 추출
String ext1 = StoredFileInfo.extractExtension("profile.jpg");      // "jpg"
String ext2 = StoredFileInfo.extractExtension("DOCUMENT.PDF");     // "pdf"
String ext3 = StoredFileInfo.extractExtension("archive.tar.gz");   // "gz"
String ext4 = StoredFileInfo.extractExtension("noextension");      // ""
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
