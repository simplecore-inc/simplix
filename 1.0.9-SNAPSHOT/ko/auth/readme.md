# SimpliX Auth

Spring Boot 기반 애플리케이션을 위한 인증/인가 스타터 라이브러리입니다.

## 주요 기능

- **JWE 토큰 인증**: JWT 대신 암호화된 JWE 토큰을 사용하여 클레임 정보 보호
- **OAuth2 소셜 로그인**: Google, Kakao, Naver, GitHub, Facebook, Apple 지원
- **토큰 블랙리스트**: 토큰 즉시 무효화 (InMemory, Caffeine, Redis)
- **키 롤링**: 데이터베이스 기반 JWE 키 로테이션
- **세션-토큰 하이브리드**: 세션 인증과 토큰 인증 동시 지원

## 빠른 시작

### 의존성 추가

```groovy
dependencies {
    implementation 'dev.simplecore:simplix-auth:${version}'
}
```

### 기본 설정

```yaml
simplix:
  auth:
    enabled: true
    jwe:
      encryption-key-location: classpath:keys/jwe-key.json
    token:
      access-token-lifetime: 1800
      refresh-token-lifetime: 604800
```

### UserDetailsService 구현

```java
@Service
public class CustomUserDetailsService implements SimpliXUserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) {
        // 사용자 조회 로직 구현
    }
}
```

## 문서

### 한국어

| 문서 | 설명 |
|------|------|
| [시작하기](getting-started.md) | 빠른 시작 가이드 |
| [JWE 토큰 인증](jwe-token.md) | JWE 토큰 생성, 검증, 갱신, 폐기 |
| [토큰 블랙리스트](token-blacklist.md) | InMemory, Caffeine, Redis 구현체 |
| [OAuth2 소셜 로그인](oauth2.md) | 소셜 로그인 설정 및 구현 |
| [보안 설정](security-configuration.md) | Security Filter Chain, CORS, CSRF |
| [설정 레퍼런스](configuration-reference.md) | 전체 설정 속성 목록 |
| [JWE 키 롤링](jwe-key-rolling.md) | 키 로테이션 개념 |
| [JWE 키 롤링 구현](jwe-key-rolling-implementation.md) | 키 로테이션 구현 가이드 |

## 아키텍처

```
simplix-auth/
├── autoconfigure/          # Spring Boot 자동 설정
│   ├── SimpliXAuthAutoConfiguration
│   ├── SimpliXAuthSecurityConfiguration
│   └── SimpliXJweKeyAutoConfiguration
├── security/               # 보안 컴포넌트
│   ├── SimpliXJweTokenProvider       # JWE 토큰 생성/검증
│   ├── SimpliXTokenAuthenticationFilter
│   └── SimpliXUserDetailsService     # 구현 필요
├── service/                # 서비스
│   └── TokenBlacklistService         # 토큰 블랙리스트
├── oauth2/                 # OAuth2 소셜 로그인
│   ├── OAuth2AuthenticationService   # 구현 필요
│   └── extractor/                    # 제공자별 사용자 정보 추출
├── jwe/                    # JWE 키 관리
│   ├── provider/                     # 키 제공자
│   └── store/                        # 키 저장소 (구현 필요)
├── web/                    # REST 컨트롤러
│   └── SimpliXAuthTokenController
└── properties/             # 설정 속성
    └── SimpliXAuthProperties
```

## API 엔드포인트

| 엔드포인트 | 메서드 | 설명 |
|-----------|--------|------|
| `/auth/token/issue` | GET | 토큰 발급 (Basic Auth) |
| `/auth/token/refresh` | GET | 토큰 갱신 (X-Refresh-Token) |
| `/auth/token/revoke` | POST | 토큰 폐기 |
| `/oauth2/authorize/{provider}` | GET | OAuth2 인증 시작 |
| `/oauth2/callback/{provider}` | GET | OAuth2 콜백 |
| `/oauth2/link/{provider}` | GET | 소셜 계정 연동 |

## 필수 구현 인터페이스

### SimpliXUserDetailsService

사용자 인증에 필수입니다.

```java
public interface SimpliXUserDetailsService extends UserDetailsService {
    MessageSource getMessageSource();
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
```

### OAuth2AuthenticationService (OAuth2 사용 시)

소셜 로그인 시 사용자 처리 로직을 구현합니다.

```java
public interface OAuth2AuthenticationService {
    UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo);
    void linkSocialAccount(String userId, OAuth2UserInfo userInfo);
    void unlinkSocialAccount(String userId, OAuth2ProviderType provider);
    Set<OAuth2ProviderType> getLinkedProviders(String userId);
}
```

### JweKeyStore (키 롤링 사용 시)

JWE 키 저장/조회 로직을 구현합니다.

```java
public interface JweKeyStore {
    JweKeyData save(JweKeyData keyData);
    Optional<JweKeyData> findByVersion(String version);
    Optional<JweKeyData> findCurrent();
    List<JweKeyData> findAll();
    void deactivateAllExcept(String exceptVersion);
}
```

## 설정 요약

| 속성 | 기본값 | 설명 |
|------|--------|------|
| `simplix.auth.enabled` | `true` | 모듈 활성화 |
| `simplix.auth.token.access-token-lifetime` | `1800` | Access 토큰 수명 (초) |
| `simplix.auth.token.refresh-token-lifetime` | `604800` | Refresh 토큰 수명 (초) |
| `simplix.auth.token.enable-blacklist` | `false` | 블랙리스트 활성화 |
| `simplix.auth.jwe.key-rolling.enabled` | `false` | 키 롤링 활성화 |
| `simplix.auth.oauth2.enabled` | `true` | OAuth2 활성화 |

전체 설정은 [설정 레퍼런스](configuration-reference.md)를 참조하세요.

## 요구사항

- Java 17+
- Spring Boot 3.5+
- Spring Security 6+

## 라이선스

SimpleCORE License 1.0 (SCL-1.0)
