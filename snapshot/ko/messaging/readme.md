# SimpliX Messaging Module

브로커 추상화(BrokerStrategy)를 통해 NATS JetStream, Redis Streams, Kafka, RabbitMQ, Local 메모리 브로커를 통합 지원하는 메시징 모듈입니다. 멱등성, 재시도, Dead Letter, 스케줄링, 리플레이, Request/Reply 등 운영 환경에서 필요한 패턴을 일관된 API로 제공합니다.

## Features

- ✔ **다중 브로커 지원** - NATS JetStream, Redis Streams, Kafka, RabbitMQ, Local
- ✔ **통합 브로커 SPI** - `BrokerStrategy`로 브로커 교체 시 코드 변경 없음
- ✔ **선언적 핸들러** - `@MessageHandler` 어노테이션으로 컨슈머 등록
- ✔ **멱등성 보장** - 메시지 ID 기반 중복 처리 방지 (`IdempotentGuard`)
- ✔ **재시도 + Dead Letter** - 지수 백오프 재시도 후 DLQ 라우팅
- ✔ **지연 메시지** - `MessageScheduler`로 시점 지정 발행
- ✔ **메시지 리플레이** - 시점/ID 범위 기반 과거 메시지 재처리
- ✔ **Request/Reply** - `RequestReplyTemplate`로 동기 응답 패턴
- ✔ **모니터링** - Health Indicator, Micrometer 메트릭 자동 노출
- ✔ **다양한 페이로드** - JSON, Wire Protobuf, raw bytes

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-messaging'

    // 브로커 백엔드 (택 1)
    implementation 'io.nats:jnats'                                    // NATS
    // 또는
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'  // Redis Streams
    // 또는
    implementation 'org.springframework.kafka:spring-kafka'           // Kafka
    // 또는
    implementation 'org.springframework.boot:spring-boot-starter-amqp' // RabbitMQ

    // Optional: Wire 프로토콜 (Protobuf 자동 직렬화)
    implementation 'com.squareup.wire:wire-runtime'
}
```

### 2. Configuration

```yaml
simplix:
  messaging:
    broker: nats              # local, redis, nats, kafka, rabbit
    instance-id: order-svc-1
    channels:
      order-events:
        content-type: application/protobuf
        max-length: 100000
    error:
      max-retries: 3
      retry-backoff: 1s
      dead-letter:
        enabled: true
    idempotent:
      ttl: 24h
```

### 3. Publishing

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MessagePublisher publisher;

    public void placeOrder(OrderProto order) {
        Message<byte[]> message = Message.ofProtobuf("order-events", order);
        PublishResult result = publisher.publish(message);
        log.info("Published recordId={}", result.getRecordId());
    }
}
```

### 4. Subscribing

```java
@Component
public class OrderSubscriber {

    @MessageHandler(channel = "order-events", group = "order-service",
                    idempotent = true, autoAck = false)
    public void handle(Message<OrderProto> message, MessageAcknowledgment ack) {
        OrderProto order = message.getPayload();
        process(order);
        ack.ack();
    }
}
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `simplix.messaging.broker` | `local` | 활성 브로커 (local/redis/nats/kafka/rabbit) |
| `simplix.messaging.instance-id` | hostname | 컨슈머 식별자 |
| `simplix.messaging.subscriber-startup-delay` | `0s` | 구독 시작 지연 |
| `simplix.messaging.publisher.auto-message-id` | `false` | UUID v4 자동 부여 |
| `simplix.messaging.idempotent.ttl` | `24h` | 처리된 메시지 ID 보관 기간 |
| `simplix.messaging.error.max-retries` | `3` | 최대 재시도 횟수 |
| `simplix.messaging.error.retry-backoff` | `1s` | 초기 백오프 |
| `simplix.messaging.error.dead-letter.enabled` | `false` | DLQ 활성화 |
| `simplix.messaging.redis.key-prefix` | `""` | Redis 키 프리픽스 |
| `simplix.messaging.redis.batch-size` | `10` | 폴링 배치 크기 |
| `simplix.messaging.redis.payload-encoding` | `BASE64` | RAW / BASE64 |
| `simplix.messaging.nats.servers` | `nats://localhost:4222` | NATS 서버 URL |
| `simplix.messaging.nats.stream-prefix` | `simplix-` | JetStream 이름 프리픽스 |
| `simplix.messaging.nats.subject-prefix` | `simplix.` | Subject 프리픽스 |
| `simplix.messaging.nats.duplicate-window` | `2m` | JetStream 중복 윈도우 |
| `simplix.messaging.nats.max-age` | `7d` | 메시지 보관 기간 |
| `simplix.messaging.nats.scheduler.enabled` | `true` | KV 기반 스케줄러 활성화 |

## Architecture

