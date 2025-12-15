# JWE 토큰 인증

SimpliX Auth는 JWT 대신 JWE(JSON Web Encryption)를 사용하여 토큰 기반 인증을 제공합니다. JWE는 토큰 페이로드를 암호화하여 클레임 정보가 외부에 노출되지 않도록 보호합니다.

## 목차

- [JWE vs JWT](#jwe-vs-jwt)
- [토큰 구조](#토큰-구조)
- [토큰 발급](#토큰-발급)
- [토큰 검증](#토큰-검증)
- [토큰 갱신](#토큰-갱신)
- [토큰 폐기](#토큰-폐기)
- [클레임 구조](#클레임-구조)
- [보안 검증 옵션](#보안-검증-옵션)
- [커스터마이징](#커스터마이징)

## JWE vs JWT

| 특성 | JWT (서명) | JWE (암호화) |
|------|-----------|-------------|
| 페이로드 가시성 | Base64로 인코딩 (누구나 읽기 가능) | 암호화됨 (키 없이 읽기 불가) |
| 무결성 보장 | 서명으로 보장 | 암호화로 보장 |
| 클레임 보호 | 노출됨 | 보호됨 |
| 사용 사례 | 일반적인 인증 | 민감한 정보 포함 시 |

SimpliX Auth는 JWE를 기본으로 사용하여 토큰 내의 사용자 정보(IP, User-Agent 등)가 외부에 노출되지 않도록 합니다.

## 토큰 구조

JWE 토큰은 5개의 파트로 구성됩니다:

```
eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidjEifQ.
<encrypted-key>.
<initialization-vector>.
<ciphertext>.
<authentication-tag>
```

### 헤더 예시

```json
{
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM",
  "kid": "v1"
}
```

- `alg`: 키 암호화 알고리즘 (RSA-OAEP-256)
- `enc`: 콘텐츠 암호화 방법 (A256GCM)
- `kid`: 키 ID (키 롤링 시 어떤 키로 복호화할지 식별)

## 토큰 발급

### REST API

**엔드포인트:** `GET /auth/token/issue`

Basic 인증 헤더를 사용하여 토큰을 발급받습니다:

```bash
curl -X GET "http://localhost:8080/auth/token/issue" \
  -H "Authorization: Basic $(echo -n 'username:password' | base64)" \
  -H "User-Agent: MyApp/1.0"
```

**응답:**
```json
{
  "accessToken": "eyJhbGciOiJSU0EtT0FFUC0yNTYi...",
  "refreshToken": "eyJhbGciOiJSU0EtT0FFUC0yNTYi...",
  "accessTokenExpiry": "2024-01-15T10:30:00+09:00",
  "refreshTokenExpiry": "2024-01-22T10:00:00+09:00"
}
```

### 프로그래밍 방식

서비스 내에서 직접 토큰을 발급할 수도 있습니다:

```java
@Service
public class AuthService {

    private final SimpliXJweTokenProvider tokenProvider;

    public TokenResponse issueTokens(String username, String clientIp, String userAgent) {
        try {
            return tokenProvider.createTokenPair(username, clientIp, userAgent);
        } catch (JOSEException e) {
            throw new RuntimeException("Token generation failed", e);
        }
    }
}
```

## 토큰 검증

### 자동 검증 (필터)

`SimpliXTokenAuthenticationFilter`가 자동으로 토큰을 검증합니다:

1. `Authorization: Bearer {token}` 헤더에서 토큰 추출
2. 쿠키에서 토큰 추출 (OAuth2 쿠키 모드 사용 시)
3. 토큰 복호화 및 클레임 검증
4. `SecurityContext`에 인증 정보 설정

### 수동 검증

특정 상황에서 직접 토큰을 검증해야 할 경우:

```java
@Service
public class TokenValidationService {

    private final SimpliXJweTokenProvider tokenProvider;

    public boolean validateToken(String token, String clientIp, String userAgent) {
        try {
            return tokenProvider.validateToken(token, clientIp, userAgent);
        } catch (TokenValidationException e) {
            // 토큰 만료, 블랙리스트, IP 불일치 등
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String extractUsername(String token) {
        try {
            JWTClaimsSet claims = tokenProvider.parseToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token", e);
        }
    }
}
```

## 토큰 갱신

### REST API

**엔드포인트:** `GET /auth/token/refresh`

```bash
curl -X GET "http://localhost:8080/auth/token/refresh" \
  -H "X-Refresh-Token: eyJhbGciOiJSU0EtT0FFUC0yNTYi..." \
  -H "User-Agent: MyApp/1.0"
```

갱신 과정:
1. Refresh 토큰 검증
2. 새로운 Access/Refresh 토큰 쌍 발급
3. 이전 Refresh 토큰 블랙리스트 등록 (활성화 시)
4. 세션 갱신 (설정된 경우)

### 토큰 로테이션

`enable-token-rotation: true` 설정 시:
- 토큰 갱신마다 새로운 Refresh 토큰 발급
- 이전 Refresh 토큰은 블랙리스트에 등록
- Replay 공격 방지

```yaml
simplix:
  auth:
    token:
      enable-token-rotation: true
      enable-blacklist: true  # 로테이션 사용 시 필요
```

## 토큰 폐기

### REST API

**엔드포인트:** `POST /auth/token/revoke`

```bash
curl -X POST "http://localhost:8080/auth/token/revoke" \
  -H "Authorization: Bearer {access_token}" \
  -H "X-Refresh-Token: {refresh_token}"
```

토큰 폐기 시:
1. Access 토큰의 JTI를 블랙리스트에 등록
2. Refresh 토큰의 JTI를 블랙리스트에 등록
3. SecurityContext 클리어
4. 세션 무효화

### 프로그래밍 방식

```java
@Service
public class LogoutService {

    private final SimpliXJweTokenProvider tokenProvider;

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            tokenProvider.revokeToken(accessToken);
        }
        if (refreshToken != null) {
            tokenProvider.revokeToken(refreshToken);
        }
    }
}
```

## 클레임 구조

JWE 토큰 내의 클레임:

```json
{
  "sub": "username",
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1705280400,
  "exp": 1705282200,
  "clientIp": "192.168.1.100",
  "userAgent": "Mozilla/5.0..."
}
```

| 클레임 | 설명 |
|--------|------|
| `sub` | 사용자 식별자 (username) |
| `jti` | 토큰 고유 ID (블랙리스트에 사용) |
| `iat` | 발급 시간 |
| `exp` | 만료 시간 |
| `clientIp` | 토큰 발급 시 클라이언트 IP |
| `userAgent` | 토큰 발급 시 User-Agent |

## 보안 검증 옵션

### IP 검증

토큰 발급 시의 IP와 요청 IP가 일치하는지 검증:

```yaml
simplix:
  auth:
    token:
      enable-ip-validation: true
```

**주의:** NAT, 프록시 환경에서 IP가 변경될 수 있으므로 신중히 사용하세요.

### User-Agent 검증

토큰 발급 시의 User-Agent와 요청 User-Agent가 일치하는지 검증:

```yaml
simplix:
  auth:
    token:
      enable-user-agent-validation: true
```

**주의:** 브라우저 업데이트 등으로 User-Agent가 변경될 수 있습니다.

## 커스터마이징

### 인증 성공/실패 핸들러

토큰 발급 성공/실패 시 커스텀 로직을 실행할 수 있습니다:

```java
@Configuration
public class AuthHandlerConfig {

    @Bean(name = "tokenAuthenticationSuccessHandler")
    public AuthenticationSuccessHandler tokenSuccessHandler(
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {

        return (request, response, authentication) -> {
            String username = authentication.getName();

            // 마지막 로그인 시간 업데이트
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setLastLoginAt(LocalDateTime.now());
                userRepository.save(user);
            });

            // 이벤트 발행
            eventPublisher.publishEvent(new TokenIssuedEvent(username));

            // 응답은 컨트롤러에서 처리하므로 여기서는 작성하지 않음
        };
    }

    @Bean(name = "tokenAuthenticationFailureHandler")
    public AuthenticationFailureHandler tokenFailureHandler(
            LoginAttemptService loginAttemptService) {

        return (request, response, exception) -> {
            String username = extractUsername(request);

            if (username != null) {
                loginAttemptService.recordFailedAttempt(username);

                // 5회 실패 시 계정 잠금
                if (loginAttemptService.getFailedAttempts(username) >= 5) {
                    // 계정 잠금 처리
                }
            }

            // 응답은 예외 핸들러에서 처리
        };
    }
}
```

### 토큰 검증 우선순위

세션 인증과 토큰 인증 중 우선순위 설정:

```yaml
simplix:
  auth:
    security:
      prefer-token-over-session: true  # 토큰 우선 (기본값)
```

- `true`: 토큰이 있으면 세션 인증을 무시하고 토큰 인증 사용
- `false`: 세션 인증이 있으면 토큰 인증 스킵

## 관련 문서

- [토큰 블랙리스트](ko/auth/token-blacklist.md)
- [JWE 키 롤링](ko/auth/jwe-key-rolling.md)
- [설정 레퍼런스](ko/auth/configuration-reference.md)
