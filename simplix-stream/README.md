# SimpliX Stream Module

SSE (Server-Sent Events) 및 WebSocket 기반 실시간 구독 시스템 모듈입니다.

## Features

- ✔ **SSE/WebSocket 지원** - 다중 전송 프로토콜
- ✔ **단일 채널 다중 구독** - 하나의 연결로 여러 리소스 구독
- ✔ **자동 구독 전환** - 페이지 이동 시 자동 구독 변경
- ✔ **스케줄러 공유** - 동일 리소스 구독자 간 스케줄러 재사용
- ✔ **분산 모드** - Redis Pub/Sub 기반 다중 인스턴스 지원 (선택적)
- ✔ **DB 기반 세션 관리** - 크로스서버 세션 복원 지원
- ✔ **서버 인스턴스 관리** - 하트비트 기반 고아 세션 감지/정리
- ✔ **전역 통계** - 모든 서버의 세션/구독 통계 조회
- ✔ **분산 Admin** - DB 기반 클러스터 전체 관리
- ✔ **권한 관리** - 리소스별 접근 권한 제어
- ✔ **모니터링** - Health Check, Micrometer 메트릭

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-stream'

    // Optional: WebSocket 지원
    implementation 'org.springframework.boot:spring-boot-starter-websocket'

    // Optional: Redis 분산 모드
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Optional: 분산 Admin (DB 기반 명령 처리)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
}
```

### 2. Configuration

```yaml
simplix:
  stream:
    enabled: true
    mode: local  # local 또는 distributed
    session:
      timeout: 5m
      heartbeat-interval: 30s
      grace-period: 30s
    scheduler:
      thread-pool-size: 10
      default-interval: 1000ms
```

### 3. SimpliXStreamDataCollector 구현

```java
@Component
public class StockPriceCollector implements SimpliXStreamDataCollector {

    private final StockService stockService;

    @Override
    public String getResource() {
        return "stock-price";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String symbol = (String) params.get("symbol");
        return stockService.getCurrentPrice(symbol);
    }

    @Override
    public long getDefaultIntervalMs() {
        return 1000L;  // 1초마다 수집
    }
}
```

### 4. 클라이언트 연결 (JavaScript)

```javascript
// SSE 연결
const eventSource = new EventSource('/api/stream/connect');

eventSource.addEventListener('connected', (event) => {
    const { sessionId } = JSON.parse(event.data);
    console.log('Connected:', sessionId);

    // 구독 등록
    updateSubscriptions(sessionId, [
        { resource: 'stock-price', params: { symbol: 'AAPL' } }
    ]);
});

eventSource.addEventListener('data', (event) => {
    const message = JSON.parse(event.data);
    console.log('Data received:', message);
});

async function updateSubscriptions(sessionId, subscriptions) {
    await fetch(`/api/stream/sessions/${sessionId}/subscriptions`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subscriptions })
    });
}
```

## Operation Modes

| 모드 | 세션 관리 | 브로드캐스트 | 리더 선출 | 용도 |
|------|----------|------------|----------|------|
| `local` | LocalSessionRegistry | LocalBroadcaster | N/A | 단일 인스턴스, 개발 환경 |
| `local` + DB | DbSessionRegistry | LocalBroadcaster | N/A | 단일 인스턴스, 세션 복원 필요 시 |
| `distributed` | DbSessionRegistry | LocalBroadcaster | N/A | 다중 인스턴스, Redis 없이 운영 |
| `distributed` + Redis | DbSessionRegistry | RedisBroadcaster | RedisLeaderElection | 다중 인스턴스, 완전 분산 운영 |

**Note**: DB 영속성 (JPA)이 설정되면 자동으로 DbSessionRegistry 사용. Redis는 `simplix.stream.distributed.redis-enabled=true` 설정 시 활성화.

### Scenario Details

#### 1. Local Mode (no JPA)

개발/테스트 환경용. 모든 세션과 구독이 메모리에만 저장됩니다.

```yaml
simplix:
  stream:
    mode: local
    # JPA 의존성 없음
