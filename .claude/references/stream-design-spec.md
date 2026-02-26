# 실시간 구독 시스템 종합 설계서

---

## 1. 개요

### 1.1 목적

REST API로 요청을 받고, 실시간 데이터는 SSE 또는 WebSocket으로 푸시하는 통합 실시간 구독 시스템 구축

### 1.2 핵심 요구사항

```
기능 요구사항
─────────────────────────────────
- 단일 채널로 다중 리소스 구독
- 페이지 이동 시 자동 구독 전환
- 동일 리소스 요청 시 스케줄러 공유
- 구독자 0명 시 스케줄러 자동 종료
- Spring Security 권한 체계 통합

비기능 요구사항
─────────────────────────────────
- 단일 서버 / 분산 환경 모두 지원
- 수천 동시 연결 처리
- 연결 끊김 시 graceful 복구
- 실시간 모니터링 및 관리
```

### 1.3 기술 스택

```
Backend
─────────────────────────────────
- Spring Boot 3.x
- Spring Security
- Spring WebFlux (SSE)
- Spring WebSocket + STOMP
- Redis (분산 모드)

Frontend
─────────────────────────────────
- React 18+
- TypeScript
- Custom Hook 기반 구독 관리
```

---

## 2. 아키텍처

### 2.1 전체 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client                                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      StreamClient                                │   │
│  │  - 전역 단일 연결 관리                                            │   │
│  │  - 구독 등록/해제                                                 │   │
│  │  - 자동 재연결                                                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                    SSE: /api/stream/connect
                    REST: /api/stream/subscriptions
                    WS: /ws (STOMP)
                                 │
┌────────────────────────────────▼────────────────────────────────────────┐
│                              Server                                     │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     Transport Layer                              │   │
│  │  ┌─────────────────┐              ┌─────────────────┐           │   │
│  │  │  SSE Controller │              │  WS Controller  │           │   │
│  │  │  @PreAuthorize  │              │  @PreAuthorize  │           │   │
│  │  └────────┬────────┘              └────────┬────────┘           │   │
│  └───────────┼────────────────────────────────┼─────────────────────┘   │
│              │                                │                         │
│              └────────────────┬───────────────┘                         │
│                               ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     Session Layer                                │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │                  SessionManager                          │    │   │
│  │  │  - 세션 생명주기 관리                                     │    │   │
│  │  │  - Transport 추상화 (StreamSession)                      │    │   │
│  │  │  - 유예 기간 관리                                         │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────┬──────────────────────────────────┘   │
│                                 ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Subscription Layer                             │   │
│  │  ┌──────────────────────┐    ┌────────────────────────────┐     │   │
│  │  │ SubscriptionManager  │    │ AuthorizationService       │     │   │
│  │  │ - 구독 등록/해제      │    │ - 리소스별 권한 검증        │     │   │
│  │  │ - Diff 계산          │    │ - @PreAuthorize 연동       │     │   │
│  │  └──────────┬───────────┘    └────────────────────────────┘     │   │
│  └─────────────┼───────────────────────────────────────────────────┘   │
│                ▼                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Scheduler Layer                               │   │
│  │  ┌──────────────────────┐    ┌────────────────────────────┐     │   │
│  │  │  SchedulerManager    │    │  DataCollectorRegistry     │     │   │
│  │  │  - 동적 스케줄러 관리 │    │  - 리소스별 수집기 등록     │     │   │
│  │  │  - 구독자 카운팅      │    │  - 데이터 수집 실행        │     │   │
│  │  └──────────┬───────────┘    └────────────────────────────┘     │   │
│  └─────────────┼───────────────────────────────────────────────────┘   │
│                ▼                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Broadcast Layer                               │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │                  BroadcastService                        │    │   │
│  │  │  - 구독자에게 메시지 전송                                  │    │   │
│  │  │  - 전송 실패 처리                                         │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Infrastructure Layer                          │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │   │
│  │  │SchedulerExecutor│  │   Broadcaster   │  │ Subscription    │  │   │
│  │  │   (Interface)   │  │   (Interface)   │  │ Registry (I/F)  │  │   │
│  │  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │   │
│  │           │                    │                    │           │   │
│  │     ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐    │   │
│  │     ▼           ▼        ▼           ▼        ▼           ▼    │   │
│  │  ┌──────┐  ┌────────┐ ┌──────┐ ┌────────┐ ┌──────┐ ┌────────┐ │   │
│  │  │Local │  │Distrib.│ │Local │ │ Redis  │ │Local │ │ Redis  │ │   │
│  │  └──────┘  └────────┘ └──────┘ └────────┘ └──────┘ └────────┘ │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 컴포넌트 책임

| 컴포넌트 | 책임 |
|----------|------|
| Transport Layer | 프로토콜별 연결 처리, 인증 |
| SessionManager | 세션 생명주기, 상태 관리 |
| SubscriptionManager | 구독 등록/해제 조율, diff 계산 |
| AuthorizationService | 리소스별 권한 검증 |
| SchedulerManager | 동적 스케줄러 생성/종료, 구독자 카운팅 |
| DataCollectorRegistry | 리소스별 데이터 수집기 관리 |
| BroadcastService | 메시지 전송, 실패 처리 |
| Infrastructure Layer | 로컬/분산 모드 추상화 |

---

## 3. 데이터 모델

### 3.1 핵심 엔티티

```
SubscriptionKey
─────────────────────────────────
목적: 구독 식별자 (리소스 + 파라미터 조합)

필드:
├── resource: String              # 리소스명 (cpu, memory, sales 등)
├── paramsHash: String            # 파라미터 MD5 해시
└── params: Map<String, Object>   # 원본 파라미터

특징:
- interval은 키에 포함하지 않음
- 같은 resource + params면 동일 키
- 해시 충돌 가능성 극히 낮음 (MD5)
```

```
Subscription
─────────────────────────────────
목적: 구독 요청 정보

필드:
├── key: SubscriptionKey
├── interval: Duration            # 희망 푸시 주기
└── requestedAt: Instant

제약:
- interval: 100ms ~ 60,000ms
```

```
StreamSession
─────────────────────────────────
목적: 클라이언트 연결 세션

필드:
├── id: String                    # UUID
├── userId: String                # 인증된 사용자 ID
├── transportType: TransportType  # SSE | WEBSOCKET
├── state: SessionState           # CONNECTED | DISCONNECTED | TERMINATED
├── connectedAt: Instant
├── lastActiveAt: Instant
├── disconnectedAt: Instant?      # 유예 기간 계산용
└── metadata: Map<String, Object> # 확장용

상태 전이:
- CONNECTED: 정상 연결 상태
- DISCONNECTED: 연결 끊김, 유예 기간 중
- TERMINATED: 완전 종료, 정리 대상
```

