# SimpliX Event Module

이벤트 기반 아키텍처를 위한 유연한 이벤트 발행 모듈입니다.

## Features

- **Strategy Pattern** - 설정 변경만으로 이벤트 브로커 교체
- **Multiple Brokers** - Local, Redis Streams, Kafka, RabbitMQ 지원
- **SPI Integration** - Core 모듈과 자동 연동
- **Auto-Configuration** - Spring Boot 자동 설정
- **Monitoring** - Micrometer 메트릭 및 Health Check

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-event:${version}'

    // Optional: 브로커 선택
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'  // Redis
    implementation 'org.springframework.kafka:spring-kafka'                    // Kafka
    implementation 'org.springframework.boot:spring-boot-starter-amqp'         // RabbitMQ
}
```

### 2. Configuration

```yaml
simplix:
  events:
    mode: local  # local, redis, kafka, rabbit
```

### 3. Publish Event

```java
import dev.simplecore.simplix.core.event.EventManager;
import dev.simplecore.simplix.core.event.GenericEvent;

GenericEvent event = GenericEvent.builder()
    .eventType("OrderCreated")
    .aggregateId("order-123")
    .payload(orderData)
    .build();

EventManager.getInstance().publish(event);
```

## Event Strategies

| Mode | Broker | Use Case |
|------|--------|----------|
| `local` | Spring ApplicationEventPublisher | 개발/테스트, 단일 인스턴스 |
| `redis` | Redis Streams | 분산 시스템 (중소규모) |
| `kafka` | Apache Kafka | 고처리량, 이벤트 스트리밍 |
| `rabbit` | RabbitMQ | 복잡한 라우팅, DLQ 필요 |

## Configuration Examples

### Development (Local)

```yaml
simplix:
  events:
    mode: local
```

### Production (Redis)

```yaml
simplix:
  events:
    mode: redis
    redis:
      stream-prefix: simplix-events
      stream:
        consumer-group: my-app-group
        max-len: 10000

spring:
  data:
    redis:
      host: redis.example.com
      port: 6379
```

### Production (Kafka)

```yaml
simplix:
  events:
    mode: kafka
    kafka:
      topic-prefix: simplix-events

spring:
  kafka:
    bootstrap-servers: kafka.example.com:9092
```

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Metrics

| Metric | Description |
|--------|-------------|
| `simplix.events.published` | 발행된 이벤트 수 |
| `simplix.events.failed` | 실패한 이벤트 수 |
| `simplix.events.publish.time` | 발행 소요 시간 |

## Documentation

- [Overview (상세 문서)](overview.md)
- [Usage Guide (사용법 가이드)](usage-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
