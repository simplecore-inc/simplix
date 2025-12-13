# 보안 모범 사례

SimpliX Encryption 모듈을 안전하게 운영하기 위한 보안 권장사항입니다.

## 환경별 보안 요구사항

| 항목 | 개발 | 스테이징 | 운영 |
|------|------|---------|------|
| KeyProvider | Simple | Configurable/Managed | Vault |
| 키 로테이션 | 불필요 | 권장 | 필수 |
| 로테이션 주기 | - | 30일 | 90일 |
| 키 저장소 암호화 | 불필요 | 권장 | 필수 |
| 감사 로깅 | 불필요 | 권장 | 필수 |
| 접근 통제 | 기본 | IAM | IAM + MFA |

---

## 1. KeyProvider 선택

### 운영 환경에서는 VaultKeyProvider 필수

```yaml
# application-prod.yml
simplix:
  encryption:
    provider: vault  # 반드시 vault 사용
    vault:
      enabled: true
```

**SimpleKeyProvider를 운영에서 사용하면 안 되는 이유:**

- 정적 키로 키 로테이션 불가
- 키 노출 시 모든 데이터 위험
- 감사 로그 없음
- 다중 인스턴스 환경 미지원

### Vault 사용이 불가능한 경우

ConfigurableKeyProvider 또는 ManagedKeyProvider를 사용하되, 추가 보안 조치를 적용하세요:

```yaml
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: ${ENCRYPTION_CURRENT_VERSION}
      keys:
        v1:
          key: ${ENCRYPTION_KEY_V1}  # 환경 변수로 주입
```

**추가 보안 조치:**

- Kubernetes Secret 또는 외부 Secret Manager 사용
- 환경 변수는 암호화된 저장소에서 주입
- 정기적인 수동 키 로테이션 일정 수립

---

## 2. 키 관리

### 키 생성

**안전한 키 생성:**

```bash
# 암호학적으로 안전한 랜덤 키 생성
openssl rand -base64 32

# 또는 Java에서
SecureRandom random = new SecureRandom();
byte[] key = new byte[32];
random.nextBytes(key);
```

**금지 사항:**

- 예측 가능한 문자열 사용 금지 (`password123`, `company-name-key` 등)
- 온라인 키 생성기 사용 금지
- 같은 키를 여러 환경에서 사용 금지

### 키 저장

| 저장 위치 | 개발 | 스테이징 | 운영 |
|----------|------|---------|------|
| 코드 저장소 | 금지 | 금지 | 금지 |
| application.yml (평문) | 허용 | 금지 | 금지 |
| 환경 변수 | 허용 | 허용 (암호화) | 권장 (암호화) |
| Kubernetes Secret | - | 허용 | 권장 |
| HashiCorp Vault | - | 권장 | 필수 |
| AWS KMS/Azure Key Vault | - | 권장 | 권장 |

### 키 접근 통제

**최소 권한 원칙:**

```hcl
# Vault 정책 예시
path "secret/data/encryption/keys/*" {
  capabilities = ["read"]  # 읽기만 허용
}

# 키 로테이션 권한은 별도 정책으로 분리
path "secret/data/encryption/keys/current" {
  capabilities = ["read", "update"]
}
```

**접근 로깅:**

```yaml
# Vault 감사 로깅 활성화
vault audit enable file file_path=/var/log/vault/audit.log
```

---

## 3. 키 로테이션

### 로테이션 주기

| 데이터 유형 | 권장 주기 |
|------------|----------|
| 일반 개인정보 | 90일 |
| 금융 정보 | 30일 |
| 의료 정보 | 30일 |
| 인증 정보 | 30일 |

### 자동 로테이션 설정

```yaml
simplix:
  encryption:
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
    rotation-cron: "0 0 2 * * ?"  # 매일 새벽 2시
```

### 긴급 로테이션

키 노출이 의심되면 즉시 로테이션하세요:

```java
// 즉시 키 로테이션
keyProvider.rotateKey();

// 노출된 키로 암호화된 데이터 재암호화
reencryptionService.reencryptAll(exposedKeyVersion);
```

---

## 4. 데이터 보호

### 암호화 대상 식별

다음 데이터는 반드시 암호화하세요:

