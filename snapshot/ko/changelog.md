# 변경 사항

이 문서는 SimpliX 라이브러리의 주요 변경 사항을 기록합니다.

> GitHub 커밋 링크를 통해 상세한 변경 내용을 확인할 수 있습니다.

---

## 최근 변경 사항

### 2026-08

#### 새로운 기능
- **Excel/CSV 다국어 라벨**
  - `@ExcelColumn(name)`을 `{key}` 형태로 적으면 메시지 키로 해석
  - 열거형은 `enums.{클래스 단순명}.{상수명}` 키 → `SimpliXLabeledEnum.getLabel()` → `name()` 순으로 해석
  - 번들이 클래스패스 와일드카드로 병합된 애플리케이션을 위한 `ExcelLabelResolver` 빈
  - 번역이 없어도 파일 생성은 실패하지 않고 키가 그대로 남음
  - [상세 문서](ko/excel/export-guide.md)

- **라이선스 검증 키 교체와 유출 키 천장**
  - 검증 키를 `classpath:license-verification-keys.json` 한 자원에 여러 개 담아 서명 키 교체 시에도 이전 라이선스 유지
  - 키 이름과 공개키를 한 항목에 함께 담아 둘이 어긋나지 않음
  - 유출로 표시된 키로 서명된 토큰은 배포본이 이미 보유한 것보다 더 줄 수 없으며, 기능·수량 한도·만료·릴리스 상한 중 하나라도 올라가면 `SIGNING_KEY_COMPROMISED`로 거절
  - 아무것도 등록되지 않은 배포본은 천장이 "보유한 것 없음"으로 고정
  - `VerificationKeyIdentity` 빈이 필요 없어짐
  - [상세 문서](ko/license/overview.md)

#### 개선 사항
- **CSV 셀 이스케이프**
  - 구분자·따옴표·줄바꿈을 담은 값을 따옴표로 감싸고 내부 따옴표를 중복
  - 천 단위로 구분한 숫자처럼 문자열이 아닌 값도 같은 규칙 적용
  - `=` `+` `-` `@`로 시작하는 값 앞에 작은따옴표를 붙여 스프레드시트가 수식으로 실행하지 않게 함 (`sanitizeFormulas`, 기본 켜짐)
  - `@ExcelColumn(format)`을 CSV 내보내기에서도 사용

- **논리값 표기 통일**
  - Excel이 참·거짓 셀 대신 CSV와 같은 표기(`format.booleanTrueValue` / `booleanFalseValue`)로 기록
  - 같은 목록을 두 형식으로 내려받았을 때 두 파일이 다르게 읽히지 않음

#### 빌드/의존성
- searchable-jpa 1.1.2
- license-sdk 1.0.8

### 2026-07

#### 새로운 기능
- **simplix-license 모듈 추가**
  - 빌드에 내장된 공개키로 서명된 라이선스 토큰 검증
  - 온라인 활성화와 폐쇄망을 위한 오프라인 활성화
  - 하트비트 갱신과 주기적 재검증
  - `@RequiresFeature` 기반 기능 게이팅, 관리자 활성화 연동
  - `QuotaCounter` 기반 수량 제한 (신규 등록만 차단)
  - 라이선스 상태별 요청 강제 필터와 최초 설치 게이트
  - JPA/파일 저장소 자동 선택, Actuator Health와 Micrometer 메트릭
  - 통합 스타터에 포함되지 않으며 개별 의존성으로 추가
  - [상세 문서](ko/license/overview.md)

- **스트림 연결 티켓**
  - `POST /api/stream/tickets`로 일회용 단기 티켓 발급
  - 헤더를 보낼 수 없는 `EventSource`가 세션 값을 쿼리 문자열에 노출하지 않고 인증
  - `simplix.stream.security.connect-ticket-validity`로 유효 시간 설정 (기본 30초)
  - [상세 문서](ko/stream/client-javascript-guide.md)

#### 개선 사항
- **스트림 익명 세션 처리**
  - 인증되지 않은 연결의 세션 소유권 비교를 null 안전하게 변경
  - 공개 상태 페이지처럼 로그인 없이 구독하는 클라이언트가 첫 요청에서 실패하지 않음

- **OpenAPI 컴포넌트 키 검증**
  - 배열 타입처럼 OpenAPI 명세가 허용하지 않는 문자를 포함한 이름을 스키마 등록에서 제외
  - 컴포넌트 키를 검증하는 코드 생성기가 문서 전체를 읽지 못하던 문제 해소

