# SimpliX Event Usage Guide

이벤트 발행 및 수신에 대한 상세 사용법 가이드입니다.

## Table of Contents

- [Basic Usage](#basic-usage)
- [Event Patterns](#event-patterns)
- [Event Listener](#event-listener)
- [Advanced Options](#advanced-options)
- [Service Integration](#service-integration)
- [Testing](#testing)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Basic Usage

### Simple Event Publishing

가장 기본적인 이벤트 발행 방법입니다:

```java
import dev.simplecore.simplix.core.event.EventManager;
import dev.simplecore.simplix.core.event.GenericEvent;

// 이벤트 생성
GenericEvent event = GenericEvent.builder()
    .eventType("UserCreated")
    .aggregateId("user-123")
    .build();

// 이벤트 발행
EventManager.getInstance().publish(event);
```

### Event with Payload

페이로드와 함께 이벤트를 발행합니다:

```java
// Map 페이로드
GenericEvent event = GenericEvent.builder()
    .eventType("OrderPlaced")
    .aggregateId("order-456")
    .aggregateType("Order")
    .payload(Map.of(
        "customerId", "cust-789",
        "totalAmount", 15000,
        "items", List.of("item-1", "item-2")
    ))
    .build();

EventManager.getInstance().publish(event);
```

```java
// DTO 페이로드
OrderCreatedPayload payload = new OrderCreatedPayload(
    "order-456", "cust-789", 15000
);

GenericEvent event = GenericEvent.builder()
    .eventType("OrderCreated")
    .aggregateId("order-456")
    .payload(payload)
    .build();

EventManager.getInstance().publish(event);
```

### Event with Context

사용자 및 테넌트 컨텍스트를 포함합니다:

```java
GenericEvent event = GenericEvent.builder()
    .eventType("DocumentUploaded")
    .aggregateId("doc-001")
    .aggregateType("Document")
    .userId("user-123")           // 이벤트 발생 사용자
    .tenantId("tenant-abc")       // 멀티테넌트 환경
    .payload(documentInfo)
    .addMetadata("source", "web-upload")
    .addMetadata("fileSize", 1024000)
    .build();

EventManager.getInstance().publish(event);
```

---

## Event Patterns

### Domain Event Pattern

도메인 서비스에서 이벤트를 발행하는 패턴입니다:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 1. 비즈니스 로직 수행
        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .totalAmount(calculateTotal(request.getItems()))
            .status(OrderStatus.CREATED)
            .build();

        Order savedOrder = orderRepository.save(order);

        // 2. 도메인 이벤트 발행
        publishEvent("OrderCreated", savedOrder);

        return savedOrder;
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        order.cancel(reason);
        Order savedOrder = orderRepository.save(order);

        publishEvent("OrderCancelled", savedOrder, Map.of("reason", reason));

        return savedOrder;
    }

    private void publishEvent(String eventType, Order order) {
        publishEvent(eventType, order, Map.of());
    }

    private void publishEvent(String eventType, Order order, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("customerId", order.getCustomerId());
        payload.put("status", order.getStatus());
        payload.put("totalAmount", order.getTotalAmount());
        payload.putAll(extra);

        GenericEvent event = GenericEvent.builder()
            .eventType(eventType)
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .payload(payload)
            .build();

        EventManager.getInstance().publish(event);
    }
}
```

### Event Factory Pattern

이벤트 생성을 팩토리로 분리합니다:

```java
public class OrderEventFactory {

    public static GenericEvent orderCreated(Order order) {
        return GenericEvent.builder()
            .eventType("OrderCreated")
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .payload(toPayload(order))
            .build();
    }

    public static GenericEvent orderShipped(Order order, String trackingNumber) {
        return GenericEvent.builder()
            .eventType("OrderShipped")
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .payload(Map.of(
                "orderId", order.getId(),
                "trackingNumber", trackingNumber,
                "shippedAt", Instant.now()
            ))
            .build();
    }

    public static GenericEvent orderCancelled(Order order, String reason) {
        return GenericEvent.builder()
            .eventType("OrderCancelled")
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .payload(Map.of(
                "orderId", order.getId(),
                "reason", reason,
                "cancelledAt", Instant.now()
            ))
            .build();
    }

    private static Map<String, Object> toPayload(Order order) {
        return Map.of(
            "orderId", order.getId(),
            "customerId", order.getCustomerId(),
            "totalAmount", order.getTotalAmount(),
            "status", order.getStatus()
        );
    }
}

// 사용
EventManager.getInstance().publish(OrderEventFactory.orderCreated(order));
EventManager.getInstance().publish(OrderEventFactory.orderShipped(order, "TRACK-123"));
```

### Transactional Outbox Pattern

트랜잭션과 이벤트 발행의 일관성을 보장합니다:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher localPublisher;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // 1. 비즈니스 로직
        Order order = createOrderEntity(request);
        Order savedOrder = orderRepository.save(order);

        // 2. Outbox에 이벤트 저장 (같은 트랜잭션)
        OutboxEvent outbox = OutboxEvent.builder()
            .eventType("OrderCreated")
            .aggregateId(savedOrder.getId().toString())
            .payload(toJson(savedOrder))
            .build();
        outboxRepository.save(outbox);

        // 3. 로컬 이벤트 발행 (트랜잭션 커밋 후 처리)
        localPublisher.publishEvent(new OrderCreatedLocalEvent(savedOrder));

        return savedOrder;
    }
}

// Outbox 이벤트 발행 스케줄러
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxRepository outboxRepository;

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findByPublishedFalse();

        for (OutboxEvent outbox : pending) {
            try {
                GenericEvent event = GenericEvent.builder()
                    .eventType(outbox.getEventType())
                    .aggregateId(outbox.getAggregateId())
                    .payload(outbox.getPayload())
                    .build();

                EventManager.getInstance().publish(event);
                outbox.markAsPublished();
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", outbox.getId(), e);
            }
        }
    }
}
```

---

## Event Listener

### Spring Event Listener

로컬 이벤트를 수신합니다 (mode=local):

```java
@Component
@Slf4j
public class OrderEventListener {

    @EventListener
    public void handleOrderEvent(GenericEvent event) {
        switch (event.getEventType()) {
            case "OrderCreated" -> handleOrderCreated(event);
            case "OrderCancelled" -> handleOrderCancelled(event);
            default -> log.debug("Unhandled event: {}", event.getEventType());
        }
    }

    private void handleOrderCreated(GenericEvent event) {
        log.info("Order created: {}", event.getAggregateId());

        // 페이로드 접근
        if (event.getPayload() instanceof Map<?, ?> payload) {
            Object customerId = payload.get("customerId");
            Object totalAmount = payload.get("totalAmount");
            // 처리 로직...
        }
    }

    private void handleOrderCancelled(GenericEvent event) {
        log.info("Order cancelled: {}", event.getAggregateId());
    }
}
```

### Async Event Listener

비동기로 이벤트를 처리합니다:

```java
@Component
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleOrderCreated(GenericEvent event) {
        if (!"OrderCreated".equals(event.getEventType())) {
            return;
        }

        log.info("Sending notification for order: {}", event.getAggregateId());
        notificationService.sendOrderConfirmation(event.getAggregateId());
    }
}
```

### Redis Stream Consumer

Redis Streams 이벤트를 수신합니다:

```java
@Component
@Slf4j
public class RedisEventConsumer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 100)
    public void consumeEvents() {
        String streamKey = "simplix-events:OrderCreated";
        String consumerGroup = "order-processor";
        String consumerName = "consumer-1";

        try {
            List<MapRecord<String, Object, Object>> messages = redisTemplate
                .opsForStream()
                .read(
                    Consumer.from(consumerGroup, consumerName),
                    StreamReadOptions.empty().count(10),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );

            for (MapRecord<String, Object, Object> message : messages) {
                processMessage(message);
                redisTemplate.opsForStream().acknowledge(streamKey, consumerGroup, message.getId());
            }
        } catch (Exception e) {
            log.error("Failed to consume events", e);
        }
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        Map<Object, Object> data = message.getValue();
        String eventType = (String) data.get("eventType");
        String payload = (String) data.get("payload");

        log.info("Processing event: {} - {}", eventType, payload);
    }
}
```

### Kafka Consumer

Kafka 이벤트를 수신합니다:

```java
@Component
@Slf4j
public class KafkaEventConsumer {

    @KafkaListener(topics = "simplix-events-OrderCreated", groupId = "order-processor")
    public void handleOrderCreated(ConsumerRecord<String, String> record) {
        log.info("Received event: key={}, value={}", record.key(), record.value());

        // 헤더에서 메타데이터 추출
        Headers headers = record.headers();
        String eventType = new String(headers.lastHeader("eventType").value());
        String aggregateId = new String(headers.lastHeader("aggregateId").value());

        // 페이로드 처리
        String payload = record.value();
        processOrderCreated(aggregateId, payload);
    }

    private void processOrderCreated(String orderId, String payload) {
        log.info("Processing order: {}", orderId);
    }
}
```

### RabbitMQ Consumer

RabbitMQ 이벤트를 수신합니다:

```java
@Component
@Slf4j
public class RabbitEventConsumer {

    @RabbitListener(queues = "simplix.events.queue")
    public void handleEvent(Message message) {
        MessageProperties props = message.getMessageProperties();
        String eventType = props.getHeader("eventType");
        String aggregateId = props.getHeader("aggregateId");

        String body = new String(message.getBody());
        log.info("Received event: type={}, aggregateId={}", eventType, aggregateId);

        switch (eventType) {
            case "OrderCreated" -> processOrderCreated(aggregateId, body);
            case "OrderCancelled" -> processOrderCancelled(aggregateId, body);
        }
    }

    private void processOrderCreated(String orderId, String payload) {
        log.info("Order created: {}", orderId);
    }

    private void processOrderCancelled(String orderId, String payload) {
        log.info("Order cancelled: {}", orderId);
    }
}
```

---

## Advanced Options

### Critical Events

실패 시 예외를 발생시키는 중요 이벤트:

```java
@Autowired
private UnifiedEventPublisher eventPublisher;

public void processPayment(Payment payment) {
    GenericEvent event = GenericEvent.builder()
        .eventType("PaymentProcessed")
        .aggregateId(payment.getId().toString())
        .payload(payment)
        .build();

    // 실패 시 EventPublishException 발생
    eventPublisher.publish(event, PublishOptions.critical());
}
```

### Fire and Forget

재시도 없이 발행하는 일회성 이벤트:

```java
public void logUserActivity(String userId, String action) {
    GenericEvent event = GenericEvent.builder()
        .eventType("UserActivity")
        .aggregateId(userId)
        .payload(Map.of("action", action, "timestamp", Instant.now()))
        .build();

    // 실패해도 재시도하지 않음
    eventPublisher.publish(event, PublishOptions.fireAndForget());
}
```

### Custom Options

세부 옵션을 지정합니다:

```java
PublishOptions options = PublishOptions.builder()
    .critical(true)              // 중요 이벤트
    .persistent(true)            // 영속화
    .async(false)                // 동기 발행
    .maxRetries(5)               // 최대 5회 재시도
    .retryDelay(2000)            // 재시도 간격 2초
    .routingKey("payments")      // 라우팅 키
    .partitionKey(customerId)    // Kafka 파티션 키
    .ttl(Duration.ofHours(1))    // 1시간 TTL
    .addHeader("priority", "high")
    .addHeader("source", "payment-service")
    .build();

eventPublisher.publish(event, options);
```

---

## Service Integration

### Spring Bean Injection

UnifiedEventPublisher를 직접 주입받아 사용합니다:

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UnifiedEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment processPayment(PaymentRequest request) {
        Payment payment = createPayment(request);
        Payment saved = paymentRepository.save(payment);

        GenericEvent event = GenericEvent.builder()
            .eventType("PaymentProcessed")
            .aggregateId(saved.getId().toString())
            .aggregateType("Payment")
            .payload(saved)
            .build();

        // 옵션과 함께 발행
        eventPublisher.publish(event, PublishOptions.critical());

        return saved;
    }
}
```

### Event Publisher Wrapper

애플리케이션 전용 이벤트 발행 서비스:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventPublisher {

    private final UnifiedEventPublisher eventPublisher;

    public void publish(String eventType, String aggregateType, Object aggregate, Object payload) {
        String aggregateId = extractId(aggregate);

        GenericEvent event = GenericEvent.builder()
            .eventType(eventType)
            .aggregateId(aggregateId)
            .aggregateType(aggregateType)
            .userId(getCurrentUserId())
            .tenantId(getCurrentTenantId())
            .payload(payload)
            .build();

        eventPublisher.publish(event);
        log.debug("Published event: {} for {}", eventType, aggregateId);
    }

    public void publishCritical(String eventType, String aggregateType, Object aggregate, Object payload) {
        String aggregateId = extractId(aggregate);

        GenericEvent event = GenericEvent.builder()
            .eventType(eventType)
            .aggregateId(aggregateId)
            .aggregateType(aggregateType)
            .userId(getCurrentUserId())
            .tenantId(getCurrentTenantId())
            .payload(payload)
            .build();

        eventPublisher.publish(event, PublishOptions.critical());
    }

    private String extractId(Object aggregate) {
        // Reflection 또는 인터페이스로 ID 추출
        if (aggregate instanceof Identifiable<?> identifiable) {
            return identifiable.getId().toString();
        }
        throw new IllegalArgumentException("Cannot extract ID from: " + aggregate.getClass());
    }

    private String getCurrentUserId() {
        // SecurityContext에서 사용자 ID 추출
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
    }

    private String getCurrentTenantId() {
        // TenantContext에서 테넌트 ID 추출
        return TenantContext.getCurrentTenant();
    }
}
```

---

## Testing

### Unit Test with Mock

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldPublishEvent() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest("cust-1", List.of("item-1"));
        Order savedOrder = Order.builder().id(1L).customerId("cust-1").build();

        when(orderRepository.save(any())).thenReturn(savedOrder);

        // Mock EventManager (static method)
        try (MockedStatic<EventManager> mockedEventManager = mockStatic(EventManager.class)) {
            EventManager mockManager = mock(EventManager.class);
            mockedEventManager.when(EventManager::getInstance).thenReturn(mockManager);

            // When
            Order result = orderService.createOrder(request);

            // Then
            verify(mockManager).publish(argThat(event ->
                "OrderCreated".equals(event.getEventType()) &&
                "1".equals(event.getAggregateId())
            ));
        }
    }
}
```

### Integration Test with Local Strategy

```java
@SpringBootTest
@TestPropertySource(properties = {
    "simplix.events.mode=local"
})
class EventPublishingIntegrationTest {

    @Autowired
    private UnifiedEventPublisher eventPublisher;

    @Autowired
    private ApplicationEventPublisher springEventPublisher;

    private final List<GenericEvent> receivedEvents = new ArrayList<>();

    @EventListener
    public void captureEvent(GenericEvent event) {
        receivedEvents.add(event);
    }

    @BeforeEach
    void setUp() {
        receivedEvents.clear();
    }

    @Test
    void shouldPublishAndReceiveEvent() {
        // Given
        GenericEvent event = GenericEvent.builder()
            .eventType("TestEvent")
            .aggregateId("test-123")
            .payload(Map.of("key", "value"))
            .build();

        // When
        eventPublisher.publish(event);

        // Then
        await().atMost(1, TimeUnit.SECONDS)
            .until(() -> receivedEvents.size() == 1);

        GenericEvent received = receivedEvents.get(0);
        assertThat(received.getEventType()).isEqualTo("TestEvent");
        assertThat(received.getAggregateId()).isEqualTo("test-123");
    }
}
```

### Integration Test with Testcontainers (Redis)

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldPublishToRedisStream() {
        // Given
        GenericEvent event = GenericEvent.builder()
            .eventType("OrderCreated")
            .aggregateId("order-123")
            .payload(Map.of("amount", 1000))
            .build();

        // When
        eventPublisher.publish(event);

        // Then
        String streamKey = "simplix-events:OrderCreated";
        Long size = redisTemplate.opsForStream().size(streamKey);
        assertThat(size).isGreaterThan(0);
    }
}
```

---

## Best Practices

### 1. Event Naming Convention

```
{Domain}{Action}  (PascalCase)

OrderCreated
OrderCancelled
PaymentProcessed
UserRegistered
DocumentUploaded
```

### 2. Payload Design

```java
// Good: 필요한 데이터만 포함
Map.of(
    "orderId", order.getId(),
    "status", order.getStatus(),
    "totalAmount", order.getTotalAmount()
)

// Bad: 전체 엔티티 포함 (민감 정보 노출, 크기 증가)
order  // Entity 전체
```

### 3. Idempotent Event Handling

```java
@Component
public class OrderEventHandler {

    private final ProcessedEventRepository processedEventRepo;

    @EventListener
    @Transactional
    public void handle(GenericEvent event) {
        // 중복 처리 방지
        if (processedEventRepo.existsByEventId(event.getEventId())) {
            log.debug("Event already processed: {}", event.getEventId());
            return;
        }

        // 이벤트 처리
        processEvent(event);

        // 처리 완료 기록
        processedEventRepo.save(new ProcessedEvent(event.getEventId()));
    }
}
```

### 4. Error Handling

```java
@EventListener
public void handleEvent(GenericEvent event) {
    try {
        processEvent(event);
    } catch (TransientException e) {
        // 일시적 오류: 재시도 가능
        log.warn("Transient error, will retry: {}", e.getMessage());
        throw e;
    } catch (PermanentException e) {
        // 영구적 오류: DLQ로 이동
        log.error("Permanent error, sending to DLQ: {}", e.getMessage());
        sendToDeadLetterQueue(event, e);
    }
}
```

### 5. Monitoring

```java
@Component
@RequiredArgsConstructor
public class EventMetricsListener {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void recordEventMetrics(GenericEvent event) {
        meterRegistry.counter(
            "app.events.received",
            "eventType", event.getEventType(),
            "aggregateType", getAggregateType(event)
        ).increment();
    }
}
```

---

## Troubleshooting

### Event Not Published

**증상**: 이벤트가 발행되지 않음

**확인사항**:
1. `simplix.events.mode` 설정 확인
2. EventManager 사용 가능 여부: `EventManager.getInstance().isAvailable()`
3. 로그 레벨 DEBUG로 설정

```yaml
logging:
  level:
    dev.simplecore.simplix.event: DEBUG
```

### Redis Connection Failed

**증상**: `RedisConnectionException`

**해결**:
```yaml
spring:
  data:
    redis:
      timeout: 5000ms
      connect-timeout: 10s
      lettuce:
        pool:
          max-active: 16
```

### Kafka Timeout

**증상**: `TimeoutException: Failed to send after 30 seconds`

**해결**:
- Kafka 브로커 상태 확인
- `acks` 설정을 "1"로 변경 (응답 시간 단축)
- 네트워크 지연 확인

### Event Loss

**증상**: 일부 이벤트가 유실됨

**해결**:
1. `PublishOptions.critical()` 사용
2. Transactional Outbox 패턴 적용
3. DLQ 설정 확인 (RabbitMQ)
