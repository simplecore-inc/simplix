# AccessCore Entra Events Module

## Overview
Events 모듈은 AccessCore Entra 프로젝트를 위한 유연하고 확장 가능한 이벤트 발행 인프라를 제공합니다. Core 모듈의 EventPublisher 인터페이스를 구현하며, 다양한 이벤트 브로커(Local, Redis, Kafka, RabbitMQ)를 지원하는 Strategy 패턴 기반의 아키텍처를 제공합니다.

## Key Features

- ✔ **Strategy Pattern**: 런타임에 이벤트 발행 전략을 선택할 수 있는 유연한 아키텍처
- ✔ **Multiple Brokers**: Local (in-memory), Redis Pub/Sub, Kafka, RabbitMQ 지원
- ✔ **SPI Integration**: Core 모듈의 Service Provider Interface를 통한 자동 연동
- ✔ **Auto-Configuration**: Spring Boot Auto-Configuration으로 간편한 설정
- ✔ **Monitoring**: Micrometer 기반 메트릭 및 Health Check 지원
- ✔ **Conditional Loading**: 클래스패스에 있는 의존성에 따라 자동으로 전략 활성화

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

**위치**: `dev.simplecore.accesscore.entra.events.publisher.UnifiedEventPublisher`

**주요 기능**:
- 설정된 mode에 따라 적절한 EventStrategy 선택
- 이벤트 메타데이터 보강 (instanceId, publisherMode, timestamp)
- Critical 이벤트와 일반 이벤트 처리 차별화
- 발행 성공/실패 로깅

#### 3. CoreEventPublisherImpl
Core 모듈과 Events 모듈을 연결하는 SPI 브릿지 구현체입니다.

**위치**: `dev.simplecore.accesscore.entra.events.provider.CoreEventPublisherImpl`

**특징**:
- SPI를 통해 Core 모듈에서 자동 검색
- UnifiedEventPublisher에 이벤트 발행 위임
- 높은 우선순위(Priority: 100)로 기본 EventPublisher로 선택

#### 4. EventStrategy
각 메시지 브로커를 위한 전략 인터페이스:

**위치**: `dev.simplecore.accesscore.entra.events.core.EventStrategy`

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

**위치**: `dev.simplecore.accesscore.entra.events.strategy.LocalEventStrategy`

#### RedisEventStrategy
Redis Pub/Sub을 사용하여 이벤트를 발행합니다.

**Mode**: `redis`

**필수 의존성**: `spring-boot-starter-data-redis`

**특징**:
- Channel 기반 발행
- 옵션으로 영구 저장 지원
- 배치 처리 지원

**위치**: `dev.simplecore.accesscore.entra.events.strategy.RedisEventStrategy`

#### KafkaEventStrategy
Apache Kafka를 사용하여 이벤트를 발행합니다.

**Mode**: `kafka`

**필수 의존성**: `spring-kafka`

**특징**:
- Topic 기반 발행
- 파티셔닝 지원
- 높은 처리량과 내구성

**위치**: `dev.simplecore.accesscore.entra.events.strategy.KafkaEventStrategy`

#### RabbitEventStrategy
RabbitMQ를 사용하여 이벤트를 발행합니다.

**Mode**: `rabbit`

**필수 의존성**: `spring-boot-starter-amqp`

**특징**:
- Exchange와 Routing Key 기반 발행
- DLQ(Dead Letter Queue) 지원
- 재시도 로직 내장

**위치**: `dev.simplecore.accesscore.entra.events.strategy.RabbitEventStrategy`

## Configuration

### Basic Configuration

`application.yml`:
```yaml
accesscore:
  events:
    # Event publishing mode (local, redis, kafka, rabbit)
    mode: local

    # Enable metadata enrichment
    enrich-metadata: true

    # Instance ID for tracking
    instance-id: ${INSTANCE_ID:#{T(java.util.UUID).randomUUID().toString()}}

    # Monitoring
    monitoring:
      metrics-enabled: true
      health-check:
        enabled: true
```

### Strategy-Specific Configuration

#### Redis Configuration
```yaml
accesscore:
  events:
    mode: redis
    redis:
      channel-prefix: "accesscore:events:"
      persistent:
        enabled: false
        ttl: 86400  # 24 hours

spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
```

