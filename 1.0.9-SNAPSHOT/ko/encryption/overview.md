# SimpliX Encryption Module Overview

## Architecture

```
+-------------------------------------------------------------+
|                      Application Layer                       |
|  +-----------------+  +---------------------------------+   |
|  |   Entity        |  |   Service                       |   |
|  |   @Convert      |  |   encryptionService.encrypt()   |   |
|  +--------+--------+  +----------------+----------------+   |
+-----------+----------------------------+--------------------+
            |                            |
            v                            v
+-------------------------------------------------------------+
|                    Encryption Service                        |
|  +-------------------------------------------------------+  |
|  |  AES/GCM/NoPadding (256-bit)                          |  |
|  |  - encrypt(plainText) -> version:iv:ciphertext        |  |
|  |  - decrypt(encryptedData) -> plainText                |  |
|  +-------------------------+-----------------------------+  |
+----------------------------+---------------------------------+
                             |
                             v
+-------------------------------------------------------------+
|                      KeyProvider                             |
|  +----------+ +--------------+ +---------+ +-------------+  |
|  | Simple   | | Configurable | | Managed | |    Vault    |  |
|  | (dev)    | | (config)     | | (file)  | |   (prod)    |  |
|  +----------+ +--------------+ +---------+ +-------------+  |
+-------------------------------------------------------------+
```

---

## Core Components

### EncryptionService

암호화/복호화의 주 진입점입니다:

```java
@Service
public class EncryptionService {
    // 암호화
    EncryptedData encrypt(String plainText);

    // 복호화
    String decrypt(String encryptedData);

    // 재암호화 (현재 키로)
    String reencrypt(String encryptedData);

    // 암호화 여부 확인
    boolean isEncrypted(String data);

    // 키 버전 추출
    String getKeyVersion(String encryptedData);

    // 설정 검증
    boolean isConfigured();
}
```

### KeyProvider Interface

키 관리의 핵심 추상화:

```java
public interface KeyProvider {
    SecretKey getCurrentKey();           // 현재 암호화 키
    SecretKey getKey(String version);    // 특정 버전의 복호화 키
    String getCurrentVersion();          // 현재 키 버전
    String rotateKey();                  // 새 키로 로테이션
    boolean isConfigured();              // 설정 완료 여부
    String getName();                    // Provider 이름
    Map<String, Object> getKeyStatistics();  // 통계 정보
}
```

### KeyProvider Implementations

| Provider | 용도 | 키 저장소 | 로테이션 |
|----------|------|----------|----------|
| SimpleKeyProvider | 개발/테스트 | 메모리 | 불가 |
| ConfigurableKeyProvider | 설정 기반 | YAML | 수동 (재시작) |
| ManagedKeyProvider | 파일 기반 | 로컬 파일 | 자동/수동 |
| VaultKeyProvider | 운영 환경 | HashiCorp Vault | 자동/수동 |

### AesEncryptionConverter

JPA Entity 필드 자동 암호화:

```java
@Entity
public class User {
    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "email", length = 500)
    private String email;  // 자동 암호화/복호화
}
```

---

## Encrypted Data Format

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

---

## Configuration Properties

### 전체 설정 구조

```yaml
simplix:
  encryption:
    # 기본 설정
    enabled: true                    # 암호화 모듈 활성화
    provider: simple                 # simple, configurable, managed, vault

    # SimpleKeyProvider 설정
    static-key: my-secret-key        # SHA-256 해싱되어 AES-256 키로 변환

    simple:
      allow-rotation: false          # 로테이션 허용 여부

    # ConfigurableKeyProvider 설정
    configurable:
      current-version: v2            # 현재 암호화에 사용할 버전
      keys:
        v1:
          key: "Base64EncodedKey32Bytes=="
          deprecated: true           # 복호화만 가능
        v2:
          key: "AnotherBase64Key32Bytes="
          deprecated: false

    # ManagedKeyProvider 설정
    key-store-path: /var/simplix/encryption/keys
    master-key: ${ENCRYPTION_MASTER_KEY}
    salt: ${ENCRYPTION_SALT}

    # VaultKeyProvider 설정
    vault:
      enabled: true
      path: secret/encryption
      namespace: my-namespace        # Enterprise only

    # 키 로테이션 설정
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
    rotation-cron: "0 0 2 * * ?"
```

### 환경 변수 매핑

