# Security Integration Guide

SimpliX Auth 모듈을 사용한 Spring Security 및 JWE 토큰 인증 통합 가이드입니다.

## Overview

SimpliX Auth는 Spring Security와 통합되어 다음 기능을 제공합니다:

- JWE (JSON Web Encryption) 기반 토큰 인증
- RSA 키 자동 롤링 (프로덕션 환경)
- 토큰 블랙리스트 지원
- OAuth2 소셜 로그인 통합
- CORS, CSRF, XSS 보호

## Prerequisites

```gradle
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix:${version}'
    // 또는 개별 모듈
    implementation 'dev.simplecore:simplix-auth:${version}'
}
```

## Configuration

### application.yml

```yaml
simplix:
  auth:
    enabled: true
    security:
      # 로그인/로그아웃 URL
      login-page-template: login
      login-processing-url: /login
      logout-url: /logout

      # 인증 없이 접근 가능한 패턴
      permit-all-patterns:
        - /api/public/**
        - /actuator/health
        - /health/**
        - /public/**
        - /api-docs/**

      # 보안 설정
      enable-cors: true
      enable-csrf: true
      enable-xss-protection: true
      enable-http-basic: false
      csrf-ignore-patterns:
        - /api/v1/auth/token/**
        - /h2-console/**

    # CORS 설정
    cors:
      allowed-origins:
        - http://localhost:3000
        - http://localhost:4200
      exposed-headers:
        - Authorization
        - X-Custom-Header

    # JWE 토큰 설정
    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM
      key-rolling:
        enabled: true          # 프로덕션: true, 개발: false
        key-size: 2048
        auto-initialize: true
        retention:
          buffer-seconds: 86400
          auto-cleanup: true

    # 토큰 수명 설정
    token:
      enable-blacklist: true
      access-token-lifetime: 1800      # 30분 (초)
      refresh-token-lifetime: 86400    # 1일 (초)
```

## SimpliXUserDetailsService Implementation

`SimpliXUserDetailsService` 인터페이스를 구현하여 사용자 인증 정보를 로드합니다.

### Interface Overview

```java
public interface SimpliXUserDetailsService extends UserDetailsService {

    MessageSource getMessageSource();

    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;

    // 기본 구현 제공
    default boolean exists(String username);
    default UserDetails loadCurrentUser(Authentication authentication);
    default boolean isAccountLocked(String username);
    default boolean isAccountExpired(String username);
    default boolean isCredentialsExpired(String username);
    default Collection<String> getUserAuthorities(String username);
    default boolean hasAuthority(String username, String authority);
    default boolean hasRole(String username, String role);
    default boolean isActive(String username);
}
```

### Implementation Example

```java
package com.example.security;

import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Primary
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements SimpliXUserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final MessageSource messageSource;

    @Override
    public MessageSource getMessageSource() {
        return messageSource;
    }

    @Override
    public CustomUserDetails loadUserByUsername(String username) {
        log.debug("Loading user by username: {}", username);

        // 1. 사용자 조회
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + username));

        // 2. 계정 상태 검증
        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("Account is disabled");
        }

        if (user.isLocked()) {
            throw new UsernameNotFoundException("Account is locked");
        }

        // 3. 권한 로드
        List<SimpleGrantedAuthority> authorities = userRoleRepository
            .findByUserId(user.getId())
            .stream()
            .map(role -> {
                String roleCode = role.getRoleCode();
                // ROLE_ 접두사 처리
                if (!roleCode.startsWith("ROLE_")) {
                    roleCode = "ROLE_" + roleCode;
                }
                return new SimpleGrantedAuthority(roleCode);
            })
            .collect(Collectors.toList());

        // 4. 기본 권한 추가 (권한이 없는 경우)
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        log.info("User loaded - Username: {}, Authorities: {}",
            username, authorities);

        return new CustomUserDetails(user, authorities);
    }

    @Override
    public boolean exists(String username) {
        return userRepository.existsByUsername(username);
    }
}
```

## CustomUserDetails Implementation

Spring Security의 `User` 클래스를 확장하여 애플리케이션별 사용자 정보를 포함합니다.

```java
package com.example.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String username;
    private final String email;
    private final String displayName;
    private final List<String> roleIds;

    public CustomUserDetails(
        com.example.entity.User account,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(
            account.getUsername(),
            account.getPasswordHash() != null ? account.getPasswordHash() : "",
            account.isEnabled(),           // enabled
            !account.isExpired(),          // accountNonExpired
            true,                          // credentialsNonExpired
            !account.isLocked(),           // accountNonLocked
            authorities
        );

        this.id = account.getId();
        this.username = account.getUsername();
        this.email = account.getEmail();
        this.displayName = account.getDisplayName();
        this.roleIds = List.of();  // 필요시 역할 ID 목록
    }
}
```

## Security Filter Chain Configuration

SimpliX는 기본 보안 설정을 자동으로 구성하지만, 추가적인 커스터마이징이 필요한 경우 `SecurityFilterChain`을 정의할 수 있습니다.

### Custom Security Configuration

