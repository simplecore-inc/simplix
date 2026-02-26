# JavaScript 클라이언트 가이드

SimpliX Stream을 위한 JavaScript 클라이언트 구현 가이드입니다.

## 목차

1. [재연결 및 구독 복구](#재연결-및-구독-복구)
2. [SSE 클라이언트](#sse-클라이언트)
3. [WebSocket 클라이언트](#websocket-클라이언트)
4. [공통 유틸리티](#공통-유틸리티)
5. [에러 처리](#에러-처리)
6. [TypeScript 지원](#typescript-지원)

---

## 재연결 및 구독 복구

SimpliX Stream에서 연결이 끊어지면 **새 세션이 생성**됩니다. 따라서 클라이언트는 다음 패턴을 따라야 합니다:

### 핵심 원칙

1. **구독 목록을 클라이언트에서 보관**: 서버는 재연결 시 이전 구독을 기억하지 않음
2. **재연결 시 구독 자동 복구**: `connected` 이벤트 수신 시 저장된 구독 재등록
3. **지수 백오프 재연결**: 연결 실패 시 점진적으로 재시도 간격 증가

### 재연결 동작 비교

| Transport | 자동 재연결 | 구독 복구 방법 |
|-----------|------------|---------------|
| SSE | EventSource 내장 | `connected` 이벤트에서 `lastSubscriptions` 재전송 |
| WebSocket | 직접 구현 필요 | STOMP connect 콜백에서 `lastSubscriptions` 재전송 |

### 재연결 흐름

```
[연결 중]
Client ──SSE/WS──> Server A
         sessionId: "abc-123"
         subscriptions: ["stock-price?symbol=AAPL"]

[연결 끊김 → 재연결 (같은 서버 또는 다른 서버)]
Client ──SSE/WS──> Server B
         → 새 세션 생성: "xyz-789"
         → subscriptions: [] (빈 상태)
         → 클라이언트가 저장된 구독 재전송
         → subscriptions: ["stock-price?symbol=AAPL"] (복구됨)
```

아래 클라이언트 구현에서 `lastSubscriptions` 필드와 `handleDisconnect` 메서드가 이 패턴을 구현합니다.

---

## SSE 클라이언트

### 기본 클라이언트

```javascript
class StreamClient {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl;
        this.eventSource = null;
        this.sessionId = null;
        this.listeners = new Map();
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 1000;
        this.lastSubscriptions = [];
    }

    /**
     * SSE 연결 수립
     * @returns {Promise<string>} 세션 ID
     */
    connect() {
        return new Promise((resolve, reject) => {
            this.eventSource = new EventSource(
                `${this.baseUrl}/api/stream/connect`,
                { withCredentials: true }  // 인증 쿠키 전송
            );

            // 연결 성공
            this.eventSource.addEventListener('connected', (event) => {
                const data = JSON.parse(event.data);
                this.sessionId = data.sessionId;
                this.reconnectAttempts = 0;
                console.log('Connected:', this.sessionId);
                resolve(this.sessionId);
            });

            // 데이터 수신
            this.eventSource.addEventListener('data', (event) => {
                const message = JSON.parse(event.data);
                this.handleData(message);
            });

            // 하트비트 수신
            this.eventSource.addEventListener('heartbeat', () => {
                console.debug('Heartbeat received');
            });

            // 에러 이벤트
            this.eventSource.addEventListener('error', (event) => {
                if (event.data) {
                    const error = JSON.parse(event.data);
                    console.error('Stream error:', error);
                    this.handleError(error);
                }
            });

            // 구독 제거 알림
            this.eventSource.addEventListener('subscription_removed', (event) => {
                const data = JSON.parse(event.data);
                console.log('Subscription removed:', data.subscriptionKey);
            });

            // 연결 에러
            this.eventSource.onerror = (error) => {
                if (this.eventSource.readyState === EventSource.CLOSED) {
                    console.log('Connection closed');
                    this.handleDisconnect(reject);
                }
            };
        });
    }

    /**
     * 구독 업데이트
     * @param {Array} subscriptions 구독 목록
     * @returns {Promise<Object>} 구독 결과
     */
    async updateSubscriptions(subscriptions) {
        if (!this.sessionId) {
            throw new Error('Not connected');
        }

        this.lastSubscriptions = subscriptions;

        const response = await fetch(
            `${this.baseUrl}/api/stream/sessions/${this.sessionId}/subscriptions`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({ subscriptions })
            }
        );

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || `HTTP ${response.status}`);
        }

        return response.json();
    }

    /**
     * 현재 구독 조회
     * @returns {Promise<Object>} 현재 구독 정보
     */
    async getSubscriptions() {
        if (!this.sessionId) {
            throw new Error('Not connected');
        }

        const response = await fetch(
            `${this.baseUrl}/api/stream/sessions/${this.sessionId}/subscriptions`,
            { credentials: 'include' }
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        return response.json();
    }

    /**
     * 리소스별 리스너 등록
     * @param {string} resource 리소스 이름
     * @param {Function} callback 콜백 함수
     */
    on(resource, callback) {
        if (!this.listeners.has(resource)) {
            this.listeners.set(resource, new Set());
        }
        this.listeners.get(resource).add(callback);
    }

    /**
     * 리스너 제거
     * @param {string} resource 리소스 이름
     * @param {Function} callback 콜백 함수
     */
    off(resource, callback) {
        if (this.listeners.has(resource)) {
            this.listeners.get(resource).delete(callback);
        }
    }

    /**
     * 모든 리스너 제거
     * @param {string} resource 리소스 이름 (선택)
     */
    removeAllListeners(resource) {
        if (resource) {
            this.listeners.delete(resource);
        } else {
            this.listeners.clear();
        }
    }

    /**
     * 데이터 수신 처리
     */
    handleData(message) {
        const { subscriptionKey, resource, data, timestamp } = message;

        if (this.listeners.has(resource)) {
            this.listeners.get(resource).forEach(callback => {
                try {
                    callback(data, { subscriptionKey, resource, timestamp });
                } catch (e) {
                    console.error('Listener error:', e);
                }
            });
        }
    }

    /**
     * 에러 처리
     */
    handleError(error) {
        console.error('Stream error:', error);
    }

    /**
     * 연결 끊김 처리
     */
    handleDisconnect(reject) {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            const delay = Math.min(
                this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
                30000
            );

            console.log(`Reconnecting in ${delay}ms...`);

            setTimeout(async () => {
                try {
                    await this.connect();
                    if (this.lastSubscriptions.length > 0) {
                        await this.updateSubscriptions(this.lastSubscriptions);
                    }
                } catch (e) {
                    console.error('Reconnection failed:', e);
                }
            }, delay);
        } else {
            reject?.(new Error('Max reconnection attempts reached'));
        }
    }

    /**
     * 연결 종료
     */
    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }

        if (this.sessionId) {
            fetch(`${this.baseUrl}/api/stream/sessions/${this.sessionId}`, {
                method: 'DELETE',
                credentials: 'include'
            }).catch(() => {});

            this.sessionId = null;
        }
    }

    /**
     * 연결 상태 확인
     * @returns {boolean}
     */
    isConnected() {
        return this.eventSource?.readyState === EventSource.OPEN;
    }
}
```

### 사용 예시

```javascript
const client = new StreamClient('https://api.example.com');

async function init() {
    try {
        await client.connect();

        // 리스너 등록
        client.on('stock-price', (data, meta) => {
            console.log(`${data.symbol}: $${data.price}`);
            updateUI(data);
        });

        // 구독 시작 (폴링 주기는 서버에서 결정)
        await client.updateSubscriptions([
            {
                resource: 'stock-price',
                params: { symbol: 'AAPL' }
            },
            {
                resource: 'stock-price',
                params: { symbol: 'GOOG' }
            }
        ]);

    } catch (error) {
        console.error('Failed:', error);
    }
}

// 정리
window.addEventListener('beforeunload', () => {
    client.disconnect();
});

init();
```

---

## WebSocket 클라이언트

### 의존성

```html
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
```

또는 npm:

```bash
npm install sockjs-client @stomp/stompjs
```

### STOMP 클라이언트

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class WebSocketStreamClient {
    constructor(baseUrl = '') {
        this.baseUrl = baseUrl;
        this.client = null;
        this.sessionId = null;
        this.listeners = new Map();
        this.lastSubscriptions = [];
        this.connected = false;
    }

    /**
     * WebSocket 연결
     * @returns {Promise<string>} 세션 ID
     */
    connect() {
        return new Promise((resolve, reject) => {
            this.client = new Client({
                webSocketFactory: () => new SockJS(`${this.baseUrl}/ws/stream`),
                reconnectDelay: 5000,
                heartbeatIncoming: 30000,
                heartbeatOutgoing: 30000,
                debug: (str) => {
                    if (process.env.NODE_ENV === 'development') {
                        console.debug('STOMP:', str);
                    }
                }
            });

            this.client.onConnect = () => {
                this.connected = true;

                // 연결 확인 구독
                this.client.subscribe('/user/queue/stream/connected', (message) => {
                    const data = JSON.parse(message.body);
                    this.sessionId = data.sessionId;
                    resolve(this.sessionId);
                });

                // 데이터 구독
                this.client.subscribe('/user/queue/stream/data', (message) => {
                    const data = JSON.parse(message.body);
                    this.handleData(data);
                });

                // 구독 응답
                this.client.subscribe('/user/queue/stream/subscriptions', (message) => {
                    const response = JSON.parse(message.body);
                    if (this.pendingSubscription) {
                        this.pendingSubscription.resolve(response);
                        this.pendingSubscription = null;
                    }
                });

                // 에러 구독
                this.client.subscribe('/user/queue/stream/error', (message) => {
                    const error = JSON.parse(message.body);
                    this.handleError(error);
                });
            };

            this.client.onDisconnect = () => {
                this.connected = false;
                this.handleDisconnect();
            };

            this.client.onStompError = (frame) => {
                console.error('STOMP error:', frame);
                reject(new Error(frame.body));
            };

            this.client.activate();
        });
    }

    /**
     * 구독 업데이트
     */
    updateSubscriptions(subscriptions) {
        if (!this.connected) {
            throw new Error('Not connected');
        }

        this.lastSubscriptions = subscriptions;

        return new Promise((resolve, reject) => {
            this.pendingSubscription = { resolve, reject };

            this.client.publish({
                destination: '/app/stream/subscribe',
                body: JSON.stringify({ subscriptions })
            });

            setTimeout(() => {
                if (this.pendingSubscription) {
                    reject(new Error('Subscription timeout'));
                    this.pendingSubscription = null;
                }
            }, 10000);
        });
    }

    /**
     * 모든 구독 해제
     */
    unsubscribeAll() {
        if (this.connected) {
            this.client.publish({
                destination: '/app/stream/unsubscribe-all',
                body: '{}'
            });
        }
    }

    /**
     * 리스너 등록
     */
    on(resource, callback) {
        if (!this.listeners.has(resource)) {
            this.listeners.set(resource, new Set());
        }
        this.listeners.get(resource).add(callback);
    }

    /**
     * 리스너 제거
     */
    off(resource, callback) {
        if (this.listeners.has(resource)) {
            this.listeners.get(resource).delete(callback);
        }
    }

    handleData(message) {
        const { subscriptionKey, resource, data, timestamp } = message;

        if (this.listeners.has(resource)) {
            this.listeners.get(resource).forEach(callback => {
                try {
                    callback(data, { subscriptionKey, resource, timestamp });
                } catch (e) {
                    console.error('Listener error:', e);
                }
            });
        }
    }

    handleError(error) {
        console.error('Stream error:', error);
    }

    handleDisconnect() {
        console.log('Disconnected, attempting to reconnect...');
    }

    disconnect() {
        if (this.client) {
            this.client.deactivate();
        }
    }

    isConnected() {
        return this.connected;
    }
}
```

---

## 공통 유틸리티

### 연결 상태 관리자

```javascript
class ConnectionState {
    static CONNECTING = 'connecting';
    static CONNECTED = 'connected';
    static DISCONNECTED = 'disconnected';
    static RECONNECTING = 'reconnecting';
    static ERROR = 'error';
}

class ConnectionManager {
    constructor(client) {
        this.client = client;
        this.state = ConnectionState.DISCONNECTED;
        this.stateListeners = new Set();
    }

    onStateChange(callback) {
        this.stateListeners.add(callback);
        return () => this.stateListeners.delete(callback);
    }

    setState(newState) {
        const oldState = this.state;
        this.state = newState;
        this.stateListeners.forEach(cb => cb(newState, oldState));
    }

    async connect() {
        this.setState(ConnectionState.CONNECTING);
        try {
            await this.client.connect();
            this.setState(ConnectionState.CONNECTED);
        } catch (error) {
            this.setState(ConnectionState.ERROR);
            throw error;
        }
    }
}
```

### 구독 빌더

```javascript
class SubscriptionBuilder {
    constructor() {
        this.subscriptions = [];
    }

    add(resource, params = {}) {
        this.subscriptions.push({ resource, params });
        return this;
    }

    stock(symbol) {
        return this.add('stock-price', { symbol });
    }

    notification(userId) {
        return this.add('notifications', { userId });
    }

    build() {
        return this.subscriptions;
    }
}

// 사용
const subs = new SubscriptionBuilder()
    .stock('AAPL')
    .stock('GOOG')
    .notification('user123')
    .build();

await client.updateSubscriptions(subs);
```

---

## 에러 처리

### 에러 타입

```javascript
class StreamError extends Error {
    constructor(code, message, details = {}) {
        super(message);
        this.code = code;
        this.details = details;
        this.name = 'StreamError';
    }
}

class ConnectionError extends StreamError {
    constructor(message) {
        super('CONNECTION_ERROR', message);
    }
}

class SubscriptionError extends StreamError {
    constructor(message, failedSubscriptions) {
        super('SUBSCRIPTION_ERROR', message, { failedSubscriptions });
    }
}

class AuthorizationError extends StreamError {
    constructor(resource) {
        super('AUTHORIZATION_ERROR', `Not authorized for ${resource}`, { resource });
    }
}
```

### 에러 핸들러

```javascript
class ErrorHandler {
    constructor() {
        this.handlers = new Map();
    }

    on(errorCode, handler) {
        if (!this.handlers.has(errorCode)) {
            this.handlers.set(errorCode, new Set());
        }
        this.handlers.get(errorCode).add(handler);
    }

    handle(error) {
        const code = error.code || 'UNKNOWN';

        if (this.handlers.has(code)) {
            this.handlers.get(code).forEach(handler => {
                try {
                    handler(error);
                } catch (e) {
                    console.error('Error handler failed:', e);
                }
            });
        }

        // 기본 에러 처리
        if (this.handlers.has('*')) {
            this.handlers.get('*').forEach(handler => handler(error));
        }
    }
}

// 사용
const errorHandler = new ErrorHandler();

errorHandler.on('AUTHORIZATION_ERROR', (error) => {
    console.warn('Authorization failed:', error.details.resource);
});

errorHandler.on('*', (error) => {
    console.error('Stream error:', error);
});
```

---

## TypeScript 지원

### 타입 정의

```typescript
// types.ts

export interface SubscriptionRequest {
    resource: string;
    params?: Record<string, unknown>;
    // Note: intervalMs is determined by server-side SimpliXStreamDataCollector for security
}

export interface SubscriptionResponse {
    success: boolean;
    subscribed: SubscribedResource[];
    failed: FailedSubscription[];
    totalCount: number;
}

export interface SubscribedResource {
    resource: string;
    params: Record<string, unknown>;
    subscriptionKey: string;
    intervalMs: number;
}

export interface FailedSubscription {
    resource: string;
    params?: Record<string, unknown>;
    reason: string;
}

export interface StreamMessage<T = unknown> {
    subscriptionKey: string;
    resource: string;
    data: T;
    timestamp: number;
    type: 'DATA' | 'CONNECTED' | 'HEARTBEAT' | 'ERROR';
}

export interface StreamMessageMeta {
    subscriptionKey: string;
    resource: string;
    timestamp: number;
}

export type DataCallback<T = unknown> = (data: T, meta: StreamMessageMeta) => void;

export interface StreamClientOptions {
    baseUrl?: string;
    reconnectDelay?: number;
    maxReconnectAttempts?: number;
    withCredentials?: boolean;
}
```

### TypeScript 클라이언트

```typescript
// StreamClient.ts
import {
    SubscriptionRequest,
    SubscriptionResponse,
    StreamMessage,
    DataCallback,
    StreamClientOptions,
    StreamMessageMeta
} from './types';

export class StreamClient {
    private baseUrl: string;
    private eventSource: EventSource | null = null;
    private sessionId: string | null = null;
    private listeners: Map<string, Set<DataCallback>> = new Map();
    private reconnectAttempts = 0;
    private maxReconnectAttempts: number;
    private reconnectDelay: number;
    private lastSubscriptions: SubscriptionRequest[] = [];
    private withCredentials: boolean;

    constructor(options: StreamClientOptions = {}) {
        this.baseUrl = options.baseUrl ?? '';
        this.reconnectDelay = options.reconnectDelay ?? 1000;
        this.maxReconnectAttempts = options.maxReconnectAttempts ?? 10;
        this.withCredentials = options.withCredentials ?? true;
    }

    async connect(): Promise<string> {
        return new Promise((resolve, reject) => {
            this.eventSource = new EventSource(
                `${this.baseUrl}/api/stream/connect`,
                { withCredentials: this.withCredentials }
            );

            this.eventSource.addEventListener('connected', (event) => {
                const data = JSON.parse((event as MessageEvent).data);
                this.sessionId = data.sessionId;
                this.reconnectAttempts = 0;
                resolve(this.sessionId);
            });

            this.eventSource.addEventListener('data', (event) => {
                const message: StreamMessage = JSON.parse((event as MessageEvent).data);
                this.handleData(message);
            });

            this.eventSource.onerror = () => {
                if (this.eventSource?.readyState === EventSource.CLOSED) {
                    reject(new Error('Connection closed'));
                }
            };
        });
    }

    async updateSubscriptions(subscriptions: SubscriptionRequest[]): Promise<SubscriptionResponse> {
        if (!this.sessionId) {
            throw new Error('Not connected');
        }

        this.lastSubscriptions = subscriptions;

        const response = await fetch(
            `${this.baseUrl}/api/stream/sessions/${this.sessionId}/subscriptions`,
            {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: this.withCredentials ? 'include' : 'omit',
                body: JSON.stringify({ subscriptions })
            }
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        return response.json();
    }

    on<T = unknown>(resource: string, callback: DataCallback<T>): void {
        if (!this.listeners.has(resource)) {
            this.listeners.set(resource, new Set());
        }
        this.listeners.get(resource)!.add(callback as DataCallback);
    }

    off<T = unknown>(resource: string, callback: DataCallback<T>): void {
        this.listeners.get(resource)?.delete(callback as DataCallback);
    }

    private handleData(message: StreamMessage): void {
        const { subscriptionKey, resource, data, timestamp } = message;
        const meta: StreamMessageMeta = { subscriptionKey, resource, timestamp };

        this.listeners.get(resource)?.forEach(callback => {
            callback(data, meta);
        });
    }

    disconnect(): void {
        this.eventSource?.close();
        this.eventSource = null;

        if (this.sessionId) {
            fetch(`${this.baseUrl}/api/stream/sessions/${this.sessionId}`, {
                method: 'DELETE',
                credentials: this.withCredentials ? 'include' : 'omit'
            }).catch(() => {});
            this.sessionId = null;
        }
    }

    isConnected(): boolean {
        return this.eventSource?.readyState === EventSource.OPEN;
    }
}
```

---

## 다음 단계

- [React/Vue 통합 가이드](./client-framework-guide.md) - 프레임워크 통합
- [SSE 튜토리얼](./tutorial-sse-standalone.md) - 서버 측 구현
