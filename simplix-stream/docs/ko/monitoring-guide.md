# 모니터링 가이드

SimpliX Stream 모듈의 헬스 체크 및 메트릭 모니터링 가이드입니다.

## 목차

1. [개요](#개요)
2. [헬스 체크](#헬스-체크)
3. [Micrometer 메트릭](#micrometer-메트릭)
4. [Prometheus 연동](#prometheus-연동)
5. [Grafana 대시보드](#grafana-대시보드)
6. [알림 설정](#알림-설정)

---

## 개요

SimpliX Stream은 Spring Boot Actuator와 Micrometer를 통해 모니터링 기능을 제공합니다.

### 설정

**application.yml:**

```yaml
simplix:
  stream:
    enabled: true
    monitoring:
      metrics-enabled: true              # 메트릭 활성화
      metrics-prefix: simplix.stream     # 메트릭 이름 프리픽스
      health-check-interval: 10s         # 헬스 체크 주기

# Actuator 설정
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

### 의존성

**build.gradle:**

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Prometheus 연동 시
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

---

## 헬스 체크

### 엔드포인트

```
GET /actuator/health
```

### 응답 예시

```json
{
  "status": "UP",
  "components": {
    "stream": {
      "status": "UP",
      "details": {
        "mode": "LOCAL",
        "sessionRegistry": "UP",
        "broadcastService": "UP",
        "activeSessions": 150,
        "activeSchedulers": 45,
        "schedulerUtilization": "9.0%"
      }
    },
    "db": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    }
  }
}
```

### 헬스 상태 판정

| 구성요소 | UP 조건 |
|----------|---------|
| sessionRegistry | Redis/메모리 저장소 가용 |
| broadcastService | 브로드캐스트 서비스 가용 |

### 장애 시 응답

```json
{
  "status": "DOWN",
  "components": {
    "stream": {
      "status": "DOWN",
      "details": {
        "mode": "DISTRIBUTED",
        "sessionRegistry": "DOWN",
        "broadcastService": "UP",
        "error": "Redis connection failed"
      }
    }
  }
}
```

### 경고 상태

스케줄러 사용률이 90%를 초과하면 경고가 표시됩니다:

```json
{
  "status": "UP",
  "components": {
    "stream": {
      "status": "UP",
      "details": {
        "schedulerUtilization": "92.0%",
        "schedulerWarning": "Scheduler limit nearly reached"
      }
    }
  }
}
```

---

## Micrometer 메트릭

### 사용 가능한 메트릭

| 메트릭 이름 | 타입 | 설명 |
|-------------|------|------|
| `simplix.stream.sessions.active` | Gauge | 현재 활성 세션 수 |
| `simplix.stream.schedulers.active` | Gauge | 현재 활성 스케줄러 수 |
| `simplix.stream.messages.delivered` | Counter | 전달 성공한 메시지 수 |
| `simplix.stream.messages.failed` | Counter | 전달 실패한 메시지 수 |
| `simplix.stream.connections.established` | Counter | 수립된 연결 수 |
| `simplix.stream.connections.closed` | Counter | 종료된 연결 수 |
| `simplix.stream.subscriptions.added` | Counter | 추가된 구독 수 |
| `simplix.stream.subscriptions.removed` | Counter | 제거된 구독 수 |

### 메트릭 조회

```bash
# 모든 메트릭 목록
curl http://localhost:8080/actuator/metrics

# 특정 메트릭 조회
curl http://localhost:8080/actuator/metrics/simplix.stream.sessions.active
```

**응답:**

```json
{
  "name": "simplix.stream.sessions.active",
  "description": "Number of active stream sessions",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 150.0
    }
  ],
  "availableTags": []
}
```

---

## Prometheus 연동

### 엔드포인트

```
GET /actuator/prometheus
```

### 메트릭 형식

```prometheus
# HELP simplix_stream_sessions_active Number of active stream sessions
# TYPE simplix_stream_sessions_active gauge
simplix_stream_sessions_active 150.0

# HELP simplix_stream_schedulers_active Number of active schedulers
# TYPE simplix_stream_schedulers_active gauge
simplix_stream_schedulers_active 45.0

# HELP simplix_stream_messages_delivered_total Total messages delivered
# TYPE simplix_stream_messages_delivered_total counter
simplix_stream_messages_delivered_total 125340.0

# HELP simplix_stream_messages_failed_total Total messages failed to deliver
# TYPE simplix_stream_messages_failed_total counter
simplix_stream_messages_failed_total 23.0
```

### Prometheus 설정

**prometheus.yml:**

```yaml
scrape_configs:
  - job_name: 'stream-service'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets:
          - 'instance1:8080'
          - 'instance2:8080'
          - 'instance3:8080'
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
```

---

## Grafana 대시보드

### 주요 패널

#### 1. 세션 현황

```promql
# 총 활성 세션
sum(simplix_stream_sessions_active)

# 인스턴스별 세션
simplix_stream_sessions_active by (instance)
```

#### 2. 스케줄러 현황

```promql
# 활성 스케줄러 수
sum(simplix_stream_schedulers_active)

# 스케줄러 사용률 (max가 500인 경우)
sum(simplix_stream_schedulers_active) / 500 * 100
```

#### 3. 메시지 처리율

```promql
# 초당 메시지 전송률
rate(simplix_stream_messages_delivered_total[5m])

# 초당 메시지 실패율
rate(simplix_stream_messages_failed_total[5m])

# 메시지 성공률
1 - (rate(simplix_stream_messages_failed_total[5m]) /
     rate(simplix_stream_messages_delivered_total[5m]))
```

#### 4. 연결 현황

```promql
# 초당 새 연결
rate(simplix_stream_connections_established_total[5m])

# 초당 연결 종료
rate(simplix_stream_connections_closed_total[5m])

# 연결 유지율 (세션 대비 종료)
1 - (rate(simplix_stream_connections_closed_total[5m]) /
     rate(simplix_stream_connections_established_total[5m]))
```

### 대시보드 JSON

```json
{
  "title": "SimpliX Stream Dashboard",
  "panels": [
    {
      "title": "Active Sessions",
      "type": "stat",
      "targets": [
        {
          "expr": "sum(simplix_stream_sessions_active)",
          "legendFormat": "Sessions"
        }
      ]
    },
    {
      "title": "Sessions by Instance",
      "type": "timeseries",
      "targets": [
        {
          "expr": "simplix_stream_sessions_active",
          "legendFormat": "{{instance}}"
        }
      ]
    },
    {
      "title": "Message Delivery Rate",
      "type": "timeseries",
      "targets": [
        {
          "expr": "rate(simplix_stream_messages_delivered_total[5m])",
          "legendFormat": "Delivered/s"
        },
        {
          "expr": "rate(simplix_stream_messages_failed_total[5m])",
          "legendFormat": "Failed/s"
        }
      ]
    },
    {
      "title": "Scheduler Utilization",
      "type": "gauge",
      "targets": [
        {
          "expr": "sum(simplix_stream_schedulers_active) / 500 * 100",
          "legendFormat": "Utilization %"
        }
      ],
      "options": {
        "thresholds": {
          "mode": "absolute",
          "steps": [
            {"color": "green", "value": null},
            {"color": "yellow", "value": 70},
            {"color": "red", "value": 90}
          ]
        }
      }
    }
  ]
}
```

---

## 알림 설정

### Prometheus Alerting Rules

**stream-alerts.yml:**

```yaml
groups:
  - name: stream-alerts
    rules:
      # 세션 급감 경고
      - alert: StreamSessionsDrop
        expr: |
          sum(simplix_stream_sessions_active) <
          sum(simplix_stream_sessions_active offset 5m) * 0.5
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Stream sessions dropped significantly"
          description: "Sessions dropped by more than 50% in 5 minutes"

      # 메시지 실패율 경고
      - alert: StreamMessageFailureHigh
        expr: |
          rate(simplix_stream_messages_failed_total[5m]) /
          rate(simplix_stream_messages_delivered_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High message failure rate"
          description: "Message failure rate is above 5%"

      # 스케줄러 한도 임박
      - alert: StreamSchedulerLimitNear
        expr: |
          sum(simplix_stream_schedulers_active) / 500 > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Scheduler limit nearly reached"
          description: "Scheduler utilization is above 90%"

      # 세션 레지스트리 다운
      - alert: StreamRegistryDown
        expr: |
          up{job="stream-service"} == 0 or
          absent(simplix_stream_sessions_active)
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Stream registry is down"
          description: "Session registry is not responding"

      # 인스턴스 불균형
      - alert: StreamInstanceImbalance
        expr: |
          max(simplix_stream_sessions_active) >
          avg(simplix_stream_sessions_active) * 2
        for: 10m
        labels:
          severity: info
        annotations:
          summary: "Session distribution imbalanced"
          description: "One instance has more than twice the average sessions"
```

### Alertmanager 설정

**alertmanager.yml:**

```yaml
route:
  receiver: 'stream-team'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
    - match:
        severity: warning
      receiver: 'slack'

receivers:
  - name: 'stream-team'
    email_configs:
      - to: 'stream-team@example.com'

  - name: 'slack'
    slack_configs:
      - channel: '#stream-alerts'
        send_resolved: true

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: 'your-pagerduty-key'
```

---

## 운영 체크리스트

### 일일 모니터링

- [ ] 활성 세션 수 확인
- [ ] 스케줄러 사용률 확인
- [ ] 메시지 실패율 확인
- [ ] 헬스 체크 상태 확인

### 주간 점검

- [ ] 인스턴스별 세션 분포 확인
- [ ] 메시지 처리량 추이 확인
- [ ] 에러 로그 검토
- [ ] 스케줄러 재시작 필요 여부 확인

### 이상 징후

| 현상 | 가능한 원인 | 조치 |
|------|-------------|------|
| 세션 급감 | 인스턴스 다운, 네트워크 이슈 | 인스턴스 상태 확인, 로드밸런서 확인 |
| 메시지 실패 증가 | 클라이언트 연결 끊김, 리소스 부족 | 연결 상태 확인, 리소스 모니터링 |
| 스케줄러 급증 | 새 구독 폭증, 리소스 누수 | 구독 패턴 분석, 메모리 확인 |
| 헬스 체크 실패 | Redis/DB 연결 실패 | 인프라 상태 확인 |

---

## 다음 단계

- [Admin API 가이드](./admin-api-guide.md) - 관리 API 사용법
- [클라이언트 가이드](./client-javascript-guide.md) - 클라이언트 연동