```

| 항목 | 동작 |
|------|------|
| 세션 저장 | 인메모리 (LocalSessionRegistry) |
| 재연결 | 동일 서버만 가능 (grace period 내) |
| 크로스서버 복원 | **불가** |
| 스케줄러 | 로컬 실행 |
| 서버 재시작 시 | 모든 세션/구독 손실 |

#### 2. Local Mode + JPA

단일 서버에서 세션 영속성이 필요한 경우. 서버 재시작 후에도 재연결 가능.

```yaml
simplix:
  stream:
    mode: local
# JPA 의존성 필요
```

| 항목 | 동작 |
|------|------|
| 세션 저장 | DB (DbSessionRegistry) |
| 재연결 | 동일 서버 + 서버 재시작 후 가능 |
| 크로스서버 복원 | **가능** (DB에서 로드) |
| 스케줄러 | 로컬 실행 |
| 서버 재시작 시 | DB에서 세션/구독 복원 |

#### 3. Distributed Mode (no Redis)

다중 인스턴스 환경에서 Redis 없이 운영. 각 서버가 독립적으로 스케줄러 실행.

```yaml
simplix:
  stream:
    mode: distributed
    distributed:
      redis-enabled: false  # 기본값
```

| 항목 | 동작 |
|------|------|
| 세션 저장 | DB (DbSessionRegistry) |
| 재연결 | 모든 서버에서 가능 |
| 크로스서버 복원 | **가능** |
| 스케줄러 | 각 서버 독립 실행 (중복 수집 가능) |
| 브로드캐스트 | 각 서버가 자기 세션에만 전송 |

> ⚠ 동일 리소스에 대해 여러 서버에서 중복 데이터 수집이 발생할 수 있습니다. 데이터 소스 부하가 낮고, 서버간 데이터 일관성이 중요하지 않은 경우 적합합니다.

#### 4. Distributed Mode + Redis

완전 분산 환경. 리더 선출로 단일 스케줄러 실행, Pub/Sub으로 모든 서버에 브로드캐스트.

```yaml
simplix:
  stream:
    mode: distributed
    distributed:
      redis-enabled: true
      leader-election:
        ttl: 30s
        renew-interval: 10s
      pubsub:
        channel-prefix: "stream:data:"
```

| 항목 | 동작 |
|------|------|
| 세션 저장 | DB (DbSessionRegistry) |
| 재연결 | 모든 서버에서 가능 |
| 크로스서버 복원 | **가능** |
| 스케줄러 | 리더만 실행 (RedisLeaderElection) |
| 브로드캐스트 | Redis Pub/Sub으로 모든 서버에 전파 |

> ℹ 리더 서버 장애 시 자동으로 새 리더 선출. 데이터 일관성과 리소스 효율성이 중요한 프로덕션 환경에 권장.

### Mode Selection Guide

```
                     ┌─────────────────┐
                     │  단일 인스턴스?  │
                     └────────┬────────┘
                              │
              ┌───────────────┴───────────────┐
              │ Yes                           │ No
              ▼                               ▼
     ┌────────────────┐              ┌────────────────┐
     │ 세션 복원 필요? │              │  Redis 사용?   │
     └───────┬────────┘              └───────┬────────┘
             │                               │
    ┌────────┴────────┐             ┌────────┴────────┐
    │ No              │ Yes         │ No              │ Yes
    ▼                 ▼             ▼                 ▼