```
SubscriptionScheduler
─────────────────────────────────
목적: 리소스별 데이터 수집 스케줄러

필드:
├── key: SubscriptionKey
├── subscribers: Set<String>      # 세션 ID 목록
├── state: SchedulerState         # CREATED | RUNNING | ERROR | STOPPED
├── interval: Duration            # 실제 적용 주기
├── createdAt: Instant
├── lastExecutedAt: Instant
├── lastSuccessAt: Instant
├── executionCount: Long
├── successCount: Long
├── errorCount: Long
├── consecutiveErrors: Int
└── lastError: String?

상태 전이:
- CREATED → RUNNING: 첫 실행
- RUNNING → ERROR: 5회 연속 실패
- ERROR → RUNNING: 1회 성공
- * → STOPPED: 구독자 0명
```

```
StreamMessage
─────────────────────────────────
목적: 클라이언트로 전송되는 메시지

필드:
├── subscriptionKey: String       # 어떤 구독에 대한 데이터인지
├── resource: String              # 리소스명
├── payload: Object               # 실제 데이터
└── timestamp: Instant
```

### 3.2 상태 다이어그램

```
세션 상태 전이
─────────────────────────────────

                    연결 성공
    ┌─────────┐ ──────────────→ ┌───────────┐
    │ (신규)  │                 │ CONNECTED │◄─────────┐
    └─────────┘                 └─────┬─────┘          │
                                      │                │
                            연결 끊김 │                │ 유예 기간 내
                                      ▼                │ 재연결
                               ┌──────────────┐        │
                               │ DISCONNECTED │────────┘
                               └──────┬───────┘
                                      │
                            유예 기간 │ 초과 (30초)
                                      ▼
                               ┌────────────┐
                               │ TERMINATED │ → 구독 정리 → 세션 제거
                               └────────────┘


스케줄러 상태 전이
─────────────────────────────────

    ┌─────────┐
    │ CREATED │
    └────┬────┘
         │ 첫 실행
         ▼
    ┌─────────┐  1회 성공   ┌─────────┐
    │ RUNNING │◄────────────│  ERROR  │
    └────┬────┘             └────┬────┘
         │                       ▲
         │ 5회 연속 실패         │
         └───────────────────────┘
         │
         │ 구독자 0명 (어느 상태에서든)
         ▼
    ┌─────────┐
    │ STOPPED │ → 리소스 정리 → 맵에서 제거
    └─────────┘
```

---

## 4. 인터페이스 설계

### 4.1 클라이언트 → 서버

```
SSE 연결
─────────────────────────────────
GET /api/stream/connect
Authorization: Bearer {token}

Response: text/event-stream

event: connected
data: {"sessionId": "uuid-xxx", "serverTime": "..."}

event: cpu
data: {"subscriptionKey": "cpu:abc123", "payload": {...}}

event: heartbeat
data: {"timestamp": "..."}
```

```
구독 변경
─────────────────────────────────
PUT /api/stream/subscriptions
Authorization: Bearer {token}
X-Session-Id: {sessionId}
Content-Type: application/json

Request:
{
  "subscriptions": [
    {
      "resource": "cpu",
      "params": {"serverId": "srv-01"},
      "intervalMs": 1000
    },
    {
      "resource": "memory",
      "params": {"serverId": "srv-01"},
      "intervalMs": 1000
    }
  ]
}

Response (200 OK):
{
  "active": [
    {
      "key": "cpu:abc123",
      "resource": "cpu",
      "params": {"serverId": "srv-01"},
      "intervalMs": 1000
    },
    {
      "key": "memory:def456",
      "resource": "memory",
      "params": {"serverId": "srv-01"},
      "intervalMs": 1000
    }
  ],
  "denied": [
    {
      "resource": "admin",
      "params": {"type": "logs"},
      "reason": "FORBIDDEN"
    }
  ],
  "invalid": [
    {
      "resource": "unknown",
      "params": {},
      "reason": "RESOURCE_NOT_FOUND"
    }
  ]
}
```

```
WebSocket 연결 (STOMP)
─────────────────────────────────
CONNECT /ws
Authorization: Bearer {token}

SUBSCRIBE /user/queue/stream

SEND /app/subscribe
{
  "subscriptions": [...]
}
```

### 4.2 서버 → 클라이언트

```
데이터 메시지
─────────────────────────────────
SSE:
event: {resource}
data: {
  "subscriptionKey": "cpu:abc123",
  "resource": "cpu",
  "payload": {
    "serverId": "srv-01",
    "usage": 45.2,
    "cores": 8
  },
  "timestamp": "2025-01-11T10:00:00Z"
}

WebSocket:
{
  "type": "DATA",
  "subscriptionKey": "cpu:abc123",
  "resource": "cpu",
  "payload": {...},
  "timestamp": "..."
}
```

```
하트비트
─────────────────────────────────
SSE:
event: heartbeat
data: {"timestamp": "..."}

WebSocket:
{
  "type": "HEARTBEAT",
  "timestamp": "..."
}
```

```
에러/알림
─────────────────────────────────
{
  "type": "ERROR",
  "subscriptionKey": "cpu:abc123",
  "code": "SCHEDULER_ERROR",
  "message": "Data collection failed",
  "timestamp": "..."
}

{
  "type": "SUBSCRIPTION_REMOVED",
  "subscriptionKey": "cpu:abc123",
  "reason": "PERMISSION_REVOKED",
  "timestamp": "..."
}
```

### 4.3 관리자 API

```
시스템 상태 조회
─────────────────────────────────
GET /admin/stream/status

Response:
{
  "mode": "distributed",
  "uptime": "PT24H30M",
  "sessions": {
    "active": 150,
    "disconnected": 5,
    "byTransport": {
      "SSE": 120,
      "WEBSOCKET": 30
    }
  },
  "schedulers": {
    "total": 45,
    "byState": {
      "RUNNING": 43,
      "ERROR": 2
    },
    "byResource": {
      "cpu": 15,
      "memory": 15,
      "sales": 10,
      "disk": 5
    }
  },
  "subscriptions": {
    "total": 380,
    "avgPerSession": 2.5
  },
  "cluster": {
    "servers": ["server-1", "server-2", "server-3"],
    "thisServer": "server-1",
    "redis": {
      "connected": true,
      "latencyMs": 2
    }
  }
}
```

