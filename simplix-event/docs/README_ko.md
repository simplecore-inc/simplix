# SimpliX Events Module

## Overview
SimpliX Events 모듈은 유연하고 확장 가능한 이벤트 발행 인프라를 제공합니다. Core 모듈의 EventPublisher 인터페이스를 구현하며, 다양한 이벤트 브로커(Local, Redis Streams, Kafka, RabbitMQ)를 지원하는 Strategy 패턴 기반의 아키텍처를 제공합니다.

## Key Features

- ✅ **Strategy Pattern**: 런타임에 이벤트 발행 전략을 선택할 수 있는 유연한 아키텍처
- ✅ **Multiple Brokers**: Local (in-memory), Redis Streams, Kafka, RabbitMQ 지원
- ✅ **SPI Integration**: Core 모듈의 Service Provider Interface를 통한 자동 연동
- ✅ **Auto-Configuration**: Spring Boot Auto-Configuration으로 간편한 설정
- ✅ **Monitoring**: Micrometer 기반 메트릭 및 Health Check 지원 (eventType 태그 포함)
- ✅ **Conditional Loading**: 클래스패스에 있는 의존성에 따라 자동으로 전략 활성화
- ✅ **Production-Ready**: 타임아웃, 재시도, 리소스 관리 등 안정성 강화
- ✅ **Metadata Enrichment**: 자동 메타데이터 보강 (instanceId, publisherMode, timestamp)

## Architecture

### Core Components

#### 1. EventPublisher (Core Interface)
Core 모듈에서 정의된 이벤트 발행 인터페이스:
```java
public interface EventPublisher {
    void publish(DomainEvent event);
    void publish(DomainEvent event, Object options);
    boolean isAvailable();
    String getName();
    int getPriority();
}
```

#### 2. UnifiedEventPublisher
EventPublisher 인터페이스의 주요 구현체로, 선택된 EventStrategy에 이벤트 발행을 위임합니다.

**위치**: `dev.simplecore.simplix.event.publisher.UnifiedEventPublisher`

**주요 기능**:
- 설정된 mode에 따라 적절한 EventStrategy 선택
- 이벤트 메타데이터 보강 (instanceId, publisherMode, timestamp)
- Critical 이벤트와 일반 이벤트 처리 차별화
- 발행 성공/실패 로깅

#### 3. CoreEventPublisherImpl
Core 모듈과 Events 모듈을 연결하는 SPI 브릿지 구현체입니다.

**위치**: `dev.simplecore.simplix.event.provider.CoreEventPublisherImpl`

**특징**:
- SPI를 통해 Core 모듈에서 자동 검색
- UnifiedEventPublisher에 이벤트 발행 위임
- 높은 우선순위(Priority: 100)로 기본 EventPublisher로 선택

#### 4. EventStrategy
각 메시지 브로커를 위한 전략 인터페이스:

**위치**: `dev.simplecore.simplix.event.core.EventStrategy`

**메서드**:
- `publish(DomainEvent event, PublishOptions options)`: 이벤트 발행
- `supports(String mode)`: 지원하는 mode 확인
- `initialize()`: 전략 초기화
- `shutdown()`: 리소스 정리
- `isReady()`: 발행 준비 상태 확인
- `getName()`: 전략 이름 반환

### Event Strategies

#### LocalEventStrategy
Spring의 ApplicationEventPublisher를 사용하여 동일 JVM 내에서 이벤트를 발행합니다.

**Mode**: `local`

**특징**:
- 외부 의존성 없음
- 동기/비동기 발행 지원
- 개발 및 테스트 환경에 적합

**위치**: `dev.simplecore.simplix.event.strategy.LocalEventStrategy`

#### RedisEventStrategy
Redis Streams를 사용하여 이벤트를 발행합니다.

**Mode**: `redis`

**필수 의존성**: `spring-boot-starter-data-redis`

**특징**:
- Redis Streams 기반 발행 (Pub/Sub에서 마이그레이션)
- Consumer Group 지원 및 lazy creation
- 자동 Stream trimming으로 메모리 관리
- ACK 기반 메시지 보장
- 실패 추적 및 모니터링

**위치**: `dev.simplecore.simplix.event.strategy.RedisEventStrategy`

#### KafkaEventStrategy
Apache Kafka를 사용하여 이벤트를 발행합니다.

**Mode**: `kafka`

**필수 의존성**: `spring-kafka`

