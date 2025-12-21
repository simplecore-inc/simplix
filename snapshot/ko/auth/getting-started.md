# SimpliX Auth 시작하기

SimpliX Auth는 Spring Boot 기반 애플리케이션을 위한 인증/인가 스타터 라이브러리입니다. JWE(JSON Web Encryption) 토큰 기반 인증, OAuth2 소셜 로그인, 세션 관리 등을 제공합니다.

## 목차

- [의존성 추가](#의존성-추가)
- [기본 설정](#기본-설정)
- [UserDetailsService 구현](#userdetailsservice-구현)
- [JWE 키 설정](#jwe-키-설정)
- [빠른 시작 예제](#빠른-시작-예제)
- [다음 단계](#다음-단계)

## 의존성 추가

### Gradle

```groovy
dependencies {
    implementation 'dev.simplecore:simplix-auth:${version}'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.simplecore</groupId>
    <artifactId>simplix-auth</artifactId>
    <version>${version}</version>
</dependency>
```

## 기본 설정

`application.yml`에 기본 설정을 추가합니다:

```yaml
simplix:
  auth:
    enabled: true

    # JWE 토큰 설정
    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM
      # 개발용 - 프로덕션에서는 key-rolling 사용 권장
      encryption-key-location: classpath:keys/jwe-key.json

    # 토큰 수명 설정
    token:
      access-token-lifetime: 1800    # 30분 (초)
      refresh-token-lifetime: 604800  # 7일 (초)

    # 보안 설정
    security:
      enable-token-endpoints: true
      enable-web-security: true
      login-page-template: /login
      permit-all-patterns:
        - /api/public/**
        - /swagger-ui/**
        - /v3/api-docs/**
```

## UserDetailsService 구현

SimpliX Auth를 사용하려면 `SimpliXUserDetailsService`를 구현해야 합니다:

```java
@Service
public class CustomUserDetailsService implements SimpliXUserDetailsService {

    private final UserRepository userRepository;
    private final MessageSource messageSource;

    public CustomUserDetailsService(UserRepository userRepository, MessageSource messageSource) {
        this.userRepository = userRepository;
        this.messageSource = messageSource;
    }

    @Override
    public MessageSource getMessageSource() {
        return messageSource;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
            .map(this::toUserDetails)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList())
            .accountExpired(false)
            .accountLocked(user.isLocked())
            .credentialsExpired(false)
            .disabled(!user.isActive())
            .build();
    }
}
```

## JWE 키 설정

### 개발 환경 - 정적 키 사용

RSA 키 쌍을 생성하여 `src/main/resources/keys/jwe-key.json`에 저장합니다:

```bash
# OpenSSL로 RSA 키 쌍 생성
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

JWK(JSON Web Key) 형식으로 변환하거나, 프로그래밍 방식으로 생성할 수 있습니다:

```java
// 키 생성 유틸리티
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

RSAKey rsaKey = new RSAKeyGenerator(2048)
    .keyID("my-key-id")
    .generate();

String jwkJson = rsaKey.toJSONString();
// 이 JSON을 jwe-key.json 파일에 저장
```

### 프로덕션 환경 - 키 롤링 사용

프로덕션 환경에서는 데이터베이스 기반 키 롤링을 권장합니다:

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

키 롤링 사용 시 `JweKeyStore` 인터페이스를 구현해야 합니다. 자세한 내용은 [JWE 키 롤링 문서](ko/auth/jwe-key-rolling.md)를 참조하세요.

## 빠른 시작 예제

### 1. 토큰 발급

```bash
# Basic 인증을 사용하여 토큰 발급
curl -X GET "http://localhost:8080/auth/token/issue" \
  -H "Authorization: Basic $(echo -n 'username:password' | base64)"
```

응답:
```json
{
  "accessToken": "eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidjEifQ...",
  "refreshToken": "eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidjEifQ...",
  "accessTokenExpiry": "2024-01-15T10:30:00+09:00",
  "refreshTokenExpiry": "2024-01-22T10:00:00+09:00"
}
```

### 2. API 요청 시 토큰 사용

```bash
curl -X GET "http://localhost:8080/api/users/me" \
  -H "Authorization: Bearer eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidjEifQ..."
```

### 3. 토큰 갱신

```bash
curl -X GET "http://localhost:8080/auth/token/refresh" \
  -H "X-Refresh-Token: eyJhbGciOiJSU0EtT0FFUC0yNTYiLCJlbmMiOiJBMjU2R0NNIiwia2lkIjoidjEifQ..."
```

### 4. 토큰 폐기 (로그아웃)

```bash
curl -X POST "http://localhost:8080/auth/token/revoke" \
  -H "Authorization: Bearer {access_token}" \
  -H "X-Refresh-Token: {refresh_token}"
```

## 다음 단계

- [JWE 토큰 인증](ko/auth/jwe-token.md) - JWE 토큰 상세 사용법
- [토큰 블랙리스트](ko/auth/token-blacklist.md) - 토큰 폐기 기능 설정
- [OAuth2 소셜 로그인](ko/auth/oauth2.md) - 소셜 로그인 구현
- [보안 설정](ko/auth/security-configuration.md) - 상세 보안 설정
- [설정 레퍼런스](ko/auth/configuration-reference.md) - 전체 설정 속성 목록
- [JWE 키 롤링](ko/auth/jwe-key-rolling.md) - 프로덕션 키 관리