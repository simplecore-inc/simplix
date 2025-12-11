# 빠른 시작 가이드

이 가이드에서는 SimpliX Encryption 모듈을 5분 만에 설정하고 사용하는 방법을 설명합니다.

## 1. 의존성 추가

```gradle
// build.gradle
dependencies {
    implementation 'dev.simplecore:simplix-encryption:${version}'
}
```

## 2. 설정

### 개발 환경 (가장 간단한 설정)

```yaml
# application.yml
spring:
  profiles:
    active: dev

simplix:
  encryption:
    enabled: true
    provider: simple
    static-key: my-dev-key-at-least-16-chars
```

### 운영 환경 (Vault 사용)

```yaml
# application-prod.yml
simplix:
  encryption:
    provider: vault
    vault:
      enabled: true
      path: secret/encryption

# Vault 연결 설정
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      token: ${VAULT_TOKEN}
```

## 3. Entity 필드 암호화

JPA Entity의 민감한 필드에 `@Convert` 어노테이션을 추가합니다:

```java
import dev.simplecore.simplix.encryption.persistence.converter.AesEncryptionConverter;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;  // 암호화하지 않음

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email", length = 500)  // 암호화된 데이터는 길이가 증가함
    private String email;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "phone_number", length = 500)
    private String phoneNumber;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "resident_number", length = 500)
    private String residentNumber;

    // getters, setters...
}
```

> **주의**: 암호화된 데이터는 원본보다 길어집니다. 컬럼 길이를 충분히 확보하세요.
> - 원본 대비 약 2~3배 길이 증가 (Base64 인코딩 + 버전 + IV)

## 4. Service에서 직접 사용

JPA Converter 외에도 Service에서 직접 암호화/복호화할 수 있습니다:

```java
import dev.simplecore.simplix.encryption.service.EncryptionService;

@Service
@RequiredArgsConstructor
public class SecureDataService {

    private final EncryptionService encryptionService;

    /**
     * 데이터 암호화
     */
    public String encrypt(String plainText) {
        EncryptionService.EncryptedData result = encryptionService.encrypt(plainText);
        return result.getData();  // "v123:iv:ciphertext" 형태
    }

    /**
     * 데이터 복호화
     */
    public String decrypt(String encryptedData) {
        return encryptionService.decrypt(encryptedData);
    }

    /**
     * 암호화 여부 확인
     */
    public boolean isEncrypted(String data) {
        return encryptionService.isEncrypted(data);
    }

    /**
     * 키 버전 확인
     */
    public String getKeyVersion(String encryptedData) {
        return encryptionService.getKeyVersion(encryptedData);
    }
}
```

## 5. 동작 확인

### 테스트 코드

```java
@SpringBootTest
class EncryptionServiceTest {

    @Autowired
    private EncryptionService encryptionService;

    @Test
    void encryptAndDecrypt() {
        // Given
        String originalData = "민감한 개인정보";

        // When
        String encrypted = encryptionService.encrypt(originalData).getData();
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertThat(encrypted).isNotEqualTo(originalData);
        assertThat(decrypted).isEqualTo(originalData);
        assertThat(encryptionService.isEncrypted(encrypted)).isTrue();
    }
}
```

### 로그 확인

애플리케이션 시작 시 다음과 같은 로그가 출력됩니다:

```
INFO  ✔ SimpliX Encryption module initialized
INFO  ✔ KeyProvider configuration initialized
INFO    Active profiles: dev
INFO    Expected KeyProvider: SimpleKeyProvider (default)
INFO  ✔ Creating SimpleKeyProvider bean (dev/test/default profile active)
WARN  ⚠ SimpleKeyProvider initialized - DO NOT USE IN PRODUCTION
INFO  ✔ Static key version: static-v1
```

## 6. DB 저장 결과 확인

암호화된 데이터는 다음과 같이 DB에 저장됩니다:

```sql
SELECT id, username, email FROM users;
```

| id | username | email |
|----|----------|-------|
| 1 | john | static-v1:dGVzdGl2MTIzNDU2Nzg5MDEy:ZW5jcnlwdGVkZW1haWxAZXhhbXBsZS5jb20... |

- `static-v1`: 사용된 키 버전
- 두 번째 부분: IV (Base64)
- 세 번째 부분: 암호문 (Base64)

## 다음 단계

- [KeyProvider 가이드](./key-providers.md): 환경에 맞는 KeyProvider 선택
- [설정 레퍼런스](./configuration.md): 전체 설정 옵션 확인
- [보안 모범 사례](./security-best-practices.md): 운영 환경 보안 권장사항