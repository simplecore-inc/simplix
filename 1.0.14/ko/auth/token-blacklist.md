# 토큰 블랙리스트

토큰 블랙리스트는 만료 전에 토큰을 무효화하는 기능입니다. 로그아웃, 토큰 로테이션, 보안 위반 시 강제 로그아웃 등에 사용됩니다.

## 목차

- [개요](#개요)
- [활성화 방법](#활성화-방법)
- [구현체 종류](#구현체-종류)
- [InMemory 구현체](#inmemory-구현체)
- [Caffeine 구현체](#caffeine-구현체)
- [Redis 구현체](#redis-구현체)
- [커스텀 구현체](#커스텀-구현체)
- [사용 시나리오](#사용-시나리오)

## 개요

JWE/JWT 토큰은 기본적으로 Stateless합니다. 토큰이 발급되면 만료될 때까지 유효합니다. 하지만 다음 상황에서는 토큰을 즉시 무효화해야 합니다:

- 사용자 로그아웃
- 비밀번호 변경
- 권한 변경
- 보안 위반 감지
- 토큰 로테이션 (이전 토큰 무효화)

블랙리스트는 토큰의 JTI(JWT ID)를 저장하여 해당 토큰이 더 이상 유효하지 않음을 표시합니다.

## 활성화 방법

```yaml
simplix:
  auth:
    token:
      enable-blacklist: true
```

블랙리스트가 활성화되면:
1. 토큰 검증 시 블랙리스트 확인
2. 토큰 폐기(`/auth/token/revoke`) 기능 활성화
3. 토큰 로테이션 시 이전 토큰 자동 블랙리스트 등록

## 구현체 종류

SimpliX Auth는 3가지 블랙리스트 구현체를 제공합니다:

| 구현체 | 적합한 환경 | 특징 |
|--------|-------------|------|
| InMemory | 개발/테스트 | 서버 재시작 시 데이터 손실 |
| Caffeine | 단일 서버 프로덕션 | 고성능 로컬 캐시 |
| Redis | 다중 서버 프로덕션 | 분산 환경 지원 |

## InMemory 구현체

가장 기본적인 구현체로, `ConcurrentHashMap`을 사용합니다.

### 특징
- 서버 재시작 시 모든 블랙리스트 데이터 손실
- 단일 서버에서만 작동
- 개발 및 테스트 환경에 적합
- 1분마다 만료된 항목 자동 정리

### 자동 활성화 조건
- `enable-blacklist: true`
- 다른 `TokenBlacklistService` 빈이 없음

### 경고 메시지
```
WARN: Using in-memory token blacklist - data will be lost on restart
WARN: Not suitable for production or multi-server deployments
```

## Caffeine 구현체

Caffeine 캐시 라이브러리를 사용하는 고성능 로컬 구현체입니다.

### 의존성 추가

```groovy
dependencies {
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
}
```

### 특징
- 고성능 로컬 캐시 (Google Guava 후속작)
- 7일 글로벌 만료 정책 (개별 TTL 미지원)
- 최대 100,000개 항목 제한 (메모리 오버플로우 방지)
- 통계 수집 지원 (`recordStats()`)
- 단일 서버에 적합

### 자동 활성화 조건
- `enable-blacklist: true`
- Caffeine 라이브러리가 클래스패스에 존재
- `redisTemplate` 빈이 없음 (Redis보다 낮은 우선순위)

### 내부 동작

```java
// Caffeine 캐시 설정 (실제 구현)
Cache<String, Boolean> cache = Caffeine.newBuilder()
    .expireAfterWrite(7, TimeUnit.DAYS)  // 최대 보존 기간
    .maximumSize(100_000)                 // 메모리 제한
    .recordStats()                        // 통계 활성화
    .build();
```

### 제한사항

- **개별 TTL 미지원**: Caffeine은 항목별 TTL을 지원하지 않습니다. 모든 항목이 7일 후 만료됩니다.
- **서버 재시작 시 데이터 손실**: InMemory와 동일하게 메모리 기반이므로 재시작 시 블랙리스트가 초기화됩니다.
- **다중 서버 미지원**: 서버 간 블랙리스트 공유가 불가능합니다.

## Redis 구현체

Redis를 사용하는 분산 환경용 구현체입니다.

### 의존성 추가

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### Redis 설정

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your-password  # 선택사항
```

### 특징
- 다중 서버 환경에서 블랙리스트 공유
- 서버 재시작 후에도 데이터 유지
- Redis TTL을 활용한 자동 만료
- 프로덕션 환경 권장

### 자동 활성화 조건
- `enable-blacklist: true`
- Spring Data Redis가 클래스패스에 존재
- `redisTemplate` 빈이 등록됨

### 키 형식

```
simplix:token:bl:{jti}
```

예시:
```
simplix:token:bl:550e8400-e29b-41d4-a716-446655440000
```

## 커스텀 구현체

특별한 요구사항이 있는 경우 직접 구현할 수 있습니다:

### TokenBlacklistService 인터페이스

```java
public interface TokenBlacklistService {

    /**
     * 토큰 JTI를 블랙리스트에 추가
     *
     * @param jti 토큰의 JWT ID
     * @param ttl 블랙리스트 유지 기간 (토큰의 남은 유효 기간)
     */
    void blacklist(String jti, Duration ttl);

    /**
     * 토큰이 블랙리스트에 있는지 확인
     *
     * @param jti 토큰의 JWT ID
     * @return 블랙리스트에 있으면 true
     */
    boolean isBlacklisted(String jti);
}
```

### 커스텀 구현 예시 - Database 기반

```java
@Service
@Primary  // 기본 구현체보다 우선
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class DatabaseTokenBlacklistService implements TokenBlacklistService {

    private final TokenBlacklistRepository repository;

    @Override
    public void blacklist(String jti, Duration ttl) {
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setJti(jti);
        entry.setExpiresAt(Instant.now().plus(ttl));
        repository.save(entry);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return repository.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }

    @Scheduled(fixedRate = 3600000)  // 매시간
    public void cleanupExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }
}
```

### Entity 및 Repository

```java
@Entity
@Table(name = "token_blacklist")
public class TokenBlacklistEntry {

    @Id
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    // getters, setters
}

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklistEntry, String> {

    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
```

## 사용 시나리오

### 1. 사용자 로그아웃

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(
        @RequestHeader("Authorization") String authHeader,
        @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {

    String accessToken = authHeader.replace("Bearer ", "");

    // Access 토큰 폐기
    tokenProvider.revokeToken(accessToken);

    // Refresh 토큰 폐기
    if (refreshToken != null) {
        tokenProvider.revokeToken(refreshToken);
    }

    return ResponseEntity.ok().build();
}
```

### 2. 비밀번호 변경 시 모든 세션 무효화

```java
@Service
public class PasswordChangeService {

    private final TokenBlacklistService blacklistService;
    private final UserSessionRepository sessionRepository;

    @Transactional
    public void changePassword(String userId, String newPassword) {
        // 비밀번호 변경 로직...

        // 해당 사용자의 모든 활성 토큰 JTI 조회 후 블랙리스트 등록
        List<UserSession> sessions = sessionRepository.findByUserId(userId);
        for (UserSession session : sessions) {
            // 토큰 남은 유효 기간 계산
            Duration ttl = Duration.between(Instant.now(), session.getTokenExpiry());
            if (!ttl.isNegative()) {
                blacklistService.blacklist(session.getTokenJti(), ttl);
            }
        }

        // 세션 정보 삭제
        sessionRepository.deleteByUserId(userId);
    }
}
```

### 3. 관리자에 의한 강제 로그아웃

```java
@Service
public class AdminService {

    private final TokenBlacklistService blacklistService;

    public void forceLogout(String userId) {
        // 사용자의 모든 활성 토큰 폐기
        // ... 위와 유사한 로직
    }

    public void forceLogoutAll() {
        // 모든 사용자 강제 로그아웃
        // 주의: 대량의 데이터가 블랙리스트에 추가됨
    }
}
```

### 4. 토큰 로테이션

토큰 갱신 시 이전 Refresh 토큰을 자동으로 블랙리스트에 등록합니다:

```yaml
simplix:
  auth:
    token:
      enable-blacklist: true
      enable-token-rotation: true
```

내부 동작:
1. Refresh 토큰으로 갱신 요청
2. 새 Access/Refresh 토큰 발급
3. 이전 Refresh 토큰의 JTI를 블랙리스트에 등록
4. Replay 공격 방지

## 성능 고려사항

### 블랙리스트 크기 관리

- TTL이 지난 항목은 자동 정리됨
- Access 토큰 수명이 짧을수록 블랙리스트 크기 감소
- Refresh 토큰만 블랙리스트에 등록하는 것도 방법

### Redis 사용 시 권장 설정

```yaml
spring:
  data:
    redis:
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

## 관련 문서

- [JWE 토큰 인증](ko/auth/jwe-token.md)
- [설정 레퍼런스](ko/auth/configuration-reference.md)
