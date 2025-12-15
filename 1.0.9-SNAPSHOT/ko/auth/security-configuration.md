# 보안 설정

SimpliX Auth의 보안 관련 설정을 상세히 설명합니다. Spring Security 기반의 다양한 보안 기능을 구성할 수 있습니다.

## 목차

- [Security Filter Chain 구조](#security-filter-chain-구조)
- [토큰 엔드포인트 보안](#토큰-엔드포인트-보안)
- [API 보안](#api-보안)
- [웹 보안](#웹-보안)
- [CORS 설정](#cors-설정)
- [CSRF 설정](#csrf-설정)
- [HTTPS 설정](#https-설정)
- [세션 관리](#세션-관리)
- [예외 처리](#예외-처리)
- [Method Security](#method-security)

## Security Filter Chain 구조

SimpliX Auth는 3개의 Security Filter Chain을 순서대로 적용합니다:

```
Order 50:  tokenSecurityFilterChain  - 토큰 발급/갱신 엔드포인트
Order 100: apiSecurityFilterChain    - /api/** 엔드포인트
Order 102: webSecurityFilterChain    - 웹 페이지 (폼 로그인)
```

### Filter Chain 우선순위

```yaml
# 요청 예시
/auth/token/issue    -> tokenSecurityFilterChain (Order 50)
/api/users           -> apiSecurityFilterChain (Order 100)
/dashboard           -> webSecurityFilterChain (Order 102)
```

## 토큰 엔드포인트 보안

토큰 발급/갱신 엔드포인트의 보안 설정:

```yaml
simplix:
  auth:
    security:
      enable-token-endpoints: true  # 토큰 엔드포인트 활성화
```

### 보호되는 엔드포인트

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/auth/token/issue` | GET | Basic Auth | 토큰 발급 |
| `/auth/token/refresh` | GET | X-Refresh-Token | 토큰 갱신 |
| `/auth/token/revoke` | POST | Bearer Token | 토큰 폐기 |

### 토큰 발급 흐름

```
1. 클라이언트 -> /auth/token/issue (Basic Auth)
2. Spring Security가 인증 처리
3. 인증 성공 시 JWE 토큰 발급
4. (선택) 세션 생성 및 인증 정보 저장
```

## API 보안

`/api/**` 경로에 대한 보안 설정:

```yaml
simplix:
  auth:
    security:
      permit-all-patterns:
        - /api/public/**
        - /api/health
        - /swagger-ui/**
        - /v3/api-docs/**
```

### 인증 흐름

```
1. 요청 수신
2. SimpliXTokenAuthenticationFilter 실행
   - Authorization: Bearer {token} 헤더 확인
   - 쿠키에서 토큰 확인 (OAuth2 쿠키 모드)
3. 토큰 검증 (만료, 블랙리스트, IP/UA 검증)
4. 인증 성공 시 SecurityContext 설정
5. 컨트롤러 실행
```

### 토큰 vs 세션 우선순위

```yaml
simplix:
  auth:
    security:
      prefer-token-over-session: true  # 기본값
```

| 설정값 | 세션 있음 | 토큰 있음 | 사용되는 인증 |
|--------|----------|----------|--------------|
| true | O | O | 토큰 |
| true | O | X | 세션 |
| false | O | O | 세션 |
| false | X | O | 토큰 |

## 웹 보안

전통적인 웹 페이지를 위한 폼 로그인 설정:

```yaml
simplix:
  auth:
    security:
      enable-web-security: true
      login-page-template: /login
      login-processing-url: /login
      logout-url: /logout
      permit-all-patterns:
        - /css/**
        - /js/**
        - /images/**
        - /public/**
```

### 폼 로그인 설정

```java
// 자동 설정되는 내용
http.formLogin(form -> form
    .loginPage("/login")
    .loginProcessingUrl("/login")
    .successHandler(authenticationSuccessHandler)
    .failureHandler(authenticationFailureHandler)
    .permitAll());
```

### 로그아웃 설정

자동으로 다음이 처리됩니다:
- SecurityContext 클리어
- 세션 무효화
- 쿠키 삭제: `JSESSIONID`, `access_token`, `refresh_token`

### 커스텀 Success/Failure Handler

```java
@Configuration
public class AuthHandlerConfig {

    @Bean(name = "authenticationSuccessHandler")
    public AuthenticationSuccessHandler authenticationSuccessHandler(
            UserRepository userRepository) {

        return (request, response, authentication) -> {
            String username = authentication.getName();

            // 마지막 로그인 시간 업데이트
            userRepository.updateLastLogin(username, LocalDateTime.now());

            // 기본 동작: 저장된 요청 또는 기본 URL로 리다이렉트
            new SavedRequestAwareAuthenticationSuccessHandler()
                .onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean(name = "authenticationFailureHandler")
    public AuthenticationFailureHandler authenticationFailureHandler(
            LoginAttemptService loginAttemptService) {

        return (request, response, exception) -> {
            String username = request.getParameter("username");

            // 실패 시도 기록
            loginAttemptService.recordFailure(username);

            // 5회 실패 시 계정 잠금
            if (loginAttemptService.isBlocked(username)) {
                response.sendRedirect("/login?error=blocked");
                return;
            }

            response.sendRedirect("/login?error");
        };
    }
}
```

## CORS 설정

Cross-Origin Resource Sharing 설정:

```yaml
simplix:
  auth:
    security:
      enable-cors: true
    cors:
      allowed-origins:
        - http://localhost:3000
        - https://your-frontend.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      allowed-headers:
        - Authorization
        - Content-Type
        - X-Refresh-Token
      exposed-headers:
        - X-Total-Count
      allow-credentials: true
      max-age: 3600
```

### CORS 설정 상세

| 속성 | 설명 | 기본값 |
|------|------|--------|
| `allowed-origins` | 허용할 오리진 목록 | `*` |
| `allowed-methods` | 허용할 HTTP 메서드 | 모든 메서드 |
| `allowed-headers` | 허용할 요청 헤더 | 모든 헤더 |
| `exposed-headers` | 클라이언트에 노출할 응답 헤더 | 없음 |
| `allow-credentials` | 인증 정보(쿠키, 헤더) 허용 | `false` |
| `max-age` | Preflight 응답 캐시 시간(초) | 1800 |

### 주의사항

`allow-credentials: true` 사용 시 `allowed-origins`에 `*`를 사용할 수 없습니다. 명시적인 오리진을 지정해야 합니다.

## CSRF 설정

Cross-Site Request Forgery 보호 설정:

```yaml
simplix:
  auth:
    security:
      enable-csrf: true
      csrf-ignore-patterns:
        - /api/token/**
        - /h2-console/**
        - /api/webhook/**
```

### CSRF 토큰 사용

Thymeleaf 템플릿에서:
```html
<form method="post" action="/update">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <!-- 폼 내용 -->
</form>
```

JavaScript에서:
```javascript
// CSRF 토큰을 메타 태그에서 읽기
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;

fetch('/api/update', {
    method: 'POST',
    headers: {
        [header]: token,
        'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
});
```

### API에서 CSRF 비활성화

REST API는 일반적으로 CSRF 보호가 필요하지 않습니다 (토큰 기반 인증 사용 시):

```yaml
simplix:
  auth:
    security:
      csrf-ignore-patterns:
        - /api/**
```

## HTTPS 설정

프로덕션 환경에서 HTTPS 강제:

```yaml
simplix:
  auth:
    security:
      require-https: true
```

활성화 시:
- 모든 HTTP 요청이 HTTPS로 리다이렉트
- 쿠키에 `Secure` 플래그 자동 설정

### SSL 인증서 설정

```yaml
server:
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: tomcat
```

## 세션 관리

### 세션 생성 정책

```yaml
# SimpliX Auth 기본 정책
# SessionCreationPolicy.IF_REQUIRED
```

| 정책 | 설명 |
|------|------|
| `IF_REQUIRED` | 필요 시 세션 생성 (기본값) |
| `ALWAYS` | 항상 세션 생성 |
| `NEVER` | 세션 생성하지 않음 (기존 세션은 사용) |
| `STATELESS` | 세션 완전 비활성화 |

### 토큰 발급 시 세션 생성

```yaml
simplix:
  auth:
    token:
      create-session-on-token-issue: true  # 기본값
```

활성화 시:
1. 토큰 발급 후 세션 생성
2. 세션 타임아웃 = Access 토큰 수명
3. SecurityContext를 세션에 저장
4. 웹 페이지에서 세션 인증 사용 가능

## 예외 처리

### AuthenticationEntryPoint

인증되지 않은 요청 처리:

```java
// SimpliXAuthenticationEntryPoint 기본 동작
@Override
public void commence(HttpServletRequest request,
                    HttpServletResponse response,
                    AuthenticationException authException) {

    if (isApiRequest(request)) {
        // API 요청: JSON 오류 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // {"error": "Unauthorized", "message": "..."}
    } else {
        // 웹 요청: 로그인 페이지로 리다이렉트
        response.sendRedirect(loginUrl);
    }
}
```

### AccessDeniedHandler

권한 부족 시 처리:

```java
// SimpliXAccessDeniedHandler 기본 동작
@Override
public void handle(HttpServletRequest request,
                  HttpServletResponse response,
                  AccessDeniedException accessDeniedException) {

    if (isApiRequest(request)) {
        // API 요청: 403 JSON 응답
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // {"error": "Forbidden", "message": "..."}
    } else {
        // 웹 요청: 403 에러 페이지
        response.sendRedirect("/error/403");
    }
}
```

### 커스텀 예외 처리기

```java
@ControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(TokenValidationException.class)
    public ResponseEntity<SimpliXApiResponse<?>> handleTokenValidation(
            TokenValidationException ex) {

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(SimpliXApiResponse.error(ex.getMessage(), ex.getDetail()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<SimpliXApiResponse<?>> handleAccessDenied(
            AccessDeniedException ex) {

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(SimpliXApiResponse.error("Access Denied", ex.getMessage()));
    }
}
```

## Method Security

메서드 레벨 보안 활성화:

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
    // SimpliXAuthMethodSecurityConfiguration에서 자동 설정됨
}
```

### 사용 예시

```java
@Service
public class UserService {

    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        // ADMIN 역할만 접근 가능
    }

    @PreAuthorize("hasRole('USER') and #userId == authentication.name")
    public User getUser(String userId) {
        // 본인 정보만 조회 가능
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public void updateUser(User user) {
        // ADMIN 또는 MANAGER 역할만 접근 가능
    }

    @PostAuthorize("returnObject.owner == authentication.name")
    public Document getDocument(String documentId) {
        // 반환된 문서의 소유자만 조회 가능
    }
}
```

### SpEL 표현식

| 표현식 | 설명 |
|--------|------|
| `hasRole('ROLE')` | 특정 역할 보유 |
| `hasAnyRole('R1', 'R2')` | 나열된 역할 중 하나 보유 |
| `hasAuthority('AUTH')` | 특정 권한 보유 |
| `isAuthenticated()` | 인증된 사용자 |
| `isAnonymous()` | 익명 사용자 |
| `#paramName` | 메서드 파라미터 참조 |
| `authentication.name` | 현재 인증된 사용자명 |
| `returnObject` | 메서드 반환값 (PostAuthorize) |

## 관련 문서

- [시작하기](getting-started.md)
- [JWE 토큰 인증](jwe-token.md)
- [OAuth2 소셜 로그인](oauth2.md)
- [설정 레퍼런스](configuration-reference.md)
