# 키 로테이션 가이드

키 로테이션은 암호화 키를 주기적으로 교체하여 보안을 강화하는 프로세스입니다. 이 가이드에서는 각 KeyProvider별 키 로테이션 방법을 설명합니다.

## 키 로테이션이 필요한 이유

1. **보안 정책 준수**: PCI-DSS, HIPAA 등 규정 요구사항
2. **키 노출 위험 최소화**: 키가 노출되어도 영향 범위 제한
3. **암호화 수명 관리**: 동일한 키로 암호화되는 데이터 양 제한
4. **인력 변동 대응**: 키 접근 권한자 변경 시

## KeyProvider별 로테이션

### SimpleKeyProvider

**지원 여부: 미지원**

SimpleKeyProvider는 키 로테이션을 지원하지 않습니다. 키를 변경하면 기존 데이터를 복호화할 수 없습니다.

```
⚠ SimpleKeyProvider는 개발/테스트 환경 전용입니다.
   운영 환경에서는 키 로테이션을 지원하는 Provider를 사용하세요.
```

---

### ConfigurableKeyProvider

**지원 여부: 수동 로테이션 (설정 변경 + 재시작)**

#### 로테이션 절차

**1단계: 새 키 생성**

```bash
# 32바이트 랜덤 키 생성 (Base64)
openssl rand -base64 32
# 출력: MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnN0dXY=
```

**2단계: 설정 파일 수정**

```yaml
# 변경 전
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: v2
      keys:
        v1:
          key: "이전키1"
          deprecated: true
        v2:
          key: "현재키"
          deprecated: false

# 변경 후
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: v3  # 새 버전으로 변경
      keys:
        v1:
          key: "이전키1"
          deprecated: true
        v2:
          key: "현재키"
          deprecated: true  # deprecated로 변경
        v3:
          key: "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnN0dXY="  # 새 키 추가
          deprecated: false
```

**3단계: 애플리케이션 재시작**

```bash
# Kubernetes
kubectl rollout restart deployment my-app

# Docker Compose
docker-compose restart app

# 일반 Java
kill -15 <PID> && java -jar app.jar
```

**4단계: 로테이션 확인**

```
INFO ✔ ConfigurableKeyProvider initialized with 3 keys, current version: v3
INFO ℹ Deprecated key versions (decrypt-only): [v1, v2]
```

#### Kubernetes 환경에서의 로테이션

**ConfigMap 업데이트:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: encryption-config
data:
  ENCRYPTION_CURRENT_VERSION: "v3"  # 업데이트
```

**Secret 업데이트:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: encryption-keys
type: Opaque
stringData:
  ENCRYPTION_KEY_V1: "이전키1"
  ENCRYPTION_KEY_V2: "현재키"
  ENCRYPTION_KEY_V3: "새키"  # 추가
```

**롤링 업데이트 트리거:**

```bash
kubectl rollout restart deployment my-app
```

---

### ManagedKeyProvider

**지원 여부: 자동/수동 로테이션**

#### 자동 로테이션 설정

```yaml
simplix:
  encryption:
    provider: managed
    key-store-path: /var/simplix/encryption/keys
    rotation:
      enabled: true
      days: 90  # 90일마다 자동 로테이션
    auto-rotation: true
    rotation-cron: "0 0 2 * * ?"  # 매일 새벽 2시 체크
```

#### 수동 로테이션

```java
@Autowired
private KeyProvider keyProvider;

public void rotateKey() {
    String newVersion = keyProvider.rotateKey();
    log.info("Key rotated to version: {}", newVersion);
}
```

#### 로테이션 결과

```
/var/simplix/encryption/keys/
├── key_v1734567890123.key  (이전 키 - 복호화 가능)
├── key_v1734667890123.key  (이전 키 - 복호화 가능)
└── key_v1734767890123.key  (현재 키 - 암호화/복호화)
```

---

### VaultKeyProvider

**지원 여부: 자동/수동 로테이션**

#### 자동 로테이션 설정

```yaml
simplix:
  encryption:
    provider: vault
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
```

#### 수동 로테이션

```java
@Autowired
private KeyProvider keyProvider;

public void rotateKey() {
    String newVersion = keyProvider.rotateKey();
    log.info("Key rotated to version: {}", newVersion);
}
```

#### Vault CLI로 로테이션

```bash
# 새 키 생성
NEW_KEY=$(openssl rand -base64 32)
VERSION="v$(date +%s%3N)"

# Vault에 새 키 저장
vault kv put secret/encryption/keys/${VERSION} \
  key="${NEW_KEY}" \
  algorithm="AES" \
  keySize=256 \
  createdAt="$(date -Iseconds)" \
  status="active"

# current 업데이트
vault kv put secret/encryption/keys/current \
  version="${VERSION}" \
  rotatedAt="$(date -Iseconds)"
```

#### 다중 인스턴스 동기화

VaultKeyProvider는 자동으로 동기화됩니다:

```
Instance A: rotateKey()
     |
     v
Vault update (current = v3)
     |
     v
Instance B: getCurrentKey()
     |
     v
refreshCurrentVersion() --> v3 detected
     |
     v
Load and cache new key
```

---

## 데이터 재암호화 (Re-encryption)

키 로테이션 후 기존 데이터를 새 키로 재암호화할 수 있습니다.

