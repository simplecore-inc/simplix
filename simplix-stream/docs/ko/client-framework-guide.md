# React 통합 가이드

SimpliX Stream을 React 프레임워크와 통합하는 가이드입니다.

## 목차

1. [의존성 설치](#의존성-설치)
2. [useStream Hook](#usestream-hook)
3. [Context Provider 패턴](#context-provider-패턴)
4. [Redux 연동](#redux-연동)
5. [성능 최적화](#성능-최적화)

---

## 의존성 설치

```bash
npm install sockjs-client @stomp/stompjs
# 또는
yarn add sockjs-client @stomp/stompjs
```

---

## useStream Hook

### SSE용 Hook

```typescript
// hooks/useStream.ts
import { useState, useEffect, useCallback, useRef } from 'react';

interface SubscriptionRequest {
    resource: string;
    params?: Record<string, unknown>;
    // Note: intervalMs is determined by server-side SimpliXStreamDataCollector for security
}

interface UseStreamOptions {
    baseUrl?: string;
    autoConnect?: boolean;
    subscriptions?: SubscriptionRequest[];
}

interface UseStreamReturn<T> {
    data: Record<string, T>;
    connected: boolean;
    error: Error | null;
    connect: () => Promise<void>;
    disconnect: () => void;
    updateSubscriptions: (subs: SubscriptionRequest[]) => Promise<void>;
}

export function useStream<T = unknown>(options: UseStreamOptions = {}): UseStreamReturn<T> {
    const { baseUrl = '', autoConnect = true, subscriptions = [] } = options;

    const [data, setData] = useState<Record<string, T>>({});
    const [connected, setConnected] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const eventSourceRef = useRef<EventSource | null>(null);
    const sessionIdRef = useRef<string | null>(null);

    const connect = useCallback(async () => {
        return new Promise<void>((resolve, reject) => {
            const es = new EventSource(`${baseUrl}/api/stream/connect`, {
                withCredentials: true
            });

            es.addEventListener('connected', (event) => {
                const connData = JSON.parse((event as MessageEvent).data);
                sessionIdRef.current = connData.sessionId;
                setConnected(true);
                setError(null);
                resolve();
            });

            es.addEventListener('data', (event) => {
                const message = JSON.parse((event as MessageEvent).data);
                setData(prev => ({
                    ...prev,
                    [message.resource]: message.data
                }));
            });

            es.onerror = () => {
                if (es.readyState === EventSource.CLOSED) {
                    setConnected(false);
                    setError(new Error('Connection closed'));
                    reject(new Error('Connection closed'));
                }
            };

            eventSourceRef.current = es;
        });
    }, [baseUrl]);

    const disconnect = useCallback(() => {
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }
        if (sessionIdRef.current) {
            fetch(`${baseUrl}/api/stream/sessions/${sessionIdRef.current}`, {
                method: 'DELETE',
                credentials: 'include'
            }).catch(() => {});
            sessionIdRef.current = null;
        }
        setConnected(false);
    }, [baseUrl]);

    const updateSubscriptions = useCallback(async (subs: SubscriptionRequest[]) => {
        if (!sessionIdRef.current) {
            throw new Error('Not connected');
        }

        const response = await fetch(
            `${baseUrl}/api/stream/sessions/${sessionIdRef.current}/subscriptions`,
            {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ subscriptions: subs })
            }
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
    }, [baseUrl]);

    // 자동 연결 및 구독
    useEffect(() => {
        if (autoConnect) {
            connect()
                .then(() => {
                    if (subscriptions.length > 0) {
                        return updateSubscriptions(subscriptions);
                    }
                })
                .catch(err => setError(err));
        }

        return () => disconnect();
    }, [autoConnect, connect, disconnect, updateSubscriptions]);

    return {
        data,
        connected,
        error,
        connect,
        disconnect,
        updateSubscriptions
    };
}
```

### 사용 예시

```tsx
// components/StockTicker.tsx
import { useStream } from '../hooks/useStream';

interface StockPrice {
    symbol: string;
    price: number;
    change: number;
    changePercent: number;
}

function StockTicker() {
    const { data, connected, error } = useStream<StockPrice>({
        subscriptions: [
            { resource: 'stock-price', params: { symbol: 'AAPL' } },
            { resource: 'stock-price', params: { symbol: 'GOOG' } }
        ]
    });

    if (error) {
        return <div className="error">Error: {error.message}</div>;
    }

    if (!connected) {
        return <div className="loading">Connecting...</div>;
    }

    const stockData = data['stock-price'] as StockPrice | undefined;

    return (
        <div className="stock-ticker">
            {stockData && (
                <div className={stockData.change >= 0 ? 'up' : 'down'}>
                    <span className="symbol">{stockData.symbol}</span>
                    <span className="price">${stockData.price.toFixed(2)}</span>
                    <span className="change">
                        {stockData.change >= 0 ? '+' : ''}{stockData.changePercent.toFixed(2)}%
                    </span>
                </div>
            )}
        </div>
    );
}

export default StockTicker;
```

---

## Context Provider 패턴

### StreamContext 구현

```tsx
// context/StreamContext.tsx
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';

interface StreamContextValue {
    connected: boolean;
    data: Record<string, unknown>;
    subscribe: (resource: string, params?: Record<string, unknown>) => void;
    unsubscribe: (resource: string) => void;
}

const StreamContext = createContext<StreamContextValue | null>(null);

export function StreamProvider({ children, baseUrl = '' }: { children: React.ReactNode; baseUrl?: string }) {
    const [connected, setConnected] = useState(false);
    const [data, setData] = useState<Record<string, unknown>>({});
    const [subscriptions, setSubscriptions] = useState<Map<string, { resource: string; params: Record<string, unknown> }>>(new Map());
    const [sessionId, setSessionId] = useState<string | null>(null);

    // 연결 설정
    useEffect(() => {
        const es = new EventSource(`${baseUrl}/api/stream/connect`, {
            withCredentials: true
        });

        es.addEventListener('connected', (event) => {
            const connData = JSON.parse((event as MessageEvent).data);
            setSessionId(connData.sessionId);
            setConnected(true);
        });

        es.addEventListener('data', (event) => {
            const message = JSON.parse((event as MessageEvent).data);
            setData(prev => ({
                ...prev,
                [message.subscriptionKey]: message.data
            }));
        });

        es.onerror = () => {
            if (es.readyState === EventSource.CLOSED) {
                setConnected(false);
            }
        };

        return () => {
            es.close();
        };
    }, [baseUrl]);

    // 구독 동기화
    useEffect(() => {
        if (!sessionId || subscriptions.size === 0) return;

        const subs = Array.from(subscriptions.values());

        fetch(`${baseUrl}/api/stream/sessions/${sessionId}/subscriptions`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ subscriptions: subs })
        }).catch(console.error);
    }, [sessionId, subscriptions, baseUrl]);

    const subscribe = useCallback((resource: string, params: Record<string, unknown> = {}) => {
        const key = `${resource}:${JSON.stringify(params)}`;
        setSubscriptions(prev => new Map(prev).set(key, { resource, params }));
    }, []);

    const unsubscribe = useCallback((resource: string) => {
        setSubscriptions(prev => {
            const next = new Map(prev);
            for (const [key] of next) {
                if (key.startsWith(`${resource}:`)) {
                    next.delete(key);
                }
            }
            return next;
        });
    }, []);

    return (
        <StreamContext.Provider value={{ connected, data, subscribe, unsubscribe }}>
            {children}
        </StreamContext.Provider>
    );
}

export function useStreamContext() {
    const context = useContext(StreamContext);
    if (!context) {
        throw new Error('useStreamContext must be used within StreamProvider');
    }
    return context;
}
```

### 컴포넌트에서 사용

```tsx
// App.tsx
import { StreamProvider } from './context/StreamContext';
import StockList from './components/StockList';

function App() {
    return (
        <StreamProvider baseUrl="https://api.example.com">
            <StockList />
        </StreamProvider>
    );
}

// components/StockList.tsx
import { useEffect } from 'react';
import { useStreamContext } from '../context/StreamContext';

function StockList() {
    const { connected, data, subscribe, unsubscribe } = useStreamContext();

    useEffect(() => {
        subscribe('stock-price', { symbol: 'AAPL' });
        subscribe('stock-price', { symbol: 'GOOG' });

        return () => {
            unsubscribe('stock-price');
        };
    }, [subscribe, unsubscribe]);

    if (!connected) return <div>Connecting...</div>;

    return (
        <div>
            {Object.entries(data).map(([key, value]) => (
                <div key={key}>
                    {JSON.stringify(value)}
                </div>
            ))}
        </div>
    );
}
```

---

## Redux 연동

### Redux Middleware

```typescript
// store/streamMiddleware.ts
import { Middleware } from 'redux';

interface StreamConfig {
    baseUrl: string;
}

export const createStreamMiddleware = (config: StreamConfig): Middleware => {
    let eventSource: EventSource | null = null;
    let sessionId: string | null = null;

    return (store) => (next) => (action) => {
        switch (action.type) {
            case 'STREAM_CONNECT':
                eventSource = new EventSource(`${config.baseUrl}/api/stream/connect`, {
                    withCredentials: true
                });

                eventSource.addEventListener('connected', (event) => {
                    const data = JSON.parse((event as MessageEvent).data);
                    sessionId = data.sessionId;
                    store.dispatch({ type: 'STREAM_CONNECTED', payload: { sessionId } });
                });

                eventSource.addEventListener('data', (event) => {
                    const message = JSON.parse((event as MessageEvent).data);
                    store.dispatch({
                        type: 'STREAM_DATA_RECEIVED',
                        payload: message
                    });
                });
                break;

            case 'STREAM_SUBSCRIBE':
                if (sessionId) {
                    fetch(`${config.baseUrl}/api/stream/sessions/${sessionId}/subscriptions`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: JSON.stringify({ subscriptions: action.payload })
                    });
                }
                break;

            case 'STREAM_DISCONNECT':
                eventSource?.close();
                break;
        }

        return next(action);
    };
};
```

### Redux Slice

```typescript
// store/streamSlice.ts
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface StreamState {
    connected: boolean;
    sessionId: string | null;
    data: Record<string, unknown>;
}

const initialState: StreamState = {
    connected: false,
    sessionId: null,
    data: {}
};

const streamSlice = createSlice({
    name: 'stream',
    initialState,
    reducers: {
        streamConnected(state, action: PayloadAction<{ sessionId: string }>) {
            state.connected = true;
            state.sessionId = action.payload.sessionId;
        },
        streamDisconnected(state) {
            state.connected = false;
            state.sessionId = null;
        },
        streamDataReceived(state, action: PayloadAction<{ subscriptionKey: string; data: unknown }>) {
            state.data[action.payload.subscriptionKey] = action.payload.data;
        }
    }
});

export const { streamConnected, streamDisconnected, streamDataReceived } = streamSlice.actions;
export default streamSlice.reducer;
```

### Store 설정

```typescript
// store/index.ts
import { configureStore } from '@reduxjs/toolkit';
import streamReducer from './streamSlice';
import { createStreamMiddleware } from './streamMiddleware';

export const store = configureStore({
    reducer: {
        stream: streamReducer
    },
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(
            createStreamMiddleware({ baseUrl: 'https://api.example.com' })
        )
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

---

## 성능 최적화

### 메모이제이션

```tsx
import { useMemo, memo } from 'react';

interface StockData {
    symbol: string;
    price: number;
}

const StockPrice = memo(function StockPrice({ data }: { data: StockData }) {
    return (
        <div className="stock-price">
            {data.symbol}: ${data.price}
        </div>
    );
});

function StockList() {
    const { data } = useStreamContext();

    const stockItems = useMemo(() => {
        return Object.entries(data)
            .filter(([key]) => key.startsWith('stock-price:'))
            .map(([key, value]) => <StockPrice key={key} data={value as StockData} />);
    }, [data]);

    return <div>{stockItems}</div>;
}
```

### 디바운싱

```typescript
import { debounce } from 'lodash-es';

// 빠른 업데이트 디바운싱
const debouncedSetData = debounce((setData, key, value) => {
    setData(prev => ({ ...prev, [key]: value }));
}, 100);

eventSource.addEventListener('data', (event) => {
    const message = JSON.parse((event as MessageEvent).data);
    debouncedSetData(setData, message.resource, message.data);
});
```

### 선택적 렌더링

```tsx
// 필요한 데이터만 구독
function useStockPrice(symbol: string) {
    const { data } = useStreamContext();

    return useMemo(() => {
        const key = `stock-price:{"symbol":"${symbol}"}`;
        return data[key] as StockPrice | undefined;
    }, [data, symbol]);
}

function StockCard({ symbol }: { symbol: string }) {
    const price = useStockPrice(symbol);

    if (!price) return <div>Loading {symbol}...</div>;

    return <div>{symbol}: ${price.price}</div>;
}
```

### Suspense 통합

```tsx
import { Suspense } from 'react';

function StockDashboard() {
    return (
        <StreamProvider baseUrl="https://api.example.com">
            <Suspense fallback={<div>Loading...</div>}>
                <StockList />
            </Suspense>
        </StreamProvider>
    );
}
```

---

## 다음 단계

- [JavaScript 클라이언트 가이드](./client-javascript-guide.md) - 기본 클라이언트
- [SSE 튜토리얼](./tutorial-sse-standalone.md) - 서버 측 구현
- [WebSocket 튜토리얼](./tutorial-websocket-standalone.md) - WebSocket 구현