| 데이터 유형 | 예시 |
|------------|------|
| 개인식별정보 (PII) | 주민등록번호, 여권번호 |
| 금융정보 | 계좌번호, 카드번호 |
| 인증정보 | 비밀번호 해시, 토큰 |
| 건강정보 | 진료기록, 처방전 |
| 연락처 | 이메일, 전화번호, 주소 |

### 검색용 해시 사용

암호화된 필드로는 검색할 수 없습니다. 검색이 필요한 경우 별도 해시 필드를 사용하세요:

```java
@Entity
public class User {
    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email")
    private String email;

    @Column(name = "email_hash", unique = true)
    private String emailHash;  // SHA-256 해시

    public void setEmail(String email) {
        this.email = email;
        this.emailHash = DigestUtils.sha256Hex(
            email.toLowerCase().trim() + PEPPER  // pepper 추가
        );
    }
}
```

### 로그에서 민감 데이터 마스킹

```java
@Slf4j
public class UserService {
    public void processUser(User user) {
        // 잘못된 예
        log.info("Processing user: {}", user.getEmail());

        // 올바른 예
        log.info("Processing user: {}", maskEmail(user.getEmail()));
    }

    private String maskEmail(String email) {
        if (email == null) return null;
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
        // "user@example.com" → "us***@example.com"
    }
}
```

---

## 5. 인프라 보안

### 네트워크 격리

```
┌─────────────────────────────────────────┐
│            Private Subnet               │
│  ┌─────────────┐    ┌──────────────┐   │
│  │ Application │───▶│    Vault     │   │
│  └─────────────┘    └──────────────┘   │
│         │                              │
│         ▼                              │
│  ┌─────────────┐                       │
│  │  Database   │  (암호화된 데이터)     │
│  └─────────────┘                       │
└─────────────────────────────────────────┘
           │
      Public Subnet 접근 불가
```

### TLS 통신

```yaml
# Vault 통신 TLS 설정
spring:
  cloud:
    vault:
      uri: https://vault.internal:8200  # HTTPS 필수
      ssl:
        trust-store: classpath:vault-truststore.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
```

### 파일 시스템 보안 (ManagedKeyProvider)

```bash
# 키 저장소 디렉토리 권한
chmod 700 /var/simplix/encryption/keys
chown app-user:app-group /var/simplix/encryption/keys

# 개별 키 파일 권한
chmod 600 /var/simplix/encryption/keys/*.key
```

---

## 6. 감사 및 모니터링

### 감사 로그

다음 이벤트를 로깅하세요:

| 이벤트 | 로그 레벨 | 정보 |
|--------|----------|------|
| 암호화 모듈 초기화 | INFO | Provider 타입, 키 버전 |
| 키 로테이션 | INFO | 이전 버전, 새 버전, 이유 |
| 키 접근 실패 | WARN | 요청된 버전, 에러 |
| 복호화 실패 | ERROR | 키 버전, 에러 상세 |

### 알림 설정

```java
@Component
@Slf4j
public class EncryptionMonitor {

    @Autowired
    private KeyProvider keyProvider;

    @Autowired
    private AlertService alertService;

    @Scheduled(fixedRate = 3600000)  // 1시간마다
    public void checkEncryptionHealth() {
        try {
            // 1. KeyProvider 상태 확인
            if (!keyProvider.isConfigured()) {
                alertService.sendCriticalAlert(
                    "Encryption KeyProvider not configured"
                );
            }

            // 2. 키 로테이션 예정 확인
            Map<String, Object> stats = keyProvider.getKeyStatistics();
            checkRotationSchedule(stats);

        } catch (Exception e) {
            log.error("Encryption health check failed", e);
            alertService.sendCriticalAlert(
                "Encryption health check failed: " + e.getMessage()
            );
        }
    }

    private void checkRotationSchedule(Map<String, Object> stats) {
        String lastRotationStr = (String) stats.get("lastRotation");
        if (lastRotationStr == null || "never".equals(lastRotationStr)) {
            return;
        }

        Instant lastRotation = Instant.parse(lastRotationStr);
        int rotationDays = (int) stats.get("rotationDays");
        Instant nextRotation = lastRotation.plus(rotationDays, ChronoUnit.DAYS);

        long daysUntilRotation = ChronoUnit.DAYS.between(
            Instant.now(), nextRotation
        );

        if (daysUntilRotation <= 7) {
            alertService.sendWarningAlert(
                "Key rotation due in " + daysUntilRotation + " days"
            );
        }
    }
}
```