### 재암호화 서비스

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReencryptionService {

    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    /**
     * 특정 버전의 데이터를 현재 키로 재암호화
     */
    @Transactional
    public ReencryptionResult reencryptUsers(String targetVersion, int batchSize) {
        int total = 0;
        int success = 0;
        int skipped = 0;
        int failed = 0;

        Pageable pageable = PageRequest.of(0, batchSize);
        Page<User> page;

        do {
            page = userRepository.findAll(pageable);

            for (User user : page.getContent()) {
                total++;
                try {
                    boolean updated = reencryptUser(user, targetVersion);
                    if (updated) {
                        success++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to reencrypt user {}: {}", user.getId(), e.getMessage());
                }
            }

            userRepository.flush();
            pageable = page.nextPageable();

            log.info("Progress: {}/{} processed", total, page.getTotalElements());

        } while (page.hasNext());

        return new ReencryptionResult(total, success, skipped, failed);
    }

    private boolean reencryptUser(User user, String targetVersion) {
        boolean updated = false;

        // 이메일 재암호화
        if (shouldReencrypt(user.getEmail(), targetVersion)) {
            String decrypted = encryptionService.decrypt(user.getEmail());
            user.setEmail(decrypted);  // Converter가 새 키로 암호화
            updated = true;
        }

        // 전화번호 재암호화
        if (shouldReencrypt(user.getPhoneNumber(), targetVersion)) {
            String decrypted = encryptionService.decrypt(user.getPhoneNumber());
            user.setPhoneNumber(decrypted);
            updated = true;
        }

        return updated;
    }

    private boolean shouldReencrypt(String encryptedValue, String targetVersion) {
        if (encryptedValue == null || !encryptionService.isEncrypted(encryptedValue)) {
            return false;
        }
        String version = encryptionService.getKeyVersion(encryptedValue);
        return targetVersion.equals(version);
    }

    @Data
    @AllArgsConstructor
    public static class ReencryptionResult {
        private int total;
        private int success;
        private int skipped;
        private int failed;
    }
}
```

### 재암호화 실행

```java
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final ReencryptionService reencryptionService;

    @PostMapping("/admin/reencrypt")
    public ResponseEntity<ReencryptionResult> reencrypt(
            @RequestParam String targetVersion,
            @RequestParam(defaultValue = "100") int batchSize) {

        ReencryptionResult result = reencryptionService.reencryptUsers(
            targetVersion, batchSize
        );

        return ResponseEntity.ok(result);
    }
}
```

### EncryptionService.reencrypt() 사용

```java
// 단일 값 재암호화
String oldEncrypted = "v1:iv:ciphertext";
String newEncrypted = encryptionService.reencrypt(oldEncrypted);
// 결과: "v2:newIv:newCiphertext"
```

---

## 이전 키 정리

재암호화 완료 후 이전 키를 제거할 수 있습니다.

### ConfigurableKeyProvider

```yaml
# 재암호화 완료 후 v1 키 제거
simplix:
  encryption:
    configurable:
      current-version: v3
      keys:
        # v1 제거됨
        v2:
          key: "..."
          deprecated: true
        v3:
          key: "..."
          deprecated: false
```

### ManagedKeyProvider

```bash
# 사용하지 않는 키 파일 삭제
rm /var/simplix/encryption/keys/key_v1734567890123.key
```

### VaultKeyProvider

```bash
# Vault에서 이전 키 삭제
vault kv delete secret/encryption/keys/v1734567890123
```

---

## 로테이션 모니터링

### 로그 확인

```
INFO  ✔ Key rotated: v1734667890123 -> v1734767890123 (reason: scheduled, provider: VaultKeyProvider)
```

### 메트릭

```java
@Autowired
private KeyProvider keyProvider;

public Map<String, Object> getKeyMetrics() {
    return keyProvider.getKeyStatistics();
}

// 반환 예시:
// {
//   "provider": "VaultKeyProvider",
//   "currentVersion": "v1734767890123",
//   "cachedKeys": 3,
//   "rotationEnabled": true,
//   "autoRotation": true,
//   "rotationDays": 90,
//   "lastRotation": "2025-01-15T10:30:00Z",
//   "nextRotation": "2025-04-15T10:30:00Z"
// }
```

### 알림 설정

```java
@Scheduled(cron = "0 0 9 * * ?")  // 매일 오전 9시
public void checkRotationStatus() {
    Map<String, Object> stats = keyProvider.getKeyStatistics();

    Instant lastRotation = Instant.parse((String) stats.get("lastRotation"));
    int rotationDays = (int) stats.get("rotationDays");
    Instant nextRotation = lastRotation.plus(rotationDays, ChronoUnit.DAYS);

    if (nextRotation.isBefore(Instant.now().plus(7, ChronoUnit.DAYS))) {
        // 7일 내 로테이션 필요
        sendSlackNotification("Key rotation needed within 7 days");
    }
}
```

---

## 로테이션 체크리스트

### 사전 준비

- [ ] 현재 키 버전 확인
- [ ] 백업 계획 수립
- [ ] 롤백 절차 준비
- [ ] 모니터링 대시보드 준비

### 로테이션 수행

- [ ] 새 키 생성
- [ ] 설정 업데이트 (ConfigurableKeyProvider) 또는 rotateKey() 호출
- [ ] 애플리케이션 재시작/롤링 업데이트
- [ ] 로그에서 새 키 버전 확인

### 사후 검증

- [ ] 새 데이터가 새 키로 암호화되는지 확인
- [ ] 이전 키로 암호화된 데이터 복호화 확인
- [ ] 에러 로그 모니터링
- [ ] 성능 모니터링

### (선택) 재암호화

- [ ] 재암호화 대상 데이터 규모 파악
- [ ] 재암호화 배치 작업 실행
- [ ] 완료 후 이전 키 제거

---

## Related Documents

- [Overview (개요)](./overview.md) - 아키텍처 및 설정
- [KeyProvider 가이드](./key-providers.md) - 환경별 KeyProvider 상세 설정
- [JPA Converter 사용법](./jpa-converter.md) - Entity 필드 자동 암호화
- [보안 모범 사례](./security-best-practices.md) - 운영 환경 보안 권장사항