┌──────────┐  ┌───────────────┐  ┌───────────────┐  ┌────────────────────┐
│ local    │  │ local + JPA   │  │ distributed   │  │ distributed + Redis│
│ (no JPA) │  │               │  │ (no Redis)    │  │                    │
└──────────┘  └───────────────┘  └───────────────┘  └────────────────────┘
    개발용         단일 운영         다중(독립)          다중(완전분산)
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `simplix.stream.enabled` | `true` | 모듈 활성화 |
| `simplix.stream.mode` | `local` | 운영 모드 (local/distributed) |
| `simplix.stream.session.timeout` | `5m` | 세션 타임아웃 |
| `simplix.stream.session.heartbeat-interval` | `30s` | 하트비트 주기 |
| `simplix.stream.session.grace-period` | `30s` | 재연결 대기 시간 |
| `simplix.stream.session.max-per-user` | `5` | 사용자당 최대 세션 수 |
| `simplix.stream.scheduler.thread-pool-size` | `10` | 스케줄러 스레드 풀 크기 |
| `simplix.stream.scheduler.default-interval` | `1000ms` | 기본 푸시 주기 |
| `simplix.stream.scheduler.min-interval` | `100ms` | 최소 푸시 주기 |
| `simplix.stream.scheduler.max-interval` | `60000ms` | 최대 푸시 주기 |
| `simplix.stream.subscription.max-per-session` | `20` | 세션당 최대 구독 수 |
| `simplix.stream.distributed.redis-enabled` | `false` | Redis 리더선출/브로드캐스트 활성화 |
| `simplix.stream.server.instance-id` | auto | 서버 인스턴스 ID |
| `simplix.stream.server.heartbeat-interval` | `30s` | 서버 하트비트 주기 |
| `simplix.stream.server.dead-threshold` | `2m` | 서버 장애 판단 기준 |
| `simplix.stream.admin.enabled` | `false` | 분산 Admin 활성화 |
| `simplix.stream.admin.polling-interval` | `2s` | 명령 폴링 주기 |
| `simplix.stream.admin.command-timeout` | `5m` | 명령 만료 시간 |

## Architecture

```
simplix-stream/
+-- autoconfigure/           # Auto-configuration classes
|   +-- SimpliXStreamAutoConfiguration
|   +-- SimpliXStreamCoreConfiguration
|   +-- SimpliXStreamSseConfiguration
|   +-- SimpliXStreamWebSocketConfiguration
|   +-- SimpliXStreamDistributedConfiguration
|   +-- SimpliXStreamPersistenceConfiguration
|   +-- SimpliXStreamSecurityConfiguration
|   +-- SimpliXStreamAdminConfiguration
+-- core/
|   +-- session/             # Session management
|   +-- subscription/        # Subscription management
|   +-- scheduler/           # Dynamic scheduler
|   +-- broadcast/           # Message broadcasting
+-- transport/
|   +-- sse/                 # SSE transport
|   +-- websocket/           # WebSocket transport
+-- infrastructure/
|   +-- local/               # Local mode implementations
|   +-- distributed/         # Redis-based implementations
+-- persistence/             # DB persistence layer
|   +-- entity/              # JPA entities
|   +-- repository/          # Spring Data repositories
|   +-- service/             # DB-based services
+-- security/                # Authorization
+-- admin/                   # Admin API
+-- monitoring/              # Health & Metrics
+-- collector/               # Data collection framework
```

## API Endpoints

### SSE Transport

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stream/connect` | SSE 연결 수립 |
| GET | `/api/stream/reconnect?sessionId={id}` | 세션 재연결 (크로스서버 지원) |
| PUT | `/api/stream/sessions/{id}/subscriptions` | 구독 업데이트 |
| GET | `/api/stream/sessions/{id}/subscriptions` | 현재 구독 조회 |
| DELETE | `/api/stream/sessions/{id}` | 세션 종료 |

### WebSocket Transport

| Destination | Description |
|-------------|-------------|
| `/ws/stream` | STOMP 연결 엔드포인트 |
| `/app/stream/subscribe` | 구독 요청 |
| `/app/stream/unsubscribe-all` | 전체 구독 해제 |
| `/user/queue/stream` | 메시지 수신 |

### Admin API

#### Local Stats (현재 인스턴스)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stream/admin/stats` | 로컬 인스턴스 통계 |
| GET | `/api/stream/admin/sessions` | 로컬 세션 목록 |
| GET | `/api/stream/admin/sessions/{id}` | 세션 상세 조회 |
| DELETE | `/api/stream/admin/sessions/{id}` | 세션 강제 종료 |

#### Global Stats (전체 서버, DB 기반)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stream/admin/stats/global` | 전역 통계 (모든 서버) |
| GET | `/api/stream/admin/sessions/global` | 전역 세션 목록 |
| GET | `/api/stream/admin/sessions/global/{id}` | 전역 세션 상세 |
| GET | `/api/stream/admin/subscriptions/resource/{name}` | 리소스별 구독 목록 |
| GET | `/api/stream/admin/servers` | 서버 인스턴스 목록 |

