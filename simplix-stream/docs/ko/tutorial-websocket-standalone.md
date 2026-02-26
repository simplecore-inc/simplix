# WebSocket 단독 모드 튜토리얼

단일 서버 환경에서 WebSocket (STOMP)을 사용한 실시간 스트리밍 구현 가이드입니다.

## 목차

1. [SSE vs WebSocket](#sse-vs-websocket)
2. [사전 요구사항](#사전-요구사항)
3. [서버 설정](#서버-설정)
4. [SimpliXStreamDataCollector 구현](#datacollector-구현)
5. [클라이언트 구현](#클라이언트-구현)
6. [테스트 및 검증](#테스트-및-검증)

---

## SSE vs WebSocket

| 특성 | SSE | WebSocket |
|------|-----|-----------|
| 연결 방향 | 단방향 (서버 → 클라이언트) | 양방향 |
| 프로토콜 | HTTP | WebSocket (ws://, wss://) |
| 자동 재연결 | 브라우저 내장 지원 | 직접 구현 필요 |
| 바이너리 데이터 | 미지원 (텍스트만) | 지원 |
| 브라우저 호환성 | IE 미지원 | 대부분 지원 |
| 프록시 호환성 | HTTP 기반으로 우수 | 추가 설정 필요할 수 있음 |

### WebSocket 선택 기준

- 클라이언트에서 서버로 메시지를 자주 보내야 할 때
- 바이너리 데이터 전송이 필요할 때
- 양방향 실시간 통신이 필요할 때 (채팅, 게임 등)

---

## 사전 요구사항

- Java 17+
- Spring Boot 3.5+
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
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // WebSocket 의존성
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}
```

### 2. 애플리케이션 설정

**application.yml:**

```yaml
simplix:
  stream:
    enabled: true
    mode: local

    # WebSocket 활성화
    websocket:
      enabled: true
      endpoint: /ws/stream       # WebSocket 엔드포인트
      allowed-origins: "*"       # CORS 설정
      sockjs-enabled: true       # SockJS 폴백 활성화

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
      max-consecutive-errors: 5

    subscription:
      max-per-session: 20
      partial-success: true

# Spring Security 기본 설정
spring:
  security:
    user:
      name: user
      password: password
```

### 3. Security 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**").permitAll()  // WebSocket 엔드포인트
                .requestMatchers("/api/stream/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

### 4. STOMP 메시지 브로커 구성

SimpliX Stream이 자동으로 구성하지만, 커스터마이징이 필요하면 직접 설정합니다:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 대상 프리픽스
        registry.enableSimpleBroker("/queue", "/topic");

        // 메시지 전송 대상 프리픽스
        registry.setApplicationDestinationPrefixes("/app");

        // 사용자별 대상 프리픽스
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stream")
                .setAllowedOriginPatterns("*")
                .withSockJS();  // SockJS 폴백

        registry.addEndpoint("/ws/stream")
                .setAllowedOriginPatterns("*");  // 순수 WebSocket
    }
}
```

---

## SimpliXStreamDataCollector 구현

SSE와 동일하게 SimpliXStreamDataCollector를 구현합니다.

### 예제 1: 실시간 채팅 데이터 수집기

```java
@Component
public class ChatMessageCollector implements SimpliXStreamDataCollector {

    private final ChatService chatService;

    public ChatMessageCollector(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public String getResource() {
        return "chat-messages";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String roomId = (String) params.get("roomId");
        Long lastMessageId = ((Number) params.getOrDefault("lastMessageId", 0L)).longValue();

        List<ChatMessage> newMessages = chatService.getNewMessages(roomId, lastMessageId);

        if (newMessages.isEmpty()) {
            return null;  // 새 메시지 없으면 전송 안 함
        }

        return Map.of(
            "roomId", roomId,
            "messages", newMessages.stream()
                .map(msg -> Map.of(
                    "id", msg.getId(),
                    "sender", msg.getSender(),
                    "content", msg.getContent(),
                    "timestamp", msg.getCreatedAt().toEpochMilli()
                ))
                .toList(),
            "lastMessageId", newMessages.get(newMessages.size() - 1).getId()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 500L;  // 빠른 폴링
    }

    @Override
    public boolean validateParams(Map<String, Object> params) {
        String roomId = (String) params.get("roomId");
        return roomId != null && !roomId.isBlank();
    }
}
```

### 예제 2: 게임 상태 수집기

```java
@Component
public class GameStateCollector implements SimpliXStreamDataCollector {

    private final GameService gameService;

    public GameStateCollector(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public String getResource() {
        return "game-state";
    }

    @Override
    public Object collect(Map<String, Object> params) {
        String gameId = (String) params.get("gameId");

        GameState state = gameService.getGameState(gameId);

        return Map.of(
            "gameId", gameId,
            "phase", state.getPhase(),
            "players", state.getPlayers(),
            "scores", state.getScores(),
            "timeRemaining", state.getTimeRemaining(),
            "timestamp", Instant.now().toEpochMilli()
        );
    }

    @Override
    public long getDefaultIntervalMs() {
        return 100L;  // 게임은 빠른 업데이트 필요
    }
}
```

---

## 이벤트 기반 스트리밍 (선택)

WebSocket에서도 폴링 대신 이벤트 기반 스트리밍을 사용할 수 있습니다. 채팅 메시지나 실시간 알림처럼 즉시 전송이 필요한 경우에 적합합니다.

### 설정

```yaml
simplix:
  stream:
    websocket:
      enabled: true
    event-source:
      enabled: true  # 이벤트 기반 스트리밍 활성화
```

### SimpliXStreamEventSource 예제 (채팅)

```java
@Component
public class ChatMessageEventSource implements SimpliXStreamEventSource {

    @Override
    public String getResource() {
        return "chat-messages";
    }

    @Override
    public String getEventType() {
        return "ChatMessageSent";
    }

    @Override
    public Map<String, Object> extractParams(Object payload) {
        ChatMessageEvent event = (ChatMessageEvent) payload;
        return Map.of("roomId", event.getRoomId());
    }

    @Override
    public Object extractData(Object payload) {
        ChatMessageEvent event = (ChatMessageEvent) payload;
        return Map.of(
            "messageId", event.getMessageId(),
            "roomId", event.getRoomId(),
            "senderId", event.getSenderId(),
            "content", event.getContent(),
            "sentAt", event.getSentAt().toEpochMilli()
        );
    }
}
```

### 폴링 vs 이벤트 선택 기준

| 사용 케이스 | 권장 방식 | 이유 |
|-------------|----------|------|
| 게임 상태 | SimpliXStreamDataCollector | 일정 주기로 전체 상태 동기화 필요 |
| 채팅 메시지 | SimpliXStreamEventSource | 메시지 발생 시 즉시 전달 |
| 실시간 알림 | SimpliXStreamEventSource | 지연 없이 즉시 푸시 |
| 대시보드 통계 | SimpliXStreamDataCollector | 주기적 집계 데이터 |

> 자세한 내용은 [이벤트 기반 스트리밍 튜토리얼](./tutorial-event-source.md)을 참조하세요.

---

## 클라이언트 구현

### STOMP 클라이언트 (JavaScript)

```javascript
class WebSocketStreamClient {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl;
        this.stompClient = null;
        this.sessionId = null;
        this.listeners = new Map();
        this.subscriptions = new Map();
        this.connected = false;
    }

    // WebSocket 연결
    connect() {
        return new Promise((resolve, reject) => {
            // SockJS + STOMP 사용
            const socket = new SockJS(`${this.baseUrl}/ws/stream`);
            this.stompClient = Stomp.over(socket);

            // 디버그 로그 비활성화 (선택)
            this.stompClient.debug = null;

            this.stompClient.connect(
                {},  // 헤더
                (frame) => {
                    console.log('Connected:', frame);
                    this.connected = true;

                    // 사용자별 구독 결과 수신
                    this.stompClient.subscribe('/user/queue/stream/subscriptions', (message) => {
                        const response = JSON.parse(message.body);
                        this.handleSubscriptionResponse(response);
                    });

                    // 사용자별 데이터 수신
                    this.stompClient.subscribe('/user/queue/stream/data', (message) => {
                        const data = JSON.parse(message.body);
                        this.handleData(data);
                    });

                    // 연결 확인 메시지 수신
                    this.stompClient.subscribe('/user/queue/stream/connected', (message) => {
                        const data = JSON.parse(message.body);
                        this.sessionId = data.sessionId;
                        resolve(this.sessionId);
                    });
                },
                (error) => {
                    console.error('Connection error:', error);
                    this.connected = false;
                    reject(error);
                }
            );
        });
    }

    // 구독 업데이트
    updateSubscriptions(subscriptions) {
        if (!this.connected) {
            throw new Error('Not connected');
        }

        const request = {
            subscriptions: subscriptions.map(sub => ({
                resource: sub.resource,
                params: sub.params || {}
            }))
        };

        this.stompClient.send('/app/stream/subscribe', {}, JSON.stringify(request));

        // Promise로 응답 대기
        return new Promise((resolve, reject) => {
            this.pendingSubscription = { resolve, reject };
            setTimeout(() => {
                if (this.pendingSubscription) {
                    reject(new Error('Subscription timeout'));
                    this.pendingSubscription = null;
                }
            }, 5000);
        });
    }

    // 모든 구독 해제
    unsubscribeAll() {
        if (!this.connected) {
            return;
        }
        this.stompClient.send('/app/stream/unsubscribe-all', {}, '{}');
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

    // 구독 응답 처리
    handleSubscriptionResponse(response) {
        if (this.pendingSubscription) {
            if (response.success) {
                this.pendingSubscription.resolve(response);
            } else {
                this.pendingSubscription.reject(new Error('Subscription failed'));
            }
            this.pendingSubscription = null;
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

    // 연결 종료
    disconnect() {
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect(() => {
                console.log('Disconnected');
                this.connected = false;
            });
        }
    }

    // 재연결
    async reconnect() {
        this.disconnect();
        await this.connect();
    }
}
```

### 사용 예시

```html
<!DOCTYPE html>
<html>
<head>
    <title>WebSocket Stream Client</title>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
</head>
<body>
    <div id="chat-messages"></div>
    <div id="game-state"></div>

    <script>
        const client = new WebSocketStreamClient();

        async function init() {
            try {
                // 연결
                const sessionId = await client.connect();
                console.log('Session ID:', sessionId);

                // 채팅 메시지 리스너
                client.on('chat-messages', (data, meta) => {
                    const messagesDiv = document.getElementById('chat-messages');
                    data.messages.forEach(msg => {
                        const msgEl = document.createElement('p');
                        msgEl.textContent = `${msg.sender}: ${msg.content}`;
                        messagesDiv.appendChild(msgEl);
                    });
                });

                // 게임 상태 리스너
                client.on('game-state', (data, meta) => {
                    document.getElementById('game-state').textContent =
                        `Phase: ${data.phase}, Time: ${data.timeRemaining}s`;
                });

                // 구독
                await client.updateSubscriptions([
                    {
                        resource: 'chat-messages',
                        params: { roomId: 'room-123' }
                    },
                    {
                        resource: 'game-state',
                        params: { gameId: 'game-456' }
                    }
                ]);

                console.log('Subscribed successfully');

            } catch (error) {
                console.error('Failed:', error);
            }
        }

        // 페이지 이탈 시 정리
        window.addEventListener('beforeunload', () => {
            client.disconnect();
        });

        init();
    </script>
</body>
</html>
```

### React 통합 예시

```jsx
import { useEffect, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

function useWebSocketStream(baseUrl, subscriptions) {
    const [data, setData] = useState({});
    const [connected, setConnected] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS(`${baseUrl}/ws/stream`),
            reconnectDelay: 5000,
            heartbeatIncoming: 30000,
            heartbeatOutgoing: 30000,
        });

        client.onConnect = () => {
            setConnected(true);

            // 데이터 수신 구독
            client.subscribe('/user/queue/stream/data', (message) => {
                const msg = JSON.parse(message.body);
                setData(prev => ({
                    ...prev,
                    [msg.resource]: msg.data
                }));
            });

            // 구독 등록
            client.publish({
                destination: '/app/stream/subscribe',
                body: JSON.stringify({ subscriptions })
            });
        };

        client.onDisconnect = () => {
            setConnected(false);
        };

        client.onStompError = (frame) => {
            setError(frame.body);
        };

        client.activate();

        return () => {
            client.deactivate();
        };
    }, [baseUrl, subscriptions]);

    return { data, connected, error };
}

// 사용 예시
function ChatRoom({ roomId }) {
    const subscriptions = [
        { resource: 'chat-messages', params: { roomId } }
    ];

    const { data, connected } = useWebSocketStream('/api', subscriptions);

    if (!connected) {
        return <div>Connecting...</div>;
    }

    const messages = data['chat-messages']?.messages || [];

    return (
        <div>
            {messages.map(msg => (
                <div key={msg.id}>
                    <strong>{msg.sender}:</strong> {msg.content}
                </div>
            ))}
        </div>
    );
}
```

---

## 테스트 및 검증

### 1. wscat으로 WebSocket 연결 테스트

```bash
# wscat 설치
npm install -g wscat

# WebSocket 연결 (SockJS 없이)
wscat -c ws://localhost:8080/ws/stream
```

### 2. Chrome DevTools로 확인

1. 개발자 도구 열기 (F12)
2. Network 탭 → WS 필터
3. WebSocket 연결 선택
4. Messages 탭에서 송수신 확인

### 3. STOMP 메시지 형식

**연결 확인 메시지:**
```json
{
  "sessionId": "abc123",
  "timestamp": 1704067200000,
  "type": "CONNECTED"
}
```

**데이터 메시지:**
```json
{
  "subscriptionKey": "chat-messages:roomId=room-123",
  "resource": "chat-messages",
  "data": {
    "roomId": "room-123",
    "messages": [...]
  },
  "timestamp": 1704067201000,
  "type": "DATA"
}
```

### 4. 연결 상태 확인

```bash
# actuator 엔드포인트
curl http://localhost:8080/actuator/metrics/simplix.stream.sessions.active
```

---

## 다음 단계

- [WebSocket 분산 모드 튜토리얼](./tutorial-websocket-distributed.md) - 다중 인스턴스 환경
- [클라이언트 프레임워크 가이드](./client-framework-guide.md) - React/Vue 통합
- [Admin API 가이드](./admin-api-guide.md) - 세션/스케줄러 관리
