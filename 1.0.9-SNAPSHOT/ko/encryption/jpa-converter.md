# JPA Converter 사용법

`AesEncryptionConverter`를 사용하면 JPA Entity의 필드를 자동으로 암호화/복호화할 수 있습니다.

## 기본 사용법

### 1. Entity 필드에 적용

```java
import dev.simplecore.simplix.encryption.persistence.converter.AesEncryptionConverter;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;  // 암호화하지 않음

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email", length = 500)
    private String email;  // 자동 암호화

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "phone_number", length = 500)
    private String phoneNumber;  // 자동 암호화

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "resident_number", length = 500)
    private String residentNumber;  // 자동 암호화

    // getters, setters...
}
```

### 2. 동작 원리

```
저장 시 (Entity → DB):
  user.setEmail("user@example.com")
        ↓
  AesEncryptionConverter.convertToDatabaseColumn()
        ↓
  DB: "v1:iv:ciphertext" 저장

조회 시 (DB → Entity):
  DB: "v1:iv:ciphertext"
        ↓
  AesEncryptionConverter.convertToEntityAttribute()
        ↓
  user.getEmail() → "user@example.com"
```

## 컬럼 길이 설정

암호화된 데이터는 원본보다 길어집니다. 충분한 컬럼 길이를 설정하세요.

### 길이 계산

```
암호화된 길이 ≈ (원본 길이 × 1.5) + 버전 길이 + IV 길이 + 구분자
            ≈ (원본 길이 × 1.5) + 30 ~ 50 bytes
```

### 권장 컬럼 길이

| 원본 최대 길이 | 권장 컬럼 길이 |
|--------------|--------------|
| 50자 | 200 |
| 100자 | 300 |
| 255자 | 500 |
| 1000자 | 2000 |
| TEXT | TEXT (제한 없음) |

```java
@Convert(converter = AesEncryptionConverter.class)
@Column(name = "email", length = 500)  // VARCHAR(500)
private String email;

@Convert(converter = AesEncryptionConverter.class)
@Column(name = "notes", columnDefinition = "TEXT")  // TEXT 타입
private String notes;
```

## 레거시 데이터 처리

`AesEncryptionConverter`는 암호화되지 않은 기존 데이터를 자동으로 처리합니다.

### 동작 방식

```java
// DB에서 읽을 때
String dbValue = "user@example.com";  // 암호화되지 않은 레거시 데이터

// convertToEntityAttribute() 내부:
if (!encryptionService.isEncrypted(dbValue)) {
    return dbValue;  // 원본 그대로 반환
}
return encryptionService.decrypt(dbValue);
```

### 점진적 마이그레이션

레거시 데이터는 다음 업데이트 시 자동으로 암호화됩니다:

```java
// 1. 레거시 데이터 조회
User user = userRepository.findById(1L).get();
// user.getEmail() = "user@example.com" (복호화 없이 그대로)

// 2. 데이터 수정 후 저장
user.setPhoneNumber("010-1234-5678");
userRepository.save(user);
// email도 암호화되어 저장됨 (dirty checking으로 전체 필드 업데이트)
```

## 이중 암호화 방지

`AesEncryptionConverter`는 이미 암호화된 데이터의 이중 암호화를 방지합니다:

```java
// convertToDatabaseColumn() 내부:
if (encryptionService.isEncrypted(plainText)) {
    return plainText;  // 이미 암호화되어 있으면 그대로 반환
}
return encryptionService.encrypt(plainText).getData();
```

## 고급 사용법

### 1. 조건부 암호화

특정 조건에서만 암호화하려면 커스텀 Converter를 생성하세요:

```java
@Converter
@Component
public class ConditionalEncryptionConverter implements AttributeConverter<String, String> {

    private static EncryptionService encryptionService;

    @Autowired
    public void setEncryptionService(EncryptionService service) {
        ConditionalEncryptionConverter.encryptionService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        // 특정 패턴은 암호화하지 않음
        if (attribute.startsWith("PUBLIC:")) {
            return attribute;
        }

        return encryptionService.encrypt(attribute).getData();
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        if (dbData.startsWith("PUBLIC:")) {
            return dbData;
        }

        if (!encryptionService.isEncrypted(dbData)) {
            return dbData;
        }

        return encryptionService.decrypt(dbData);
    }
}
```

### 2. JSON 필드 암호화

JSON 필드 전체를 암호화할 수 있습니다:

