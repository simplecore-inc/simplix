# Getting Started with SimpliX Sync

simplix-sync 모듈을 처음 도입할 때 따라할 수 있는 단계별 통합 가이드입니다. 단일 인스턴스에서 분산 환경까지의 점진적 전환 절차를 다룹니다.

## Table of Contents

- [전제 조건](#전제-조건)
- [1단계: 의존성 추가](#1단계-의존성-추가)
- [2단계: 첫 SyncChannel 만들기](#2단계-첫-syncchannel-만들기)
- [3단계: 분산 모드로 전환](#3단계-분산-모드로-전환)
- [Common Pitfalls](#common-pitfalls)
- [Related Documents](#related-documents)

---

## 전제 조건

- Java 17+
- Spring Boot 3.5+
- 분산 모드 사용 시: Redis 6+ 또는 NATS 2.10+

---

## 1단계: 의존성 추가

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-sync'

    // 분산 모드 백엔드 (택 1)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    // 또는
    implementation 'io.nats:jnats'
}
```

별도 설정 없이 부팅하면 `simplix.sync.enabled=true`(기본값)로 `NoOpInstanceSyncBroadcaster`가 자동 등록됩니다. 단일 인스턴스 환경에서는 추가 설정 없이 그대로 사용 가능합니다.

```
INFO  d.s.s.s.a.SimpliXSyncAutoConfiguration - SimpliX Sync: local mode (NoOp broadcaster)
```

이 상태에서 `InstanceSyncBroadcaster` 빈을 주입받아도 `broadcast()`/`subscribe()` 호출이 모두 no-op으로 처리되므로, 분산 환경 전환 시점에 코드를 수정할 필요가 없도록 `SyncChannel<T>` 사용 코드를 미리 작성해두는 것을 권장합니다.

---

## 2단계: 첫 SyncChannel 만들기

### 도메인 객체

```java
public class CacheInvalidationEvent {
    private String cacheName;
    private String key;
    private Instant timestamp;

    // getters/setters/constructors omitted
}
```

### 코덱 + 채널 빈 등록

```java
@Configuration
@RequiredArgsConstructor
public class SyncConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SyncChannel<CacheInvalidationEvent> cacheInvalidationChannel(
        InstanceSyncBroadcaster broadcaster
    ) {
        PayloadCodec<CacheInvalidationEvent> codec = PayloadCodec.of(
            event -> {
                try {
                    return objectMapper.writeValueAsBytes(event);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Failed to encode event", e);
                }
            },
            bytes -> objectMapper.readValue(bytes, CacheInvalidationEvent.class)
        );
        return new SyncChannel<>("cache-invalidation", codec, broadcaster);
    }
}
```

### 발행과 구독

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final SyncChannel<CacheInvalidationEvent> channel;
    private final LocalCacheManager localCache;

    @PostConstruct
    public void init() {
        channel.subscribe(this::onPeerInvalidation);
    }

    public void invalidate(String cacheName, String key) {
        localCache.evict(cacheName, key);

        CacheInvalidationEvent event = new CacheInvalidationEvent(
            cacheName, key, Instant.now()
        );
        channel.broadcast(event);
    }

    private void onPeerInvalidation(CacheInvalidationEvent event) {
        log.debug("Peer invalidation: {}/{}", event.getCacheName(), event.getKey());
        localCache.evict(event.getCacheName(), event.getKey());
    }
}
```

로컬 모드에서는 `broadcast()`가 no-op이므로 다른 인스턴스에는 전파되지 않지만 코드는 정상 동작합니다.

---

## 3단계: 분산 모드로 전환

### Redis 백엔드

```yaml
spring:
  data:
    redis:
      host: redis
      port: 6379

simplix:
  sync:
    mode: DISTRIBUTED
    distributed:
      broker: REDIS    # 기본값이지만 명시
```

부팅 로그:

```
INFO  d.s.s.s.i.r.RedisInstanceSyncBroadcaster - Redis instance sync broadcaster initialized [instanceId=550e8400-...]
INFO  d.s.s.s.a.SimpliXSyncAutoConfiguration - SimpliX Sync: distributed mode (Redis Pub/Sub broadcaster)
INFO  d.s.s.s.i.r.RedisInstanceSyncBroadcaster - Subscribed to sync channel: cache-invalidation
```

다중 인스턴스를 띄우면 한 노드의 `broadcast()` 호출이 자기 자신을 제외한 모든 노드의 `subscribe()` 핸들러를 트리거합니다.

### NATS 백엔드

이미 NATS 인프라가 있거나 simplix-messaging을 NATS 모드로 사용 중이라면 NATS로 전환할 수 있습니다.

```yaml
simplix:
  sync:
    mode: DISTRIBUTED
    distributed:
      broker: NATS

  messaging:
    broker: nats          # Connection 빈 자동 제공
    nats:
      servers: nats://nats:4222
```

simplix-messaging이 등록한 `Connection` 빈을 simplix-sync가 자동 재사용합니다.

---

## Common Pitfalls

### 1. 자기 메시지를 받지 않으니 로컬 처리는 직접

```java
// ✔ Correct
public void onLocalChange(Event e) {
    applyLocally(e);     // 직접 적용
    channel.broadcast(e); // 다른 인스턴스에 알림
}

// ✖ Wrong - 자기 자신은 수신하지 않으므로 로컬 적용 누락
public void onLocalChange(Event e) {
    channel.broadcast(e);  // 본인은 수신 못 함
}
```

### 2. 메시지 손실은 정상 동작

simplix-sync는 베스트-에포트 전송입니다. 비즈니스 critical한 데이터는 simplix-messaging(JetStream/Streams) 사용. simplix-sync는 [Reconciliation](./advanced-guide.md#reconciliation)으로 보완하세요.

### 3. NATS subject 명명 규칙

채널 이름이 NATS subject로 직접 사용되므로 다음 제약이 있습니다.

- 공백 불가
- `.`(계층) `-` 허용
- `*` `>` (와일드카드)는 리터럴 채널명에 사용 X

권장: `device-state`, `cache.invalidation`, `session-events`

### 4. 코덱 일관성

발신자와 수신자의 코덱이 다르면 디코딩 실패합니다. 같은 채널을 사용하는 모든 인스턴스가 동일한 직렬화 방식을 써야 합니다.

### 5. 채널마다 별도 SyncChannel

한 채널을 여러 타입으로 공유하지 마세요.

```java
// ✖ 같은 채널을 두 타입으로 공유
SyncChannel<EventA> chA = new SyncChannel<>("events", codecA, broadcaster);
SyncChannel<EventB> chB = new SyncChannel<>("events", codecB, broadcaster);

// ✔
SyncChannel<EventA> chA = new SyncChannel<>("events-a", codecA, broadcaster);
SyncChannel<EventB> chB = new SyncChannel<>("events-b", codecB, broadcaster);
```

---

## Related Documents

- [Overview](./overview.md) - 모듈 개요, 아키텍처, 백엔드 비교, 설정 속성
- [Advanced Guide](./advanced-guide.md) - State Store, Reconciliation, Resilience, Custom Backend
