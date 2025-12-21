# 변경 사항

이 문서는 SimpliX 라이브러리의 주요 변경 사항을 기록합니다.

> GitHub 커밋 링크를 통해 상세한 변경 내용을 확인할 수 있습니다.

---

## 최근 변경 사항

### 2025-12

#### 새로운 기능
- **@I18nTrans 중첩 모드 지원** [`9d3e4e9`](https://github.com/simplecore-dev/simplix/commit/9d3e4e9)
  - `target` 속성 추가로 중첩 객체의 필드 번역 지원
  - Dot notation 경로 지원 (예: `tagGroup.nameI18n`)
  - 필드 레벨 `@JsonIncludeProperties` 어노테이션 호환
  - 직렬화 후 원본 객체 복원으로 side-effect 방지
  - [상세 문서](/ko/core/i18n-translation.md)

- **정렬 가능한 트리 서비스** [`e5774bd`](https://github.com/simplecore-dev/simplix/commit/e5774bd)
  - `SortableTreeEntity` 인터페이스 추가 (정렬 순서 변경 가능한 엔티티)
  - `SimpliXSortableTreeBaseService` 추가 (`reorderChildren()` 기본 구현)
  - 트리 유틸리티 메서드: `normalizeParentId()`, `validateNoCircularReference()`, `validateNoChildren()`
  - 트리 변환 메서드: `buildTreeFromFlatList()`, `mapToTreeDto()`
  - [상세 문서](/ko/core/tree-structure.md)

- **커스텀 로그아웃 핸들러 지원** [`1e7ceef`](https://github.com/simplecore-dev/simplix/commit/1e7ceef)
  - `LogoutHandler` 빈 주입 지원 (감사 로깅, 토큰 블랙리스트 등)
  - `LogoutSuccessHandler` 빈 주입 지원 (커스텀 리다이렉트)
  - 토큰 컨트롤러 로그아웃 엔드포인트에서 `LogoutHandler` 호출
  - [상세 문서](/ko/auth/security-configuration.md)

- **Hibernate6Module 통합** [`4ad33b2`](https://github.com/simplecore-dev/simplix/commit/4ad33b2)
  - Jackson ObjectMapper에 Hibernate6Module 자동 등록
  - 지연 로딩된 엔티티의 직렬화 개선

- **Unique 검증에 Soft Delete 지원** [`3581791`](https://github.com/simplecore-dev/simplix/commit/3581791)
  - `@UniqueField`, `@UniqueFields` 검증 시 Soft Delete 고려
  - 삭제된 레코드는 유니크 검증에서 제외

- **I18n 번역 설정 시스템** [`8fe7bd2`](https://github.com/simplecore-dev/simplix/commit/8fe7bd2)
  - `SimpliXI18nConfigHolder`를 통한 중앙 집중식 설정
  - 기본 로케일 및 지원 로케일 목록 설정 가능

- **Unique 검증 어노테이션** [`d5fd78a`](https://github.com/simplecore-dev/simplix/commit/d5fd78a)
  - `@UniqueField`: 단일 필드 유니크 검증
  - `@UniqueFields`: 복합 필드 유니크 검증
  - JPA 리포지토리 기반 중복 검사

#### 빌드/의존성
- **searchable-jpa 버전 업데이트** [`04705c6`](https://github.com/simplecore-dev/simplix/commit/04705c6)
  - searchableJpaVersion 1.0.5로 업그레이드

---

## 버전별 변경 사항

### v1.0.11
- Hibernate6Module 통합으로 Lazy Loading 직렬화 개선

### v1.0.10
- 버그 수정 및 안정성 개선

### v1.0.0
- 최초 정식 릴리즈
- Spring Boot 3.x 지원
- Jakarta EE 9+ 호환

---

## 관련 링크

- [GitHub 저장소](https://github.com/simplecore-dev/simplix)
- [이슈 트래커](https://github.com/simplecore-dev/simplix/issues)
- [전체 커밋 히스토리](https://github.com/simplecore-dev/simplix/commits/main)