```
스케줄러 목록 조회
─────────────────────────────────
GET /admin/stream/schedulers?resource=cpu&state=RUNNING

Response:
{
  "schedulers": [
    {
      "key": "cpu:abc123",
      "resource": "cpu",
      "params": {"serverId": "srv-01"},
      "state": "RUNNING",
      "intervalMs": 1000,
      "subscriberCount": 5,
      "executionCount": 15023,
      "successCount": 15020,
      "errorCount": 3,
      "consecutiveErrors": 0,
      "lastExecutedAt": "2025-01-11T10:00:00Z",
      "lastSuccessAt": "2025-01-11T10:00:00Z",
      "createdAt": "2025-01-11T09:30:00Z",
      "leader": "server-1"
    }
  ],
  "total": 15,
  "page": 1,
  "size": 20
}
```

```
세션 목록 조회
─────────────────────────────────
GET /admin/stream/sessions?userId=user-001

Response:
{
  "sessions": [
    {
      "id": "session-abc",
      "userId": "user-001",
      "transport": "SSE",
      "state": "CONNECTED",
      "subscriptions": ["cpu:abc123", "memory:def456"],
      "connectedAt": "2025-01-11T09:45:00Z",
      "lastActiveAt": "2025-01-11T10:00:00Z",
      "server": "server-1"
    }
  ]
}
```

```
스케줄러 강제 종료
─────────────────────────────────
DELETE /admin/stream/schedulers/{key}

Response:
{
  "key": "cpu:abc123",
  "action": "STOPPED",
  "affectedSubscribers": 5,
  "notificationSent": true
}
```

```
세션 강제 종료
─────────────────────────────────
DELETE /admin/stream/sessions/{sessionId}

Response:
{
  "sessionId": "session-abc",
  "action": "TERMINATED",
  "subscriptionsCleared": 3
}
```

```
리더십 이전 (분산 모드)
─────────────────────────────────
POST /admin/stream/schedulers/{key}/transfer
Content-Type: application/json

Request:
{
  "targetServer": "server-2"
}

Response:
{
  "key": "cpu:abc123",
  "previousLeader": "server-1",
  "newLeader": "server-2",
  "transferredAt": "2025-01-11T10:00:00Z"
}
```

---

## 5. 프로세스 흐름

### 5.1 연결 수립

```
┌────────┐          ┌────────────┐          ┌────────────────┐
│ Client │          │ Controller │          │ SessionManager │
└───┬────┘          └─────┬──────┘          └───────┬────────┘
    │                     │                         │
    │ GET /stream/connect │                         │
    │ Authorization: xxx  │                         │
    │────────────────────>│                         │
    │                     │                         │
    │                     │ Spring Security 인증    │
    │                     │ @PreAuthorize 검증      │
    │                     │                         │
    │                     │ createSession(userId)   │
    │                     │────────────────────────>│
    │                     │                         │
    │                     │     StreamSession       │
    │                     │     (id, userId, SSE)   │
    │                     │<────────────────────────│
    │                     │                         │
    │                     │ register(session)       │
    │                     │────────────────────────>│
    │                     │                         │
    │   SSE Stream        │                         │
    │   event: connected  │                         │
    │   {sessionId: xxx}  │                         │
    │<────────────────────│                         │
    │                     │                         │
```

### 5.2 구독 등록

```
┌────────┐      ┌────────────┐      ┌─────────────┐      ┌──────────────┐      ┌──────────────────┐
│ Client │      │ Controller │      │ AuthService │      │ SubsManager  │      │ SchedulerManager │
└───┬────┘      └─────┬──────┘      └──────┬──────┘      └──────┬───────┘      └────────┬─────────┘
    │                 │                    │                    │                       │
    │ PUT /subscriptions                   │                    │                       │
    │ [cpu:srv-01, memory:srv-01]          │                    │                       │
    │────────────────>│                    │                    │                       │
    │                 │                    │                    │                       │
    │                 │ 세션 검증          │                    │                       │
    │                 │ (sessionId + userId 일치 확인)          │                       │
    │                 │                    │                    │                       │
    │                 │ validateAll(subs)  │                    │                       │
    │                 │───────────────────>│                    │                       │
    │                 │                    │                    │                       │
    │                 │  {valid, denied,   │                    │                       │
    │                 │   invalid}         │                    │                       │
    │                 │<───────────────────│                    │                       │
    │                 │                    │                    │                       │
    │                 │ updateSubscriptions(sessionId, valid)   │                       │
    │                 │─────────────────────────────────────────>                       │
    │                 │                    │                    │                       │
    │                 │                    │                    │ diff 계산             │
    │                 │                    │                    │ (추가/제거 목록)      │
    │                 │                    │                    │                       │
    │                 │                    │                    │ addSubscriber(cpu)    │
    │                 │                    │                    │──────────────────────>│
    │                 │                    │                    │                       │
    │                 │                    │                    │  스케줄러 없음?       │
    │                 │                    │                    │  → 생성               │
    │                 │                    │                    │  스케줄러 있음?       │
    │                 │                    │                    │  → 구독자 추가        │
    │                 │                    │                    │                       │
    │                 │                    │                    │ addSubscriber(memory) │
    │                 │                    │                    │──────────────────────>│
    │                 │                    │                    │                       │
    │  Response       │                    │                    │                       │
    │  {active, denied, invalid}           │                    │                       │
    │<────────────────│                    │                    │                       │
    │                 │                    │                    │                       │
```

### 5.3 데이터 푸시

```
┌──────────────────┐      ┌───────────────┐      ┌──────────────────┐      ┌────────────────┐
│SchedulerManager  │      │ DataCollector │      │ BroadcastService │      │ SessionManager │
└────────┬─────────┘      └───────┬───────┘      └────────┬─────────┘      └───────┬────────┘
         │                        │                       │                        │
         │ [주기적 실행]          │                       │                        │
         │                        │                       │                        │
         │ collect(params)        │                       │                        │
         │───────────────────────>│                       │                        │
         │                        │                       │                        │
         │                        │ DB/API 조회           │                        │
         │                        │                       │                        │
         │      data              │                       │                        │
         │<───────────────────────│                       │                        │
         │                        │                       │                        │
         │ broadcast(subscribers, message)                │                        │
         │───────────────────────────────────────────────>│                        │
         │                        │                       │                        │
         │                        │                       │ 각 세션에 전송         │
         │                        │                       │───────────────────────>│
         │                        │                       │                        │
         │                        │                       │   session.send(msg)    │
         │                        │                       │                        │
         │                        │                       │   전송 실패 시         │
         │                        │                       │   → 세션 정리 트리거   │
         │                        │                       │                        │
         │ 통계 업데이트          │                       │                        │
         │ (executionCount++)     │                       │                        │
         │                        │                       │                        │
```

