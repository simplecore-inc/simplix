# KeyProvider 가이드

KeyProvider는 암호화 키의 생성, 저장, 조회, 로테이션을 담당하는 핵심 컴포넌트입니다. 환경에 따라 적절한 KeyProvider를 선택하세요.

## KeyProvider 비교

| 특성 | SimpleKeyProvider | ConfigurableKeyProvider | ManagedKeyProvider | VaultKeyProvider |
|------|-------------------|-------------------------|-------------------|------------------|
| **용도** | 개발/테스트 | 설정 기반 다중 키 | 파일 기반 | 운영 환경 |
| **프로파일** | dev, test, local | 명시적 설정 | file-based, managed | prod, staging |
| **키 저장소** | 메모리 | YAML 설정 | 로컬 파일 | HashiCorp Vault |
| **키 로테이션** | 불가 | 수동 (재시작) | 자동/수동 | 자동/수동 |
| **다중 인스턴스** | N/A | 동일 설정 배포 | ShedLock 필요 | 자동 동기화 |
| **보안 수준** | 낮음 | 중간 | 중간 | 높음 |
| **외부 의존성** | 없음 | 없음 | 없음 (ShedLock 선택) | Vault 필수 |

## 1. SimpleKeyProvider

### 개요

개발 및 테스트 환경을 위한 가장 간단한 KeyProvider입니다. 설정 문자열에서 SHA-256 해싱을 통해 AES-256 키를 생성합니다.

### 특징

- 고정 키 버전: `static`
- 키 로테이션 미지원
- 설정 즉시 사용 가능
- **운영 환경 사용 금지**

### 설정

```yaml
simplix:
  encryption:
    provider: simple
    static-key: my-development-key-minimum-16-chars

    simple:
      allow-rotation: false  # 항상 false 권장
```

### 환경 변수

```bash
SIMPLIX_ENCRYPTION_PROVIDER=simple
SIMPLIX_ENCRYPTION_STATIC_KEY=my-development-key
```

### 키 생성 과정

```
Input: "my-development-key"
    |
    v
SHA-256 hashing
    |
    v
32 bytes (256 bits) AES key
    |
    v
Version: "static"
```

### 주의사항

- 키 문자열이 16자 미만이면 경고 로그 출력
- 기본 키(`dev-default-key-do-not-use-in-production`) 사용 시 경고
- 시작 시 `⚠ SimpleKeyProvider initialized - DO NOT USE IN PRODUCTION` 로그 출력

---

## 2. ConfigurableKeyProvider

### 개요

YAML 설정 파일을 통해 여러 버전의 키를 관리합니다. Vault 없이도 키 버전 관리가 필요한 환경에 적합합니다.

### 특징

- 다중 키 버전 지원
- Deprecated 키 지원 (복호화 전용)
- Base64 인코딩된 키 직접 지정
- Kubernetes ConfigMap/Secret 연동 용이
- 키 변경 시 애플리케이션 재시작 필요

### 설정

```yaml
simplix:
  encryption:
    provider: configurable

    configurable:
      current-version: v2  # 암호화에 사용할 버전

      keys:
        v1:
          # Base64 인코딩된 32바이트 AES-256 키
          key: "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
          deprecated: true  # 복호화만 가능

        v2:
          key: "eHl6MTIzNDU2YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4"
          deprecated: false  # 암호화/복호화 모두 가능

        v3:
          key: "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnN0dXY="
          deprecated: false
```

### 환경 변수 사용

민감한 키 값은 환경 변수로 주입할 수 있습니다:

```yaml
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: ${ENCRYPTION_CURRENT_VERSION:v2}
      keys:
        v1:
          key: ${ENCRYPTION_KEY_V1}
          deprecated: true
        v2:
          key: ${ENCRYPTION_KEY_V2}
```

### AES-256 키 생성 방법

32바이트 랜덤 키를 생성하고 Base64로 인코딩합니다:

```java
// Java
import java.security.SecureRandom;
import java.util.Base64;

SecureRandom random = new SecureRandom();
byte[] key = new byte[32];
random.nextBytes(key);
String base64Key = Base64.getEncoder().encodeToString(key);
System.out.println(base64Key);
```

```bash
# Linux/macOS
openssl rand -base64 32
```

### 동작 규칙

| 작업 | 사용 키 | 조건 |
|------|---------|------|
| 암호화 | `current-version` 키 | deprecated가 false여야 함 |
| 복호화 | 데이터의 버전에 해당하는 키 | deprecated여도 사용 가능 |

### 검증 규칙

초기화 시 다음을 검증합니다:

1. `current-version` 필수
2. `current-version`이 `keys`에 존재해야 함
3. `current-version` 키는 `deprecated: false`여야 함
4. 모든 키는 유효한 Base64
5. 모든 키는 디코딩 후 32바이트

### 키 로테이션 절차

1. **새 키 추가**
   ```yaml
   keys:
     v2:
       key: "기존키"
       deprecated: true  # deprecated로 변경
     v3:
       key: "새로운키"
       deprecated: false  # 새 키 추가
   current-version: v3  # 버전 변경
   ```

2. **애플리케이션 재시작**

3. **(선택) 데이터 마이그레이션**
   - 기존 데이터를 새 키로 재암호화
   - 완료 후 이전 키 제거 가능

---

## 3. ManagedKeyProvider

### 개요

로컬 파일 시스템에 키를 저장하고 자동 로테이션을 지원합니다. Vault 없이 자동화된 키 관리가 필요한 환경에 적합합니다.

### 특징

