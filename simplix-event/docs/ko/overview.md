# SimpliX Event Module

## Overview

SimpliX Event 모듈은 전략 패턴(Strategy Pattern) 기반의 유연한 이벤트 발행 시스템입니다. 런타임에 Local, Redis Streams, Kafka, RabbitMQ 등 다양한 메시지 브로커를 선택할 수 있습니다.

## Features

- **Strategy Pattern**: 설정 변경만으로 이벤트 브로커 교체
- **Multiple Brokers**: Local, Redis Streams, Kafka, RabbitMQ 지원
- **SPI Integration**: Core 모듈과 자동 연동
- **Auto-Configuration**: Spring Boot 자동 설정
- **Monitoring**: Micrometer 메트릭 및 Health Check
- **Conditional Loading**: 클래스패스 의존성에 따라 전략 자동 활성화

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application                               │
│                             │                                    │
│              EventManager.getInstance().publish(event)           │
│                             │                                    │
└─────────────────────────────┼───────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SimpliX Core Module                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     │
│  │ Event        │     │ GenericEvent │     │ EventManager │     │
│  │ (interface)  │     │ (impl)       │     │ (singleton)  │     │
│  └──────────────┘     └──────────────┘     └──────┬───────┘     │
│                                                    │ SPI        │
└────────────────────────────────────────────────────┼────────────┘
                                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SimpliX Event Module                          │
│                                                                  │
│  ┌────────────────────┐    ┌──────────────────────────┐         │
│  │ CoreEventPublisher │───▶│ UnifiedEventPublisher    │         │
│  │ Impl (SPI Bridge)  │    │ (Main Implementation)    │         │
│  └────────────────────┘    └────────────┬─────────────┘         │
│                                         │                        │
│                    ┌────────────────────┼────────────────────┐  │
│                    ▼                    ▼                    ▼  │
│           ┌──────────────┐    ┌──────────────┐    ┌────────────┐│
│           │ Local        │    │ Redis        │    │ Kafka/     ││
│           │ Strategy     │    │ Strategy     │    │ Rabbit     ││
│           └──────────────┘    └──────────────┘    └────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### 1. Dependency

```gradle
implementation 'dev.simplecore:simplix-event:${version}'

// Optional: Choose your broker
implementation 'org.springframework.boot:spring-boot-starter-data-redis'  // Redis
implementation 'org.springframework.kafka:spring-kafka'                    // Kafka
implementation 'org.springframework.boot:spring-boot-starter-amqp'         // RabbitMQ
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

---

## Core Components

### Event Interface

이벤트의 기본 인터페이스입니다:

```java
public interface Event {
    String getEventId();       // UUID (자동 생성)
    String getEventType();     // 이벤트 타입 (필수)
    String getAggregateId();   // 대상 엔티티 ID (필수)
    Instant getOccurredAt();   // 발생 시간 (자동 생성)
    String getUserId();        // 이벤트 발생 사용자
    String getTenantId();      // 테넌트 ID
    Map<String, Object> getMetadata();  // 추가 메타데이터
    Object getPayload();       // 이벤트 페이로드
}
```

### GenericEvent

범용 이벤트 구현체입니다. Builder 패턴으로 생성합니다:

```java
GenericEvent event = GenericEvent.builder()
    .eventType("UserRegistered")     // 필수
    .aggregateId("user-456")         // 필수
    .aggregateType("User")           // metadata에 저장
    .userId("admin-001")             // 이벤트 발생자
    .tenantId("tenant-123")          // 멀티테넌트
    .payload(userData)               // 페이로드 (Object)
    .addMetadata("source", "api")    // 추가 메타데이터
    .build();
```

| 필드 | 필수 | 설명 | 기본값 |
|------|:----:|------|--------|
| `eventType` | O | 이벤트 유형 | - |
| `aggregateId` | O | 대상 엔티티 ID | - |
| `eventId` | - | 이벤트 고유 ID | UUID 자동 생성 |
| `occurredAt` | - | 발생 시간 | 현재 시간 |
| `aggregateType` | - | 엔티티 타입 | - |
| `userId` | - | 이벤트 발생 사용자 | - |
| `tenantId` | - | 테넌트 ID | - |
| `payload` | - | 이벤트 데이터 | - |
| `metadata` | - | 추가 메타데이터 | - |

### EventManager

싱글톤 이벤트 발행 관리자입니다:

```java
// 이벤트 발행
EventManager.getInstance().publish(event);

// 발행자 상태 확인
boolean available = EventManager.getInstance().isAvailable();

// 발행자 이름
String name = EventManager.getInstance().getPublisherName();
```

### PublishOptions

이벤트 발행 옵션을 지정합니다:

```java
PublishOptions options = PublishOptions.builder()
    .critical(true)         // 중요 이벤트 (실패 시 예외)
    .persistent(true)       // 영속화
    .async(false)           // 동기 발행
    .maxRetries(5)          // 최대 재시도
    .retryDelay(1000)       // 재시도 간격 (ms)
    .routingKey("orders")   // 라우팅 키
    .partitionKey("user-1") // Kafka 파티션 키
    .ttl(Duration.ofHours(24))  // TTL
    .addHeader("key", "value")  // 커스텀 헤더
    .build();