**특징**:
- Topic 기반 발행
- 파티셔닝 지원 (partitionKey 옵션)
- 높은 처리량과 내구성
- **30초 타임아웃**: 무한 대기 방지
- **개선된 재시도**: CompletableFuture.delayedExecutor 사용으로 메모리 효율적
- **UTF-8 헤더 인코딩**: 명시적 인코딩으로 호환성 보장
- **Lazy 초기화**: 애플리케이션 시작 시간 단축

**위치**: `dev.simplecore.simplix.event.strategy.KafkaEventStrategy`

#### RabbitEventStrategy
RabbitMQ를 사용하여 이벤트를 발행합니다.

**Mode**: `rabbit` 또는 `rabbitmq`

**필수 의존성**: `spring-boot-starter-amqp`

**특징**:
- Exchange와 Routing Key 기반 발행
- DLQ(Dead Letter Queue) 지원
- **비동기 재시도**: CompletableFuture.delayedExecutor로 스레드 블로킹 없음
- **Exponential Backoff**: 재시도 간격 자동 증가
- **AggregateId Null-Safe**: null 시 "unknown" 사용
- 메시지 우선순위 지원 (Critical 이벤트는 우선순위 9)

**위치**: `dev.simplecore.simplix.event.strategy.RabbitEventStrategy`

## Configuration

### Basic Configuration

`application.yml`:
```yaml
simplix:
  events:
    # Event publishing mode (local, redis, kafka, rabbit)
    mode: local

    # Enable metadata enrichment (adds instanceId, publisherMode, timestamps)
    enrich-metadata: true

    # Make events persistent by default
    persistent-by-default: false

    # Default async mode for publishing
    async-by-default: true

    # Instance ID for tracking
    instance-id: ${INSTANCE_ID:#{T(java.util.UUID).randomUUID().toString()}}

    # Monitoring
    monitoring:
      metrics-enabled: true
      metrics-prefix: simplix.events
      health-check:
        enabled: true
        interval: 30000  # milliseconds
```

### Strategy-Specific Configuration

#### Redis Streams Configuration
```yaml
simplix:
  events:
    mode: redis
    redis:
      # Redis stream prefix
      stream-prefix: simplix-events

      # Default TTL for stream entries
      default-ttl: 24h  # Duration format

      # Redis Stream configuration
      stream:
        enabled: true
        consumer-group: simplix-events-group
        consumer-name: ${HOSTNAME:auto}  # Auto-generated if not specified

        # Maximum stream length (approximate trimming)
        max-len: 10000

        # Auto-create consumer group if it doesn't exist
        auto-create-group: true

        # Start reading from the beginning if consumer group is new
        read-from-beginning: false

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: 2000ms
      lettuce:
        pool:
          enabled: true
          max-active: 8
          max-idle: 8
          min-idle: 0
```

#### Kafka Configuration
```yaml
simplix:
  events:
    mode: kafka
    kafka:
      topic-prefix: simplix-events
      default-topic: domain-events
      producer:
        acks: "1"  # Leader acknowledgment (use "all" for production)
        retries: 3
        retry-backoff-ms: 100
        compression-type: snappy
        batch-size: 16384
        linger-ms: 10

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
```

#### RabbitMQ Configuration
```yaml
simplix:
  events:
    mode: rabbit
    rabbit:
      # Exchange configuration
      exchange: simplix.events
      exchange-type: topic
      durable-exchange: true

      # Routing configuration
      routing-key-prefix: event.

      # Message properties
      ttl: 86400000  # 24 hours in milliseconds

      # Retry configuration
      max-retries: 3
      retry-delay: 1000  # milliseconds
      retry-multiplier: 2.0
      max-retry-delay: 30000  # 30 seconds

      # Dead Letter Queue configuration
      dlq:
        enabled: true
        exchange: simplix.events.dlq
        queue: simplix.events.dlq.queue
        ttl: 604800000  # 7 days

spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: ${RABBITMQ_VHOST:/}
```

## Usage

### Publishing Events from Domain Module

Domain 모듈에서는 Core 모듈의 EventManager를 통해 이벤트를 발행합니다:

```java
@Service
@RequiredArgsConstructor
public class VisitService {

    private final EventManager eventManager;
    private final VisitRepository visitRepository;

    @Transactional
    public void createVisit(VisitCreateDto dto) {
        // Create entity
        Visit visit = new Visit();
        visit.setVisitorName(dto.getVisitorName());
        Visit savedVisit = visitRepository.save(visit);

        // Publish event through EventManager
        DomainEventMessage message = DomainEventMessage.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("VisitCreated")
            .aggregateId(savedVisit.getId().toString())
            .aggregateType("Visit")
            .payload(convertToJson(savedVisit))
            .occurredAt(Instant.now())
            .build();

        eventManager.publishEvent(message);
    }
}
```

