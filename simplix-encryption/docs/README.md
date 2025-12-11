# SimpliX Encryption 모듈

SimpliX Encryption은 개인정보 및 민감 데이터의 암호화/복호화를 위한 통합 암호화 인프라를 제공합니다.

## 주요 기능

- **AES-GCM 256-bit 암호화**: 인증된 암호화(Authenticated Encryption)로 기밀성과 무결성 동시 보장
- **다중 환경 지원**: 개발(dev), 스테이징(staging), 운영(prod) 환경별 최적화된 KeyProvider
- **키 버전 관리**: 모든 암호문에 키 버전 포함, 이전 키로 암호화된 데이터 복호화 지원
- **키 로테이션**: 자동/수동 키 로테이션 지원 (VaultKeyProvider, ManagedKeyProvider)
- **JPA 통합**: AttributeConverter를 통한 투명한 암호화/복호화

## 문서 목록

| 문서 | 설명 |
|------|------|
| [빠른 시작 가이드](./quick-start.md) | 5분 만에 시작하기 |
| [KeyProvider 가이드](./key-providers.md) | 환경별 KeyProvider 선택 및 설정 |
| [설정 레퍼런스](./configuration.md) | 전체 설정 옵션 상세 설명 |
| [JPA Converter 사용법](./jpa-converter.md) | Entity 필드 자동 암호화 |
| [키 로테이션 가이드](./key-rotation.md) | 키 교체 및 데이터 마이그레이션 |
| [보안 모범 사례](./security-best-practices.md) | 운영 환경 보안 권장사항 |

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                      Application Layer                       │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │   Entity        │  │   Service                       │   │
│  │   @Convert      │  │   encryptionService.encrypt()   │   │
│  └────────┬────────┘  └────────────────┬────────────────┘   │
└───────────┼────────────────────────────┼────────────────────┘
            │                            │
            ▼                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Encryption Service                        │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  AES/GCM/NoPadding (256-bit)                        │    │
│  │  - encrypt(plainText) → version:iv:ciphertext       │    │
│  │  - decrypt(encryptedData) → plainText               │    │
│  └─────────────────────────┬───────────────────────────┘    │
└────────────────────────────┼────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      KeyProvider                             │
│  ┌──────────┐ ┌──────────────┐ ┌─────────┐ ┌─────────────┐  │
│  │ Simple   │ │ Configurable │ │ Managed │ │    Vault    │  │
│  │ (dev)    │ │ (config)     │ │ (file)  │ │   (prod)    │  │
│  └──────────┘ └──────────────┘ └─────────┘ └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 환경별 권장 KeyProvider

| 환경 | KeyProvider | 설명 |
|------|-------------|------|
| 로컬 개발 | SimpleKeyProvider | 설정 간단, 즉시 사용 가능 |
| 테스트 | SimpleKeyProvider | 고정 키로 테스트 재현성 보장 |
| 스테이징 | ConfigurableKeyProvider 또는 ManagedKeyProvider | 운영 환경 시뮬레이션 |
| 운영 | VaultKeyProvider | 중앙 집중식 키 관리, 감사 로그 |

## 의존성

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-encryption:${version}'
}
```

## 빠른 시작

### 1. 기본 설정 (개발 환경)

```yaml
# application.yml
simplix:
  encryption:
    enabled: true
    provider: simple
    static-key: my-development-key-32-characters!
```

### 2. Entity 필드 암호화

```java
@Entity
public class User {
    @Id
    private Long id;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email")
    private String email;  // DB에 암호화되어 저장

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "phone_number")
    private String phoneNumber;
}
```

### 3. Service에서 직접 사용

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final EncryptionService encryptionService;

    public String encryptSensitiveData(String data) {
        return encryptionService.encrypt(data).getData();
    }

    public String decryptSensitiveData(String encryptedData) {
        return encryptionService.decrypt(encryptedData);
    }
}
```

## 암호화 데이터 포맷

암호화된 데이터는 다음 포맷으로 저장됩니다:

```
{version}:{iv}:{ciphertext}

예시:
v1734567890123:dGVzdGl2ZGF0YTE=:ZW5jcnlwdGVkY29udGVudA==
│               │                 │
버전 식별자      IV (Base64)       암호문 (Base64)
```

- **version**: 암호화에 사용된 키의 버전
- **iv**: 초기화 벡터 (12 bytes, Base64 인코딩)
- **ciphertext**: 암호화된 데이터 (Base64 인코딩)

## 지원 및 기여

- 이슈 리포트: GitHub Issues
- 기여 가이드: CONTRIBUTING.md 참조