#### Kafka Configuration
```yaml
accesscore:
  events:
    mode: kafka
    kafka:
      topic-prefix: "accesscore-events"
      default-topic: "domain-events"
      producer:
        acks: "1"
        compression-type: "snappy"

spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

#### RabbitMQ Configuration
```yaml
accesscore:
  events:
    mode: rabbit
    rabbit:
      exchange: "accesscore.events"
      exchange-type: "topic"
      routing-key-prefix: "event."
      dlq:
        enabled: true
        exchange: "accesscore.events.dlq"

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
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

Micrometer를 통해 다양한 메트릭을 수집합니다:

```bash
# Metrics endpoint
curl http://localhost:8080/actuator/metrics/events.publish.total
```

**Available Metrics**:
- `events.publish.total`: 총 발행된 이벤트 수
- `events.publish.duration`: 이벤트 발행 소요 시간
- `events.publish.errors`: 발행 실패 횟수
- `events.publish.critical`: Critical 이벤트 발행 수

## Switching Event Strategies

### Development (Local)
```yaml
accesscore:
  events:
    mode: local
```

### Production with Redis
```yaml
accesscore:
  events:
    mode: redis
    redis:
      channel-prefix: "prod:events:"
      persistent:
        enabled: true
```

### Production with Kafka
```yaml
accesscore:
  events:
    mode: kafka
    kafka:
      topic-prefix: "prod-events"
      producer:
        acks: "all"  # Stronger guarantee
        compression-type: "snappy"
```

## Dependencies

### Core Dependencies (Always Required)
```gradle
implementation project(':accesscore-entra-core')
implementation 'org.springframework.boot:spring-boot-starter'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

### Optional Dependencies (Strategy-specific)
```gradle
// For Redis strategy
compileOnly 'org.springframework.boot:spring-boot-starter-data-redis'

// For Kafka strategy
compileOnly 'org.springframework.kafka:spring-kafka'

// For RabbitMQ strategy
compileOnly 'org.springframework.boot:spring-boot-starter-amqp'
```

## Testing

### Unit Testing with Local Strategy

```java
@SpringBootTest
@TestPropertySource(properties = {
    "accesscore.events.mode=local"
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
    "accesscore.events.mode=redis"
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

1. **Use Local Strategy for Development**: 외부 인프라 없이 빠른 개발 가능
2. **Choose Strategy Based on Requirements**:
   - Simple use cases: Local
   - Distributed systems: Redis or RabbitMQ
   - High throughput: Kafka
3. **Enable Monitoring**: 프로덕션 환경에서는 항상 메트릭과 헬스체크 활성화
4. **Configure Critical Events**: 중요한 이벤트는 `PublishOptions.critical(true)` 설정
5. **Test with Real Brokers**: Testcontainers를 사용하여 실제 브로커로 테스트

## Troubleshooting

### No Event Strategy Found
**문제**: `IllegalStateException: No event strategy found for mode: xxx`

**해결**:
- 해당 mode에 필요한 의존성이 클래스패스에 있는지 확인
- Redis/Kafka/RabbitMQ 의존성을 `compileOnly`가 아닌 `implementation`으로 추가

### Events Not Being Published
**문제**: 이벤트 발행 시 아무 동작도 하지 않음

**해결**:
- `eventPublisher.isAvailable()` 호출하여 상태 확인
- 로그 레벨을 DEBUG로 설정: `logging.level.dev.simplecore.accesscore.entra.events=DEBUG`
- Health endpoint 확인

### Connection Failures
**문제**: Redis/Kafka/RabbitMQ 연결 실패

**해결**:
- 브로커 서버가 실행 중인지 확인
- 연결 정보(host, port, credentials) 확인
- 네트워크 방화벽 설정 확인

## Migration Guide

### From Legacy Event System

기존 이벤트 시스템에서 마이그레이션하는 경우:

1. **Local Strategy로 시작**:
   ```yaml
   accesscore:
     events:
       mode: local
   ```

2. **EventManager를 통한 발행으로 변경**:
   ```java
   // Before
   applicationEventPublisher.publishEvent(event);

   // After
   eventManager.publishEvent(event);
   ```

3. **프로덕션 환경에서 Strategy 전환**:
   ```yaml
   accesscore:
     events:
       mode: kafka  # or redis, rabbit
   ```

## Additional Resources

- [Core Module Documentation](../../accesscore-entra-core/docs/)
- [Domain Module Documentation](../../accesscore-entra-domain/docs/)
- [Spring Events Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Redis Pub/Sub](https://redis.io/docs/manual/pubsub/)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [RabbitMQ](https://www.rabbitmq.com/documentation.html)