### Direct Usage (Advanced)

특별한 경우에는 UnifiedEventPublisher를 직접 사용할 수도 있습니다:

```java
@Service
@RequiredArgsConstructor
public class CustomEventService {

    private final UnifiedEventPublisher eventPublisher;

    public void publishCustomEvent(DomainEvent event) {
        // Basic publish
        eventPublisher.publish(event);

        // Publish with options
        PublishOptions options = PublishOptions.builder()
            .critical(true)
            .async(false)
            .persistent(true)
            .build();

        eventPublisher.publish(event, options);
    }
}
```

## Monitoring

### Health Check

이벤트 발행 시스템의 상태를 확인할 수 있습니다:

```bash
# Health endpoint
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "eventPublisher": {
      "status": "UP",
      "details": {
        "strategy": "LocalEventStrategy",
        "mode": "local",
        "ready": true
      }
    }
  }
}
```

### Metrics

Micrometer를 통해 다양한 메트릭을 수집합니다. **eventType별로 태그가 추가**되어 세분화된 모니터링이 가능합니다:

```bash
# Metrics endpoint
curl http://localhost:8080/actuator/metrics/simplix.events.published
curl http://localhost:8080/actuator/metrics/simplix.events.failed
curl http://localhost:8080/actuator/metrics/simplix.events.publish.time
```

**Available Metrics**:
- `simplix.events.published`: 발행된 이벤트 수 (eventType 태그 포함)
- `simplix.events.failed`: 발행 실패 횟수 (eventType 태그 포함)
- `simplix.events.publish.time`: 이벤트 발행 소요 시간

**메트릭 예시** (eventType 태그 활용):
```json
{
  "name": "simplix.events.published",
  "measurements": [{"value": 1250.0}],
  "availableTags": [
    {"tag": "eventType", "values": ["UserCreated", "UserUpdated", "OrderPlaced"]}
  ]
}
```

**Grafana 쿼리 예시**:
```promql
# 이벤트 타입별 발행 수
sum by (eventType) (simplix_events_published_total)

# 실패율
rate(simplix_events_failed_total[5m]) / rate(simplix_events_published_total[5m])
```

## Switching Event Strategies

### Development (Local)
```yaml
simplix:
  events:
    mode: local
```

### Production with Redis Streams
```yaml
simplix:
  events:
    mode: redis
    redis:
      stream-prefix: prod-events
      default-ttl: 7d  # 7 days
      stream:
        enabled: true
        consumer-group: prod-events-group
        max-len: 100000  # Higher limit for production
        auto-create-group: true
```

### Production with Kafka
```yaml
simplix:
  events:
    mode: kafka
    kafka:
      topic-prefix: prod-events
      producer:
        acks: "all"  # Stronger guarantee - wait for all replicas
        retries: 5
        compression-type: snappy
```

### Production with RabbitMQ
```yaml
simplix:
  events:
    mode: rabbit
    rabbit:
      exchange: prod.events
      dlq:
        enabled: true  # Always enable DLQ in production
      max-retries: 5
```

## Dependencies

### Core Dependencies (Always Required)
```gradle
implementation project(':simplix-core')
implementation 'org.springframework.boot:spring-boot-starter'
implementation 'com.fasterxml.jackson.core:jackson-databind'
```

### Optional Dependencies (Strategy-specific)
```gradle
// For Redis Streams strategy
compileOnly 'org.springframework.boot:spring-boot-starter-data-redis'

// For Kafka strategy
compileOnly 'org.springframework.kafka:spring-kafka'

// For RabbitMQ strategy
compileOnly 'org.springframework.boot:spring-boot-starter-amqp'

// For Monitoring (recommended)
compileOnly 'io.micrometer:micrometer-core'
compileOnly 'org.springframework.boot:spring-boot-starter-actuator'
```

## Testing

### Unit Testing with Local Strategy

```java
@SpringBootTest
@TestPropertySource(properties = {
    "simplix.events.mode=local"
})
class EventPublishingTest {

    @Autowired
    private UnifiedEventPublisher eventPublisher;

    @Test
    void shouldPublishEventSuccessfully() {
        // Given
        DomainEventMessage event = createTestEvent();

        // When
        eventPublisher.publish(event);

        // Then
        assertTrue(eventPublisher.isAvailable());
    }
}
```

### Integration Testing with Testcontainers

```java
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "simplix.events.mode=redis"
})
class RedisEventPublishingTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private UnifiedEventPublisher eventPublisher;

    @Test
    void shouldPublishToRedis() {
        // Test implementation
    }
}
```

