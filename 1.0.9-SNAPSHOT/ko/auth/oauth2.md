# OAuth2 소셜 로그인

SimpliX Auth는 다양한 OAuth2/OIDC 제공자를 통한 소셜 로그인을 지원합니다. Google, Kakao, Naver, GitHub, Facebook, Apple 로그인을 쉽게 구현할 수 있습니다.

## 목차

- [지원 제공자](#지원-제공자)
- [기본 설정](#기본-설정)
- [OAuth2AuthenticationService 구현](#oauth2authenticationservice-구현)
- [로그인 흐름](#로그인-흐름)
- [인텐트 기반 인증](#인텐트-기반-인증)
- [토큰 전달 방식](#토큰-전달-방식)
- [계정 연동](#계정-연동)
- [이메일 충돌 정책](#이메일-충돌-정책)
- [커스터마이징](#커스터마이징)

## 지원 제공자

| 제공자 | Registration ID | OIDC 지원 |
|--------|-----------------|-----------|
| Google | `google` | O |
| Kakao | `kakao` | O |
| Naver | `naver` | X |
| GitHub | `github` | X |
| Facebook | `facebook` | X |
| Apple | `apple` | O |

## 기본 설정

### 1. Spring Security OAuth2 클라이언트 설정

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/oauth2/callback/{registrationId}"
            scope: profile_nickname, profile_image, account_email
            client-authentication-method: client_secret_post
          naver:
            client-id: ${NAVER_CLIENT_ID}
            client-secret: ${NAVER_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/oauth2/callback/{registrationId}"
            scope: name, email, profile_image
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id
          naver:
            authorization-uri: https://nid.naver.com/oauth2.0/authorize
            token-uri: https://nid.naver.com/oauth2.0/token
            user-info-uri: https://openapi.naver.com/v1/nid/me
            user-name-attribute: response
```

### 2. SimpliX OAuth2 설정

```yaml
simplix:
  auth:
    oauth2:
      enabled: true
      success-url: /dashboard
      failure-url: /login?error=social

      # 토큰 전달 방식
      token-delivery-method: COOKIE  # REDIRECT, COOKIE, POST_MESSAGE

      # 쿠키 설정 (COOKIE 모드 시)
      cookie:
        access-token-name: access_token
        refresh-token-name: refresh_token
        http-only: true
        secure: true
        same-site: Lax

      # POST_MESSAGE 모드 시 허용 오리진
      allowed-origins:
        - http://localhost:3000
        - https://your-domain.com

      # 이메일 충돌 정책
      email-conflict-policy: REJECT  # REJECT, AUTO_LINK
```

## OAuth2AuthenticationService 구현

소셜 로그인을 사용하려면 `OAuth2AuthenticationService` 인터페이스를 구현해야 합니다:

```java
@Service
public class OAuth2AuthenticationServiceImpl implements OAuth2AuthenticationService {

    private final UserRepository userRepository;
    private final SocialConnectionRepository socialConnectionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo) {
        // 1. 소셜 연결 정보로 기존 사용자 찾기
        return socialConnectionRepository
            .findByProviderAndProviderId(
                userInfo.getProvider().name(),
                userInfo.getProviderId())
            .map(conn -> loadUserDetails(conn.getUser()))
            .orElseGet(() -> handleNewSocialLogin(userInfo));
    }

    @Override
    @Transactional
    public UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo, OAuth2Intent intent) {
        // 인텐트에 따른 처리
        return switch (intent) {
            case LOGIN -> handleLoginOnly(userInfo);
            case REGISTER -> handleRegisterOnly(userInfo);
            case AUTO -> authenticateOAuth2User(userInfo);
        };
    }

    private UserDetails handleLoginOnly(OAuth2UserInfo userInfo) {
        // 기존 사용자만 허용, 없으면 예외
        return socialConnectionRepository
            .findByProviderAndProviderId(
                userInfo.getProvider().name(),
                userInfo.getProviderId())
            .map(conn -> loadUserDetails(conn.getUser()))
            .orElseThrow(() -> new OAuth2AuthenticationException(
                "NO_LINKED_ACCOUNT",
                "No account linked to this social profile"));
    }

    private UserDetails handleRegisterOnly(OAuth2UserInfo userInfo) {
        // 이미 연결된 계정이 있으면 예외
        if (socialConnectionRepository.existsByProviderAndProviderId(
                userInfo.getProvider().name(),
                userInfo.getProviderId())) {
            throw new OAuth2AuthenticationException(
                "ALREADY_REGISTERED",
                "This social account is already registered");
        }
        return createNewUser(userInfo);
    }

    private UserDetails handleNewSocialLogin(OAuth2UserInfo userInfo) {
        // 이메일로 기존 사용자 찾기
        String email = userInfo.getEmail();
        if (email != null) {
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                // 이메일 충돌 - 설정에 따라 처리
                throw new OAuth2AuthenticationException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email already registered with different login method");
            }
        }
        return createNewUser(userInfo);
    }

    private UserDetails createNewUser(OAuth2UserInfo userInfo) {
        // 새 사용자 생성
        User user = new User();
        user.setEmail(userInfo.getEmail());
        user.setName(userInfo.getName());
        user.setProfileImage(userInfo.getProfileImage());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user = userRepository.save(user);

        // 소셜 연결 정보 저장
        SocialConnection connection = new SocialConnection();
        connection.setUser(user);
        connection.setProvider(userInfo.getProvider().name());
        connection.setProviderId(userInfo.getProviderId());
        socialConnectionRepository.save(connection);

        return loadUserDetails(user);
    }

    @Override
    @Transactional
    public void linkSocialAccount(String userId, OAuth2UserInfo userInfo) {
        // 이미 다른 사용자에게 연결되어 있는지 확인
        if (socialConnectionRepository.existsByProviderAndProviderId(
                userInfo.getProvider().name(),
                userInfo.getProviderId())) {
            throw new OAuth2AuthenticationException(
                "ALREADY_LINKED",
                "This social account is already linked to another user");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        SocialConnection connection = new SocialConnection();
        connection.setUser(user);
        connection.setProvider(userInfo.getProvider().name());
        connection.setProviderId(userInfo.getProviderId());
        socialConnectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void unlinkSocialAccount(String userId, OAuth2ProviderType provider) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // 마지막 로그인 방법인지 확인
        long linkedCount = socialConnectionRepository.countByUser(user);
        boolean hasPassword = user.getPassword() != null;

        if (linkedCount <= 1 && !hasPassword) {
            throw new OAuth2AuthenticationException(
                "LAST_LOGIN_METHOD",
                "Cannot unlink the last login method");
        }

        socialConnectionRepository.deleteByUserAndProvider(user, provider.name());
    }

    @Override
    public Set<OAuth2ProviderType> getLinkedProviders(String userId) {
        return socialConnectionRepository.findByUserId(userId).stream()
            .map(conn -> OAuth2ProviderType.fromRegistrationId(conn.getProvider()))
            .collect(Collectors.toSet());
    }

    @Override
    public String findUserIdByProviderConnection(OAuth2ProviderType provider, String providerId) {
        return socialConnectionRepository
            .findByProviderAndProviderId(provider.name(), providerId)
            .map(conn -> conn.getUser().getId())
            .orElse(null);
    }

    @Override
    public String findUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElse(null);
    }
}
```

## 로그인 흐름

### 기본 흐름

```
1. 사용자 -> 프론트엔드: 소셜 로그인 버튼 클릭
2. 프론트엔드 -> 백엔드: /oauth2/authorize/{provider} 리다이렉트
3. 백엔드 -> 소셜 제공자: OAuth2 인증 요청
4. 소셜 제공자 -> 사용자: 로그인/동의 페이지
5. 사용자 -> 소셜 제공자: 인증 완료
6. 소셜 제공자 -> 백엔드: /oauth2/callback/{provider} (인증 코드)
7. 백엔드: 토큰 교환 및 사용자 정보 조회
8. 백엔드: OAuth2AuthenticationService.authenticateOAuth2User() 호출
9. 백엔드 -> 프론트엔드: JWE 토큰 전달 (설정된 방식으로)
```

### 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `/oauth2/authorize/{provider}` | OAuth2 인증 시작 |
| `/oauth2/callback/{provider}` | OAuth2 콜백 (제공자가 호출) |
| `/oauth2/login/{provider}` | 로그인 전용 (기존 사용자만) |
| `/oauth2/register/{provider}` | 회원가입 전용 (신규 사용자만) |
| `/oauth2/link/{provider}` | 계정 연동 (로그인 상태 필요) |

## 인텐트 기반 인증

OAuth2Intent를 사용하여 인증 목적을 명시할 수 있습니다:

### LOGIN (로그인 전용)

```
GET /oauth2/login/google
```

- 기존에 연동된 계정이 있는 사용자만 로그인 허용
- 신규 사용자는 오류 (NO_LINKED_ACCOUNT)

### REGISTER (회원가입 전용)

```
GET /oauth2/register/google
```

- 신규 사용자만 가입 허용
- 이미 가입된 소셜 계정은 오류 (ALREADY_REGISTERED)

### AUTO (기본값)

```
GET /oauth2/authorize/google
```

- 기존 사용자면 로그인
- 신규 사용자면 자동 가입

## 토큰 전달 방식

### 1. REDIRECT

URL 쿼리 파라미터로 토큰 전달:

```yaml
simplix:
  auth:
    oauth2:
      token-delivery-method: REDIRECT
      success-url: /callback
```

리다이렉트 URL:
```
/callback?access_token=eyJ...&refresh_token=eyJ...
```

**주의:** 브라우저 히스토리에 토큰이 남을 수 있습니다.

### 2. COOKIE (권장)

HttpOnly 쿠키로 토큰 전달:

```yaml
simplix:
  auth:
    oauth2:
      token-delivery-method: COOKIE
      cookie:
        access-token-name: access_token
        refresh-token-name: refresh_token
        path: /
        http-only: true
        secure: true
        same-site: Lax
```

**장점:**
- XSS 공격으로부터 보호 (HttpOnly)
- CSRF 보호 (SameSite)
- 프론트엔드에서 토큰 관리 불필요

### 3. POST_MESSAGE (SPA 권장)

팝업 창에서 postMessage로 토큰 전달:

```yaml
simplix:
  auth:
    oauth2:
      token-delivery-method: POST_MESSAGE
      allowed-origins:
        - http://localhost:3000
        - https://your-spa.com
```

프론트엔드 구현:

```javascript
// 팝업 열기
const popup = window.open(
  '/oauth2/authorize/google',
  'oauth2_popup',
  'width=500,height=600'
);

// 메시지 수신
window.addEventListener('message', (event) => {
  // 오리진 검증
  if (!['http://localhost:3000', 'https://your-spa.com'].includes(event.origin)) {
    return;
  }

  const { accessToken, refreshToken, error } = event.data;

  if (error) {
    console.error('OAuth2 failed:', error);
    return;
  }

  // 토큰 저장
  localStorage.setItem('access_token', accessToken);
  localStorage.setItem('refresh_token', refreshToken);

  // 팝업 닫기
  popup.close();
});
```

## 계정 연동

### 소셜 계정 연동

로그인한 사용자가 소셜 계정을 추가로 연동:

```
GET /oauth2/link/kakao
```

설정:
```yaml
simplix:
  auth:
    oauth2:
      link-base-url: /oauth2/link
      link-success-url: /settings/social?linked=true
      link-failure-url: /settings/social?error=link_failed
```

### 소셜 계정 해제

```java
@DeleteMapping("/api/social/{provider}")
public ResponseEntity<Void> unlinkSocial(
        @PathVariable String provider,
        @AuthenticationPrincipal UserDetails user) {

    OAuth2ProviderType providerType = OAuth2ProviderType.fromRegistrationId(provider);
    oauth2Service.unlinkSocialAccount(user.getUsername(), providerType);

    return ResponseEntity.ok().build();
}
```

### 연동된 소셜 계정 조회

```java
@GetMapping("/api/social/linked")
public ResponseEntity<Set<String>> getLinkedProviders(
        @AuthenticationPrincipal UserDetails user) {

    Set<OAuth2ProviderType> providers = oauth2Service.getLinkedProviders(user.getUsername());

    return ResponseEntity.ok(providers.stream()
        .map(OAuth2ProviderType::getRegistrationId)
        .collect(Collectors.toSet()));
}
```

## 이메일 충돌 정책

소셜 로그인 시 이메일이 이미 다른 방식으로 가입되어 있을 때:

### REJECT (기본값, 권장)

```yaml
simplix:
  auth:
    oauth2:
      email-conflict-policy: REJECT
```

- 로그인 거부 및 오류 메시지 표시
- 사용자에게 기존 방식으로 로그인 후 계정 연동 안내
- 보안상 가장 안전

### AUTO_LINK

```yaml
simplix:
  auth:
    oauth2:
      email-conflict-policy: AUTO_LINK
```

- 이메일이 같으면 자동으로 소셜 계정 연동
- 편의성 높지만 보안 위험 존재
- 이메일 인증이 확실한 경우에만 사용

## 커스터마이징

### 인증 성공 콜백

```java
@Service
public class OAuth2AuthenticationServiceImpl implements OAuth2AuthenticationService {

    @Override
    public void onAuthenticationSuccess(
            UserDetails user,
            OAuth2UserInfo socialInfo,
            String ipAddress,
            String userAgent) {

        // 로그인 기록
        log.info("OAuth2 login success: {} via {} from {}",
            user.getUsername(),
            socialInfo.getProvider(),
            ipAddress);

        // 마지막 로그인 시간 업데이트
        userRepository.updateLastLogin(user.getUsername(), LocalDateTime.now());

        // 이벤트 발행
        eventPublisher.publishEvent(new OAuth2LoginSuccessEvent(
            user.getUsername(),
            socialInfo.getProvider(),
            ipAddress
        ));
    }
}
```

### OAuth2 사용자 정보 추출 커스터마이징

각 제공자별로 사용자 정보 추출 로직을 커스터마이징할 수 있습니다:

```java
@Component
public class CustomKakaoUserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public OAuth2ProviderType getProvider() {
        return OAuth2ProviderType.KAKAO;
    }

    @Override
    public OAuth2UserInfo extract(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        return OAuth2UserInfo.builder()
            .provider(OAuth2ProviderType.KAKAO)
            .providerId(String.valueOf(attributes.get("id")))
            .email((String) kakaoAccount.get("email"))
            .name((String) profile.get("nickname"))
            .profileImage((String) profile.get("profile_image_url"))
            .build();
    }
}
```

## 관련 문서

- [시작하기](ko/auth/getting-started.md)
- [JWE 토큰 인증](ko/auth/jwe-token.md)
- [보안 설정](ko/auth/security-configuration.md)
- [설정 레퍼런스](ko/auth/configuration-reference.md)