eventPublisher.publish(event, options);
```

**정적 팩토리 메서드:**

```java
// 기본 옵션
PublishOptions.defaults();

// 중요 이벤트 (persistent=true, maxRetries=5)
PublishOptions.critical();

// 일회성 이벤트 (재시도 없음)
PublishOptions.fireAndForget();
```

---

## Event Strategies

### LocalEventStrategy

Spring ApplicationEventPublisher를 사용합니다. 외부 의존성 없이 동일 JVM 내에서 이벤트를 발행합니다.

**Mode**: `local`

```yaml
simplix:
  events:
    mode: local
```

**특징:**
- 외부 인프라 불필요
- 개발/테스트 환경에 적합
- 단일 인스턴스 애플리케이션용

---

### RedisEventStrategy

Redis Streams를 사용한 분산 이벤트 발행입니다.

**Mode**: `redis`

```yaml
simplix:
  events:
    mode: redis
    redis:
      stream-prefix: simplix-events
      stream:
        consumer-group: my-app-group
        consumer-name: ${HOSTNAME:auto}
        max-len: 10000
        auto-create-group: true

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**특징:**
- Consumer Group 지원
- 자동 스트림 트리밍 (메모리 관리)
- ACK 기반 메시지 보장

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `stream-prefix` | simplix-events | 스트림 키 접두사 |
| `stream.consumer-group` | simplix-events-group | 컨슈머 그룹명 |
| `stream.consumer-name` | 호스트명 | 컨슈머명 |
| `stream.max-len` | 10000 | 최대 스트림 길이 |
| `stream.auto-create-group` | true | 그룹 자동 생성 |

---

### KafkaEventStrategy

Apache Kafka를 사용한 고처리량 이벤트 스트리밍입니다.

**Mode**: `kafka`

```yaml
simplix:
  events:
    mode: kafka
    kafka:
      topic-prefix: simplix-events
      default-topic: domain-events

spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

**특징:**
- Topic 기반 라우팅
- 파티셔닝 지원 (partitionKey)
- 30초 타임아웃 (무한 대기 방지)
- 지수 백오프 재시도

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `topic-prefix` | simplix-events | 토픽 접두사 |
| `default-topic` | domain-events | 기본 토픽 |

---

### RabbitEventStrategy

RabbitMQ를 사용한 신뢰성 있는 메시지 큐입니다.

**Mode**: `rabbit` 또는 `rabbitmq`

```yaml
simplix:
  events:
    mode: rabbit
    rabbit:
      exchange: simplix.events
      routing-key-prefix: event.
      ttl: 86400000  # 24시간
      max-retries: 3
      retry-delay: 1000
      dlq:
        enabled: true
        exchange: simplix.events.dlq

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

**특징:**
- Exchange/Routing Key 기반 라우팅
- Dead Letter Queue (DLQ) 지원
- 메시지 우선순위 (critical=9, normal=5)
- 지수 백오프 재시도

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `exchange` | simplix.events | 교환기명 |
| `routing-key-prefix` | event. | 라우팅 키 접두사 |
| `ttl` | 86400000 | 메시지 TTL (ms) |
| `max-retries` | 3 | 최대 재시도 |
| `dlq.enabled` | true | DLQ 활성화 |

---

## Configuration Reference

### 전체 설정

```yaml
simplix:
  events:
    # 발행 모드 (local, redis, kafka, rabbit)
    mode: local

    # 메타데이터 자동 추가 (instanceId, publisherMode, enrichedAt)
    enrich-metadata: true

    # 기본 영속화 여부
    persistent-by-default: false

    # 기본 비동기 발행 여부
    async-by-default: true

    # 인스턴스 ID (분산 환경 추적용)
    instance-id: ${HOSTNAME:auto}

    # 모니터링
    monitoring:
      metrics-enabled: true
      metrics-prefix: simplix.events
      health-check:
        enabled: true
        interval: 30000
```

---

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "eventPublisher": {
      "status": "UP",
      "details": {
        "mode": "LocalEventStrategy",
        "enrichMetadata": true,
        "persistentByDefault": false
      }
    }
  }
}
```

### Metrics

Micrometer 기반 메트릭 (eventType 태그 포함):

| 메트릭 | 설명 |
|--------|------|
| `simplix.events.published` | 발행된 이벤트 수 |
| `simplix.events.failed` | 실패한 이벤트 수 |
| `simplix.events.publish.time` | 발행 소요 시간 |

```promql
# Grafana 쿼리 예시
sum by (eventType) (simplix_events_published_total)
rate(simplix_events_failed_total[5m]) / rate(simplix_events_published_total[5m])
```

---

## Strategy Selection Guide

| 환경 | 권장 전략 | 이유 |
|------|----------|------|
| 개발/테스트 | Local | 외부 인프라 불필요 |
| 분산 시스템 (중소규모) | Redis | Consumer Group, 간편한 설정 |
| 고처리량 | Kafka | 파티셔닝, 확장성 |
| 복잡한 라우팅 | RabbitMQ | Exchange/Routing, DLQ |

---

## Related Documents

- [Usage Guide](./usage-guide.md) - 상세 사용법 가이드
- [SimpliX Core Module](../../../simplix-core/)