## Best Practices

### 1. Strategy 선택 가이드
- **개발/테스트**: Local (외부 인프라 불필요)
- **분산 시스템**: Redis Streams (Consumer Group 지원)
- **높은 처리량**: Kafka (파티셔닝 및 확장성)
- **복잡한 라우팅**: RabbitMQ (Exchange/Routing Key)

### 2. 프로덕션 설정
```yaml
simplix:
  events:
    enrich-metadata: true  # 항상 활성화
    monitoring:
      metrics-enabled: true
      health-check:
        enabled: true
```

### 3. Critical 이벤트 처리
```java
PublishOptions options = PublishOptions.builder()
    .critical(true)        // 실패 시 예외 발생
    .persistent(true)      // 메시지 영속성 보장
    .maxRetries(5)         // 재시도 횟수 증가
    .async(false)          // 동기 발행으로 확실한 전송
    .build();

eventPublisher.publish(event, options);
```

### 4. 성능 최적화
- **Kafka**: `linger.ms`와 `batch.size` 조정으로 배치 처리
- **Redis**: `max-len` 설정으로 메모리 관리
- **RabbitMQ**: Connection pool 크기 조정

### 5. 모니터링
- eventType별 메트릭 활용
- 실패율 알람 설정 (> 1%)
- Health check 주기적 확인

### 6. 테스트
- Testcontainers로 실제 브로커 테스트
- Critical 이벤트는 재시도 로직 검증
- DLQ 동작 테스트 (RabbitMQ)

## Troubleshooting

### 1. No Event Strategy Found
**증상**: `IllegalStateException: No event strategy found for mode: xxx`

**원인**: 필요한 의존성이 클래스패스에 없음

**해결**:
```gradle
// compileOnly가 아닌 implementation으로 변경
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
// 또는
implementation 'org.springframework.kafka:spring-kafka'
// 또는
implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

### 2. Events Not Being Published
**증상**: 이벤트 발행 시 아무 동작도 하지 않음

**진단**:
```java
// Strategy 상태 확인
boolean ready = eventPublisher.isAvailable();
String strategyName = eventPublisher.getName();

// Health endpoint 확인
curl http://localhost:8080/actuator/health/eventPublisher
```

**해결**:
```yaml
# 로그 레벨 DEBUG로 설정
logging:
  level:
    dev.simplecore.simplix.event: DEBUG
```

### 3. Redis Connection Timeout
**증상**: `RedisConnectionException: Unable to connect to Redis`

**해결**:
```yaml
spring:
  data:
    redis:
      timeout: 5000ms          # 타임아웃 증가
      connect-timeout: 10s     # 연결 타임아웃
      lettuce:
        pool:
          max-active: 16       # Pool 크기 증가
```

### 4. Kafka Publish Timeout
**증상**: `TimeoutException: Failed to send after 30 seconds`

**원인**: 30초 타임아웃 내에 브로커가 응답하지 않음

**해결**:
- Kafka 브로커 상태 확인
- 네트워크 지연 확인
- `acks` 설정을 "1"로 낮추기 (응답 시간 단축)

### 5. RabbitMQ DLQ 메시지 확인
**증상**: 메시지가 DLQ에 쌓임

**확인**:
```bash
# RabbitMQ Management UI
http://localhost:15672

# 또는 CLI로 확인
rabbitmqadmin get queue=simplix.events.dlq.queue count=10
```

### 6. Redis Stream Memory 증가
**증상**: Redis 메모리 사용량이 계속 증가

**원인**: Stream trimming이 비활성화되거나 max-len이 너무 큼

**해결**:
```yaml
simplix:
  events:
    redis:
      stream:
        max-len: 10000  # 적절한 값으로 조정
```

### 7. Null Event Exception
**증상**: `IllegalArgumentException: Attempted to publish null event`

**원인**: null 이벤트 발행 시도 (fail-fast 설계)

**해결**: 이벤트가 null이 아닌지 확인
```java
if (event != null) {
    eventPublisher.publish(event);
}
```

## Migration Guide

### From Local to Production Strategy

1. **개발 환경 (Local)**:
```yaml
simplix:
  events:
    mode: local
```

2. **스테이징/프로덕션**:
```yaml
simplix:
  events:
    mode: kafka  # or redis, rabbit
    enrich-metadata: true
    monitoring:
      metrics-enabled: true
```

## Additional Resources

- [SimpliX Core Module](../../simplix-core/)
- [Spring Events Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Redis Streams Documentation](https://redis.io/docs/data-types/streams/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [Micrometer Documentation](https://micrometer.io/docs)
