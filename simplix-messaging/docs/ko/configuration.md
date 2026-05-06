# SimpliX Messaging Configuration Reference

SimpliX Messaging의 전체 설정 옵션을 정리한 레퍼런스입니다. `simplix.messaging` 프리픽스 아래 구조화되어 있습니다.

## Quick Reference

```yaml
simplix:
  messaging:
    broker: nats
    instance-id: app-1
    error:
      max-retries: 3
      retry-backoff: 1s
```

---

## Configuration Sections

### Core Settings

```yaml
simplix:
  messaging:
    broker: nats
    instance-id: app-1
    subscriber-startup-delay: 5s
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.messaging.broker` | enum | `local` | 활성 브로커 (`local`/`redis`/`nats`/`kafka`/`rabbit`) |
| `simplix.messaging.instance-id` | String | hostname | 컨슈머 식별자. 기본값은 호스트명, 실패 시 `simplix-<pid>` |
| `simplix.messaging.subscriber-startup-delay` | Duration | `0s` | 구독 시작 지연. SSE 등 다운스트림이 연결될 시간 확보 |

---

### Publisher

```yaml
simplix:
  messaging:
    publisher:
      auto-message-id: true
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.messaging.publisher.auto-message-id` | boolean | `false` | 호출자가 ID를 지정하지 않은 경우 UUID v4 자동 부여. NATS publish-time dedup과 함께 사용할 때 권장 |

---

### Per-Channel Configuration

```yaml
simplix:
  messaging:
    channels:
      order-events:
        content-type: application/protobuf
        max-length: 100000
        duplicate-window: 5m
        deliver-policy: all
      audit-events:
        content-type: application/json
        max-length: 1000000
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `channels.<name>.content-type` | String | `application/json` | 메시지 콘텐츠 타입 |
| `channels.<name>.max-length` | long | `50000` | 스트림 최대 보존 메시지 수 (Redis MAXLEN ~ / NATS max-msgs) |
| `channels.<name>.duplicate-window` | Duration | (글로벌) | NATS JetStream duplicate window 채널별 오버라이드 |
| `channels.<name>.deliver-policy` | String | (글로벌) | NATS deliver policy: `all`/`new`/`last`/`last_per_subject` |

---

### Idempotent Guard

```yaml
simplix:
  messaging:
    idempotent:
      ttl: 48h
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.messaging.idempotent.ttl` | Duration | `24h` | 처리된 메시지 ID 보관 기간 |

`@MessageHandler(idempotent=true)`인 핸들러에 대해 `IdempotencyStore`에 처리 완료 ID를 TTL과 함께 기록합니다.

---

### Error Handling

```yaml
simplix:
  messaging:
    error:
      max-retries: 5
      retry-backoff: 2s
      dead-letter:
        enabled: true
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.messaging.error.max-retries` | int | `3` | 재시도 최대 횟수 |
| `simplix.messaging.error.retry-backoff` | Duration | `1s` | 초기 백오프 |
| `simplix.messaging.error.dead-letter.enabled` | boolean | `false` | DLQ 라우팅 활성화 |

재시도는 지수 백오프(multiplier 2.0, jitter 10%)로 진행되며 최대 30초까지 증가합니다. `max-retries`를 초과하면 DLQ가 활성화된 경우 routing되고, 아니면 ERROR 로그 후 ack됩니다.

---

### Redis Streams

```yaml
simplix:
  messaging:
    broker: redis
    redis:
      key-prefix: "pacs:"
      poll-timeout: 3s
      batch-size: 20
      pending-check-interval: 30s
      claim-min-idle-time: 5m
      payload-encoding: BASE64
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.messaging.redis.key-prefix` | String | `""` | Redis 키 프리픽스 (예: `pacs:` → `pacs:order-events`) |
| `simplix.messaging.redis.poll-timeout` | Duration | `2s` | XREADGROUP 블로킹 시간 |
| `simplix.messaging.redis.batch-size` | int | `10` | 폴링 사이클당 최대 메시지 |
| `simplix.messaging.redis.pending-check-interval` | Duration | `30s` | 미확인 메시지 체크 주기 |
| `simplix.messaging.redis.claim-min-idle-time` | Duration | `5m` | 다른 컨슈머가 claim 가능한 최소 idle 시간 |
| `simplix.messaging.redis.payload-encoding` | enum | `BASE64` | `BASE64` 또는 `RAW`. RAW는 Redis 클라이언트에서 protobuf viewer 활용 가능 |

---

### NATS JetStream

```yaml
simplix:
  messaging:
    broker: nats
    nats:
      servers: "nats://nats-1:4222,nats://nats-2:4222"
      username: app
      password: secret
      connection-name: simplix-app
      connection-timeout: 5s
      reconnect-wait: 2s
      max-reconnects: -1

      stream-prefix: "myapp-"
      subject-prefix: "myapp."

      auto-create-streams: true
      auto-update-streams: true

      ack-policy: explicit
      deliver-policy: all
      retention: limits
      storage: file
      discard-policy: old
      max-msgs: -1
      max-age: 7d
      max-bytes: -1
      duplicate-window: 2m
      replicas: 3

      poll-timeout: 2s
      batch-size: 10
      pending-check-interval: 30s

      tls:
        enabled: false
        trust-store: ""
        key-store: ""

      scheduler:
        enabled: true
        kv-bucket: simplix-scheduled
        poll-interval: 5s
        leader-lock-ttl: 30s
