# SSE 분산 모드 (DB Admin) 튜토리얼

Redis 없이 데이터베이스 기반 Admin 명령을 사용한 분산 환경 구성 가이드입니다.

## 목차

1. [아키텍처 개요](#아키텍처-개요)
2. [사전 요구사항](#사전-요구사항)
3. [서버 설정](#서버-설정)
4. [DB Admin 명령 시스템](#db-admin-명령-시스템)
5. [SimpliXStreamDataCollector 구현](#datacollector-구현)
6. [클라이언트 구현](#클라이언트-구현)
7. [운영 가이드](#운영-가이드)

---

## 아키텍처 개요

DB Admin 모드는 Redis 없이 다중 인스턴스 환경에서 관리 작업을 수행합니다.

```
+-------------+         +------------------+
|   Client A  | <-----> |   Instance 1     |
+-------------+         |  (로컬 세션 관리) |
                        +--------+---------+
                                 |
+-------------+         +--------+---------+
|   Client B  | <-----> |   Instance 2     |
+-------------+         |  (로컬 세션 관리) |
                        +--------+---------+
                                 |
                        +--------v---------+
                        |    Database      |
                        |  +------------+  |
                        |  | Admin      |  |
                        |  | Commands   |  |
                        |  +------------+  |
                        +------------------+
```

### Redis 분산 모드와의 차이점

| 특성 | Redis 분산 모드 | DB Admin 모드 |
|------|----------------|---------------|
| 세션 저장소 | Redis (공유) | 메모리 (인스턴스별) |
| 메시지 브로드캐스트 | Redis Pub/Sub | 직접 전송 (로컬 세션만) |
| 스케줄러 | 리더 선출 후 단일 실행 | 각 인스턴스 독립 실행 |
| Admin 명령 | 즉시 실행 | DB 폴링 후 실행 |
| 적합 환경 | 대규모 운영 | Redis 없는 분산 환경 |

### 사용 시나리오

- Redis 인프라가 없는 환경
- 각 인스턴스가 독립적으로 동작해야 하는 경우
- Sticky session이 보장되는 로드 밸런서 환경
- 관리 작업 지연이 허용되는 환경 (2-5초)

---

## 사전 요구사항

- Java 17+
- Spring Boot 3.5+
- JPA 지원 데이터베이스 (MySQL, PostgreSQL 등)
- Gradle 또는 Maven

---

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

    // JPA 의존성 (DB Admin 필수)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'  // 또는 PostgreSQL
}
```

### 2. 애플리케이션 설정

**application.yml:**

```yaml
simplix:
  stream:
    enabled: true
    mode: local  # DB Admin은 local 모드와 함께 사용

    session:
      timeout: 5m
      heartbeat-interval: 30s
      grace-period: 30s
      max-per-user: 5

    scheduler:
      thread-pool-size: 10
      default-interval: 1000ms
      min-interval: 100ms
      max-interval: 60000ms

    subscription:
      max-per-session: 20
      partial-success: true

    # DB Admin 설정
    admin:
      enabled: true                  # DB Admin 활성화
      polling-interval: 2s           # 명령 폴링 주기
      command-timeout: 5m            # 명령 타임아웃
      retention-period: 7d           # 완료된 명령 보관 기간
      cleanup-cron: "0 0 3 * * ?"    # 정리 작업 스케줄

# 데이터베이스 설정
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/simplix?useSSL=false&serverTimezone=UTC
    username: simplix
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update  # 운영 환경에서는 validate 권장
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### 3. 인스턴스 ID 설정

각 인스턴스에 고유 ID를 할당합니다:

```yaml
simplix:
  stream:
    instance-id: ${HOSTNAME:instance-1}
```

또는 환경변수로:

```bash
export HOSTNAME=instance-1
java -jar app.jar
```

### 4. 데이터베이스 스키마

JPA가 자동으로 생성하거나, 직접 생성할 수 있습니다:

```sql
CREATE TABLE stream_admin_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    target_instance_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    executed_by VARCHAR(64),
    error_message VARCHAR(500),

    INDEX idx_stream_admin_cmd_status (status, created_at),
    INDEX idx_stream_admin_cmd_target (target_id)
);
```

### 5. Security 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/stream/**").authenticated()
                .requestMatchers("/api/admin/stream/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

---

## DB Admin 명령 시스템

### 명령 유형

| 명령 | 설명 | 대상 |
|------|------|------|
| `TERMINATE_SESSION` | 세션 강제 종료 | 세션 ID |
| `STOP_SCHEDULER` | 스케줄러 정지 | 구독 키 |
| `TRIGGER_SCHEDULER` | 즉시 데이터 전송 | 구독 키 |

### 명령 상태

```
PENDING → EXECUTED  (정상 실행)
PENDING → FAILED    (실행 실패)
PENDING → NOT_FOUND (대상 없음)
PENDING → EXPIRED   (타임아웃)
```

### 명령 흐름

```
1. Admin API 호출
   POST /api/admin/stream/sessions/{sessionId}/terminate
   |
   v
2. AdminCommand 레코드 생성 (status=PENDING)
   |
   v
3. 각 인스턴스가 폴링 (2초 간격)
   |
   +---> Instance 1: "내 세션 아님" → 무시
   +---> Instance 2: "내 세션임!" → 실행
   |
   v
4. 명령 실행 후 상태 업데이트 (status=EXECUTED)
```

### API 응답 형식

```json
{
  "commandId": 123,
  "status": "PENDING",
  "message": "Command queued for execution",
  "estimatedExecutionTime": "2-5 seconds"
}
```

### 명령 상태 조회

```bash
curl -X GET "http://localhost:8080/api/admin/stream/commands/123" \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

응답:
```json
{
  "id": 123,
  "commandType": "TERMINATE_SESSION",
  "targetId": "session-abc123",
  "status": "EXECUTED",
  "createdAt": "2024-01-15T10:30:00Z",
  "executedAt": "2024-01-15T10:30:02Z",
  "executedBy": "instance-2"
}
```

---

## SimpliXStreamDataCollector 구현

DB Admin 모드에서도 SimpliXStreamDataCollector 구현은 동일합니다.

### 예제: 주식 가격 수집기

```java
@Component
public class StockPriceCollector implements SimpliXStreamDataCollector {

    private final StockService stockService;

    public StockPriceCollector(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String getResource() {
        return "stock-price";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String symbol = (String) params.get("symbol");
        StockPrice price = stockService.getCurrentPrice(symbol);

        return Map.of(
            "symbol", symbol,
            "price", price.getValue(),
            "change", price.getChange(),
            "timestamp", Instant.now().toEpochMilli()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 1000L;
    }
}
```

### 스케줄러 동작 특성

DB Admin 모드에서 스케줄러는 각 인스턴스에서 **독립적으로** 실행됩니다:

```
Instance 1: 구독자 A, B 관리
  └── stock-price:AAPL 스케줄러 실행 중

Instance 2: 구독자 C, D 관리
  └── stock-price:AAPL 스케줄러 실행 중 (별도)

→ 동일 리소스에 대해 각 인스턴스가 독립 스케줄러 운영
→ 외부 API 호출이 2배가 될 수 있음 (주의)
```

### 외부 API 호출 최적화

중복 호출을 줄이려면 캐싱을 사용하세요:

```java
@Component
public class CachedStockPriceCollector implements SimpliXStreamDataCollector {

    private final StockService stockService;
    private final CacheManager cacheManager;

    @Override
    public Object collect(Map<String, Object> params) {
        String symbol = (String) params.get("symbol");

        // 캐시에서 먼저 조회 (TTL: 500ms)
        Cache cache = cacheManager.getCache("stock-prices");
        StockPrice cached = cache.get(symbol, StockPrice.class);

        if (cached != null) {
            return formatPrice(cached);
        }

        // 캐시 미스 시 외부 API 호출
        StockPrice price = stockService.getCurrentPrice(symbol);
        cache.put(symbol, price);

        return formatPrice(price);
    }
}
```

---

## 이벤트 기반 스트리밍 (선택)

DB Admin 모드에서도 이벤트 기반 스트리밍을 사용할 수 있습니다. 단, 세션이 인스턴스별로 분리되어 있으므로 이벤트는 로컬 인스턴스의 구독자에게만 전달됩니다.

### 설정

```yaml
simplix:
  stream:
    mode: local  # DB Admin 모드는 local
    event-source:
      enabled: true

    admin:
      enabled: true
      polling-interval: 2s

  events:
    mode: local  # 로컬 이벤트 (인스턴스 내)
```

### SimpliXStreamEventSource 예제

```java
@Component
public class NotificationEventSource implements SimpliXStreamEventSource {

    @Override
    public String getResource() {
        return "notifications";
    }

    @Override
    public String getEventType() {
        return "NotificationCreated";
    }

    @Override
    public Map<String, Object> extractParams(Object payload) {
        NotificationEvent event = (NotificationEvent) payload;
        return Map.of("userId", event.getUserId());
    }

    @Override
    public Object extractData(Object payload) {
        NotificationEvent event = (NotificationEvent) payload;
        return Map.of(
            "id", event.getId(),
            "title", event.getTitle(),
            "message", event.getMessage(),
            "createdAt", event.getCreatedAt().toEpochMilli()
        );
    }
}
```

> ⚠ **참고**: DB Admin 모드에서 이벤트 기반 스트리밍을 사용하면, 이벤트를 발행한 인스턴스에 연결된 구독자만 데이터를 받습니다. 모든 인스턴스에 이벤트를 전파하려면 Redis 분산 모드를 사용하세요.

> 자세한 내용은 [이벤트 기반 스트리밍 튜토리얼](ko/stream/tutorial-event-source.md)을 참조하세요.

---

## 클라이언트 구현

### Sticky Session 설정

DB Admin 모드에서는 **Sticky Session이 필수**입니다:

**Nginx:**

```nginx
upstream stream_backend {
    ip_hash;  # IP 기반 sticky session

    server instance1:8080;
    server instance2:8080;
}

server {
    location /api/stream/ {
        proxy_pass http://stream_backend;

        # SSE 설정
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;

        proxy_read_timeout 3600s;
    }
}
```

**AWS ALB:**

```yaml
# Target Group Stickiness 설정
TargetGroupAttributes:
  - Key: stickiness.enabled
    Value: "true"
  - Key: stickiness.type
    Value: "lb_cookie"
  - Key: stickiness.lb_cookie.duration_seconds
    Value: "3600"
```

### JavaScript 클라이언트

단독 모드와 동일합니다:

```javascript
const client = new StreamClient('https://api.example.com');

await client.connect();

client.on('stock-price', (data, meta) => {
    console.log(`${data.symbol}: $${data.price}`);
});

await client.updateSubscriptions([
    {
        resource: 'stock-price',
        params: { symbol: 'AAPL' }
    }
]);
```

### 재연결 처리

Sticky session이 깨지면 새 인스턴스에 연결됩니다:

```javascript
class StickyStreamClient extends StreamClient {

    async reconnect() {
        // 새 연결 시도
        await this.connect();

        // 구독 정보는 이전 인스턴스에 있었으므로 재등록 필요
        if (this.lastSubscriptions.length > 0) {
            await this.updateSubscriptions(this.lastSubscriptions);
        }

        console.log('Reconnected to new instance');
    }
}
```

---

## 운영 가이드

### Admin API 사용

#### 세션 목록 조회

```bash
# 로컬 인스턴스의 세션만 반환됨
curl "http://localhost:8080/api/admin/stream/sessions" \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

#### 세션 강제 종료

```bash
# 명령이 DB에 큐잉됨
curl -X DELETE "http://localhost:8080/api/admin/stream/sessions/abc123" \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

응답:
```json
{
  "commandId": 456,
  "status": "PENDING",
  "message": "Command queued. Session will be terminated within 2-5 seconds."
}
```

#### 스케줄러 즉시 실행

```bash
curl -X POST "http://localhost:8080/api/admin/stream/schedulers/stock-price:symbol=AAPL/trigger" \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

### 명령 처리 모니터링

#### 대기 중인 명령 조회

```bash
curl "http://localhost:8080/api/admin/stream/commands?status=PENDING" \
  -H "Authorization: Basic YWRtaW46YWRtaW4="
```

#### 명령 처리 로그

```yaml
logging:
  level:
    dev.simplecore.simplix.stream.admin.command: DEBUG
```

로그 출력 예시:
```
DEBUG AdminCommandProcessor - Processing 1 pending admin commands
INFO  AdminCommandProcessor - Executed admin command: TERMINATE_SESSION, session=abc123, instance=instance-2
```

### 스케일링 고려사항

#### 인스턴스 추가 시

```
새 인스턴스 시작
  |
  +---> DB Admin 폴링 시작
  +---> 새 SSE 연결 수신 시작
  +---> Sticky session으로 부하 분산
```

#### 인스턴스 제거 시

```
인스턴스 종료
  |
  +---> 해당 인스턴스의 세션 끊어짐
  +---> 클라이언트 재연결 시 다른 인스턴스에 연결
  +---> 해당 인스턴스 대상 명령은 타임아웃 처리
```

### 데이터 정리

오래된 명령 레코드 정리:

```yaml
simplix:
  stream:
    admin:
      retention-period: 7d        # 7일 후 삭제
      cleanup-cron: "0 0 3 * * ?" # 매일 새벽 3시
```

수동 정리:

```sql
DELETE FROM stream_admin_commands
WHERE status IN ('EXECUTED', 'EXPIRED', 'FAILED')
  AND executed_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
```

### 장애 대응

#### DB 연결 장애

Admin 명령은 실패하지만 스트리밍은 정상 동작:

```java
// AdminCommandProcessor는 장애 시 다음 폴링까지 대기
@Scheduled(fixedRateString = "${simplix.stream.admin.polling-interval:2000}")
public void processCommands() {
    try {
        // DB 조회 및 처리
    } catch (DataAccessException e) {
        log.warn("Failed to poll admin commands: {}", e.getMessage());
        // 다음 폴링에서 재시도
    }
}
```

#### 명령 타임아웃

```yaml
simplix:
  stream:
    admin:
      command-timeout: 5m  # 5분 내 실행되지 않으면 EXPIRED
```

타임아웃된 명령 확인:
```bash
curl "http://localhost:8080/api/admin/stream/commands?status=EXPIRED"
```

### 디버깅 팁

#### 명령 상태 직접 확인

```sql
SELECT * FROM stream_admin_commands
WHERE status = 'PENDING'
ORDER BY created_at DESC
LIMIT 10;
```

#### 인스턴스별 세션 확인

각 인스턴스의 actuator 엔드포인트 사용:

```bash
# Instance 1
curl http://instance1:8080/actuator/metrics/simplix.stream.sessions.active

# Instance 2
curl http://instance2:8080/actuator/metrics/simplix.stream.sessions.active
```

---

## 다음 단계

- [WebSocket 단독 모드 튜토리얼](ko/stream/tutorial-websocket-standalone.md) - WebSocket 전송 방식
- [Admin API 가이드](ko/stream/admin-api-guide.md) - 상세 API 문서
- [모니터링 가이드](ko/stream/monitoring-guide.md) - 메트릭 및 헬스 체크
