# JWE 키 롤링 (Key Rolling)

JWE(JSON Web Encryption) 토큰의 암호화 키를 주기적으로 교체하여 보안을 강화하는 기능입니다.

## 목차

- [개요](#개요)
- [아키텍처](#아키텍처)
- [구성 요소](#구성-요소)
- [설정](#설정)
- [애플리케이션 구현](#애플리케이션-구현)
- [분산 환경](#분산-환경)
- [마이그레이션](#마이그레이션)

---

## 개요

### 왜 키 롤링이 필요한가?

1. **보안 강화**: 키가 노출되더라도 피해 범위를 제한
2. **컴플라이언스**: PCI-DSS, HIPAA 등 보안 규정 준수
3. **키 수명 관리**: 암호화 키의 수명 주기 관리

### 주요 기능

| 기능 | 설명 |
|------|------|
| DB 기반 키 저장 | RSA 키를 암호화하여 데이터베이스에 저장 |
| 다중 키 버전 | 여러 버전의 키를 동시에 유지하여 기존 토큰 복호화 지원 |
| kid 헤더 | JWE 토큰에 Key ID를 포함하여 올바른 키로 복호화 |
| 자동 만료 | 키 만료 시간을 설정하여 오래된 키 자동 정리 |

---

## 아키텍처

```
+-------------------------------------------------------------------+
|                        SimpliX Library                            |
+-------------------------------------------------------------------+
|                                                                   |
|  +------------------+    +------------------+                     |
|  | JweKeyProvider   |<---| SimpliXJwe       |                     |
|  | (interface)      |    | TokenProvider    |                     |
|  +--------+---------+    +------------------+                     |
|           |                                                       |
|  +--------+----------+                                            |
|  |                   |                                            |
|  v                   v                                            |
|  +--------------+  +------------------+                           |
|  | Static       |  | Database         |                           |
|  | JweKey       |  | JweKeyProvider   |<--- Key Rolling Mode      |
|  | Provider     |  +--------+---------+                           |
|  +--------------+           |                                     |
|  (Legacy Mode)              |                                     |
|                             v                                     |
|                    +------------------+                           |
|                    | JweKeyRotation   |                           |
|                    | Service          |                           |
|                    +--------+---------+                           |
|                             |                                     |
+-----------------------------+-------------------------------------+
                              |
                              v
+-------------------------------------------------------------------+
|                      Application Implementation                   |
+-------------------------------------------------------------------+
|  +------------------+    +------------------+                     |
|  | JweKeyStore      |    | Scheduler        |                     |
|  | (impl interface) |    | (ShedLock etc)   |                     |
|  +--------+---------+    +------------------+                     |
|           |                                                       |
|           v                                                       |
|  +------------------+                                             |
|  | Database         |                                             |
|  | (JWE Key Table)  |                                             |
|  +------------------+                                             |
+-------------------------------------------------------------------+
```

### 키 생명주기

```
Created                 Expires
  |                      |
  v                      v
--o----------------------o----------------------> Time
  |                      |
  |<-- Token Lifetime -->|<- Buffer ->|
  |      (7 days)        |  (1 day)   |
  |                      |            |
  | All tokens issued    |            |
  | during this period   |            |
  | can be decrypted     |            |
  |                                   |
  +---- Can be deleted after 8 days --+
```

---

## 구성 요소

### 라이브러리 제공 (simplix-auth)

| 클래스 | 역할 |
|--------|------|
| `JweKeyProvider` | 키 제공자 인터페이스 |
| `JweKeyStore` | 키 저장소 인터페이스 (애플리케이션 구현 필요) |
| `JweKeyData` | 키 데이터 DTO |
| `DatabaseJweKeyProvider` | DB 기반 키 제공자 구현체 |
| `StaticJweKeyProvider` | 단일 키 제공자 (레거시/개발용) |
| `JweKeyRotationService` | 키 로테이션 서비스 |

### 애플리케이션 구현 필요

| 항목 | 설명 |
|------|------|
| `JweKeyStore` 구현체 | JPA, MyBatis 등으로 키 저장소 구현 |
| JWE 키 테이블 | 암호화된 키를 저장할 DB 테이블 |
| 스케줄러 | 키 로테이션 및 캐시 갱신 스케줄러 |

---

## 설정

### application.yml

```yaml
simplix:
  # simplix-encryption 설정 (키 암호화에 사용)
  encryption:
    enabled: true
    provider: configurable
    configurable:
      current-version: v1
      keys:
        v1:
          key: "Base64로 인코딩된 32바이트 AES 키"
          deprecated: false

  auth:
    # 토큰 설정
    token:
      access-token-lifetime: 1800        # 30분 (초)
      refresh-token-lifetime: 604800     # 7일 (초) - 키 만료 계산 기준
      enable-token-rotation: true
      enable-blacklist: false

    # JWE 설정
    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM

      # 키 롤링 설정
      key-rolling:
        enabled: true                    # 키 롤링 활성화
        key-size: 2048                   # RSA 키 크기 (비트)
        auto-initialize: true            # 시작 시 키 자동 생성

        # 키 보관 정책
        retention:
          buffer-seconds: 86400          # 버퍼 기간 (1일)
          auto-cleanup: true             # 만료 키 자동 삭제
```

### 설정 상세 설명

#### key-rolling

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `enabled` | boolean | false | 키 롤링 기능 활성화 |
| `key-size` | int | 2048 | RSA 키 크기 (비트). 2048 또는 4096 권장 |
| `auto-initialize` | boolean | true | 애플리케이션 시작 시 키가 없으면 자동 생성 |

#### retention

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `buffer-seconds` | int | 86400 | 토큰 유효기간에 추가되는 버퍼 (초) |
| `auto-cleanup` | boolean | false | 로테이션 시 만료된 키 자동 삭제 |

### 로테이션 주기 vs 키 보존 시간

**중요**: 이 두 개념은 서로 독립적입니다.

| 개념 | 설명 | 설정 위치 | 제약 조건 |
|------|------|----------|----------|
| **로테이션 주기** | 새 키를 생성하는 간격 | 스케줄러 cron 표현식 | 보안 정책에 따라 자유롭게 설정 |
| **키 보존 시간** | 이전 키를 삭제하기까지의 시간 | `buffer-seconds` | ≥ refresh-token-lifetime |

#### 로테이션 주기

로테이션 주기는 **보안 정책**에 따라 자유롭게 설정합니다:

```java
// 매주 일요일 02:00 (일반적)
@Scheduled(cron = "0 0 2 * * SUN")

// 매월 1일 02:00 (느슨한 정책)
@Scheduled(cron = "0 0 2 1 * *")

// 매일 02:00 (엄격한 정책)
@Scheduled(cron = "0 0 2 * * *")
```

#### 키 보존 시간 (키 만료 계산)

키 보존 시간은 **토큰 유효기간에 의해 결정**됩니다:

```
키 만료 시간 = 키 생성 시간 + refresh-token-lifetime + buffer-seconds
```

예시:

| refresh-token-lifetime | buffer-seconds | 키 보존 시간 |
|------------------------|----------------|-------------|
| 7일 (604800초) | 1일 (86400초) | 8일 |
| 3시간 (10800초) | 1시간 (3600초) | 4시간 |
| 30분 (1800초) | 10분 (600초) | 40분 |

#### 조합 예시

```
refresh-token-lifetime = 3 hours
Rotation period = Every Sunday (7 days)
Key retention = 3 hours + 1 hour (buffer) = 4 hours

Timeline:
+--------------------------------------------------------------+
| Day 0: Key v1 created (active)                               |
| Day 7: Key v2 created (active), v1 deletable after 4 hours   |
| Day 14: Key v3 created (active), v2 deletable after 4 hours  |
+--------------------------------------------------------------+

Even with short token lifetime, rotation period can be long!
Old keys are quickly deleted after token expiry, saving storage
```

---

## 애플리케이션 구현

자세한 구현 가이드는 [jwe-key-rolling-implementation.md](./jwe-key-rolling-implementation.md)를 참조하세요.

### 빠른 시작

1. **의존성 추가**
   ```groovy
   implementation 'dev.simplecore:spring-boot-starter-simplix-auth'
   implementation 'dev.simplecore:simplix-encryption'
   ```

2. **DB 테이블 생성**
   ```sql
   CREATE TABLE jwe_keys (
       version VARCHAR(50) PRIMARY KEY,
       encrypted_public_key TEXT NOT NULL,
       encrypted_private_key TEXT NOT NULL,
       active BOOLEAN NOT NULL DEFAULT FALSE,
       created_at TIMESTAMP NOT NULL,
       expires_at TIMESTAMP
   );
   ```

3. **JweKeyStore 구현**
   ```java
   @Repository
   public class JpaJweKeyStore implements JweKeyStore {
       // 구현...
   }
   ```

4. **스케줄러 설정**
   ```java
   @Scheduled(cron = "0 0 2 * * SUN")  // 매주 일요일 02:00
   @SchedulerLock(name = "jweKeyRotation")
   public void rotateKey() {
       jweKeyRotationService.rotateKey();
   }
   ```

---

## 분산 환경

### 멀티 노드 환경에서의 키 관리

```
+-------------------------------------------------------------------+
|                         Database                                  |
|                    +---------------------+                        |
|                    |  jwe-v1702345678901 | (active)               |
|                    |  jwe-v1702245678901 | (inactive, not expired)|
|                    +---------------------+                        |
+---------------------------+---------------------------------------+
                            |
        +-------------------+-------------------+
        v                   v                   v
   +---------+         +---------+         +---------+
   | Node A  |         | Node B  |         | Node C  |
   | +-----+ |         | +-----+ |         | +-----+ |
   | |Cache| |         | |Cache| |         | |Cache| |
   | +-----+ |         | +-----+ |         | +-----+ |
   +---------+         +---------+         +---------+
```

### 스케줄러 전략

| 작업 | 스케줄러 유형 | 이유 |
|------|-------------|------|
| 키 로테이션 | 분산 (ShedLock) | 하나의 노드만 실행해야 함 |
| 캐시 갱신 | 로컬 | 모든 노드가 독립적으로 실행 |

### 분산락 권장 및 DB 레벨 보호

분산 환경에서 키 로테이션 스케줄링에는 **ShedLock과 같은 분산락 사용을 권장**합니다. 그러나 분산락 구현이 완벽하지 않더라도 **DB 레벨의 unique constraint가 이중 보호**를 제공합니다.

#### DB 레벨 레이스 컨디션 방지

`jwe_keys` 테이블의 `initialization_marker` 컬럼에 unique constraint를 적용하여, 동시에 여러 서버가 키를 생성하더라도 하나만 성공합니다:

```sql
-- initialization_marker에 unique constraint
CONSTRAINT uk_jwe_keys_init_marker UNIQUE (initialization_marker)
```

#### 마커 동작 원리

| 상황 | 마커 값 | 설명 |
|------|---------|------|
| 초기 키 생성 | `INITIAL` | 첫 번째 키 생성 시 |
| 로테이션 | `AFTER-{현재버전}` | 예: `AFTER-jwe-v1702345678901` |

동일한 작업을 수행하는 서버들은 **같은 마커 값을 사용**하므로, 첫 번째로 INSERT가 성공한 서버만 키를 생성하고 나머지는 unique constraint violation으로 실패합니다.

#### 예외 없는 안전한 실패 처리

**중요**: 마커 충돌로 인한 unique constraint violation이 발생해도 **애플리케이션 예외가 발생하지 않습니다**. 대신:

1. 경고 로그만 기록됨
2. 다른 서버가 생성한 키를 캐시에 로드
3. 정상적으로 계속 동작

```
WARN - JWE key rotation skipped - another server already completed rotation
```

이 설계 덕분에:
- **분산락이 완벽하지 않아도 안전**: 2개 이상의 서버가 동시에 로테이션을 시도해도 문제없음
- **초기화 레이스 컨디션 방지**: 여러 서버가 동시 시작해도 하나만 초기 키 생성
- **애플리케이션 안정성 보장**: unique violation으로 인한 500 에러 없음

### 구현 예시

```java
@Component
@RequiredArgsConstructor
public class JweKeyScheduler {

    private final JweKeyRotationService rotationService;
    private final DatabaseJweKeyProvider keyProvider;

    /**
     * 분산 스케줄: 키 로테이션 (한 노드만 실행)
     *
     * ShedLock 사용을 권장하지만, DB unique constraint가
     * 분산락 실패 시에도 이중 보호를 제공합니다.
     */
    @Scheduled(cron = "0 0 2 * * SUN")  // 매주 일요일 02:00
    @SchedulerLock(name = "jweKeyRotation", lockAtMostFor = "10m")
    public void rotateKey() {
        rotationService.rotateKey();
    }

    /**
     * 로컬 스케줄: 캐시 갱신 (모든 노드 실행)
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)  // 5분마다
    public void refreshCache() {
        keyProvider.refresh();
    }
}
```

### 안전성 보장

1. **복호화는 항상 성공**: 캐시에 없는 키는 DB에서 조회
2. **5분 갭은 문제없음**: 일부 노드가 이전 키로 암호화해도 유효
3. **토큰에 kid 포함**: 어떤 키로 암호화됐는지 토큰이 알고 있음
4. **DB 레벨 이중 보호**: 분산락 없이도 레이스 컨디션 안전
5. **Copy-on-write 캐시**: 캐시 갱신 중에도 서비스 중단 없음
6. **Save-before-deactivate**: 저장 실패 시에도 기존 키 유지

### 분산 스케줄링 권장사항

분산 환경에서 키 로테이션을 안전하게 수행하기 위해 **ShedLock, Quartz 등의 분산 스케줄러 사용을 강력히 권장**합니다.

#### 왜 분산 스케줄링이 필요한가?

| 항목 | 분산 스케줄링 사용 | 미사용 |
|------|------------------|--------|
| 로테이션 실행 | 하나의 노드만 실행 | 모든 노드가 시도 |
| DB 부하 | 최소 | 불필요한 INSERT 시도 |
| 로그 노이즈 | 없음 | unique violation 경고 |
| 권장 여부 | ✔ 권장 | ⚠ 비권장 (동작은 함) |

#### UUID 접미사는 최후의 방어선

버전 ID의 UUID 접미사는 **분산 스케줄링 실패 시의 안전장치**입니다:

```
jwe-v1702345678901-a1b2c3d4
         ↑              ↑
    타임스탬프       UUID 8자리 (충돌 방지)
```

이 설계는 다음 상황을 대비합니다:
- 분산락 획득 실패 후 동시 실행
- 네트워크 파티션으로 인한 락 만료
- 락 서버 장애

**그러나 UUID 접미사에만 의존하지 마세요.** 분산 스케줄링이 기본이고, UUID는 예외 상황의 방어선입니다.

---

### 동기화 지연 안전성

분산 환경에서 키 로테이션 후 모든 노드가 즉시 새 키를 인식하지 못할 수 있습니다. 그러나 **현재 구현은 이 문제로부터 안전**합니다.

#### On-Demand 키 로딩

토큰에는 `kid` (Key ID) 헤더가 포함되어 있어, 복호화 시 정확한 키 버전을 알 수 있습니다. 캐시에 해당 키가 없으면 **DB에서 직접 조회**합니다:

```
Token decrypt request (kid=v2)
              |
              v
      +---------------+
      | v2 in cache?  |----Yes----> Return immediately
      +---------------+
              |
             No
              |
              v
      +-------------------+
      |loadKeyOnDemand(v2)|
      +-------------------+
              |
              v
      +---------------------+
      | Load from DB &      |
      | add to cache        |
      +---------------------+
              |
              v
       Decrypt success
```

#### 동기화 지연 시나리오 분석

```
T+0s:  ServerA creates & activates new key v2
T+0s:  ServerA encrypts token with v2 (kid=v2) -> issued to user
T+1s:  User requests to ServerB (ServerB cache: only v1)
T+1s:  ServerB checks token's kid=v2
T+1s:  Cache miss -> loadKeyOnDemand("v2") -> Load from DB
T+1s:  Decrypt success!
```

**결론**: 캐시 갱신 주기와 관계없이 토큰 복호화는 항상 성공합니다.

#### 캐시 갱신 주기 권장

on-demand 로딩이 있더라도 **캐시 갱신 주기는 5분 이내로 설정**을 권장합니다:

| 갱신 주기 | 장점 | 단점 |
|----------|------|------|
| 1분 | DB 조회 최소화 | DB 부하 약간 증가 |
| **5분 (권장)** | 균형 잡힌 설정 | - |
| 10분 이상 | DB 부하 최소 | on-demand 조회 증가 |

```java
@Scheduled(fixedRate = 5 * 60 * 1000)  // 5분 권장
public void refreshCache() {
    keyProvider.refresh();
}
```

---

### 분산 환경 안전성 설계

#### 캐시 갱신 안전성

캐시 갱신 시 서비스 중단을 방지하기 위해 **불변 레코드와 copy-on-write 패턴**을 사용합니다:

```java
// 불변 레코드로 키 캐시와 현재 버전을 원자적으로 관리
private record KeyCacheState(Map<String, KeyPair> keys, String currentVersion) {
    KeyCacheState {
        keys = keys != null ? Map.copyOf(keys) : Map.of();
    }
}

// volatile 참조로 가시성 보장
private volatile KeyCacheState cacheState = new KeyCacheState(Map.of(), null);

private void loadAllKeys() {
    // 기존 캐시를 유지한 채 새 캐시 구성
    Map<String, KeyPair> newCache = new HashMap<>();
    String newCurrentVersion = null;
    // ... 새 캐시에 키 로드 ...

    // 완성 후 원자적 교체 - 기존 요청은 계속 동작
    this.cacheState = new KeyCacheState(newCache, newCurrentVersion);
}
```

#### 로테이션 작업 순서

로테이션 실패 시 기존 키가 유지되도록 **저장 후 비활성화** 순서를 따릅니다:

```java
// 1. 새 키 저장 (실패하면 기존 키 유지)
keyStore.save(keyData);

// 2. 저장 성공 후 기존 키 비활성화
keyStore.deactivateAllExcept(newVersion);
```

---

## 마이그레이션

### 기존 단일 키 → 키 롤링 전환

#### 1단계: 기존 키를 JweKeyStore에 저장

```java
@Component
public class JweKeyMigration implements ApplicationRunner {

    private final JweKeyStore keyStore;
    private final EncryptionService encryptionService;
    private final SimpliXAuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        // 기존 키가 있고, DB에 키가 없는 경우에만 마이그레이션
        if (keyStore.findAll().isEmpty() && properties.getJwe().getEncryptionKey() != null) {
            migrateExistingKey();
        }
    }

    private void migrateExistingKey() {
        // 기존 JWK에서 키 추출 및 저장
        // ... 구현
    }
}
```

#### 2단계: 설정 변경

```yaml
# 변경 전
simplix:
  auth:
    jwe:
      encryption-key: "${JWE_KEY}"

# 변경 후
simplix:
  auth:
    jwe:
      key-rolling:
        enabled: true
        auto-initialize: false  # 마이그레이션 완료 후 true로 변경
```

#### 3단계: 기존 토큰 호환성

- 기존 토큰에는 `kid` 헤더가 없음
- `SimpliXJweTokenProvider`는 `kid`가 없으면 기본 decrypter 사용
- 마이그레이션된 키가 기본 decrypter로 설정되므로 기존 토큰도 복호화 가능

---

## 트러블슈팅

### 키 로테이션 후 토큰 복호화 실패

**증상**: `JweKeyException: JWE key version not found`

**원인**:
- 다른 노드의 캐시가 갱신되지 않음
- 만료된 키가 삭제됨

**해결**:
1. 캐시 갱신 주기 확인 (기본 5분)
2. `buffer-seconds` 값 증가
3. `auto-cleanup: false`로 설정 후 수동 정리

### 키 저장 실패

**증상**: `EncryptionException: Failed to encrypt data`

**원인**:
- simplix-encryption 설정 누락
- AES 키 미설정

**해결**:
1. `simplix.encryption.enabled: true` 확인
2. `simplix.encryption.configurable.keys` 설정 확인

### 스케줄러 미실행

**증상**: 키 로테이션이 실행되지 않음

**원인**:
- `@EnableScheduling` 누락
- ShedLock 설정 누락

**해결**:
1. 메인 클래스에 `@EnableScheduling` 추가
2. ShedLock 의존성 및 테이블 확인

---

## 관련 문서

- [애플리케이션 구현 가이드](./jwe-key-rolling-implementation.md)
- [simplix-encryption 문서](/ko/encryption/)