#### Schedulers

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stream/admin/schedulers` | 스케줄러 목록 조회 |
| GET | `/api/stream/admin/schedulers/{key}` | 스케줄러 상세 조회 |
| DELETE | `/api/stream/admin/schedulers/{key}` | 스케줄러 중지 |
| POST | `/api/stream/admin/schedulers/{key}/trigger` | 스케줄러 즉시 실행 |

#### Distributed Commands (분산 Admin)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/stream/admin/commands/{id}` | 명령 상태 조회 |
| GET | `/api/stream/admin/commands/pending` | 대기 중인 명령 목록 |

> ℹ 분산 Admin 모드에서 제어 명령(DELETE, POST)은 202 Accepted를 반환하며, 명령 ID로 상태를 조회할 수 있습니다.

## Client Implementation

### 재연결 동작

| 시나리오 | 재연결 방법 | 구독 복원 |
|---------|-----------|---------|
| 동일 서버 재연결 | `/api/stream/reconnect?sessionId={id}` | 자동 복원 |
| 크로스서버 재연결 (DB 기반) | `/api/stream/reconnect?sessionId={id}` | DB에서 자동 복원 |
| 새 연결 | `/api/stream/connect` | 수동 등록 필요 |

> ℹ DB 영속성 사용 시 크로스서버 재연결이 가능합니다. 클라이언트가 다른 서버로 재연결해도 DB에서 세션과 구독이 복원됩니다.

상세한 클라이언트 구현 가이드는 다음 문서를 참고하세요:

- [JavaScript 클라이언트 가이드](./docs/ko/client-javascript-guide.md) - SSE/WebSocket 클라이언트 구현
- [React/Vue 프레임워크 통합](./docs/ko/client-framework-guide.md) - 프레임워크별 통합 가이드

## Required Implementations

### SimpliXStreamDataCollector

리소스 데이터 수집기를 구현합니다.

```java
public interface SimpliXStreamDataCollector {

    /**
     * 리소스 이름 반환
     */
    String getResource();

    /**
     * 데이터 수집
     */
    Object collect(Map<String, Object> params);

    /**
     * 기본 수집 주기 (밀리초)
     */
    default long getDefaultIntervalMs() {
        return 1000L;
    }

    /**
     * 파라미터 유효성 검사
     */
    default boolean validateParams(Map<String, Object> params) {
        return true;
    }
}
```

### ResourceAuthorizer (Optional)

리소스 접근 권한을 제어합니다.

```java
public interface ResourceAuthorizer {

    /**
     * 대상 리소스 이름
     */
    String getResource();

    /**
     * 접근 권한 확인
     */
    boolean authorize(String userId, Map<String, Object> params);

    /**
     * 필요한 권한 (Spring Security 연동용)
     */
    default String getRequiredPermission() {
        return null;
    }
}
```

## Advanced Configuration

### WebSocket 활성화

```yaml
simplix:
  stream:
    websocket:
      enabled: true
      endpoint: /ws/stream
      allowed-origins: "*"
```

### 분산 모드 (Redis)

```yaml
simplix:
  stream:
    mode: distributed
    distributed:
      leader-election:
        ttl: 30s
        renew-interval: 10s
      pubsub:
        channel-prefix: "stream:data:"
      registry:
        key-prefix: "stream:"
        ttl: 1h
```

### 보안 설정

```yaml
simplix:
  stream:
    security:
      enforce-authorization: false  # true면 authorizer 없는 리소스 거부
      require-authentication: false # true면 인증 필수
```

### 분산 Admin (DB 기반)

Redis 없이 다중 인스턴스 환경에서 Admin 기능을 사용하려면 DB 기반 명령 처리를 활성화합니다.

```yaml
simplix:
  stream:
    admin:
      enabled: true              # 분산 Admin 활성화
      polling-interval: 2s       # 명령 폴링 주기
      command-timeout: 5m        # 명령 만료 시간
      retention-period: 7d       # 완료된 명령 보관 기간
```

**동작 방식:**

