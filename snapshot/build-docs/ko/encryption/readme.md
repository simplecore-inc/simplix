# SimpliX Encryption Module

Spring Boot 애플리케이션을 위한 AES-256 기반 데이터 암호화 모듈입니다.

## Features

- ✔ **AES-256-GCM 암호화** - 업계 표준 대칭키 암호화
- ✔ **다중 KeyProvider** - Simple, Configurable, Managed, Vault
- ✔ **JPA 자동 암호화** - AttributeConverter 기반 투명한 암호화
- ✔ **키 로테이션** - 자동/수동 키 교체 지원
- ✔ **버전 관리** - 암호화 키 버전 추적 및 하위 호환성
- ✔ **레거시 데이터 처리** - 기존 평문 데이터 자동 인식

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-encryption:${version}'

    // Optional: HashiCorp Vault 사용 시
    implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
}
```

### 2. Configuration

```yaml
simplix:
  encryption:
    enabled: true
    provider: simple
    static-key: my-dev-key-at-least-16-chars
```

### 3. Usage

**JPA Entity 필드 암호화:**

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;  // 암호화하지 않음

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email", length = 500)
    private String email;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "phone_number", length = 500)
    private String phoneNumber;
}
```

**Service에서 직접 사용:**

```java
@Service
@RequiredArgsConstructor
public class SecureDataService {

    private final EncryptionService encryptionService;

    public String encrypt(String plainText) {
        return encryptionService.encrypt(plainText).getData();
    }

    public String decrypt(String encryptedData) {
        return encryptionService.decrypt(encryptedData);
    }

    public boolean isEncrypted(String data) {
        return encryptionService.isEncrypted(data);
    }
}
```

## KeyProviders

| Provider | 용도 | 키 저장소 | 로테이션 |
|----------|------|----------|----------|
| SimpleKeyProvider | 개발/테스트 | 메모리 | 불가 |
| ConfigurableKeyProvider | 설정 기반 | YAML | 수동 (재시작) |
| ManagedKeyProvider | 파일 기반 | 로컬 파일 | 자동/수동 |
| VaultKeyProvider | 운영 환경 | HashiCorp Vault | 자동/수동 |

## Configuration

```yaml
simplix:
  encryption:
    enabled: true
    provider: simple              # simple, configurable, managed, vault

    # SimpleKeyProvider (개발용)
    static-key: my-dev-key

    # ConfigurableKeyProvider (다중 키)
    configurable:
      current-version: v2
      keys:
        v1:
          key: "Base64EncodedKey=="
          deprecated: true
        v2:
          key: "AnotherBase64Key=="
          deprecated: false

    # ManagedKeyProvider (파일 기반)
    key-store-path: /var/simplix/encryption/keys
    master-key: ${ENCRYPTION_MASTER_KEY}
    salt: ${ENCRYPTION_SALT}

    # VaultKeyProvider (운영)
    vault:
      enabled: true
      path: secret/encryption

    # 키 로테이션
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
```

## Encrypted Data Format

```
{version}:{iv}:{ciphertext}

예시: v1734567890123:dGVzdGl2ZGF0YTE=:ZW5jcnlwdGVkY29udGVudA==
```

## Documentation

- [Overview (아키텍처 상세)](ko/encryption/overview.md)
- [KeyProvider Guide (환경별 설정)](ko/encryption/key-providers.md)
- [JPA Converter (Entity 암호화)](ko/encryption/jpa-converter.md)
- [Key Rotation (키 교체)](ko/encryption/key-rotation.md)
- [Security Best Practices (보안 권장사항)](ko/encryption/security-best-practices.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