### 5.4 연결 종료 및 복구

```
┌────────┐      ┌────────────────┐      ┌─────────────┐      ┌──────────────────┐
│ Client │      │ SessionManager │      │ SubsManager │      │ SchedulerManager │
└───┬────┘      └───────┬────────┘      └──────┬──────┘      └────────┬─────────┘
    │                   │                      │                      │
    │ [연결 끊김]       │                      │                      │
    │ ─ ─ ─ ─ ─ ─ ─ ─ >│                      │                      │
    │                   │                      │                      │
    │                   │ state = DISCONNECTED │                      │
    │                   │ disconnectedAt = now │                      │
    │                   │                      │                      │
    │                   │ [유예 타이머 시작]   │                      │
    │                   │ (30초)               │                      │
    │                   │                      │                      │
    │                   │                      │                      │
    │ ═══════════════════════════════════════════════════════════════│
    │ 시나리오 A: 유예 기간 내 재연결                                 │
    │ ═══════════════════════════════════════════════════════════════│
    │                   │                      │                      │
    │ 재연결 요청       │                      │                      │
    │──────────────────>│                      │                      │
    │                   │                      │                      │
    │                   │ state = CONNECTED    │                      │
    │                   │ 타이머 취소          │                      │
    │                   │ 기존 구독 유지       │                      │
    │                   │                      │                      │
    │   기존 sessionId  │                      │                      │
    │   유지            │                      │                      │
    │<──────────────────│                      │                      │
    │                   │                      │                      │
    │ ═══════════════════════════════════════════════════════════════│
    │ 시나리오 B: 유예 기간 초과                                      │
    │ ═══════════════════════════════════════════════════════════════│
    │                   │                      │                      │
    │                   │ [타임아웃]           │                      │
    │                   │ state = TERMINATED   │                      │
    │                   │                      │                      │
    │                   │ clearSubscriptions   │                      │
    │                   │─────────────────────>│                      │
    │                   │                      │                      │
    │                   │                      │ removeAll(sessionId) │
    │                   │                      │─────────────────────>│
    │                   │                      │                      │
    │                   │                      │  구독자 0명 스케줄러 │
    │                   │                      │  → 종료              │
    │                   │                      │                      │
    │                   │ unregister(session)  │                      │
    │                   │                      │                      │
    │                   │                      │                      │
```

### 5.5 페이지 전환 (클라이언트)

```
┌────────────┐      ┌──────────────┐      ┌────────┐
│   PageA    │      │ StreamClient │      │ Server │
└─────┬──────┘      └──────┬───────┘      └───┬────┘
      │                    │                  │
      │ useStream('cpu')   │                  │
      │───────────────────>│                  │
      │                    │                  │
      │ useStream('memory')│                  │
      │───────────────────>│                  │
      │                    │                  │
      │                    │ [debounce 50ms]  │
      │                    │                  │
      │                    │ PUT /subscriptions
      │                    │ [cpu, memory]    │
      │                    │─────────────────>│
      │                    │                  │
      │                    │    200 OK        │
      │                    │<─────────────────│
      │                    │                  │
      │ ════════════════════════════════════════
      │ 사용자가 PageB로 이동
      │ ════════════════════════════════════════
      │                    │                  │
      │ [unmount]          │                  │
      │ unsubscribe('cpu') │                  │
      │───────────────────>│                  │
      │                    │                  │
      │ unsubscribe('memory')                 │
      │───────────────────>│                  │
      │                    │                  │
┌─────┴──────┐             │                  │
│   PageB    │             │                  │
└─────┬──────┘             │                  │
      │                    │                  │
      │ useStream('network')                  │
      │───────────────────>│                  │
      │                    │                  │
      │ useStream('disk')  │                  │
      │───────────────────>│                  │
      │                    │                  │
      │                    │ [debounce 50ms]  │
      │                    │                  │
      │                    │ PUT /subscriptions
      │                    │ [network, disk]  │
      │                    │─────────────────>│
      │                    │                  │
      │                    │    200 OK        │
      │                    │<─────────────────│
      │                    │                  │
```

---

## 6. 보안 설계

### 6.1 인증/인가 흐름

```
인증 (Authentication)
─────────────────────────────────
1. 연결 시점
   - SSE: Authorization 헤더의 Bearer 토큰 검증
   - WebSocket: 핸드셰이크 시 토큰 검증
   - Spring Security Filter Chain 통과

2. 세션 바인딩
   - 인증된 사용자 ID를 세션에 저장
   - 이후 요청에서 세션-사용자 일치 검증


인가 (Authorization)
─────────────────────────────────
1. 연결 수준
   @PreAuthorize("isAuthenticated()")

2. 구독 수준
   - 각 리소스에 대해 개별 권한 검증
   - ResourceAuthorizer 인터페이스로 추상화
```

### 6.2 세션 보안

```
세션 하이재킹 방지
─────────────────────────────────
문제: 타인의 sessionId를 알면 구독 조작 가능

대책:
1. 세션 조회 시 userId 일치 확인
   if (!session.getUserId().equals(authenticatedUserId)) {
       throw new AccessDeniedException("Session not owned by user");
   }

2. sessionId는 UUID v4 (추측 불가)

3. 세션 메타데이터에 IP/User-Agent 저장 (옵션)
   - 불일치 시 경고 로그
```

### 6.3 리소스 권한 모델

```
ResourceAuthorizer 인터페이스
─────────────────────────────────
역할: 리소스별 세부 권한 검증

구현 예시:

@Component
public class CpuResourceAuthorizer implements ResourceAuthorizer {

    @Override
    public String getResource() {
        return "cpu";
    }

    @Override
    public String getRequiredPermission() {
        return "MONITOR_SERVER";  // Spring Security 권한명
    }

    @Override
    public boolean authorize(String userId, Map<String, Object> params) {
        String serverId = (String) params.get("serverId");
        // 사용자가 해당 서버에 접근 권한이 있는지 확인
        return serverAccessService.hasAccess(userId, serverId);
    }
}


@PreAuthorize 스타일 지원 (옵션)
─────────────────────────────────
@StreamResource(
    value = "sales",
    preAuthorize = "hasRole('SALES_VIEWER') and @regionAccess.check(#params['region'])"
)
public class SalesDataCollector implements DataCollector {
    // ...
}
```