```java
@Entity
public class UserPreferences {

    @Id
    private Long id;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferencesJson;  // JSON 문자열 전체 암호화
}
```

### 3. 검색을 위한 해시 필드

암호화된 필드는 검색이 불가능합니다. 검색이 필요한 경우 별도 해시 필드를 사용하세요:

```java
@Entity
public class User {

    @Id
    private Long id;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email", length = 500)
    private String email;

    // 검색용 해시 (SHA-256)
    @Column(name = "email_hash", length = 64, unique = true)
    private String emailHash;

    public void setEmail(String email) {
        this.email = email;
        this.emailHash = hashEmail(email);
    }

    private String hashEmail(String email) {
        // 이메일을 소문자로 정규화 후 SHA-256 해싱
        String normalized = email.toLowerCase().trim();
        return DigestUtils.sha256Hex(normalized);
    }
}
```

**검색 시:**
```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    default Optional<User> findByEmail(String email) {
        String hash = DigestUtils.sha256Hex(email.toLowerCase().trim());
        return findByEmailHash(hash);
    }
}
```

## 주의사항

### 1. 인덱스 사용 불가

암호화된 컬럼에는 인덱스를 사용할 수 없습니다. 동일한 원본 값이라도 매번 다른 IV로 암호화되어 다른 결과가 생성됩니다.

```java
// 잘못된 사용
@Convert(converter = AesEncryptionConverter.class)
@Column(name = "email", unique = true)  // unique 제약 무의미
@Index(name = "idx_email")  // 인덱스 무의미
private String email;

// 올바른 사용 - 해시 필드 활용
@Convert(converter = AesEncryptionConverter.class)
@Column(name = "email", length = 500)
private String email;

@Column(name = "email_hash", length = 64, unique = true)
@Index(name = "idx_email_hash")
private String emailHash;
```

### 2. 정렬 불가

암호화된 값으로는 정렬할 수 없습니다.

```java
// 의미 없는 정렬
userRepository.findAll(Sort.by("email"));  // 암호화된 값 기준 정렬
```

### 3. LIKE 검색 불가

```java
// 작동하지 않음
@Query("SELECT u FROM User u WHERE u.email LIKE %:keyword%")
List<User> searchByEmail(@Param("keyword") String keyword);
```

### 4. 트랜잭션 고려

암호화/복호화 실패 시 예외가 발생하므로 트랜잭션 롤백이 필요합니다:

```java
@Transactional
public void updateUser(Long userId, String newEmail) {
    User user = userRepository.findById(userId)
        .orElseThrow();
    user.setEmail(newEmail);  // 암호화 실패 시 트랜잭션 롤백
    userRepository.save(user);
}
```

## 디버깅

### 암호화 확인

```java
@Autowired
private EncryptionService encryptionService;

public void debugEncryption(String value) {
    log.info("Is encrypted: {}", encryptionService.isEncrypted(value));
    log.info("Key version: {}", encryptionService.getKeyVersion(value));

    if (encryptionService.isEncrypted(value)) {
        String decrypted = encryptionService.decrypt(value);
        log.info("Decrypted length: {}", decrypted.length());
    }
}
```

### DB 직접 조회

```sql
-- 암호화된 데이터 확인
SELECT id, email FROM users LIMIT 10;

-- 결과 예시:
-- id: 1
-- email: v1734567890123:dGVzdGl2MTIz:ZW5jcnlwdGVkZW1haWw=
```

## 마이그레이션 스크립트

기존 평문 데이터를 일괄 암호화하려면:

```java
@Service
@RequiredArgsConstructor
public class EncryptionMigrationService {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public void migrateUsers(int batchSize) {
        int offset = 0;
        List<User> users;

        do {
            users = userRepository.findAll(
                PageRequest.of(offset / batchSize, batchSize)
            ).getContent();

            for (User user : users) {
                // dirty checking으로 자동 업데이트
                // AesEncryptionConverter가 저장 시 암호화 수행
                user.setEmail(user.getEmail());
            }

            userRepository.flush();
            offset += batchSize;

            log.info("Migrated {} users", offset);
        } while (!users.isEmpty());
    }
}
```

---

## Related Documents

- [Overview (개요)](overview.md) - 아키텍처 및 설정
- [KeyProvider 가이드](key-providers.md) - 환경별 KeyProvider 상세 설정
- [키 로테이션 가이드](key-rotation.md) - 키 교체 및 데이터 마이그레이션
- [보안 모범 사례](security-best-practices.md) - 운영 환경 보안 권장사항
```