```

#### Connection

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.servers` | String | `nats://localhost:4222` | NATS 서버 URL (콤마 구분) |
| `nats.username` | String | `""` | 사용자명 |
| `nats.password` | String | `""` | 비밀번호 |
| `nats.token` | String | `""` | 인증 토큰 |
| `nats.creds-file` | String | `""` | NATS 자격증명 파일 경로 |
| `nats.nkey-file` | String | `""` | NKey 파일 경로 |
| `nats.connection-name` | String | `simplix-messaging` | 연결 이름 |
| `nats.connection-timeout` | Duration | `5s` | 연결 타임아웃 |
| `nats.reconnect-wait` | Duration | `2s` | 재연결 대기 시간 |
| `nats.max-reconnects` | int | `-1` | 재연결 시도 (`-1`은 무한) |

#### Stream / Subject

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.stream-prefix` | String | `simplix-` | Stream 이름 프리픽스 |
| `nats.subject-prefix` | String | `simplix.` | Subject 프리픽스 |
| `nats.auto-create-streams` | boolean | `true` | 첫 사용 시 stream 자동 생성 |
| `nats.auto-update-streams` | boolean | `true` | 기존 stream 설정 자동 업데이트. 외부(IaC)가 stream을 관리하면 `false` |

#### Consumer / Delivery

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.ack-policy` | String | `explicit` | ACK 정책 |
| `nats.ack-wait` | Duration | `30s` | ACK 미수신 시 재전달 대기 시간 |
| `nats.max-deliver` | int | `max-retries+1` | 최대 전달 시도 |
| `nats.deliver-policy` | String | `all` | `all`/`new`/`last`/`last_per_subject` |

#### Stream Storage

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.retention` | String | `limits` | 보존 정책 (limits/interest/work-queue) |
| `nats.storage` | String | `file` | 저장 방식 (file/memory) |
| `nats.discard-policy` | String | `old` | 한도 도달 시 폐기 정책 (old/new) |
| `nats.max-msgs` | long | `-1` | 최대 메시지 수 (`-1`은 무제한) |
| `nats.max-age` | Duration | `7d` | 메시지 보관 기간 |
| `nats.max-bytes` | long | `-1` | 최대 byte (`-1`은 무제한) |
| `nats.duplicate-window` | Duration | `2m` | publish dedup 윈도우 |
| `nats.replicas` | int | `1` | 클러스터 replica 수 |

#### Subscriber Polling

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.poll-timeout` | Duration | `2s` | Pull 폴링 타임아웃 |
| `nats.batch-size` | int | `10` | 배치 크기 |
| `nats.pending-check-interval` | Duration | `30s` | pending 체크 주기 |

#### NATS KV Scheduler

> ⚠ KV 스케줄러는 NATS 사용자에게 KV 권한(`$JS.API.STREAM.INFO.KV_<bucket>`, `$KV.<bucket>.>`)이 필요합니다. 권한이 없으면 `enabled: false`로 비활성화하세요.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.scheduler.enabled` | boolean | `true` | KV 기반 `MessageScheduler` 활성화 |
| `nats.scheduler.kv-bucket` | String | `simplix-scheduled` | KV 버킷 이름 |
| `nats.scheduler.poll-interval` | Duration | `5s` | 만료 메시지 폴링 주기 |
| `nats.scheduler.leader-lock-ttl` | Duration | `30s` | 리더 락 TTL |

#### TLS

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `nats.tls.enabled` | boolean | `false` | TLS 활성화 |
| `nats.tls.trust-store` | String | `""` | 트러스트스토어 경로 |
| `nats.tls.trust-store-password` | String | `""` | 트러스트스토어 비밀번호 |
| `nats.tls.key-store` | String | `""` | 키스토어 경로 |
| `nats.tls.key-store-password` | String | `""` | 키스토어 비밀번호 |

---

## Environment Variables

대표 속성에 대한 환경 변수 매핑입니다. Spring Boot가 자동 변환하므로 `simplix.messaging.broker`는 `SIMPLIX_MESSAGING_BROKER`로 설정할 수 있습니다.

| Property | Environment Variable |
|----------|---------------------|
| `simplix.messaging.broker` | `SIMPLIX_MESSAGING_BROKER` |
| `simplix.messaging.instance-id` | `SIMPLIX_MESSAGING_INSTANCE_ID` |
| `simplix.messaging.nats.servers` | `SIMPLIX_MESSAGING_NATS_SERVERS` |
| `simplix.messaging.nats.username` | `SIMPLIX_MESSAGING_NATS_USERNAME` |
| `simplix.messaging.nats.password` | `SIMPLIX_MESSAGING_NATS_PASSWORD` |
| `simplix.messaging.error.max-retries` | `SIMPLIX_MESSAGING_ERROR_MAX_RETRIES` |

---

## Configuration Profiles

### Development

```yaml
simplix:
  messaging:
    broker: local
    error:
      max-retries: 1
    idempotent:
      ttl: 5m
```

### Production (NATS)

```yaml
simplix:
  messaging:
    broker: nats
    instance-id: ${HOSTNAME}
    subscriber-startup-delay: 10s
    publisher:
      auto-message-id: true
    nats:
      servers: ${NATS_URL}
      replicas: 3
      max-age: 30d
      duplicate-window: 5m
    error:
      max-retries: 5
      dead-letter:
        enabled: true
    idempotent:
      ttl: 48h
```

### Production (Redis)

```yaml
simplix:
  messaging:
    broker: redis
    redis:
      key-prefix: "${spring.application.name}:"
      batch-size: 50
      claim-min-idle-time: 10m
    error:
      max-retries: 5
      dead-letter:
        enabled: true
```

---

## Related Documents

- [Overview](./overview.md) - 모듈 개요 및 아키텍처
- [Broker Guide](./broker-guide.md) - 브로커별 상세 사용법