### 2025-12

#### 새로운 기능
- **simplix-scheduler 모듈 추가**
  - Spring `@Scheduled` 메서드 실행 자동 로깅
  - AOP 기반 실행 시간 추적 및 상태 기록
  - database/in-memory 저장 전략 지원
  - ShedLock 통합으로 분산 환경 지원
  - `@SchedulerName` 어노테이션으로 커스텀 이름 지정
  - Spring `${...}` placeholder를 실제 값으로 치환하여 저장
  - [상세 문서](ko/scheduler/overview.md)

- **@I18nTrans 중첩 모드 지원** [`9d3e4e9`](https://github.com/simplecore-inc/simplix/commit/9d3e4e9)
  - `target` 속성 추가로 중첩 객체의 필드 번역 지원
  - Dot notation 경로 지원 (예: `tagGroup.nameI18n`)
  - 필드 레벨 `@JsonIncludeProperties` 어노테이션 호환
  - 직렬화 후 원본 객체 복원으로 side-effect 방지
  - [상세 문서](ko/core/i18n-translation.md)

- **정렬 가능한 트리 서비스** [`e5774bd`](https://github.com/simplecore-inc/simplix/commit/e5774bd)
  - `SortableTreeEntity` 인터페이스 추가 (정렬 순서 변경 가능한 엔티티)
  - `SimpliXSortableTreeBaseService` 추가 (`reorderChildren()` 기본 구현)
  - 트리 유틸리티 메서드: `normalizeParentId()`, `validateNoCircularReference()`, `validateNoChildren()`
  - 트리 변환 메서드: `buildTreeFromFlatList()`, `mapToTreeDto()`
  - [상세 문서](ko/core/tree-structure.md)

- **커스텀 로그아웃 핸들러 지원** [`1e7ceef`](https://github.com/simplecore-inc/simplix/commit/1e7ceef)
  - `LogoutHandler` 빈 주입 지원 (감사 로깅, 토큰 블랙리스트 등)
  - `LogoutSuccessHandler` 빈 주입 지원 (커스텀 리다이렉트)
  - 토큰 컨트롤러 로그아웃 엔드포인트에서 `LogoutHandler` 호출
  - [상세 문서](ko/auth/security-configuration.md)

- **Hibernate6Module 통합** [`4ad33b2`](https://github.com/simplecore-inc/simplix/commit/4ad33b2)
  - Jackson ObjectMapper에 Hibernate6Module 자동 등록
  - 지연 로딩된 엔티티의 직렬화 개선

- **Unique 검증에 Soft Delete 지원** [`3581791`](https://github.com/simplecore-inc/simplix/commit/3581791)
  - `@UniqueField`, `@UniqueFields` 검증 시 Soft Delete 고려
  - 삭제된 레코드는 유니크 검증에서 제외

- **I18n 번역 설정 시스템** [`8fe7bd2`](https://github.com/simplecore-inc/simplix/commit/8fe7bd2)
  - `SimpliXI18nConfigHolder`를 통한 중앙 집중식 설정
  - 기본 로케일 및 지원 로케일 목록 설정 가능

- **Unique 검증 어노테이션** [`d5fd78a`](https://github.com/simplecore-inc/simplix/commit/d5fd78a)
  - `@UniqueField`: 단일 필드 유니크 검증
  - `@UniqueFields`: 복합 필드 유니크 검증
  - JPA 저장소 기반 중복 검사

#### 빌드/의존성
- **searchable-jpa 버전 업데이트** [`04705c6`](https://github.com/simplecore-inc/simplix/commit/04705c6)
  - searchableJpaVersion 1.0.5로 업그레이드

---

## 버전별 변경 사항

### v1.0.11
- Hibernate6Module 통합으로 Lazy Loading 직렬화 개선

### v1.0.10
- 버그 수정 및 안정성 개선

### v1.0.0
- 최초 정식 릴리스
- Spring Boot 3.x 지원
- Jakarta EE 9+ 호환

---

## 관련 링크

- [GitHub 저장소](https://github.com/simplecore-inc/simplix)
- [이슈 트래커](https://github.com/simplecore-inc/simplix/issues)
- [전체 커밋 히스토리](https://github.com/simplecore-inc/simplix/commits/main)