### 6.4 권한 검증 흐름

```
구독 요청 시 권한 검증
─────────────────────────────────

Request: [cpu:srv-01, sales:asia, admin:logs]

1. cpu:srv-01
   ├─ ResourceAuthorizer 조회 → CpuResourceAuthorizer
   ├─ getRequiredPermission() → "MONITOR_SERVER"
   ├─ Spring Security 권한 확인 → OK
   └─ authorize(userId, {serverId: srv-01}) → OK
   결과: VALID

2. sales:asia
   ├─ ResourceAuthorizer 조회 → SalesResourceAuthorizer
   ├─ getRequiredPermission() → "VIEW_SALES"
   ├─ Spring Security 권한 확인 → OK
   └─ authorize(userId, {region: asia}) → OK
   결과: VALID

3. admin:logs
   ├─ ResourceAuthorizer 조회 → AdminResourceAuthorizer
   ├─ getRequiredPermission() → "ADMIN"
   ├─ Spring Security 권한 확인 → FAIL
   └─ (파라미터 검증 스킵)
   결과: DENIED

Response:
{
  "active": ["cpu:xxx", "sales:yyy"],
  "denied": [{"resource": "admin", "reason": "FORBIDDEN"}]
}
```

---

## 7. 분산 환경 설계

### 7.1 모드 선택

```
Local 모드
─────────────────────────────────
- 단일 서버 또는 Sticky Session 환경
- 모든 상태 인메모리
- 서버 간 통신 없음
- 장점: 단순, 빠름
- 단점: 서버별 스케줄러 중복

Distributed 모드
─────────────────────────────────
- 다중 서버, 로드밸런서 환경
- Redis 기반 상태 공유
- 리더 선출로 스케줄러 단일 실행
- 장점: 리소스 효율, 무중단 배포
- 단점: Redis 의존성, 복잡도 증가
```

### 7.2 분산 모드 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                      Load Balancer                          │
└─────────────────────────┬───────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   ┌─────────┐       ┌─────────┐       ┌─────────┐
   │Server 1 │       │Server 2 │       │Server 3 │
   │         │       │         │       │         │
   │ Sessions│       │ Sessions│       │ Sessions│
   │ [A,B,C] │       │ [D,E,F] │       │ [G,H,I] │
   │         │       │         │       │         │
   │ Leader: │       │ Leader: │       │ Leader: │
   │ cpu:x   │       │ mem:y   │       │ sales:z │
   └────┬────┘       └────┬────┘       └────┬────┘
        │                 │                 │
        └─────────────────┼─────────────────┘
                          ▼
                   ┌─────────────┐
                   │    Redis    │
                   │             │
                   │ - Pub/Sub   │
                   │ - Leader    │
                   │ - Registry  │
                   └─────────────┘

동작 방식:
1. 각 서버가 자기 세션만 관리
2. 리소스별로 리더 선출 (한 서버만 스케줄러 실행)
3. 리더가 데이터 수집 후 Redis Pub/Sub으로 브로드캐스트
4. 모든 서버가 메시지 수신, 자기 세션에만 전송
```

### 7.3 리더 선출

```
선출 알고리즘 (Redis SETNX 기반)
─────────────────────────────────

선출 시도:
SET stream:leader:{key} {serverId} NX EX 30

성공 → 리더가 됨, 스케줄러 시작
실패 → 다른 서버가 리더, 대기


리더십 유지 (10초마다):
EXPIRE stream:leader:{key} 30

실패 → 리더십 상실, 스케줄러 중지


리더십 해제:
DEL stream:leader:{key}


장애 시 자동 복구:
- 리더 서버 다운 → TTL 만료 (30초)
- 다른 서버가 선출 시도 → 새 리더
- 최대 30초 데이터 푸시 중단
```

### 7.4 메시지 흐름 (분산 모드)

```
┌──────────┐      ┌──────────┐      ┌───────┐      ┌──────────┐      ┌──────────┐
│ Server 1 │      │  Redis   │      │Server2│      │ Server 3 │      │ Clients  │
│ (Leader) │      │          │      │       │      │          │      │          │
└────┬─────┘      └────┬─────┘      └───┬───┘      └────┬─────┘      └────┬─────┘
     │                 │                │               │                 │
     │ collect()       │                │               │                 │
     │ (데이터 수집)   │                │               │                 │
     │                 │                │               │                 │
     │ PUBLISH         │                │               │                 │
     │ stream:data:cpu │                │               │                 │
     │ {payload}       │                │               │                 │
     │────────────────>│                │               │                 │
     │                 │                │               │                 │
     │                 │ broadcast      │               │                 │
     │                 │───────────────>│               │                 │
     │                 │────────────────────────────────>               │
     │                 │                │               │                 │
     │ 자기 세션에     │                │ 자기 세션에   │ 자기 세션에     │
     │ 전송 (A,B,C)    │                │ 전송 (D,E,F)  │ 전송 (G,H,I)    │
     │─────────────────────────────────────────────────────────────────>│
     │                 │                │               │                 │
```

### 7.5 구독자 수 관리 (분산 모드)

```
문제:
- 각 서버가 자기 세션의 구독만 알고 있음
- 전체 구독자 수를 알아야 스케줄러 종료 판단 가능

해결:
- Redis Set으로 전역 구독자 관리

SADD stream:subs:{key} {sessionId}    # 구독 추가
SREM stream:subs:{key} {sessionId}    # 구독 제거
SCARD stream:subs:{key}               # 구독자 수


종료 판단 흐름:
1. removeSubscriber() 호출
2. Redis에서 SREM 실행
3. SCARD로 전체 구독자 수 확인
4. 0명이면 리더에게 종료 신호 (PUBLISH)
5. 리더가 스케줄러 종료 + 리더십 해제
```

### 7.6 장애 대응

```
Redis 연결 실패
─────────────────────────────────
감지: RedisConnectionFailureException

대응:
1. 즉시 Local 모드로 폴백 (옵션)
2. 또는 서비스 degraded 상태로 전환
3. 새 구독 거부, 기존 구독 유지 시도
4. 재연결 시도 (지수 백오프)
5. 복구 시 상태 동기화


서버 다운
─────────────────────────────────
감지: 리더십 TTL 만료

