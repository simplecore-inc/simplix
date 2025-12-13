# SimpliX Hibernate Cache Module

Spring Boot 애플리케이션을 위한 Hibernate 2nd-level 캐시 자동 관리 모듈입니다.

## Features

- ✔ **제로 설정 (Zero Configuration)** - 의존성 추가만으로 자동 활성화
- ✔ **자동 캐시 무효화** - 엔티티 변경 시 자동 캐시 제거
- ✔ **EhCache 기반 로컬 캐시** - JCache 통합
- ✔ **쿼리 캐시 관리** - 엔티티 변경 시 연관 쿼리 캐시 자동 제거
- ✔ **JPA 엔티티 리스너 통합** - @PostPersist, @PostUpdate, @PostRemove
- ✔ **AOP 기반 Repository 인터셉트** - save*, delete* 자동 감지
- ✔ **Micrometer 메트릭** - 캐시 무효화 통계
- ✔ **Actuator 관리 엔드포인트** - 캐시 상태 조회 및 수동 제거

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-hibernate:${version}'

    // EhCache (권장)
    implementation 'org.hibernate.orm:hibernate-jcache'
    implementation 'org.ehcache:ehcache:3.10.8'
}
```

### 2. Entity 설정

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
}
```

### 3. 완료

**추가 설정 없이 자동으로 작동합니다:**
- 엔티티 저장/수정/삭제 시 캐시 자동 무효화
- 연관된 쿼리 캐시 자동 제거
- 분산 환경 대비 이벤트 브로드캐스트

## Cache Modes

| Mode | 설명 | 사용 환경 |
|------|------|----------|
| AUTO | 프로바이더 기반 자동 감지 | 기본값 |
| LOCAL | 로컬 캐시만 사용 | 단일 인스턴스 |
| DISTRIBUTED | 분산 캐시 동기화 | 다중 인스턴스 |
| HYBRID | 로컬 + 분산 혼합 | 고성능 다중 인스턴스 |
| DISABLED | 캐시 관리 비활성화 | 디버깅/테스트 |

## Configuration

```yaml
simplix:
  hibernate:
    cache:
      disabled: false                    # 모듈 비활성화
      mode: AUTO                         # 캐시 모드
      query-cache-auto-eviction: true    # 쿼리 캐시 자동 제거
      scan-packages:                     # @Cache 엔티티 스캔 패키지
        - com.example.domain
```

## 수동 캐시 제거

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    private final HibernateCacheManager cacheManager;

    public void evictUser(Long userId) {
        cacheManager.evictEntity(User.class, userId);
    }

    public void evictAllUsers() {
        cacheManager.evictEntityCache(User.class);
    }

    public void evictAll() {
        cacheManager.evictAll();
    }
}
```

## 비활성화

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

## Documentation

- [Overview (아키텍처 상세)](docs/ko/overview.md)
- [Configuration Guide (설정 가이드)](docs/ko/configuration.md)
- [Cache Eviction Guide (캐시 무효화)](docs/ko/cache-eviction.md)
- [Monitoring Guide (모니터링)](docs/ko/monitoring.md)

## Requirements

- Spring Boot 3.x
- Hibernate 6.x
- Java 17+

## License

SimpleCORE License 1.0 (SCL-1.0)