| 설정 | 환경 변수 |
|------|----------|
| `provider` | `SIMPLIX_ENCRYPTION_PROVIDER` |
| `static-key` | `SIMPLIX_ENCRYPTION_STATIC_KEY` |
| `master-key` | `SIMPLIX_ENCRYPTION_MASTER_KEY` |
| `salt` | `SIMPLIX_ENCRYPTION_SALT` |
| `key-store-path` | `SIMPLIX_ENCRYPTION_KEY_STORE_PATH` |
| `configurable.current-version` | `SIMPLIX_ENCRYPTION_CURRENT_VERSION` |
| `vault.enabled` | `SIMPLIX_ENCRYPTION_VAULT_ENABLED` |
| `vault.namespace` | `SIMPLIX_ENCRYPTION_VAULT_NAMESPACE` |
| `rotation.enabled` | `SIMPLIX_ENCRYPTION_ROTATION_ENABLED` |
| `rotation.days` | `SIMPLIX_ENCRYPTION_ROTATION_DAYS` |
| `auto-rotation` | `SIMPLIX_ENCRYPTION_AUTO_ROTATION` |

---

## Environment-specific Configuration

### 개발 환경 (dev / local / test)

```yaml
simplix:
  encryption:
    provider: simple
    static-key: dev-encryption-key-for-testing
    vault:
      enabled: false
    rotation:
      enabled: false
```

### 스테이징 환경

```yaml
simplix:
  encryption:
    provider: ${SIMPLIX_ENCRYPTION_PROVIDER:managed}
    rotation:
      enabled: true
      days: 30
    auto-rotation: true
```

### 운영 환경 (prod)

```yaml
simplix:
  encryption:
    provider: vault
    vault:
      enabled: true
      path: secret/encryption
    rotation:
      enabled: true
      days: 90
    auto-rotation: true

spring:
  cloud:
    vault:
      uri: ${VAULT_ADDR}
      authentication: KUBERNETES
      kubernetes:
        role: my-app
```

### Kubernetes 환경

```yaml
simplix:
  encryption:
    provider: configurable
    configurable:
      current-version: ${ENCRYPTION_CURRENT_VERSION}
      keys:
        v1:
          key: ${ENCRYPTION_KEY_V1}
          deprecated: true
        v2:
          key: ${ENCRYPTION_KEY_V2}
          deprecated: false
```

**Kubernetes Secret:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: encryption-keys
type: Opaque
stringData:
  ENCRYPTION_CURRENT_VERSION: "v2"
  ENCRYPTION_KEY_V1: "Base64EncodedOldKey32BytesHere="
  ENCRYPTION_KEY_V2: "Base64EncodedNewKey32BytesHere="
```

---

## Spring Cloud Vault Configuration

VaultKeyProvider 사용 시 Spring Cloud Vault 설정이 필요합니다:

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.example.com:8200
      authentication: TOKEN  # TOKEN, APPROLE, KUBERNETES, AWS_IAM

      # 토큰 인증
      token: ${VAULT_TOKEN}

      # AppRole 인증
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
        app-role-path: approle

      # Kubernetes 인증
      kubernetes:
        role: my-app
        kubernetes-path: kubernetes
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token

      # KV 시크릿 엔진
      kv:
        enabled: true
        backend: secret

      # SSL 설정
      ssl:
        trust-store: classpath:vault-truststore.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
```

---

## KeyProvider Selection Guide

### Decision Tree

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

### 환경별 권장 KeyProvider

| 환경 | KeyProvider | 설명 |
|------|-------------|------|
| 로컬 개발 | SimpleKeyProvider | 설정 간단, 즉시 사용 가능 |
| 테스트 | SimpleKeyProvider | 고정 키로 테스트 재현성 보장 |
| 스테이징 | ConfigurableKeyProvider 또는 ManagedKeyProvider | 운영 환경 시뮬레이션 |
| 운영 | VaultKeyProvider | 중앙 집중식 키 관리, 감사 로그 |

---

## Validation Rules (ConfigurableKeyProvider)

초기화 시 다음 항목이 검증됩니다:

| 검증 항목 | 실패 조건 | 에러 메시지 |
|----------|----------|------------|
| current-version | null 또는 빈 문자열 | "current-version must be specified" |
| current-version 존재 | keys에 없음 | "current-version 'X' does not exist in keys" |
| current-version deprecated | deprecated=true | "current-version 'X' cannot be deprecated" |
| keys | 비어있음 | "At least one key must be configured" |
| 키 값 | null 또는 빈 문자열 | "Key for version 'X' is empty" |
| 키 포맷 | 유효하지 않은 Base64 | "Key for version 'X' is not valid Base64" |
| 키 크기 | 32바이트가 아님 | "Key for version 'X' must be 32 bytes" |

---

## Related Documents

- [KeyProvider 가이드](key-providers.md) - 환경별 KeyProvider 상세 설정
- [JPA Converter 사용법](jpa-converter.md) - Entity 필드 자동 암호화
- [키 로테이션 가이드](key-rotation.md) - 키 교체 및 데이터 마이그레이션
- [보안 모범 사례](security-best-practices.md) - 운영 환경 보안 권장사항