대응:
1. 다른 서버가 자동으로 리더 선출
2. 최대 30초 데이터 푸시 지연
3. 다운된 서버의 세션들은 연결 끊김 처리


네트워크 파티션
─────────────────────────────────
감지: Redis 부분 접근 불가

대응:
1. Split-brain 방지: 리더십 갱신 실패 시 즉시 중지
2. 다수 파티션에서만 리더 선출
3. 소수 파티션은 대기
```

---

## 8. 모니터링 및 운영

### 8.1 메트릭

```
세션 메트릭
─────────────────────────────────
stream_sessions_total
  - 태그: transport (SSE/WEBSOCKET), server
  - 유형: Gauge
  - 설명: 현재 활성 세션 수

stream_sessions_created_total
  - 태그: transport
  - 유형: Counter
  - 설명: 생성된 세션 총 수

stream_session_duration_seconds
  - 유형: Histogram
  - 설명: 세션 유지 시간 분포


구독 메트릭
─────────────────────────────────
stream_subscriptions_total
  - 태그: resource, server
  - 유형: Gauge
  - 설명: 현재 활성 구독 수

stream_subscription_changes_total
  - 태그: resource, action (add/remove)
  - 유형: Counter
  - 설명: 구독 변경 횟수


스케줄러 메트릭
─────────────────────────────────
stream_schedulers_total
  - 태그: resource, state, server
  - 유형: Gauge
  - 설명: 현재 스케줄러 수

stream_scheduler_executions_total
  - 태그: resource, status (success/error)
  - 유형: Counter
  - 설명: 스케줄러 실행 횟수

stream_scheduler_execution_duration_seconds
  - 태그: resource
  - 유형: Histogram
  - 설명: 데이터 수집 소요 시간


브로드캐스트 메트릭
─────────────────────────────────
stream_broadcast_total
  - 태그: resource, status (success/error)
  - 유형: Counter
  - 설명: 브로드캐스트 횟수

stream_broadcast_latency_seconds
  - 태그: resource
  - 유형: Histogram
  - 설명: 전송 지연 시간


분산 환경 메트릭 (Distributed 모드)
─────────────────────────────────
stream_leader_elections_total
  - 태그: resource, result (won/lost)
  - 유형: Counter
  - 설명: 리더 선출 시도 횟수

stream_leader_tenure_seconds
  - 태그: resource, server
  - 유형: Histogram
  - 설명: 리더 유지 시간

stream_redis_operations_total
  - 태그: operation, status
  - 유형: Counter
  - 설명: Redis 작업 횟수

stream_redis_latency_seconds
  - 태그: operation
  - 유형: Histogram
  - 설명: Redis 응답 시간
```

### 8.2 헬스 체크

```
StreamHealthIndicator
─────────────────────────────────

체크 항목:
1. 세션 관리 정상 여부
   - SessionManager null 아님
   - 최근 1분간 세션 생성/종료 동작 확인

2. 스케줄러 상태
   - ERROR 상태 비율 < 10%
   - 스레드풀 활성 스레드 < 최대치의 80%

3. 브로드캐스트 성공률
   - 최근 5분간 성공률 > 95%

4. Redis 연결 (분산 모드)
   - 연결 상태 확인
   - Ping 응답 시간 < 100ms


상태 반환:
- UP: 모든 체크 통과
- DEGRADED: 경고 수준 (일부 지표 임계치 초과)
- DOWN: 핵심 기능 불가
```

### 8.3 알림 규칙

```
Critical (즉시 알림)
─────────────────────────────────
조건:
- 스케줄러 ERROR 상태 1시간 이상 지속
- Redis 연결 실패 5분 이상 (분산 모드)
- 전송 성공률 < 90% (5분간)
- 세션 수 급감 (1분 내 50% 이상 감소)

액션:
- PagerDuty / Slack 즉시 알림
- 자동 진단 리포트 생성


Warning (집계 알림)
─────────────────────────────────
조건:
- 스케줄러 ERROR 상태 진입
- 연속 재연결 5회 이상인 세션 존재
- 리더 선출 경합 빈번 (1분 내 10회 이상)
- 메모리 사용량 80% 초과

액션:
- 1시간 단위 집계 알림
- 대시보드 하이라이트


Info (로그만)
─────────────────────────────────
- 스케줄러 생성/종료
- 리더십 변경
- 구독자 수 변화 (임계치 초과 시)
```

### 8.4 대시보드 구성

```
Overview 패널
─────────────────────────────────
- 활성 세션 수 (실시간)
- 활성 구독 수 (실시간)
- 활성 스케줄러 수 (실시간)
- 전송 성공률 (5분 이동 평균)


Sessions 패널
─────────────────────────────────
- Transport별 세션 분포 (파이 차트)
- 세션 생성/종료 추이 (시계열)
- 세션 유지 시간 분포 (히스토그램)


Schedulers 패널
─────────────────────────────────
- 리소스별 스케줄러 수 (바 차트)
- 상태별 스케줄러 수 (파이 차트)
- 실행 성공/실패 추이 (시계열)
- 평균 실행 시간 (시계열)


