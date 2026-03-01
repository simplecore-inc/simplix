# Admin API 가이드

SimpliX Stream 모듈의 관리 API 상세 문서입니다.

## 목차

1. [개요](#개요)
2. [인증 설정](#인증-설정)
3. [통계 API](#통계-api)
4. [세션 관리 API](#세션-관리-api)
5. [스케줄러 관리 API](#스케줄러-관리-api)
6. [분산 명령 API](#분산-명령-api)
7. [응답 형식](#응답-형식)

---

## 개요

Admin API는 스트리밍 시스템의 세션, 스케줄러, 전체 상태를 모니터링하고 관리합니다.

### 기본 경로

```
/api/stream/admin
```

### 운영 모드별 동작

| 모드 | 제어 명령 동작 |
|------|----------------|
| 단독 모드 (local) | 즉시 실행, 204 No Content 반환 |
| 분산 + DB Admin | DB에 큐잉, 202 Accepted 반환 |
| 분산 + Redis | 즉시 실행, 204 No Content 반환 |

---

## 인증 설정

### Spring Security 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/stream/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/stream/**").authenticated()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

### 요청 예시

```bash
curl -X GET "http://localhost:8080/api/stream/admin/stats" \
  -H "Authorization: Basic $(echo -n admin:password | base64)"
```

---

## 통계 API

### 전체 통계 조회

```
GET /api/stream/admin/stats
```

**응답:**

```json
{
  "activeSessions": 150,
  "activeSchedulers": 45,
  "totalSubscriptions": 320,
  "mode": "LOCAL",
  "sessionRegistryAvailable": true,
  "broadcastServiceAvailable": true,
  "serverStartedAt": "2024-01-15T10:00:00Z",
  "instanceId": "instance-1",
  "distributedAdminEnabled": false
}
```

**필드 설명:**

| 필드 | 타입 | 설명 |
|------|------|------|
| activeSessions | long | 현재 활성 세션 수 |
| activeSchedulers | int | 실행 중인 스케줄러 수 |
| totalSubscriptions | long | 전체 구독 수 |
| mode | string | 운영 모드 (LOCAL/DISTRIBUTED) |
| sessionRegistryAvailable | boolean | 세션 저장소 가용 여부 |
| broadcastServiceAvailable | boolean | 브로드캐스트 서비스 가용 여부 |
| serverStartedAt | ISO8601 | 서버 시작 시간 |
| instanceId | string | 인스턴스 ID |
| distributedAdminEnabled | boolean | DB Admin 모드 활성화 여부 |

---

## 세션 관리 API

### 모든 세션 조회

```
GET /api/stream/admin/sessions
```

**응답:**

```json
[
  {
    "sessionId": "abc123",
    "userId": "user1",
    "transportType": "SSE",
    "state": "CONNECTED",
    "connectedAt": "2024-01-15T10:30:00Z",
    "lastActiveAt": "2024-01-15T10:35:00Z",
    "subscriptions": [
      "stock-price:symbol=AAPL",
      "stock-price:symbol=GOOG"
    ],
    "subscriptionCount": 2,
    "instanceId": "instance-1"
  }
]
```

### 특정 세션 조회

```
GET /api/stream/admin/sessions/{sessionId}
```

**예시:**

```bash
curl "http://localhost:8080/api/stream/admin/sessions/abc123" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답:** (200 OK 또는 404 Not Found)

```json
{
  "sessionId": "abc123",
  "userId": "user1",
  "transportType": "SSE",
  "state": "CONNECTED",
  "connectedAt": "2024-01-15T10:30:00Z",
  "lastActiveAt": "2024-01-15T10:35:00Z",
  "subscriptions": ["stock-price:symbol=AAPL"],
  "subscriptionCount": 1,
  "instanceId": "instance-1"
}
```

### 사용자별 세션 조회

```
GET /api/stream/admin/sessions/user/{userId}
```

**예시:**

```bash
curl "http://localhost:8080/api/stream/admin/sessions/user/user1" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답:**

```json
[
  {
    "sessionId": "abc123",
    "userId": "user1",
    "transportType": "SSE",
    "state": "CONNECTED",
    ...
  },
  {
    "sessionId": "def456",
    "userId": "user1",
    "transportType": "WEBSOCKET",
    "state": "CONNECTED",
    ...
  }
]
```

### 세션 강제 종료

```
DELETE /api/stream/admin/sessions/{sessionId}
```

**예시:**

```bash
curl -X DELETE "http://localhost:8080/api/stream/admin/sessions/abc123" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답 (단독 모드):** 204 No Content

**응답 (DB Admin 모드):** 202 Accepted

```json
{
  "commandId": 123,
  "commandType": "TERMINATE_SESSION",
  "targetId": "abc123",
  "status": "PENDING",
  "message": "Command queued for execution",
  "estimatedExecutionTime": "2-5 seconds"
}
```

### 세션 상태

| 상태 | 설명 |
|------|------|
| CONNECTED | 활성 연결 상태 |
| DISCONNECTED | 일시 연결 끊김 (grace period 중) |
| TERMINATED | 완전 종료 |

---

## 스케줄러 관리 API

### 모든 스케줄러 조회

```
GET /api/stream/admin/schedulers
```

**응답:**

```json
[
  {
    "subscriptionKey": "stock-price:symbol=AAPL",
    "resource": "stock-price",
    "params": {
      "symbol": "AAPL"
    },
    "state": "RUNNING",
    "intervalMs": 1000,
    "subscribers": ["session1", "session2", "session3"],
    "subscriberCount": 3,
    "createdAt": "2024-01-15T10:30:00Z",
    "lastExecutedAt": "2024-01-15T10:35:30Z",
    "executionCount": 330,
    "errorCount": 0,
    "consecutiveErrors": 0,
    "instanceId": "instance-1"
  }
]
```

### 특정 스케줄러 조회

```
GET /api/stream/admin/schedulers/{subscriptionKey}
```

**예시:**

```bash
# URL 인코딩 필요
curl "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답:** (200 OK 또는 404 Not Found)

### 스케줄러 정지

```
DELETE /api/stream/admin/schedulers/{subscriptionKey}
```

**예시:**

```bash
curl -X DELETE "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답 (단독 모드):** 204 No Content

**응답 (DB Admin 모드):** 202 Accepted

```json
{
  "commandId": 124,
  "commandType": "STOP_SCHEDULER",
  "targetId": "stock-price:symbol=AAPL",
  "status": "PENDING",
  "message": "Command queued for execution"
}
```

### 스케줄러 즉시 실행

```
POST /api/stream/admin/schedulers/{subscriptionKey}/trigger
```

**예시:**

```bash
curl -X POST "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL/trigger" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**설명:** 다음 스케줄 주기를 기다리지 않고 즉시 데이터를 수집하여 구독자에게 전송합니다.

### 스케줄러 상태

| 상태 | 설명 |
|------|------|
| RUNNING | 정상 실행 중 |
| PAUSED | 일시 중지 |
| ERROR | 오류 상태 (연속 오류 임계치 초과) |
| STOPPED | 완전 정지 |

---

## 분산 명령 API

DB Admin 모드에서만 사용 가능합니다.

### 명령 상태 조회

```
GET /api/stream/admin/commands/{commandId}
```

**예시:**

```bash
curl "http://localhost:8080/api/stream/admin/commands/123" \
  -H "Authorization: Basic YWRtaW46cGFzc3dvcmQ="
```

**응답:**

```json
{
  "commandId": 123,
  "commandType": "TERMINATE_SESSION",
  "targetId": "abc123",
  "targetInstanceId": null,
  "status": "EXECUTED",
  "createdAt": "2024-01-15T10:35:00Z",
  "executedAt": "2024-01-15T10:35:02Z",
  "executedBy": "instance-2",
  "errorMessage": null
}
```

### 대기 중인 명령 조회

```
GET /api/stream/admin/commands/pending
```

**응답:**

```json
[
  {
    "commandId": 125,
    "commandType": "STOP_SCHEDULER",
    "targetId": "notifications:userId=user1",
    "status": "PENDING",
    "createdAt": "2024-01-15T10:36:00Z"
  }
]
```

### 명령 상태

| 상태 | 설명 |
|------|------|
| PENDING | 대기 중 (아직 처리되지 않음) |
| EXECUTED | 정상 실행 완료 |
| FAILED | 실행 실패 |
| NOT_FOUND | 대상을 찾을 수 없음 |
| EXPIRED | 타임아웃 (command-timeout 초과) |

---

## 응답 형식

### 성공 응답

| HTTP 상태 | 의미 |
|----------|------|
| 200 OK | 조회 성공 |
| 202 Accepted | 명령이 큐에 등록됨 (DB Admin 모드) |
| 204 No Content | 제어 명령 즉시 실행 완료 |

### 오류 응답

| HTTP 상태 | 의미 |
|----------|------|
| 401 Unauthorized | 인증 필요 |
| 403 Forbidden | 권한 없음 |
| 404 Not Found | 리소스를 찾을 수 없음 |
| 500 Internal Server Error | 서버 오류 |

### 오류 응답 형식

```json
{
  "timestamp": "2024-01-15T10:40:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Session not found: abc123",
  "path": "/api/stream/admin/sessions/abc123"
}
```

---

## 사용 시나리오

### 문제 있는 세션 강제 종료

```bash
# 1. 사용자 세션 조회
curl "http://localhost:8080/api/stream/admin/sessions/user/problem-user"

# 2. 세션 강제 종료
curl -X DELETE "http://localhost:8080/api/stream/admin/sessions/session-id"

# 3. (DB Admin 모드) 명령 상태 확인
curl "http://localhost:8080/api/stream/admin/commands/123"
```

### 스케줄러 디버깅

```bash
# 1. 스케줄러 상태 확인
curl "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL"

# 2. 즉시 실행으로 데이터 확인
curl -X POST "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL/trigger"

# 3. 문제 시 스케줄러 재시작
curl -X DELETE "http://localhost:8080/api/stream/admin/schedulers/stock-price%3Asymbol%3DAAPL"
```

### 시스템 상태 모니터링

```bash
# 전체 통계
curl "http://localhost:8080/api/stream/admin/stats"

# 활성 세션 수 확인
curl "http://localhost:8080/api/stream/admin/sessions" | jq 'length'

# 실행 중인 스케줄러 수 확인
curl "http://localhost:8080/api/stream/admin/schedulers" | jq 'length'
```

---

## 다음 단계

- [모니터링 가이드](ko/stream/monitoring-guide.md) - 메트릭 및 헬스 체크
- [SSE 분산 모드 (DB Admin)](ko/stream/tutorial-sse-distributed-db.md) - 분산 Admin 상세