### 메트릭 수집

```java
@Component
@RequiredArgsConstructor
public class EncryptionMetrics {

    private final MeterRegistry meterRegistry;
    private final KeyProvider keyProvider;

    @PostConstruct
    public void registerMetrics() {
        // 캐시된 키 수
        Gauge.builder("encryption.keys.cached", keyProvider,
            kp -> kp.getKeyStatistics().get("cachedKeys"))
            .register(meterRegistry);

        // 현재 키 버전 (태그로 노출)
        Gauge.builder("encryption.key.version", () -> 1)
            .tag("version", keyProvider.getCurrentVersion())
            .register(meterRegistry);
    }

    // 암호화/복호화 카운터
    private final Counter encryptCounter = Counter.builder("encryption.operations")
        .tag("operation", "encrypt")
        .register(meterRegistry);

    private final Counter decryptCounter = Counter.builder("encryption.operations")
        .tag("operation", "decrypt")
        .register(meterRegistry);
}
```

---

## 7. 인시던트 대응

### 키 노출 시 대응 절차

1. **즉시 키 로테이션**
   ```java
   keyProvider.rotateKey();
   ```

2. **영향 범위 파악**
   - 노출된 키 버전 확인
   - 해당 버전으로 암호화된 데이터 목록 추출

3. **데이터 재암호화**
   ```java
   reencryptionService.reencryptByVersion(exposedVersion, batchSize);
   ```

4. **이전 키 폐기**
   - Vault에서 삭제 또는 설정에서 제거

5. **인시던트 보고**
   - 영향 범위, 대응 조치, 재발 방지책 문서화

### 복호화 실패 대응

```java
try {
    String decrypted = encryptionService.decrypt(encryptedData);
} catch (DecryptionException e) {
    log.error("Decryption failed for data with version: {}",
        encryptionService.getKeyVersion(encryptedData), e);

    // 1. 키 버전 확인
    String version = encryptionService.getKeyVersion(encryptedData);

    // 2. 해당 버전 키 존재 여부 확인
    try {
        keyProvider.getKey(version);
    } catch (IllegalArgumentException ex) {
        // 키가 삭제된 경우 - 데이터 복구 불가
        log.error("Key version {} not found - data may be unrecoverable", version);
    }
}
```

---

## 8. 체크리스트

### 배포 전 보안 체크리스트

- [ ] 운영 환경에서 SimpleKeyProvider 사용하지 않음
- [ ] 키가 코드 저장소에 포함되지 않음
- [ ] 환경 변수/Secret으로 키 주입
- [ ] TLS 통신 설정
- [ ] 키 로테이션 정책 수립
- [ ] 감사 로깅 활성화
- [ ] 모니터링 알림 설정
- [ ] 인시던트 대응 절차 문서화

### 정기 보안 점검

- [ ] 키 로테이션 주기 준수 확인
- [ ] 접근 로그 검토
- [ ] 불필요한 키 정리
- [ ] 암호화 대상 데이터 누락 확인
- [ ] 의존성 취약점 스캔

---

## 9. 규정 준수

### PCI-DSS

| 요구사항 | SimpliX Encryption 대응 |
|---------|------------------------|
| 3.5 키 보호 | VaultKeyProvider + 접근 통제 |
| 3.6 키 관리 | 자동 키 로테이션 |
| 10.5 감사 로그 | Vault 감사 로깅 |

### GDPR

| 요구사항 | SimpliX Encryption 대응 |
|---------|------------------------|
| 개인정보 암호화 | AesEncryptionConverter |
| 접근 기록 | 감사 로깅 |
| 삭제권 | 키 삭제로 데이터 무효화 가능 |

### HIPAA

| 요구사항 | SimpliX Encryption 대응 |
|---------|------------------------|
| 기술적 보호조치 | AES-256-GCM 암호화 |
| 접근 통제 | Vault 정책 기반 접근 통제 |
| 감사 통제 | 감사 로깅 및 모니터링 |

---

## Related Documents

- [Overview (개요)](./overview.md) - 아키텍처 및 설정
- [KeyProvider 가이드](./key-providers.md) - 환경별 KeyProvider 상세 설정
- [JPA Converter 사용법](./jpa-converter.md) - Entity 필드 자동 암호화
- [키 로테이션 가이드](./key-rotation.md) - 키 교체 및 데이터 마이그레이션