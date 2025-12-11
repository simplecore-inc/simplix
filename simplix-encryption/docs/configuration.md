# 설정 레퍼런스

SimpliX Encryption 모듈의 전체 설정 옵션을 설명합니다.

## 설정 프리픽스

모든 설정은 `simplix.encryption` 프리픽스를 사용합니다.

## 전체 설정 옵션

```yaml
simplix:
  encryption:
    # ==========================================
    # 기본 설정
    # ==========================================

    # 암호화 모듈 활성화 여부
    # 타입: boolean
    # 기본값: true
    enabled: true

    # KeyProvider 타입 선택
    # 타입: string
    # 옵션: simple, configurable, managed, vault
    # 기본값: 프로파일에 따라 자동 선택
    provider: simple

    # ==========================================
    # SimpleKeyProvider 설정
    # ==========================================

    # 정적 키 문자열 (SimpleKeyProvider용)
    # SHA-256 해싱을 통해 AES-256 키로 변환됨
    # 타입: string
    # 기본값: dev-default-key-do-not-use-in-production
    # 환경변수: SIMPLIX_ENCRYPTION_STATIC_KEY
    static-key: my-secret-key

    simple:
      # 로테이션 허용 여부 (SimpleKeyProvider는 실제 로테이션 미지원)
      # 타입: boolean
      # 기본값: false
      allow-rotation: false

    # ==========================================
    # ConfigurableKeyProvider 설정
    # ==========================================

    configurable:
      # 현재 암호화에 사용할 키 버전
      # 타입: string
      # 필수: provider=configurable인 경우
      # 환경변수: SIMPLIX_ENCRYPTION_CURRENT_VERSION
      current-version: v2

      # 키 버전별 설정
      # 타입: Map<String, KeyConfig>
      keys:
        v1:
          # Base64 인코딩된 AES-256 키 (32바이트)
          # 타입: string
          # 필수: true
          key: "Base64EncodedKey32BytesHere=="

          # Deprecated 여부 (true면 복호화만 가능)
          # 타입: boolean
          # 기본값: false
          deprecated: true

        v2:
          key: "AnotherBase64EncodedKey32Bytes="
          deprecated: false

    # ==========================================
    # ManagedKeyProvider 설정
    # ==========================================

    # 키 저장소 경로
    # 타입: string
    # 환경변수: SIMPLIX_ENCRYPTION_KEY_STORE_PATH
    key-store-path: /var/simplix/encryption/keys

    # 마스터 키 (PBKDF2 키 유도용)
    # 타입: string
    # 환경변수: SIMPLIX_ENCRYPTION_MASTER_KEY
    master-key: ${MASTER_KEY}

    # 솔트 (PBKDF2 키 유도용)
    # 타입: string
    # 환경변수: SIMPLIX_ENCRYPTION_SALT
    salt: ${ENCRYPTION_SALT}

    # ==========================================
    # VaultKeyProvider 설정
    # ==========================================

    vault:
      # Vault 연동 활성화
      # 타입: boolean
      # 기본값: true (prod/staging 프로파일)
      # 환경변수: SIMPLIX_ENCRYPTION_VAULT_ENABLED
      enabled: true

      # Vault 키 저장 경로
      # 타입: string
      # 기본값: secret/encryption
      path: secret/encryption

      # Vault 네임스페이스 (Enterprise 전용)
      # 타입: string
      # 환경변수: SIMPLIX_ENCRYPTION_VAULT_NAMESPACE
      namespace: my-namespace

    # ==========================================
    # 키 로테이션 설정
    # ==========================================

    rotation:
      # 키 로테이션 활성화
      # 타입: boolean
      # 기본값: false
      # 환경변수: SIMPLIX_ENCRYPTION_ROTATION_ENABLED
      enabled: true

      # 로테이션 주기 (일)
      # 타입: int
      # 기본값: 90
      # 환경변수: SIMPLIX_ENCRYPTION_ROTATION_DAYS
      days: 90

    # 자동 키 로테이션 활성화
    # 타입: boolean
    # 기본값: false
    # 환경변수: SIMPLIX_ENCRYPTION_AUTO_ROTATION
    auto-rotation: true

    # 자동 로테이션 스케줄 (cron 표현식)
    # 타입: string
    # 기본값: "0 0 2 * * ?" (매일 새벽 2시)
    rotation-cron: "0 0 2 * * ?"
```

## 프로파일별 기본 설정

### dev / local / test 프로파일

```yaml
simplix:
  encryption:
    provider: simple
    vault:
      enabled: false
    rotation:
      enabled: false
```

### staging 프로파일

```yaml
simplix:
  encryption:
    provider: ${SIMPLIX_ENCRYPTION_PROVIDER:managed}
    rotation:
      enabled: true
      days: 30
    auto-rotation: true
```

### prod 프로파일

```yaml
simplix:
  encryption:
    enabled: true
    provider: vault
    vault:
      enabled: true
    rotation:
      enabled: true
      days: 90
    auto-rotation: true
```

## 환경 변수 매핑

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

## Spring Cloud Vault 설정 (VaultKeyProvider 사용 시)

VaultKeyProvider를 사용하려면 Spring Cloud Vault 설정이 필요합니다:

```yaml
spring:
  cloud:
    vault:
      # Vault 서버 URI
      uri: https://vault.example.com:8200

      # 인증 방식
      authentication: TOKEN  # TOKEN, APPROLE, KUBERNETES, AWS_IAM 등

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

      # KV 시크릿 엔진 설정
      kv:
        enabled: true
        backend: secret
        default-context: application
        application-name: my-app

      # 연결 설정
      connection-timeout: 5000
      read-timeout: 15000

      # SSL 설정
      ssl:
        trust-store: classpath:vault-truststore.jks
        trust-store-password: ${VAULT_TRUSTSTORE_PASSWORD}
```

## 설정 검증

### ConfigurableKeyProvider 검증

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

### 설정 예시 - 개발 환경

```yaml
# application-dev.yml
simplix:
  encryption:
    enabled: true
    provider: simple
    static-key: dev-encryption-key-for-testing
```

### 설정 예시 - Kubernetes 환경

```yaml
# application-k8s.yml
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

### 설정 예시 - 운영 환경 (Vault)

```yaml
# application-prod.yml
simplix:
  encryption:
    provider: vault
    vault:
      enabled: true
      path: secret/data/encryption
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