# Hibernate Cache Module 개요

## 소개

Hibernate Cache Module은 Hibernate 2차 캐시(Second-Level Cache)를 자동으로 관리하는 모듈입니다. 별도의 설정 없이 의존성만 추가하면 즉시 작동하며, 분산 환경에서도 캐시 일관성을 자동으로 유지합니다.

## 주요 기능

### 1. 제로 설정 (Zero Configuration)
- 의존성 추가만으로 즉시 작동
- 복잡한 설정 파일 불필요
- 기본값으로 최적의 성능 제공

### 2. 자동 캐시 무효화
- 엔티티 변경 시 자동으로 캐시 제거
- 연관된 쿼리 캐시도 함께 처리
- 캐시 일관성 자동 유지

### 3. 스마트 프로바이더 선택
- Redis, Hazelcast, Infinispan, Local 캐시 자동 감지
- 우선순위에 따라 최적의 프로바이더 선택
- 장애 시 자동 폴백

### 4. 분산 캐시 지원
- 멀티 노드 환경에서 캐시 동기화
- Redis Pub/Sub, Hazelcast Topic 등 활용
- 네트워크 장애 시 우아한 처리

## 동작 원리

### 캐시 무효화 메커니즘

모듈은 4가지 인터셉션 포인트를 통해 모든 엔티티 변경을 감지합니다:

1. **Global Entity Listener (orm.xml)**
   - 모든 JPA 엔티티에 자동 적용
   - `@PostPersist`, `@PostUpdate`, `@PostRemove` 이벤트 처리

2. **Hibernate SPI Integrator**
   - Hibernate 내부 이벤트 시스템 직접 연동
   - Spring 없이도 작동

3. **Spring AOP**
   - Repository 메서드 인터셉트
   - 배치 작업 최적화

4. **JPA Event Listener**
   - 추가 안전 레이어
   - 엔티티 라이프사이클 이벤트 처리

### 프로바이더 선택 알고리즘

```
우선순위:
1. Redis (spring-boot-starter-data-redis 감지 시)
2. Hazelcast (hazelcast 라이브러리 감지 시)
3. Infinispan (infinispan-core 감지 시)
4. Local Cache (항상 사용 가능)
```

### 분산 캐시 동기화

```
노드 A: 엔티티 수정
    ↓
캐시 무효화 이벤트 생성
    ↓
Redis Pub/Sub 또는 Hazelcast Topic으로 브로드캐스트
    ↓
노드 B, C, D: 이벤트 수신 및 로컬 캐시 무효화
```

## 성능 최적화

### 1. 배치 무효화
- 여러 엔티티 변경을 한 번에 처리
- 네트워크 오버헤드 최소화

### 2. 선택적 무효화
- 실제로 변경된 필드만 체크
- 불필요한 캐시 제거 방지

### 3. 비동기 처리
- 분산 캐시 동기화는 비동기로 수행
- 메인 트랜잭션 성능 영향 최소화

## 모니터링

### 로그 확인
```
✔ Hibernate Cache Auto-Management activated
✔ Selected cache provider: REDIS
✔ Found 12 cached entities across 4 regions
```

### 메트릭 수집
- Micrometer 통합
- 캐시 히트율, 무효화 빈도 추적
- Actuator 엔드포인트 제공

## 문제 해결

### 캐시가 무효화되지 않는 경우
1. 엔티티에 `@Cache` 어노테이션 확인
2. Hibernate 2차 캐시 활성화 여부 확인
3. 로그에서 프로바이더 연결 상태 확인

### 성능이 저하되는 경우
1. 너무 많은 엔티티를 캐싱하고 있는지 확인
2. 캐시 TTL 설정 검토
3. 네트워크 지연 시간 체크

### 분산 환경에서 동기화 문제
1. Redis/Hazelcast 연결 상태 확인
2. 네트워크 방화벽 설정 검토
3. 로컬 캐시로 폴백 고려