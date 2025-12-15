# 설정 레퍼런스

SimpliX Auth의 전체 설정 속성을 정리합니다. 모든 속성은 `simplix.auth` 프리픽스 아래에 위치합니다.

## 목차

- [기본 설정](#기본-설정)
- [JWE 설정](#jwe-설정)
- [키 롤링 설정](#키-롤링-설정)
- [토큰 설정](#토큰-설정)
- [보안 설정](#보안-설정)
- [CORS 설정](#cors-설정)
- [OAuth2 설정](#oauth2-설정)
- [전체 예제](#전체-예제)

## 기본 설정

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `simplix.auth.enabled` | boolean | `true` | SimpliX Auth 모듈 활성화 |

```yaml
simplix:
  auth:
    enabled: true
```

## JWE 설정

`simplix.auth.jwe.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `encryption-key` | String | - | JWE 암호화 키 (JWK JSON 형식) |
| `encryption-key-location` | String | - | JWE 키 파일 경로 (classpath:, file:) |
| `algorithm` | String | `RSA-OAEP-256` | 키 암호화 알고리즘 |
| `encryption-method` | String | `A256GCM` | 콘텐츠 암호화 방법 |

### 지원 알고리즘

**키 암호화 알고리즘 (alg):**
- `RSA-OAEP-256` (권장)
- `RSA-OAEP`
- `RSA1_5`

**콘텐츠 암호화 방법 (enc):**
- `A256GCM` (권장)
- `A192GCM`
- `A128GCM`
- `A256CBC-HS512`
- `A192CBC-HS384`
- `A128CBC-HS256`

```yaml
simplix:
  auth:
    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM
      # 개발 환경: 파일에서 키 로드
      encryption-key-location: classpath:keys/jwe-key.json
      # 또는 직접 키 지정 (권장하지 않음)
      # encryption-key: |
      #   {"kty":"RSA","n":"...","e":"...","d":"..."}
```

## 키 롤링 설정

`simplix.auth.jwe.key-rolling.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `enabled` | boolean | `false` | 키 롤링 활성화 |
| `key-size` | int | `2048` | RSA 키 크기 (비트) |
| `auto-initialize` | boolean | `true` | 키가 없을 때 자동 생성 |
| `retention.buffer-seconds` | int | `86400` | 키 만료 버퍼 시간 (초) |
| `retention.auto-cleanup` | boolean | `false` | 만료된 키 자동 삭제 |

```yaml
simplix:
  auth:
    jwe:
      key-rolling:
        enabled: true
        key-size: 2048
        auto-initialize: true
        retention:
          buffer-seconds: 86400  # 1일
          auto-cleanup: false
```

**키 크기 권장사항:**
- `2048`: 최소 보안 수준 (2030년까지 권장)
- `3072`: 중간 보안 수준
- `4096`: 높은 보안 수준 (성능 영향)

## 토큰 설정

`simplix.auth.token.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `access-token-lifetime` | int | `1800` | Access 토큰 수명 (초, 30분) |
| `refresh-token-lifetime` | int | `604800` | Refresh 토큰 수명 (초, 7일) |
| `enable-ip-validation` | boolean | `false` | IP 주소 검증 활성화 |
| `enable-user-agent-validation` | boolean | `false` | User-Agent 검증 활성화 |
| `enable-token-rotation` | boolean | `true` | 토큰 로테이션 활성화 |
| `enable-blacklist` | boolean | `false` | 토큰 블랙리스트 활성화 |
| `create-session-on-token-issue` | boolean | `true` | 토큰 발급 시 세션 생성 |

```yaml
simplix:
  auth:
    token:
      access-token-lifetime: 1800      # 30분
      refresh-token-lifetime: 604800   # 7일
      enable-ip-validation: false
      enable-user-agent-validation: false
      enable-token-rotation: true
      enable-blacklist: true
      create-session-on-token-issue: true
```

**토큰 수명 권장값:**

| 환경 | Access Token | Refresh Token |
|------|--------------|---------------|
| 높은 보안 | 5-15분 | 1-7일 |
| 일반 | 30분-1시간 | 7-30일 |
| 편의 중심 | 1-24시간 | 30-90일 |

## 보안 설정

`simplix.auth.security.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `enable-token-endpoints` | boolean | `true` | 토큰 엔드포인트 활성화 |
| `enable-web-security` | boolean | `true` | 웹 보안 (폼 로그인) 활성화 |
| `enable-cors` | boolean | `true` | CORS 활성화 |
| `enable-csrf` | boolean | `true` | CSRF 보호 활성화 |
| `enable-http-basic` | boolean | `false` | HTTP Basic 인증 활성화 |
| `require-https` | boolean | `false` | HTTPS 강제 |
| `prefer-token-over-session` | boolean | `true` | 토큰 인증 우선 |
| `csrf-ignore-patterns` | String[] | `/api/token/**`, `/h2-console/**` | CSRF 제외 경로 |
| `login-page-template` | String | `login` | 로그인 페이지 경로 |
| `login-processing-url` | String | `/login` | 로그인 처리 URL |
| `logout-url` | String | `/logout` | 로그아웃 URL |
| `permit-all-patterns` | String[] | - | 인증 불필요 경로 |

```yaml
simplix:
  auth:
    security:
      enable-token-endpoints: true
      enable-web-security: true
      enable-cors: true
      enable-csrf: true
      enable-http-basic: false
      require-https: false
      prefer-token-over-session: true
      csrf-ignore-patterns:
        - /api/token/**
        - /h2-console/**
        - /api/webhook/**
      login-page-template: /login
      login-processing-url: /login
      logout-url: /logout
      permit-all-patterns:
        - /api/public/**
        - /swagger-ui/**
        - /v3/api-docs/**
        - /actuator/health
```

## CORS 설정

`simplix.auth.cors.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `allowed-origins` | String[] | - | 허용 오리진 |
| `allowed-methods` | String[] | - | 허용 HTTP 메서드 |
| `allowed-headers` | String[] | - | 허용 요청 헤더 |
| `exposed-headers` | String[] | - | 노출 응답 헤더 |
| `allow-credentials` | Boolean | - | 인증 정보 허용 |
| `max-age` | Long | - | Preflight 캐시 시간 (초) |

```yaml
simplix:
  auth:
    cors:
      allowed-origins:
        - http://localhost:3000
        - https://your-frontend.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - PATCH
        - OPTIONS
      allowed-headers:
        - Authorization
        - Content-Type
        - X-Refresh-Token
        - X-Requested-With
      exposed-headers:
        - X-Total-Count
        - X-Page-Number
      allow-credentials: true
      max-age: 3600
```

## OAuth2 설정

`simplix.auth.oauth2.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `enabled` | boolean | `true` | OAuth2 활성화 |
| `success-url` | String | `/` | 로그인 성공 리다이렉트 |
| `failure-url` | String | `/login?error=social` | 로그인 실패 리다이렉트 |
| `link-success-url` | String | `/settings/social?linked=true` | 연동 성공 리다이렉트 |
| `link-failure-url` | String | `/settings/social?error=link_failed` | 연동 실패 리다이렉트 |
| `link-base-url` | String | `/oauth2/link` | 계정 연동 기본 URL |
| `authorization-base-url` | String | `/oauth2/authorize` | 인증 시작 기본 URL |
| `callback-base-url` | String | `/oauth2/callback` | 콜백 기본 URL |
| `login-base-url` | String | `/oauth2/login` | 로그인 전용 기본 URL |
| `register-base-url` | String | `/oauth2/register` | 회원가입 전용 기본 URL |
| `email-conflict-policy` | Enum | `REJECT` | 이메일 충돌 정책 |
| `token-delivery-method` | Enum | `COOKIE` | 토큰 전달 방식 |
| `allowed-origins` | List | - | postMessage 허용 오리진 |
| `pending-registration-ttl-seconds` | long | `600` | 대기 중 등록 정보 TTL |

### 이메일 충돌 정책 (EmailConflictPolicy)

| 값 | 설명 |
|----|------|
| `REJECT` | 충돌 시 거부 (권장) |
| `AUTO_LINK` | 자동 계정 연동 |

### 토큰 전달 방식 (TokenDeliveryMethod)

| 값 | 설명 |
|----|------|
| `REDIRECT` | URL 쿼리 파라미터 |
| `COOKIE` | HttpOnly 쿠키 (권장) |
| `POST_MESSAGE` | window.postMessage (SPA) |

### OAuth2 쿠키 설정

`simplix.auth.oauth2.cookie.*`

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `access-token-name` | String | `access_token` | Access 토큰 쿠키명 |
| `refresh-token-name` | String | `refresh_token` | Refresh 토큰 쿠키명 |
| `path` | String | `/` | 쿠키 경로 |
| `http-only` | boolean | `true` | HttpOnly 플래그 |
| `secure` | boolean | `true` | Secure 플래그 |
| `same-site` | String | `Lax` | SameSite 속성 |

```yaml
simplix:
  auth:
    oauth2:
      enabled: true
      success-url: /dashboard
      failure-url: /login?error=social
      token-delivery-method: COOKIE
      email-conflict-policy: REJECT
      cookie:
        access-token-name: access_token
        refresh-token-name: refresh_token
        path: /
        http-only: true
        secure: true
        same-site: Lax
      allowed-origins:
        - http://localhost:3000
        - https://your-spa.com
```

## 전체 예제

### 개발 환경

```yaml
simplix:
  auth:
    enabled: true

    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM
      encryption-key-location: classpath:keys/dev-jwe-key.json

    token:
      access-token-lifetime: 3600      # 1시간
      refresh-token-lifetime: 604800   # 7일
      enable-blacklist: false
      create-session-on-token-issue: true

    security:
      enable-token-endpoints: true
      enable-web-security: true
      enable-cors: true
      enable-csrf: false    # 개발 시 편의를 위해
      require-https: false
      permit-all-patterns:
        - /api/public/**
        - /swagger-ui/**
        - /v3/api-docs/**
        - /h2-console/**

    cors:
      allowed-origins:
        - http://localhost:3000
        - http://localhost:5173
      allow-credentials: true

    oauth2:
      enabled: true
      token-delivery-method: POST_MESSAGE
      allowed-origins:
        - http://localhost:3000
```

### 프로덕션 환경

```yaml
simplix:
  auth:
    enabled: true

    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM
      key-rolling:
        enabled: true
        key-size: 2048
        auto-initialize: true
        retention:
          buffer-seconds: 86400
          auto-cleanup: true

    token:
      access-token-lifetime: 900       # 15분
      refresh-token-lifetime: 604800   # 7일
      enable-ip-validation: false
      enable-user-agent-validation: false
      enable-token-rotation: true
      enable-blacklist: true
      create-session-on-token-issue: true

    security:
      enable-token-endpoints: true
      enable-web-security: true
      enable-cors: true
      enable-csrf: true
      require-https: true
      prefer-token-over-session: true
      csrf-ignore-patterns:
        - /api/**
      permit-all-patterns:
        - /api/public/**
        - /api/health
        - /actuator/health

    cors:
      allowed-origins:
        - https://your-frontend.com
        - https://admin.your-domain.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - PATCH
      allowed-headers:
        - Authorization
        - Content-Type
        - X-Refresh-Token
      allow-credentials: true
      max-age: 3600

    oauth2:
      enabled: true
      success-url: /dashboard
      failure-url: /login?error=social
      token-delivery-method: COOKIE
      email-conflict-policy: REJECT
      cookie:
        http-only: true
        secure: true
        same-site: Strict
```

## 환경 변수 사용

민감한 설정은 환경 변수로 관리하세요:

```yaml
simplix:
  auth:
    jwe:
      encryption-key-location: ${JWE_KEY_LOCATION:classpath:keys/jwe-key.json}

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
```

## 관련 문서

- [시작하기](getting-started.md)
- [JWE 토큰 인증](jwe-token.md)
- [토큰 블랙리스트](token-blacklist.md)
- [OAuth2 소셜 로그인](oauth2.md)
- [보안 설정](security-configuration.md)
- [JWE 키 롤링](jwe-key-rolling.md)