- 파일 기반 키 영구 저장
- PBKDF2 키 유도 지원
- ShedLock을 통한 분산 환경 지원
- 자동 키 로테이션
- 모든 이전 키 영구 보관

### 설정

```yaml
simplix:
  encryption:
    provider: managed

    # 키 저장 경로 (필수)
    key-store-path: /var/simplix/encryption/keys

    # PBKDF2 키 유도 (선택)
    master-key: ${ENCRYPTION_MASTER_KEY}
    salt: ${ENCRYPTION_SALT}

    # 로테이션 설정
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
```

### 키 저장소 구조

```
/var/simplix/encryption/keys/
├── key_v1734567890123.key    # Base64(key):timestamp
├── key_v1734667890123.key
├── key_v1734767890123.key
└── metadata.json             # 메타데이터
```

**metadata.json 예시:**
```json
{
  "currentVersion": "v1734767890123",
  "lastRotation": "2025-01-15T10:30:00Z",
  "totalKeys": 3
}
```

### 키 생성 방식

| masterKey | salt | 키 생성 방식 |
|-----------|------|-------------|
| 있음 | 있음 | PBKDF2 유도 |
| 있음 | 없음 | 랜덤 생성 (경고) |
| 없음 | - | 랜덤 생성 (경고) |

### 파일 권한

Unix 계열 시스템에서 보안을 위해 파일 권한이 자동 설정됩니다:

- 키 파일: `600` (소유자만 읽기/쓰기)
- 디렉토리: `700` (소유자만 접근)

### 분산 환경 설정

여러 인스턴스가 동일한 키 저장소를 사용할 때 ShedLock이 필요합니다:

```yaml
# ShedLock 설정 (Redis 예시)
spring:
  data:
    redis:
      host: localhost
      port: 6379

shedlock:
  defaults:
    lock-at-most-for: 10m
```

---

## 4. VaultKeyProvider

### 개요

HashiCorp Vault와 연동하여 키를 중앙에서 관리합니다. 운영 환경에서 권장되는 KeyProvider입니다.

### 특징

- 중앙 집중식 키 관리
- Vault 감사 로그 자동 생성
- 다중 인스턴스 자동 동기화
- Lazy loading (필요 시 Vault에서 로드)
- 영구 캐싱 (메모리)
- 최고 수준 보안

### 사전 요구사항

1. HashiCorp Vault 서버
2. Spring Cloud Vault 의존성
3. Vault 인증 토큰 또는 AppRole

### 의존성 추가

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
}
```

### 설정

```yaml
# application-prod.yml
simplix:
  encryption:
    provider: vault
    vault:
      enabled: true
      path: secret/encryption
      namespace: ${VAULT_NAMESPACE:}  # Enterprise only

    rotation:
      enabled: true
      days: 90
    auto-rotation: true

# Spring Cloud Vault 설정
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      authentication: TOKEN  # 또는 APPROLE, KUBERNETES 등
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
```

### Vault 경로 구조

```
secret/encryption/keys/
├── current           # {"version": "v1734567890123", "rotatedAt": "..."}
├── v1734567890123    # {"key": "Base64Key", "algorithm": "AES", ...}
├── v1734667890123
└── v1734767890123
```

### Vault 정책 예시

```hcl
# encryption-app-policy.hcl
path "secret/data/encryption/keys/*" {
  capabilities = ["create", "read", "update", "list"]
}

path "secret/metadata/encryption/keys/*" {
  capabilities = ["list", "delete"]
}
```

### Kubernetes 환경

```yaml
# application-k8s.yml
spring:
  cloud:
    vault:
      authentication: KUBERNETES
      kubernetes:
        role: my-app
        kubernetes-path: kubernetes
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
```

### 다중 인스턴스 동기화

VaultKeyProvider는 `refreshCurrentVersion()` 메서드를 통해 주기적으로 Vault의 현재 버전을 확인합니다. 한 인스턴스에서 키 로테이션이 발생하면 다른 인스턴스들은 다음 요청 시 자동으로 새 버전을 감지합니다.

```
Instance A: rotateKey() --> Vault update
                |
                v
Vault: current = v2
                |
                v
Instance B: getCurrentKey() --> refreshCurrentVersion() --> v2 detected --> Load new key
```

---

## KeyProvider 선택 가이드

### 의사결정 트리

```
Is this a production environment?
+-- Yes --> Is Vault available?
|           +-- Yes --> VaultKeyProvider
|           +-- No  --> Is key rotation needed?
|                       +-- Yes --> ManagedKeyProvider
|                       +-- No  --> ConfigurableKeyProvider
|
+-- No --> Development/Test environment
           +-- SimpleKeyProvider
```

### 환경별 권장 구성

**로컬 개발:**
```yaml
simplix:
  encryption:
    provider: simple
    static-key: dev-key-for-local-testing
```

**CI/CD 테스트:**
```yaml
simplix:
  encryption:
    provider: simple
    static-key: ${TEST_ENCRYPTION_KEY:test-key-for-ci}
```

**스테이징:**
```yaml
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: v1
      keys:
        v1:
          key: ${STAGING_ENCRYPTION_KEY}
```

**운영:**
```yaml
simplix:
  encryption:
    provider: vault
    vault:
      enabled: true
```

---

## Related Documents

- [Overview (개요)](ko/encryption/overview.md) - 아키텍처 및 설정
- [JPA Converter 사용법](ko/encryption/jpa-converter.md) - Entity 필드 자동 암호화
- [키 로테이션 가이드](ko/encryption/key-rotation.md) - 키 교체 및 데이터 마이그레이션
- [보안 모범 사례](ko/encryption/security-best-practices.md) - 운영 환경 보안 권장사항