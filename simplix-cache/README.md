# SimpliX Cache Module

Spring Boot 애플리케이션을 위한 유연한 전략 기반 캐싱 모듈입니다.

## Features

- ✔ **다중 캐시 전략** - Local (Caffeine), Redis 지원
- ✔ **전략 패턴** - 설정만으로 캐시 구현체 전환
- ✔ **Spring Boot 통합** - 자동 구성 및 @Cacheable 지원
- ✔ **모니터링** - Health Check 및 메트릭 수집
- ✔ **유연한 설정** - 캐시별 TTL 설정
- ✔ **분산 캐싱** - Redis를 통한 다중 인스턴스 지원

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-cache:${version}'

    // Optional: Redis 지원
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### 2. Configuration

```yaml
simplix:
  cache:
    mode: local  # local 또는 redis
    default-ttl-seconds: 3600
```

### 3. Usage

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final CacheService cacheService;

    public User getUser(String userId) {
        return cacheService.getOrCompute(
            "users",              // 캐시 이름
            userId,               // 캐시 키
            () -> loadUser(userId),  // 값 로더
            User.class,           // 반환 타입
            Duration.ofHours(1)   // TTL
        );
    }

    public void updateUser(User user) {
        saveUser(user);
        cacheService.evict("users", user.getId());
    }
}
```

## Cache Strategies

| 모드 | 구현체 | 용도 |
|------|--------|------|
| `local` | Caffeine | 단일 인스턴스, 개발 환경 |
| `redis` | Redis | 다중 인스턴스, 운영 환경 |

## Configuration

### 기본 설정

```yaml
simplix:
  cache:
    mode: local
    default-ttl-seconds: 3600
    cache-null-values: false
```

### 캐시별 설정

```yaml
simplix:
  cache:
    cache-configs:
      users:
        ttl-seconds: 900      # 15분
      configs:
        ttl-seconds: 86400    # 24시간
```

### Redis 설정

```yaml
simplix:
  cache:
    mode: redis
    redis:
      key-prefix: "app:cache:"

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## Spring @Cacheable

```java
@Service
public class ProductService {

    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(String productId) {
        return productRepository.findById(productId).orElse(null);
    }

    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(String productId) {
        productRepository.deleteById(productId);
    }
}
```

## Monitoring

```bash
# Health Check
curl http://localhost:8080/actuator/health/cache

# Metrics
curl http://localhost:8080/actuator/metrics/cache.hits
```

## Documentation

- [Overview (상세 문서)](docs/ko/overview.md)
- [CacheService Guide (CacheService 사용법)](docs/ko/cacheservice-guide.md)
- [Spring Cache Guide (@Cacheable 통합)](docs/ko/spring-cache-guide.md)
- [Advanced Guide (고급 사용법)](docs/ko/advanced-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
