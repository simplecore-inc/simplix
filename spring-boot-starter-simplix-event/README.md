# AccessCore Entra Events Module

Event-driven architecture support module for the AccessCore Entra application.

## Features

- ✔ **Strategy Pattern** - Runtime selection of event publishing mechanism
- ✔ **Multiple Brokers** - Local (in-memory), Redis Pub/Sub, Kafka, RabbitMQ support
- ✔ **SPI Integration** - Automatic integration with Core module via Service Provider Interface
- ✔ **Auto-Configuration** - Spring Boot auto-configuration for easy setup
- ✔ **Monitoring** - Micrometer-based metrics and health checks
- ✔ **Conditional Loading** - Strategies are auto-enabled based on classpath dependencies

## Quick Start

### 1. Add Dependency

```gradle
dependencies {
    implementation project(':accesscore-entra-events')

    // Optional: Add strategy-specific dependencies
    // implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    // implementation 'org.springframework.kafka:spring-kafka'
    // implementation 'org.springframework.boot:spring-boot-starter-amqp'
}
```

### 2. Configure Event Mode

```yaml
accesscore:
  events:
    mode: local  # Options: local, redis, kafka, rabbit
    enrich-metadata: true
```

### 3. Publish Events

Events are published through the Core module's `EventManager`:

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final EventManager eventManager;

    public void doSomething() {
        DomainEventMessage event = DomainEventMessage.builder()
            .eventType("SomethingHappened")
            .aggregateId("123")
            .build();

        eventManager.publishEvent(event);
    }
}
```

## Architecture

### Core Components

```
Core Module (SPI)
    ↓
CoreEventPublisherImpl (Bridge)
    ↓
UnifiedEventPublisher (Main Publisher)
    ↓
EventStrategy (Strategy Pattern)
    ├── LocalEventStrategy
    ├── RedisEventStrategy
    ├── KafkaEventStrategy
    └── RabbitEventStrategy
```

### Component Descriptions

- **EventPublisher** (Core Interface): Defined in core module for event publishing
- **UnifiedEventPublisher**: Main implementation that delegates to selected strategy
- **CoreEventPublisherImpl**: SPI bridge connecting core and events modules
- **EventStrategy**: Interface for different broker implementations

## Event Strategies

### Local Strategy (Default)
- **Mode**: `local`
- **Broker**: Spring ApplicationEventPublisher
- **Use Case**: Development, single-instance applications
- **Dependencies**: None (built-in)

### Redis Strategy
- **Mode**: `redis`
- **Broker**: Redis Pub/Sub
- **Use Case**: Distributed systems with simple event patterns
- **Dependencies**: `spring-boot-starter-data-redis`

### Kafka Strategy
- **Mode**: `kafka`
- **Broker**: Apache Kafka
- **Use Case**: High-throughput, event streaming applications
- **Dependencies**: `spring-kafka`

### RabbitMQ Strategy
- **Mode**: `rabbit`
- **Broker**: RabbitMQ
- **Use Case**: Complex routing, DLQ support needed
- **Dependencies**: `spring-boot-starter-amqp`

## Configuration Examples

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
        ttl: 86400

spring:
  data:
    redis:
      host: redis.example.com
      port: 6379
```

### Production with Kafka
```yaml
accesscore:
  events:
    mode: kafka
    kafka:
      topic-prefix: "prod-events"
      default-topic: "domain-events"
      producer:
        acks: "all"
        compression-type: "snappy"

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
- `events.publish.total` - Total events published
- `events.publish.duration` - Publishing duration
- `events.publish.errors` - Publishing errors
- `events.publish.critical` - Critical events published

## Documentation

For detailed documentation, see:
- [Korean Documentation (한국어 문서)](docs/README_ko.md)

## Testing

### Unit Tests
```java
@SpringBootTest
@TestPropertySource(properties = "accesscore.events.mode=local")
class EventPublishingTest {
    @Autowired
    private UnifiedEventPublisher eventPublisher;

    @Test
    void shouldPublishEvent() {
        eventPublisher.publish(createTestEvent());
        assertTrue(eventPublisher.isAvailable());
    }
}
```

### Integration Tests with Testcontainers
```java
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "accesscore.events.mode=redis")
class RedisEventPublishingTest {
    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // Tests...
}
```

## License

This project is developed for internal use.