Cluster 패널 (분산 모드)
─────────────────────────────────
- 서버별 세션 분포 (바 차트)
- 서버별 리더십 분포 (테이블)
- Redis 응답 시간 (시계열)
- 리더 선출 이벤트 (로그 뷰)
```

---

## 9. 설정

### 9.1 전체 설정 스키마

```yaml
simplix:
  stream:
    # 기능 활성화 여부
    enabled: true

    # 동작 모드: local | distributed
    mode: local

    # ─────────────────────────────────────
    # 세션 설정
    # ─────────────────────────────────────
    session:
      # SSE 연결 타임아웃 (0 = 무제한)
      timeout: 5m

      # 하트비트 전송 주기
      heartbeat-interval: 30s

      # 연결 끊김 후 유예 기간 (재연결 대기)
      grace-period: 30s

      # 비활성 세션 정리 주기
      cleanup-interval: 30s

      # 사용자당 최대 세션 수 (0 = 무제한)
      max-per-user: 5

    # ─────────────────────────────────────
    # 스케줄러 설정
    # ─────────────────────────────────────
    scheduler:
      # 스케줄러 스레드풀 크기
      thread-pool-size: 10

      # 기본 푸시 주기
      default-interval: 1000ms

      # 최소/최대 푸시 주기
      min-interval: 100ms
      max-interval: 60000ms

      # ERROR 상태 진입 기준 (연속 실패 횟수)
      max-consecutive-errors: 5

      # 최대 스케줄러 수 (0 = 무제한)
      max-total-schedulers: 500

    # ─────────────────────────────────────
    # 구독 설정
    # ─────────────────────────────────────
    subscription:
      # 세션당 최대 구독 수
      max-per-session: 20

      # 부분 성공 허용 여부
      # true: 권한 있는 것만 구독, 나머지 denied 반환
      # false: 하나라도 권한 없으면 전체 실패
      partial-success: true

    # ─────────────────────────────────────
    # 브로드캐스트 설정
    # ─────────────────────────────────────
    broadcast:
      # 전송 타임아웃
      timeout: 5s

      # 배치 전송 크기 (0 = 배치 비활성화)
      batch-size: 0

    # ─────────────────────────────────────
    # 분산 모드 설정 (mode: distributed일 때만 사용)
    # ─────────────────────────────────────
    distributed:
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD:}
        database: 0

        # 연결 풀 설정
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5

      # 리더 선출 설정
      leader-election:
        # 리더십 TTL
        ttl: 30s

        # 리더십 갱신 주기
        renew-interval: 10s

        # 선출 재시도 주기
        retry-interval: 5s

      # Pub/Sub 설정
      pubsub:
        # 채널 접두사
        channel-prefix: "stream:data:"

      # 구독 레지스트리 설정
      registry:
        # 키 접두사
        key-prefix: "stream:subs:"

        # 구독 정보 TTL (정리용)
        ttl: 1h

    # ─────────────────────────────────────
    # 모니터링 설정
    # ─────────────────────────────────────
    monitoring:
      # 메트릭 활성화
      metrics-enabled: true

      # 헬스체크 주기
      health-check-interval: 10s

    # ─────────────────────────────────────
    # 알림 설정
    # ─────────────────────────────────────
    alert:
      # ERROR 상태 알림 기준 시간
      error-state-threshold: 1h

      # 전송 성공률 알림 기준
      delivery-rate-threshold: 0.9

      # 세션 급감 알림 기준 (비율)
      session-drop-threshold: 0.5
```

### 9.2 환경별 설정 예시

```yaml
# application-local.yml (개발 환경)
simplix:
  stream:
    enabled: true
    mode: local
    session:
      timeout: 0  # 무제한 (디버깅 용이)
      grace-period: 5s
    scheduler:
      thread-pool-size: 4
    monitoring:
      metrics-enabled: false

---
# application-prod.yml (운영 환경)
simplix:
  stream:
    enabled: true
    mode: distributed
    session:
      timeout: 5m
      max-per-user: 10
    scheduler:
      thread-pool-size: 20
      max-total-schedulers: 1000
    distributed:
      redis:
        host: ${REDIS_CLUSTER_HOST}
        password: ${REDIS_PASSWORD}
    monitoring:
      metrics-enabled: true
```

---

## 10. 시나리오별 동작

### 10.1 기본 시나리오

```
시나리오 1: 단일 사용자 단일 구독
─────────────────────────────────
1. UserA 연결 → 세션 생성
2. cpu:srv-01 구독 → 스케줄러 생성, 구독자 1
3. 데이터 푸시 시작
4. UserA 페이지 이탈 → 구독 해제, 구독자 0
5. 스케줄러 종료


시나리오 2: 다중 사용자 동일 리소스
─────────────────────────────────
1. UserA가 cpu:srv-01 구독 → 스케줄러 생성
2. UserB가 cpu:srv-01 구독 → 스케줄러 재사용, 구독자 2
3. UserC가 cpu:srv-01 구독 → 스케줄러 재사용, 구독자 3
4. UserA 구독 해제 → 구독자 2, 스케줄러 유지
5. UserB 구독 해제 → 구독자 1, 스케줄러 유지
6. UserC 구독 해제 → 구독자 0, 스케줄러 종료


시나리오 3: 동일 리소스 다른 파라미터
─────────────────────────────────
1. UserA가 cpu:srv-01 구독 → 스케줄러 A 생성
2. UserB가 cpu:srv-02 구독 → 스케줄러 B 생성 (별도)
3. UserC가 cpu:srv-01 구독 → 스케줄러 A 재사용
4. 스케줄러 A와 B 독립 동작


시나리오 4: 페이지 전환
─────────────────────────────────
1. UserA가 PageA 진입 → cpu, memory 구독
2. UserA가 PageB로 이동
3. 클라이언트: cpu, memory 해제 + network, disk 구독
4. 서버: diff 계산 → cpu, memory 제거 / network, disk 추가
5. 구독자 없는 스케줄러 정리
```

### 10.2 장애 시나리오

```
시나리오 5: 클라이언트 비정상 종료
─────────────────────────────────
1. UserA 3개 리소스 구독 중
2. 브라우저 강제 종료
3. 서버: 연결 끊김 감지 → DISCONNECTED 상태
4. 30초 유예 기간 대기
5. 유예 기간 초과 → TERMINATED
6. 구독 정리, 세션 제거


시나리오 6: 클라이언트 재연결
─────────────────────────────────
1. UserA 연결 끊김 → DISCONNECTED
2. 15초 후 재연결 시도
3. 유예 기간 내 → CONNECTED 복원
4. 기존 구독 유지, 새 sessionId 발급
5. 클라이언트가 구독 목록 재전송 (동기화)


시나리오 7: 스케줄러 에러
─────────────────────────────────
1. cpu:srv-01 스케줄러 실행 중
2. DB 연결 실패로 데이터 수집 실패
3. consecutiveErrors++ (1회)
4. 다음 주기에 재시도
5. 5회 연속 실패 → ERROR 상태
6. ERROR 상태에서도 계속 시도
7. DB 복구 → 수집 성공 → RUNNING 상태 복귀


시나리오 8: 권한 없는 리소스 구독
─────────────────────────────────
1. UserA가 [cpu:srv-01, admin:logs] 구독 요청
2. cpu:srv-01 → 권한 OK → valid
3. admin:logs → 권한 FAIL → denied
4. Response: {active: [cpu], denied: [admin]}
5. cpu만 구독 처리