```java
package com.example.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurityConfig {

    @Value("${api.version.prefix:/api/v1}")
    private String apiVersionPrefix;

    /**
     * 개발 엔드포인트용 보안 필터 체인
     * localhost에서만 접근 가능
     */
    @Bean
    @Order(2)
    public SecurityFilterChain devEndpointsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher(apiVersionPrefix + "/dev/**")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(apiVersionPrefix + "/dev/**")
                    .access((authentication, context) ->
                        new AuthorizationDecision(isLocalhostRequest(context.getRequest()))
                    )
            )
            .build();
    }

    private boolean isLocalhostRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr) ||
               "0:0:0:0:0:0:0:1".equals(remoteAddr) ||
               "localhost".equals(request.getServerName());
    }
}
```

### Role-Based Access Control

```java
@Bean
@Order(3)
public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/api/admin/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
        )
        .build();
}
```

## JWE Token Usage

### Token Generation

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SimpliXJweTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/login")
    public ResponseEntity<SimpliXApiResponse<TokenResponse>> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // 1. 인증
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword()
            )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 2. 토큰 생성
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        TokenResponse tokens = tokenProvider.createTokenPair(
            authentication.getName(),
            clientIp,
            userAgent
        );

        return ResponseEntity.ok(SimpliXApiResponse.success(tokens));
    }
}
```

### Token Refresh

```java
@PostMapping("/refresh")
public ResponseEntity<SimpliXApiResponse<TokenResponse>> refresh(
        @RequestHeader("Authorization") String authorization,
        HttpServletRequest httpRequest) {

    String refreshToken = authorization.replace("Bearer ", "");
    String clientIp = httpRequest.getRemoteAddr();
    String userAgent = httpRequest.getHeader("User-Agent");

    TokenResponse tokens = tokenProvider.refreshTokens(
        refreshToken,
        clientIp,
        userAgent
    );

    return ResponseEntity.ok(SimpliXApiResponse.success(tokens));
}
```

### Token Revocation (Logout)

```java
@PostMapping("/logout")
public ResponseEntity<SimpliXApiResponse<Void>> logout(
        @RequestHeader("Authorization") String authorization) {

    String accessToken = authorization.replace("Bearer ", "");

    boolean revoked = tokenProvider.revokeToken(accessToken);

    SecurityContextHolder.clearContext();

    return ResponseEntity.ok(SimpliXApiResponse.success(null));
}
```

## OAuth2 Integration

SimpliX Auth는 OAuth2 소셜 로그인을 지원합니다.

### Configuration

```yaml
simplix:
  auth:
    oauth2:
      enabled: true
      success-url: /oauth2/success
      failure-url: /oauth2/failure
      email-conflict-policy: REJECT  # REJECT or AUTO_LINK
      token-delivery-method: POST_MESSAGE  # REDIRECT, COOKIE, or POST_MESSAGE

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
            redirect-uri: "{baseUrl}/oauth2/callback/{registrationId}"

          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/oauth2/callback/{registrationId}"
            scope: profile_nickname, profile_image, account_email
            client-authentication-method: client_secret_post

        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id
```

## Best Practices

### 1. Key Rolling (Production)

프로덕션 환경에서는 반드시 키 롤링을 활성화하세요:

```yaml
simplix:
  auth:
    jwe:
      key-rolling:
        enabled: true
        key-size: 2048
        auto-initialize: true
```

### 2. Token Blacklist

토큰 무효화를 위해 블랙리스트를 활성화하세요:

```yaml
simplix:
  auth:
    token:
      enable-blacklist: true
```

### 3. HTTPS Only

프로덕션에서는 HTTPS를 강제하세요:

```yaml
simplix:
  auth:
    security:
      require-https: true
      enable-hsts: true
      hsts-max-age-seconds: 31536000
```

### 4. CSRF Protection

API 엔드포인트 외에는 CSRF 보호를 유지하세요:

```yaml
simplix:
  auth:
    security:
      enable-csrf: true
      csrf-ignore-patterns:
        - /api/v1/auth/token/**
```

### 5. Password Security

비밀번호는 BCrypt로 해싱하세요:

```java
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## Troubleshooting

### Token Validation Failed

```
Token validation failed: JWE decrypter not initialized
```

**해결**: JWE 키가 올바르게 설정되었는지 확인하세요. Key rolling이 활성화된 경우 데이터베이스 연결을 확인하세요.

### CORS Error

```
Access-Control-Allow-Origin header missing
```

**해결**: `simplix.auth.cors.allowed-origins`에 프론트엔드 URL을 추가하세요.

### Authentication Failed

```
Bad credentials
```

**해결**: `CustomUserDetailsService`에서 올바른 비밀번호 해시를 반환하는지 확인하세요. 비밀번호는 BCrypt로 인코딩되어야 합니다.

## Next Steps

- [Application Setup Guide](ko/starter/application-setup.md) - 애플리케이션 설정
- [Exception Handler Guide](ko/starter/exception-handler.md) - 예외 처리
- [CRUD Tutorial](ko/crud-tutorial.md) - CRUD 구현 튜토리얼
