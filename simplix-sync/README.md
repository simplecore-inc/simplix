# SimpliX Sync Module

다중 인스턴스 환경에서 인스턴스 간 상태 동기화(state reconciliation)와 가벼운 pub/sub 브로드캐스트를 위한 경량 모듈입니다. Redis Pub/Sub 또는 NATS core pub/sub를 백엔드로 선택할 수 있으며, 단일 인스턴스 환경에서는 No-Op 모드로 자동 동작합니다.

## Features

- ✔ **타입 안전 채널** - `SyncChannel<T>`로 직렬화/역직렬화 통합 처리
- ✔ **자기 메시지 필터링** - UUID 기반으로 발신자 자신은 수신하지 않음
- ✔ **다중 백엔드** - Redis Pub/Sub, NATS core pub/sub 자유 선택
- ✔ **로컬 모드** - 단일 인스턴스에서 No-Op으로 무비용 동작
- ✔ **상태 저장소** - `InMemoryStateStore<S>`로 키 기반 동기화 락 제공
- ✔ **라운드로빈 reconciliation** - `ReconciliationScheduler`로 부하 분산 배치 처리
- ✔ **Resilience 통합** - Circuit Breaker, Rate Limiter (simplix-core) 연동

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-sync'

    // Distributed mode 백엔드 (택 1)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    // 또는
    implementation 'io.nats:jnats'
}
```

### 2. Configuration

```yaml
simplix:
  sync:
    enabled: true
    mode: DISTRIBUTED   # LOCAL 또는 DISTRIBUTED
    distributed:
      broker: REDIS     # REDIS 또는 NATS
```

### 3. Usage

타입 안전 동기화 채널 정의:

```java
@Configuration
public class SyncConfig {

    @Bean
    public SyncChannel<DeviceState> deviceStateChannel(
        InstanceSyncBroadcaster broadcaster,
        ObjectMapper objectMapper
    ) {
        PayloadCodec<DeviceState> codec = PayloadCodec.of(
            state -> {
                try {
                    return objectMapper.writeValueAsBytes(state);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            },
            bytes -> objectMapper.readValue(bytes, DeviceState.class)
        );
        return new SyncChannel<>("device-state", codec, broadcaster);
    }
}
```

브로드캐스트 및 구독:

```java
@Service
@RequiredArgsConstructor
public class DeviceStateService {

    private final SyncChannel<DeviceState> channel;

    @PostConstruct
    public void init() {
        channel.subscribe(this::onPeerUpdate);
    }

    public void updateState(DeviceState state) {
        applyLocally(state);
        channel.broadcast(state);   // Sent to all peer instances (self-filtered)
    }

    private void onPeerUpdate(DeviceState state) {
        applyLocally(state);
    }
}
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `simplix.sync.enabled` | `true` | 모듈 활성화 |
| `simplix.sync.mode` | `LOCAL` | 동작 모드 (LOCAL/DISTRIBUTED) |
| `simplix.sync.distributed.broker` | `REDIS` | 분산 백엔드 (REDIS/NATS) |

## Architecture

```
simplix-sync/
+-- autoconfigure/
|   +-- SimpliXSyncAutoConfiguration   # Mode-based broadcaster selection
+-- config/
|   +-- SyncProperties                 # Configuration properties
+-- core/
|   +-- InstanceSyncBroadcaster        # Broadcasting SPI
|   +-- NoOpInstanceSyncBroadcaster    # Local mode (no-op)
|   +-- SyncChannel                    # Typed pub/sub channel
|   +-- PayloadCodec                   # Encoder/decoder interface
|   +-- InMemoryStateStore             # Concurrent state store
|   +-- ReconciliationScheduler        # Round-robin batch processor
+-- infrastructure/
    +-- redis/
    |   +-- RedisInstanceSyncBroadcaster   # Redis Pub/Sub backend
    +-- nats/
        +-- NatsInstanceSyncBroadcaster    # NATS core pub/sub backend
```

## Operation Modes

| 모드 | Broadcaster | 외부 의존성 | 용도 |
|------|-------------|-------------|------|
| `LOCAL` | `NoOpInstanceSyncBroadcaster` | 없음 | 단일 인스턴스, 개발 환경 |
| `DISTRIBUTED` + `REDIS` | `RedisInstanceSyncBroadcaster` | Redis | 일반 분산 운영 |
| `DISTRIBUTED` + `NATS` | `NatsInstanceSyncBroadcaster` | NATS Connection | NATS 기반 인프라 |

> ℹ NATS 모드는 `io.nats.client.Connection` 빈이 별도로 제공되어야 합니다. simplix-messaging이 `simplix.messaging.broker=nats`로 설정된 경우 자동으로 제공됩니다.

## Required Implementations

### PayloadCodec

타입 `T`를 바이트 배열로 인/디코딩하는 코덱을 구현합니다. JSON, Protobuf, Avro 등 어떤 형식이든 사용 가능합니다.

```java
public interface PayloadCodec<T> {
    byte[] encode(T message);
    T decode(byte[] payload) throws IOException;
}
```

### InstanceSyncBroadcaster (선택적 커스터마이징)

기본 구현체 외에 사용자 정의 백엔드를 사용하려면 인터페이스를 직접 구현하여 빈으로 등록하면 됩니다.

```java
public interface InstanceSyncBroadcaster {
    void broadcast(String channel, byte[] payload);
    void subscribe(String channel, InboundPayloadListener listener);
}
```

## Documentation

| Document | Description |
|----------|-------------|
| [Overview](docs/ko/overview.md) | 모듈 개요, 아키텍처, 백엔드 비교, 설정 속성 |
| [Getting Started](docs/ko/getting-started.md) | 단계별 통합 가이드 (LOCAL → DISTRIBUTED 전환) |
| [Advanced Guide](docs/ko/advanced-guide.md) | State Store, Reconciliation, Resilience, Custom Backend |

## Requirements

- Java 17+
- Spring Boot 3.5+
- (Optional) Spring Data Redis - Redis 백엔드 사용 시
- (Optional) jnats client - NATS 백엔드 사용 시

## License

SimpleCORE License 1.0 (SCL-1.0)