시나리오 9: 분산 환경 리더 장애 (Distributed 모드)
─────────────────────────────────
1. Server1이 cpu:srv-01 리더로 스케줄러 실행 중
2. Server1 다운
3. Redis 리더십 TTL 만료 (30초)
4. Server2가 리더 선출 시도 → 성공
5. Server2에서 스케줄러 시작
6. 최대 30초 데이터 푸시 중단 후 정상화
```

### 10.3 엣지 케이스

```
시나리오 10: 동시 구독 변경 (같은 세션)
─────────────────────────────────
1. UserA가 구독 변경 요청 A 전송
2. 네트워크 지연으로 요청 A 처리 중
3. UserA가 구독 변경 요청 B 전송
4. synchronized로 순차 처리
5. 요청 A 완료 → 요청 B 처리
6. 최종 상태는 요청 B 기준


시나리오 11: 최대 세션 수 초과
─────────────────────────────────
1. UserA가 이미 5개 탭 열어서 5개 세션 보유
2. 6번째 탭에서 연결 시도
3. 설정: max-per-user: 5
4. 거부 옵션 A: 연결 거부 + 에러 반환
5. 대체 옵션 B: 가장 오래된 세션 종료 + 새 연결 허용


시나리오 12: 존재하지 않는 리소스 구독
─────────────────────────────────
1. UserA가 unknown:xxx 구독 요청
2. DataCollectorRegistry에서 조회 실패
3. Response: {invalid: [{resource: unknown, reason: RESOURCE_NOT_FOUND}]}
4. 스케줄러 생성하지 않음


시나리오 13: interval 충돌
─────────────────────────────────
1. UserA가 cpu:srv-01, interval=1000ms 구독 → 스케줄러 생성
2. UserB가 cpu:srv-01, interval=500ms 구독
3. 정책: 첫 번째 구독의 interval 사용
4. UserB는 1000ms 주기로 데이터 수신
5. Response에 실제 적용된 intervalMs 포함
```

---

## 11. 확장 가이드

### 11.1 새 리소스 추가

```java
// 1. DataCollector 구현
@Component
public class OrderDataCollector implements DataCollector {

    private final OrderRepository orderRepository;

    @Override
    public String getResource() {
        return "orders";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String status = (String) params.getOrDefault("status", "all");
        LocalDate date = LocalDate.parse((String) params.get("date"));

        return orderRepository.findByStatusAndDate(status, date);
    }
}

// 2. ResourceAuthorizer 구현
@Component
public class OrderResourceAuthorizer implements ResourceAuthorizer {

    @Override
    public String getResource() {
        return "orders";
    }

    @Override
    public String getRequiredPermission() {
        return "VIEW_ORDERS";
    }

    @Override
    public boolean authorize(String userId, Map<String, Object> params) {
        // 모든 권한 있는 사용자 허용
        return true;
    }
}

// 끝! 자동으로 구독 가능해짐
```

### 11.2 커스텀 Transport 추가

```java
// 1. StreamSession 구현
public class GrpcStreamSession implements StreamSession {

    private final StreamObserver<StreamMessage> observer;

    @Override
    public TransportType getTransportType() {
        return TransportType.GRPC;  // enum에 추가 필요
    }

    @Override
    public void send(StreamMessage message) {
        observer.onNext(message);
    }

    // ... 나머지 구현
}

// 2. Controller/Handler 구현
@GrpcService
public class GrpcStreamService {

    private final SessionManager sessionManager;

    public void connect(StreamObserver<StreamMessage> observer) {
        GrpcStreamSession session = new GrpcStreamSession(userId, observer);
        sessionManager.register(session);
    }
}
```

### 11.3 커스텀 Broadcaster 추가

```java
// Kafka 기반 Broadcaster 예시
@Component
@ConditionalOnProperty(name = "simplix.stream.broadcast.type", havingValue = "kafka")
public class KafkaBroadcaster implements Broadcaster {

    private final KafkaTemplate<String, StreamMessage> kafkaTemplate;
    private final SessionManager sessionManager;

    @Override
    public void broadcast(String key, StreamMessage message) {
        // Kafka로 발행
        kafkaTemplate.send("stream-data", key, message);
    }

    @KafkaListener(topics = "stream-data")
    public void onMessage(StreamMessage message) {
        // 이 서버의 세션에만 전송
        // ...
    }
}
```

---

## 12. 체크리스트

### 12.1 구현 전 확인

- [ ] Spring Security 설정 완료
- [ ] Redis 연결 설정 (분산 모드 시)
- [ ] 메트릭 수집 인프라 (Prometheus/Micrometer)
- [ ] 로깅 설정 (구조화된 로그 권장)

### 12.2 기능 검증

- [ ] SSE 연결/해제 정상 동작
- [ ] WebSocket 연결/해제 정상 동작
- [ ] 구독 등록/해제 정상 동작
- [ ] 스케줄러 생성/종료 정상 동작
- [ ] 다중 사용자 동일 리소스 공유
- [ ] 권한 검증 동작
- [ ] 하트비트 동작
- [ ] 재연결 유예 기간 동작

### 12.3 부하 테스트

- [ ] 1000 동시 연결 처리
- [ ] 100 스케줄러 동시 실행
- [ ] 연결/해제 반복 (메모리 누수 확인)
- [ ] 장시간 연결 유지 (24시간+)

### 12.4 장애 테스트

- [ ] 클라이언트 비정상 종료 복구
- [ ] 서버 재시작 시 클라이언트 재연결
- [ ] Redis 연결 끊김/복구 (분산 모드)
- [ ] 리더 서버 다운 시 failover (분산 모드)

---

## 13. 모듈 정보

### 13.1 모듈 구조

```
simplix-stream/
├── src/main/java/io/github/simplixcore/stream/
│   ├── autoconfigure/           # Auto-configuration classes
│   ├── config/                  # Configuration properties
│   ├── core/                    # Core domain models
│   │   ├── session/             # Session management
│   │   ├── subscription/        # Subscription management
│   │   ├── scheduler/           # Scheduler management
│   │   └── broadcast/           # Broadcast service
│   ├── transport/               # Transport layer (SSE, WebSocket)
│   ├── security/                # Authorization components
│   ├── collector/               # Data collector interfaces
│   ├── infrastructure/          # Local/Distributed implementations
│   │   ├── local/               # In-memory implementations
│   │   └── distributed/         # Redis-based implementations
│   ├── admin/                   # Admin API controllers
│   └── monitoring/              # Health indicators, metrics
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 13.2 의존성

```
Required:
- simplix-core
- spring-boot-starter-web
- spring-boot-starter-webflux (for SSE)
- spring-boot-starter-security

Optional:
- spring-boot-starter-websocket (for WebSocket support)
- spring-boot-starter-data-redis (for distributed mode)
- micrometer-registry-prometheus (for metrics)
```