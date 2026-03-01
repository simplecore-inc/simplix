# SSE 단독 모드 튜토리얼

단일 서버 환경에서 SSE (Server-Sent Events)를 사용한 실시간 스트리밍 구현 가이드입니다.

## 목차

1. [사전 요구사항](#사전-요구사항)
2. [서버 설정](#서버-설정)
3. [SimpliXStreamDataCollector 구현](#datacollector-구현)
4. [권한 관리 (선택)](#권한-관리-선택)
5. [클라이언트 구현](#클라이언트-구현)
6. [테스트 및 검증](#테스트-및-검증)

---

## 사전 요구사항

- Java 17+
- Spring Boot 3.5+
- Gradle 또는 Maven

## 서버 설정

### 1. 의존성 추가

**build.gradle:**

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-stream'

    // Spring Boot 기본 의존성
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-security'
}
```

### 2. 애플리케이션 설정

**application.yml:**

```yaml
simplix:
  stream:
    enabled: true
    mode: local  # 단독 모드

    session:
      timeout: 5m              # 세션 타임아웃 (비활성 세션 정리)
      heartbeat-interval: 30s  # 하트비트 전송 주기
      grace-period: 30s        # 연결 끊김 후 재연결 대기 시간
      max-per-user: 5          # 사용자당 최대 세션 수

    scheduler:
      thread-pool-size: 10     # 스케줄러 스레드 풀 크기
      default-interval: 1000ms # 기본 데이터 푸시 주기
      min-interval: 100ms      # 최소 허용 주기
      max-interval: 60000ms    # 최대 허용 주기
      max-consecutive-errors: 5 # 연속 오류 허용 횟수

    subscription:
      max-per-session: 20      # 세션당 최대 구독 수
      partial-success: true    # 일부 구독 실패 시 성공 처리

# Spring Security 기본 설정 (테스트용)
spring:
  security:
    user:
      name: user
      password: password
```

### 3. Security 설정 (선택)

인증 없이 테스트하려면 Security 설정을 추가합니다:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/stream/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

---

## SimpliXStreamDataCollector 구현

SimpliXStreamDataCollector는 실시간으로 수집할 데이터를 정의합니다.

> ⚠ **보안 참고사항**: 폴링 주기(interval)는 보안상의 이유로 서버의 SimpliXStreamDataCollector에서만 설정할 수 있습니다. 클라이언트가 폴링 주기를 지정할 수 있으면 악의적인 클라이언트가 매우 짧은 간격으로 요청해서 서버에 DoS 공격을 할 수 있습니다. `getDefaultIntervalMs()`와 `getMinIntervalMs()` 메서드를 통해 각 리소스별로 적절한 주기를 설정하세요.

### 예제 1: 주식 가격 수집기

```java
@Component
public class StockPriceCollector implements SimpliXStreamDataCollector {

    private final StockService stockService;

    public StockPriceCollector(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String getResource() {
        return "stock-price";  // 구독 시 사용할 리소스 이름
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String symbol = (String) params.get("symbol");

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }

        StockPrice price = stockService.getCurrentPrice(symbol);

        return Map.of(
            "symbol", symbol,
            "price", price.getValue(),
            "change", price.getChange(),
            "changePercent", price.getChangePercent(),
            "timestamp", Instant.now().toEpochMilli()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 1000L;  // 1초마다 수집
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        String symbol = (String) params.get("symbol");
        return symbol != null && !symbol.isBlank();
    }
}
```

### 예제 2: 시스템 메트릭 수집기

```java
@Component
public class SystemMetricsCollector implements SimpliXStreamDataCollector {

    private final Runtime runtime = Runtime.getRuntime();

    @Override
    public String getResource() {
        return "system-metrics";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return Map.of(
            "usedMemory", usedMemory / (1024 * 1024),  // MB
            "freeMemory", freeMemory / (1024 * 1024),
            "totalMemory", totalMemory / (1024 * 1024),
            "processors", runtime.availableProcessors(),
            "timestamp", Instant.now().toEpochMilli()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 5000L;  // 5초마다 수집
    }
}
```

### 예제 3: 알림 수집기 (외부 이벤트 기반)

```java
@Component
public class NotificationCollector implements SimpliXStreamDataCollector {

    private final NotificationService notificationService;

    public NotificationCollector(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String getResource() {
        return "notifications";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String userId = (String) params.get("userId");

        List<Notification> notifications =
            notificationService.getUnreadNotifications(userId);

        if (notifications.isEmpty()) {
            return null;  // null 반환 시 클라이언트에 전송하지 않음
        }

        return Map.of(
            "count", notifications.size(),
            "notifications", notifications.stream()
                .map(n -> Map.of(
                    "id", n.getId(),
                    "title", n.getTitle(),
                    "message", n.getMessage(),
                    "type", n.getType(),
                    "createdAt", n.getCreatedAt().toEpochMilli()
                ))
                .toList()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 3000L;  // 3초마다 확인
    }
}
```

---

## 이벤트 기반 스트리밍 (선택)

폴링 방식(SimpliXStreamDataCollector) 대신 이벤트 발생 시 즉시 데이터를 푸시하려면 `SimpliXStreamEventSource`를 사용합니다.

### 폴링 vs 이벤트 비교

| 방식 | 인터페이스 | 특징 | 적합한 케이스 |
|------|-----------|------|--------------|
| 폴링 | `SimpliXStreamDataCollector` | 주기적 데이터 수집 | 시스템 메트릭, 대시보드 |
| 이벤트 | `SimpliXStreamEventSource` | 즉시 푸시 (ms 단위) | 주문 상태, 채팅, 알림 |

### SimpliXStreamEventSource 예제

```java
@Component
public class OrderStatusEventSource implements SimpliXStreamEventSource {

    @Override
    public String getResource() {
        return "order-status";
    }

    @Override
    public String getEventType() {
        return "OrderStatusChanged";  // simplix-event 이벤트 타입
    }

    @Override
    public Map<String, Object> extractParams(Object payload) {
        OrderStatusChangedEvent event = (OrderStatusChangedEvent) payload;
        return Map.of("orderId", event.getOrderId());
    }

    @Override
    public Object extractData(Object payload) {
        OrderStatusChangedEvent event = (OrderStatusChangedEvent) payload;
        return Map.of(
            "orderId", event.getOrderId(),
            "status", event.getNewStatus(),
            "updatedAt", event.getUpdatedAt().toEpochMilli()
        );
    }
}
```

### 설정

```yaml
simplix:
  stream:
    event-source:
      enabled: true  # 이벤트 기반 스트리밍 활성화
```

> 자세한 내용은 [이벤트 기반 스트리밍 튜토리얼](ko/stream/tutorial-event-source.md)을 참조하세요.

---

## 권한 관리 (선택)

특정 리소스에 대한 접근 권한을 제어하려면 ResourceAuthorizer를 구현합니다.

### 기본 권한 검사기

```java
@Component
public class StockPriceAuthorizer implements ResourceAuthorizer {

    private final SubscriptionService subscriptionService;

    public StockPriceAuthorizer(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public String getResource() {
        return "stock-price";  // SimpliXStreamDataCollector의 리소스 이름과 일치
    }

    @Override
    public String getRequiredPermission() {
        return "STOCK_VIEW";  // Spring Security 권한과 연동
    }

    @Override
    public boolean authorize(String userId, Map<String, Object> params) {
        String symbol = (String) params.get("symbol");

        // 사용자가 해당 종목을 구독했는지 확인
        return subscriptionService.hasSubscription(userId, symbol);
    }

    @Override
    public String getDenialReason(String userId, Map<String, Object> params) {
        String symbol = (String) params.get("symbol");
        return "User " + userId + " is not subscribed to " + symbol;
    }
}
```

### 권한 강제 설정

```yaml
simplix:
  stream:
    security:
      enforce-authorization: true   # true: authorizer 없으면 거부
      require-authentication: true  # true: 인증 필수
```

---

## 클라이언트 구현

### JavaScript (Vanilla)

```javascript
class StreamClient {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl;
        this.eventSource = null;
        this.sessionId = null;
        this.listeners = new Map();
    }

    // SSE 연결 수립
    connect() {
        return new Promise((resolve, reject) => {
            this.eventSource = new EventSource(`${this.baseUrl}/api/stream/connect`);

            this.eventSource.addEventListener('connected', (event) => {
                const data = JSON.parse(event.data);
                this.sessionId = data.sessionId;
                console.log('Connected:', this.sessionId);
                resolve(this.sessionId);
            });

            this.eventSource.addEventListener('data', (event) => {
                const message = JSON.parse(event.data);
                this.handleData(message);
            });

            this.eventSource.addEventListener('heartbeat', () => {
                console.debug('Heartbeat received');
            });

            this.eventSource.addEventListener('error', (event) => {
                const data = event.data ? JSON.parse(event.data) : null;
                console.error('Stream error:', data);
                this.handleError(data);
            });

            this.eventSource.addEventListener('subscription_removed', (event) => {
                const data = JSON.parse(event.data);
                console.log('Subscription removed:', data.subscriptionKey);
            });

            this.eventSource.onerror = (error) => {
                if (this.eventSource.readyState === EventSource.CLOSED) {
                    console.log('Connection closed');
                    reject(new Error('Connection closed'));
                }
            };
        });
    }

    // 구독 업데이트
    async updateSubscriptions(subscriptions) {
        if (!this.sessionId) {
            throw new Error('Not connected');
        }

        const response = await fetch(
            `${this.baseUrl}/api/stream/sessions/${this.sessionId}/subscriptions`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ subscriptions })
            }
        );

        if (!response.ok) {
            throw new Error(`Failed to update subscriptions: ${response.status}`);
        }

        return response.json();
    }

    // 현재 구독 조회
    async getSubscriptions() {
        if (!this.sessionId) {
            throw new Error('Not connected');
        }

        const response = await fetch(
            `${this.baseUrl}/api/stream/sessions/${this.sessionId}/subscriptions`
        );

        if (!response.ok) {
            throw new Error(`Failed to get subscriptions: ${response.status}`);
        }

        return response.json();
    }

    // 리소스별 리스너 등록
    on(resource, callback) {
        if (!this.listeners.has(resource)) {
            this.listeners.set(resource, []);
        }
        this.listeners.get(resource).push(callback);
    }

    // 리스너 제거
    off(resource, callback) {
        if (this.listeners.has(resource)) {
            const callbacks = this.listeners.get(resource);
            const index = callbacks.indexOf(callback);
            if (index > -1) {
                callbacks.splice(index, 1);
            }
        }
    }

    // 데이터 수신 처리
    handleData(message) {
        const { subscriptionKey, resource, data, timestamp } = message;

        if (this.listeners.has(resource)) {
            this.listeners.get(resource).forEach(callback => {
                callback(data, { subscriptionKey, resource, timestamp });
            });
        }
    }

    // 에러 처리
    handleError(error) {
        console.error('Stream error:', error);
    }

    // 연결 종료
    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }

        if (this.sessionId) {
            // 서버에 세션 종료 알림
            fetch(`${this.baseUrl}/api/stream/sessions/${this.sessionId}`, {
                method: 'DELETE'
            }).catch(() => {});
            this.sessionId = null;
        }
    }
}
```

### 사용 예시

```javascript
// 클라이언트 생성 및 연결
const client = new StreamClient();

async function init() {
    try {
        // 연결
        await client.connect();
        console.log('Connected to stream');

        // 데이터 리스너 등록
        client.on('stock-price', (data, meta) => {
            console.log(`Stock ${data.symbol}: $${data.price} (${data.changePercent}%)`);
            updateStockUI(data);
        });

        client.on('system-metrics', (data, meta) => {
            console.log(`Memory: ${data.usedMemory}MB / ${data.totalMemory}MB`);
            updateMetricsUI(data);
        });

        // 구독 등록 (폴링 주기는 서버의 SimpliXStreamDataCollector에서 결정됨)
        const result = await client.updateSubscriptions([
            {
                resource: 'stock-price',
                params: { symbol: 'AAPL' }
            },
            {
                resource: 'stock-price',
                params: { symbol: 'GOOG' }
            },
            {
                resource: 'system-metrics',
                params: {}
            }
        ]);

        console.log('Subscribed:', result);

    } catch (error) {
        console.error('Failed to connect:', error);
    }
}

// 페이지 이동 시 구독 변경
async function onPageChange(page) {
    if (page === 'dashboard') {
        await client.updateSubscriptions([
            { resource: 'system-metrics', params: {} }
        ]);
    } else if (page === 'stocks') {
        await client.updateSubscriptions([
            { resource: 'stock-price', params: { symbol: 'AAPL' } },
            { resource: 'stock-price', params: { symbol: 'GOOG' } }
        ]);
    }
}

// 페이지 종료 시 정리
window.addEventListener('beforeunload', () => {
    client.disconnect();
});

// 초기화
init();
```

---

## 테스트 및 검증

### 1. curl로 SSE 연결 테스트

```bash
# SSE 연결
curl -N -H "Accept: text/event-stream" http://localhost:8080/api/stream/connect
```

**예상 출력:**

```
event: connected
data: {"sessionId":"abc123-def456","timestamp":1704067200000,"type":"CONNECTED"}

event: heartbeat
data: {"timestamp":1704067230000,"type":"HEARTBEAT"}
```

### 2. 구독 등록 테스트

```bash
# 세션 ID를 환경변수에 저장 (위에서 받은 sessionId 사용)
SESSION_ID="abc123-def456"

# 구독 등록 (폴링 주기는 서버에서 결정)
curl -X PUT "http://localhost:8080/api/stream/sessions/${SESSION_ID}/subscriptions" \
  -H "Content-Type: application/json" \
  -d '{
    "subscriptions": [
      {
        "resource": "stock-price",
        "params": {"symbol": "AAPL"}
      }
    ]
  }'
```

**예상 응답:**

```json
{
  "success": true,
  "subscribed": [
    {
      "resource": "stock-price",
      "params": {"symbol": "AAPL"},
      "subscriptionKey": "stock-price:a1b2c3",
      "intervalMs": 1000
    }
  ],
  "failed": [],
  "totalCount": 1
}
```

### 3. 현재 구독 조회

```bash
curl "http://localhost:8080/api/stream/sessions/${SESSION_ID}/subscriptions"
```

### 4. 데이터 수신 확인

SSE 연결 터미널에서 데이터가 수신되는지 확인:

```
event: data
data: {"subscriptionKey":"stock-price:a1b2c3","resource":"stock-price","data":{"symbol":"AAPL","price":178.50,"change":2.30,"changePercent":1.31},"timestamp":1704067201000,"type":"DATA"}
```

### 5. 세션 종료

```bash
curl -X DELETE "http://localhost:8080/api/stream/sessions/${SESSION_ID}"
```

---

## 다음 단계

- [SSE 분산 모드 (Redis) 튜토리얼](ko/stream/tutorial-sse-distributed-redis.md) - 다중 인스턴스 환경 구성
- [Admin API 가이드](ko/stream/admin-api-guide.md) - 세션/스케줄러 관리
- [모니터링 가이드](ko/stream/monitoring-guide.md) - 메트릭 및 헬스 체크
