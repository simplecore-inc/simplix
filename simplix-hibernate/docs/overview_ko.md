# Hibernate Cache Module 개요

## 소개

Hibernate Cache Module은 Hibernate 2차 캐시(Second-Level Cache)를 자동으로 관리하는 모듈입니다. 별도의 설정 없이 의존성만 추가하면 즉시 작동하며, 캐시 일관성을 자동으로 유지합니다.

## 주요 기능

### 1. 제로 설정 (Zero Configuration)
- 의존성 추가만으로 즉시 작동
- 복잡한 설정 파일 불필요
- 기본값으로 최적의 성능 제공

### 2. 자동 캐시 무효화
- 엔티티 변경 시 자동으로 캐시 제거
- 연관된 쿼리 캐시도 함께 처리
- 캐시 일관성 자동 유지

### 3. 로컬 캐시 프로바이더
- EhCache 기반 로컬 캐시 사용
- JCache (JSR-107) 표준 준수
- 최적화된 기본 설정 제공

> **참고**: 분산 캐시 프로바이더(Redis, Hazelcast 등)는 향후 버전에서 지원 예정입니다.

## 동작 원리

### 캐시 무효화 메커니즘

모듈은 4가지 인터셉션 포인트를 통해 모든 엔티티 변경을 감지합니다:

1. **Global Entity Listener (orm.xml)**
   - 모든 JPA 엔티티에 자동 적용
   - `@PostPersist`, `@PostUpdate`, `@PostRemove` 이벤트 처리

2. **Hibernate SPI Integrator**
   - Hibernate 내부 이벤트 시스템 직접 연동
   - Hibernate SessionFactory 초기화 시 통합

3. **Spring AOP**
   - `@Modifying` 쿼리 인터셉트
   - 벌크 업데이트/삭제 시 캐시 무효화

4. **JPA Event Listener**
   - 추가 안전 레이어
   - 엔티티 라이프사이클 이벤트 처리

### 캐시 프로바이더

현재 EhCache 기반 로컬 캐시를 사용합니다:
- JCache (JSR-107) 표준 구현
- 메모리 내 캐싱으로 빠른 응답 속도
- 힙 메모리 관리 및 자동 eviction

## 성능 최적화

### 1. 배치 무효화
- 여러 엔티티 변경을 한 번에 처리
- 네트워크 오버헤드 최소화

### 2. 선택적 무효화
- 실제로 변경된 필드만 체크
- 불필요한 캐시 제거 방지

### 3. 효율적인 캐시 관리
- 엔티티별 리전 분리로 격리된 캐시 관리
- 쿼리 캐시 자동 무효화

## 모니터링

### 로그 확인
```
✔ Hibernate Cache Auto-Management activated
✔ Selected cache provider: LOCAL
✔ Found 12 cached entities across 4 regions
```

### 메트릭 수집
- Micrometer 통합
- 캐시 히트율, 무효화 빈도 추적
- JMX를 통한 캐시 통계 확인

## 문제 해결

### 캐시가 무효화되지 않는 경우
1. 엔티티에 `@Cache` 어노테이션 확인
2. Hibernate 2차 캐시 활성화 여부 확인
3. 로그에서 프로바이더 연결 상태 확인

### 성능이 저하되는 경우
1. 너무 많은 엔티티를 캐싱하고 있는지 확인
2. 캐시 TTL 설정 검토
3. 힙 메모리 사용량 모니터링

### 메모리 부족 문제
1. 캐시 리전별 최대 엔트리 수 조정
2. TTL 설정으로 자동 만료 활성화
3. 불필요한 엔티티 캐싱 비활성화