1. Admin API 호출 시 명령이 DB에 저장됨 (202 Accepted 반환)
2. 각 인스턴스가 주기적으로 명령 테이블 폴링
3. 타겟 리소스를 소유한 인스턴스가 명령 실행
4. 명령 상태를 DB에 업데이트

**필요 테이블:**

```sql
CREATE TABLE stream_admin_commands (
    id BIGSERIAL PRIMARY KEY,
    command_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    target_instance_id VARCHAR(64),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    executed_at TIMESTAMP,
    executed_by VARCHAR(64),
    error_message VARCHAR(500)
);

CREATE INDEX idx_stream_admin_cmd_status ON stream_admin_commands(status, created_at);
```

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health/stream
```

**Response:**
```json
{
  "status": "UP",
  "details": {
    "mode": "LOCAL",
    "sessionRegistry": "UP",
    "broadcastService": "UP",
    "activeSessions": 10,
    "activeSchedulers": 5
  }
}
```

### Metrics

| Metric | Description |
|--------|-------------|
| `simplix.stream.sessions.active` | 활성 세션 수 |
| `simplix.stream.schedulers.active` | 활성 스케줄러 수 |
| `simplix.stream.messages.delivered` | 전달된 메시지 수 |
| `simplix.stream.messages.failed` | 실패한 메시지 수 |
| `simplix.stream.connections.established` | 연결 수립 횟수 |
| `simplix.stream.subscriptions.added` | 구독 추가 횟수 |

## Message Types

| Type | Description |
|------|-------------|
| `CONNECTED` | 연결 성공 알림 |
| `RECONNECTED` | 재연결 성공 알림 (복원된 구독 목록 포함) |
| `DATA` | 데이터 메시지 |
| `HEARTBEAT` | 연결 유지 신호 |
| `ERROR` | 에러 알림 |
| `SUBSCRIPTION_REMOVED` | 구독 제거 알림 |

## Database Schema

DB 영속성을 사용하려면 다음 테이블이 필요합니다 (JPA auto-ddl 또는 수동 생성).

### stream_sessions

```sql
CREATE TABLE stream_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255),
    transport_type VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    instance_id VARCHAR(64) NOT NULL,
    connected_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    disconnected_at TIMESTAMP,
    terminated_at TIMESTAMP,
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    metadata_json TEXT,
    messages_sent BIGINT DEFAULT 0,
    bytes_sent BIGINT DEFAULT 0
);

CREATE INDEX idx_stream_sessions_user ON stream_sessions(user_id);
CREATE INDEX idx_stream_sessions_state ON stream_sessions(state);
CREATE INDEX idx_stream_sessions_instance ON stream_sessions(instance_id);
```

### stream_subscriptions

```sql
CREATE TABLE stream_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    subscription_key VARCHAR(255) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    params_json TEXT,
    interval_ms BIGINT NOT NULL,
    subscribed_at TIMESTAMP NOT NULL,
    unsubscribed_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_stream_sub_session_key UNIQUE (session_id, subscription_key)
);

CREATE INDEX idx_stream_sub_key ON stream_subscriptions(subscription_key);
CREATE INDEX idx_stream_sub_resource ON stream_subscriptions(resource);
CREATE INDEX idx_stream_sub_active ON stream_subscriptions(active);
```

### stream_server_instances

```sql
CREATE TABLE stream_server_instances (
    instance_id VARCHAR(64) PRIMARY KEY,
    hostname VARCHAR(255),
    port INT,
    started_at TIMESTAMP NOT NULL,
    last_heartbeat_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_sessions INT DEFAULT 0,
    active_schedulers INT DEFAULT 0
);

CREATE INDEX idx_stream_server_status ON stream_server_instances(status);
CREATE INDEX idx_stream_server_heartbeat ON stream_server_instances(last_heartbeat_at);
```

## Requirements

- Java 17+
- Spring Boot 3.5+
- Spring WebFlux (SSE 지원)
- Spring Security
- (Optional) Spring WebSocket
- (Optional) Spring Data Redis
- (Optional) Spring Data JPA (분산 Admin)

## License

SimpleCORE License 1.0 (SCL-1.0)