```
simplix-messaging/
+-- core/
|   +-- Message                  # Generic message envelope (immutable)
|   +-- MessageHeaders           # Header bag with standard keys
|   +-- MessagePublisher         # Publishing API
|   +-- MessageListener          # Listener callback
|   +-- MessageAcknowledgment    # Manual ack/nack handle
|   +-- PublishResult            # Broker-assigned record ID
|   +-- RetryPolicy              # Backoff + max-attempts
|   +-- JsonCodec                # JSON serialization helper
+-- broker/
|   +-- BrokerStrategy           # Broker SPI
|   +-- BrokerCapabilities       # Feature flags per broker
|   +-- SubscribeRequest         # Subscription parameters
|   +-- Subscription             # Lifecycle handle
|   +-- local/                   # Local in-process broker
|   +-- redis/                   # Redis Streams broker
|   +-- nats/                    # NATS JetStream broker
|   +-- kafka/                   # Kafka broker
|   +-- rabbit/                  # RabbitMQ broker
+-- subscriber/
|   +-- @MessageHandler          # Handler annotation
|   +-- MessageHandlerRegistrar  # Annotation discovery and registration
|   +-- IdempotentGuard          # Message ID-based dedup
+-- pattern/
|   +-- RequestReplyTemplate     # Sync request/reply
|   +-- ScheduledMessagePublisher # Scheduled publish wrapper
|   +-- StreamReplayService      # Replay across brokers
+-- scheduler/
|   +-- MessageScheduler         # Delayed delivery SPI
+-- replay/
|   +-- ReplayService            # Historical replay SPI
+-- error/
|   +-- DeadLetterStrategy       # DLQ routing strategy
|   +-- PoisonMessageHandler     # Repeat-failure handler
+-- dedup/
|   +-- IdempotencyStore         # Pluggable dedup store
+-- monitoring/
|   +-- MessagingHealthIndicator # Actuator health
|   +-- MessagingMetrics         # Micrometer metrics
+-- autoconfigure/
    +-- MessagingAutoConfiguration   # Spring Boot auto-config
    +-- MessagingProperties          # Configuration properties
```

## Broker Comparison

| 기능 | Local | Redis Streams | NATS JetStream | Kafka | RabbitMQ |
|------|-------|---------------|----------------|-------|----------|
| Consumer Groups | ✔ | ✔ | ✔ | ✔ | ✔ |
| Replay | ✔ | ✔ | ✔ | ✔ | ✖ |
| Ordering | ✔ | ✔ | ✔ | ✔ (파티션 단위) | ✔ |
| Dead Letter | ✖ | ✔ (직접) | ✔ (직접) | ✔ | ✔ (네이티브) |
| 지연 발행 | ✔ | ✔ (KV) | ✔ (KV) | ✖ | ✔ |
| 네이티브 중복 제거 | ✖ | ✖ | ✔ (Nats-Msg-Id) | ✖ | ✖ |
| 네이티브 Request/Reply | ✖ | ✖ | ✔ | ✖ | ✔ |
| 외부 의존성 | 없음 | Redis 6+ | NATS 2.10+ | Kafka 3.x | RabbitMQ 3.x |

> ℹ 브로커별 기능 가용성은 `BrokerStrategy.capabilities()`를 통해 런타임에 조회할 수 있습니다.

## Required Implementations

대부분의 사용은 자동 구성으로 충분하지만, 다음 SPI를 직접 구현하여 동작을 확장할 수 있습니다.

### MessageHandler (어노테이션)

```java
@MessageHandler(
    channel = "order-events",
    group = "order-service",
    concurrency = 1,
    autoAck = true,
    idempotent = false
)
public void handle(Message<T> message);

// 또는 수동 ACK
public void handle(Message<T> message, MessageAcknowledgment ack);
```

### IdempotencyStore (선택적 커스터마이징)

```java
public interface IdempotencyStore {
    boolean isProcessed(String messageId);
    void markProcessed(String messageId, Duration ttl);
}
```

기본 구현체:
- `LocalIdempotencyStore` (Caffeine in-process)
- `RedisIdempotencyStore` (Redis SET NX)
- `NatsNativeIdempotencyStore` (JetStream Nats-Msg-Id 활용)

### DeadLetterStrategy (선택적 커스터마이징)

```java
public interface DeadLetterStrategy {
    void route(Message<?> message, Throwable cause, int attempts);
}
```

## Documentation

| Document | Description |
|----------|-------------|
| [Overview](ko/messaging/overview.md) | 상세 아키텍처 및 브로커 비교 |
| [Configuration Reference](ko/messaging/configuration.md) | 설정 옵션 전체 목록 |
| [Broker Guide](ko/messaging/broker-guide.md) | 브로커별 상세 사용법 |
| [Patterns Guide](ko/messaging/patterns-guide.md) | Request/Reply, Replay, Scheduled |

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health/messaging
```

### Metrics

| Metric | Description |
|--------|-------------|
| `simplix.messaging.publish.success` | 발행 성공 카운트 |
| `simplix.messaging.publish.failure` | 발행 실패 카운트 |
| `simplix.messaging.consume.success` | 처리 성공 카운트 |
| `simplix.messaging.consume.failure` | 처리 실패 카운트 |
| `simplix.messaging.consume.duration` | 핸들러 처리 시간 |
| `simplix.messaging.dlq.count` | DLQ 라우팅 횟수 |

## Requirements

- Java 17+
- Spring Boot 3.5+
- (택 1) NATS 2.10+, Redis 6+, Kafka 3.x, RabbitMQ 3.x
- (Optional) Wire 5.x - Protobuf 자동 직렬화
- (Optional) Spring Boot Actuator + Micrometer - 모니터링

## License

SimpleCORE License 1.0 (SCL-1.0)
