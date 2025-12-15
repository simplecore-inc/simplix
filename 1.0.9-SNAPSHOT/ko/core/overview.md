# SimpliX Core Module Overview

## Architecture

```
+-------------------------------------------------------------------+
|                     Application Modules                           |
|  +-------------+ +-------------+ +-------------+ +------------+   |
|  |simplix-auth | |simplix-file | |simplix-event| |simplix-... |   |
|  +------+------+ +------+------+ +------+------+ +-----+------+   |
+--------+---------------+---------------+--------------+-----------+
         |               |               |              |
         +---------------+-------+-------+--------------+
                                 v
+-------------------------------------------------------------------+
|                        SimpliX Core                               |
|  +-------------------------------------------------------------+  |
|  |  Entity & Repository                                        |  |
|  |  +-----------------+  +---------------------------------+   |  |
|  |  |SimpliXBaseEntity|  |SimpliXBaseRepository            |   |  |
|  |  |- getId()        |  |- JpaRepository +                |   |  |
|  |  |- setId()        |  |  JpaSpecificationExecutor       |   |  |
|  |  +-----------------+  +---------------------------------+   |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Tree Structure                                             |  |
|  |  +------------+ +------------------+ +-----------------+    |  |
|  |  |TreeEntity  | |SimpliXTree       | |SimpliXTree      |    |  |
|  |  |- parentId  | |Repository        | |Service          |    |  |
|  |  |- children  | |- findHierarchy() | |- move()         |    |  |
|  |  |- sortKey   | |- findDescendants | |- copySubtree()  |    |  |
|  |  +------------+ +------------------+ +-----------------+    |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Type Conversion                                            |  |
|  |  +----------------+ +----------------+ +---------------+    |  |
|  |  |BooleanConverter| |EnumConverter   | |DateTime       |    |  |
|  |  |                | |- toMap()       | |Converter      |    |  |
|  |  +----------------+ +----------------+ +---------------+    |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Security                                                   |  |
|  |  +--------------+ +--------------+ +--------------------+   |  |
|  |  |HtmlSanitizer | |HashingUtils  | |DataMaskingUtils    |   |  |
|  |  |- XSS prevent | |- SHA-256/512 | |- Sensitive masking |   |  |
|  |  +--------------+ +--------------+ +--------------------+   |  |
|  |  +--------------+ +--------------+                          |  |
|  |  |@SafeHtml     | |@ValidateWith |                          |  |
|  |  |- Bean Valid. | |- Custom valid|                          |  |
|  |  +--------------+ +--------------+                          |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Validation                                                 |  |
|  |  +--------------+ +--------------+ +--------------------+   |  |
|  |  |@Unique       | |@UniqueFields | |UniqueValidator     |   |  |
|  |  |- Field level | |- Class level | |- JPA EntityManager |   |  |
|  |  |- DB unique   | |- Multi field | |- Update exclusion  |   |  |
|  |  +--------------+ +--------------+ +--------------------+   |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  I18n Translation                                           |  |
|  |  +-------------------+ +-------------------+                |  |
|  |  |@I18nTrans         | |I18nConfigHolder   |                |  |
|  |  |- JSON field trans | |- Config holder    |                |  |
|  |  |- Locale fallback  | |- Supported locales|                |  |
|  |  +-------------------+ +-------------------+                |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Exception & API                                            |  |
|  |  +------------------+ +-------------------------------+     |  |
|  |  |ErrorCode         | |SimpliXApiResponse<T>          |     |  |
|  |  |- 30+ std codes   | |- success(), failure(), error()|     |  |
|  |  |- Categorized     | |- Standard API response wrapper|     |  |
|  |  +------------------+ +-------------------------------+     |  |
|  +-------------------------------------------------------------+  |
|  +-------------------------------------------------------------+  |
|  |  Utilities                                                  |  |
|  |  +------------+ +------------+ +------------+               |  |
|  |  |CacheManager| |EntityUtils | |UuidV7      |               |  |
|  |  |- SPI based | |- Conv util | |Generator   |               |  |
|  |  +------------+ +------------+ +------------+               |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|                    External Dependencies                          |
|  Spring Boot Data JPA | OWASP Sanitizer | ModelMapper | ...       |
+-------------------------------------------------------------------+
```

---

## Package Structure

```
simplix-core/
└── src/main/java/dev/simplecore/simplix/core/
    ├── entity/                     # 베이스 엔티티
    │   ├── SimpliXBaseEntity.java
    │   ├── SimpliXCompositeKey.java
    │   ├── converter/              # JPA 컨버터
    │   │   ├── HashingAttributeConverter.java
    │   │   ├── MaskingConverter.java
    │   │   └── JsonMapConverter.java
    │   └── listener/               # JPA 리스너
    │       ├── MaskSensitive.java
    │       └── UniversalMaskingListener.java
    │
    ├── repository/                 # 베이스 리포지토리
    │   └── SimpliXBaseRepository.java
    │
    ├── tree/                       # 트리 구조 지원
    │   ├── entity/
    │   │   └── TreeEntity.java
    │   ├── repository/
    │   │   ├── SimpliXTreeRepository.java
    │   │   └── SimpliXTreeRepositoryImpl.java
    │   ├── service/
    │   │   ├── SimpliXTreeService.java
    │   │   └── SimpliXTreeBaseService.java
    │   ├── annotation/
    │   │   ├── TreeEntityAttributes.java
    │   │   ├── LookupColumn.java
    │   │   └── SortDirection.java
    │   └── factory/
    │       └── SimpliXRepositoryFactoryBean.java
    │
    ├── convert/                    # 타입 변환기
    │   ├── bool/
    │   │   ├── BooleanConverter.java
    │   │   └── StandardBooleanConverter.java
    │   ├── enumeration/
    │   │   ├── EnumConverter.java
    │   │   └── StandardEnumConverter.java
    │   └── datetime/
    │       ├── DateTimeConverter.java
    │       └── StandardDateTimeConverter.java
    │
    ├── security/                   # 보안 기능
    │   ├── hashing/
    │   │   └── HashingUtils.java
    │   ├── sanitization/
    │   │   ├── HtmlSanitizer.java
    │   │   ├── DataMaskingUtils.java
    │   │   ├── IpAddressMaskingUtils.java
    │   │   └── LogMasker.java
    │   └── validation/
    │       ├── SafeHtml.java
    │       ├── SafeHtmlValidator.java
    │       ├── InputSanitizer.java
    │       └── SqlInjectionValidator.java
    │
    ├── exception/                  # 예외 처리
    │   ├── ErrorCode.java
    │   └── SimpliXGeneralException.java
    │
    ├── model/                      # API 모델
    │   └── SimpliXApiResponse.java
    │
    ├── validator/                  # 커스텀 검증
    │   ├── ValidateWith.java
    │   ├── ValidateWithValidator.java
    │   ├── Unique.java             # 필드 레벨 유니크 검증
    │   ├── UniqueValidator.java
    │   ├── UniqueField.java        # 클래스 레벨 유니크 필드 정의
    │   ├── UniqueFields.java       # 클래스 레벨 유니크 검증
    │   └── UniqueFieldsValidator.java
    │
    ├── config/                      # 설정 홀더
    │   ├── SimpliXI18nProperties.java
    │   └── SimpliXI18nConfigHolder.java
    │
    ├── cache/                      # 캐시 추상화
    │   ├── CacheManager.java
    │   └── CacheProvider.java
    │
    ├── enums/                      # Enum 지원
    │   └── SimpliXLabeledEnum.java
    │
    ├── jackson/                    # Jackson 직렬화
    │   ├── SimpliXBooleanSerializer.java
    │   ├── SimpliXEnumSerializer.java
    │   └── SimpliXDateTimeSerializer.java
    │
    ├── hibernate/                  # Hibernate 확장
    │   ├── UuidV7Generator.java
    │   └── UuidV7GeneratorImpl.java
    │
    └── util/                       # 유틸리티
        ├── EntityUtils.java
        ├── DtoUtils.java
        └── UuidUtils.java
```

---

## Core Components

| 컴포넌트 | 패키지 | 설명 |
|----------|--------|------|
| **SimpliXBaseEntity<K>** | entity | 모든 엔티티의 추상 베이스 클래스 |
| **SimpliXBaseRepository<E, ID>** | repository | JpaRepository + JpaSpecificationExecutor 확장 |
| **TreeEntity<T, ID>** | tree.entity | 트리 구조 엔티티 인터페이스 |
| **SimpliXTreeRepository<T, ID>** | tree.repository | 트리 전용 리포지토리 |
| **SimpliXTreeService<T, ID>** | tree.service | 트리 CRUD, 탐색, 조작, 분석 서비스 |
| **@TreeEntityAttributes** | tree.annotation | 트리 엔티티 메타데이터 설정 |
| **BooleanConverter** | convert.bool | Boolean ↔ String 변환 |
| **EnumConverter** | convert.enumeration | Enum ↔ String/Map 변환 |
| **DateTimeConverter** | convert.datetime | 날짜/시간 타입 변환 |
| **HtmlSanitizer** | security.sanitization | OWASP 기반 XSS 방지 |
| **HashingUtils** | security.hashing | SHA-256/512 해싱 |
| **DataMaskingUtils** | security.sanitization | 민감 데이터 마스킹 |
| **@SafeHtml** | security.validation | HTML 검증 어노테이션 |
| **@ValidateWith** | validator | 커스텀 서비스 메서드 검증 |
| **@Unique** | validator | 필드 레벨 DB 유니크 검증 어노테이션 |
| **@UniqueFields** | validator | 클래스 레벨 다중 필드 유니크 검증 |
| **SimpliXI18nConfigHolder** | config | I18n 번역 설정 홀더 |
| **@I18nTrans** | jackson.annotation | JSON 필드 다국어 번역 어노테이션 |
| **ErrorCode** | exception | 30+ 표준화된 에러 코드 |
| **SimpliXGeneralException** | exception | 표준 예외 클래스 |
| **SimpliXApiResponse<T>** | model | 표준 API 응답 래퍼 |
| **CacheManager** | cache | SPI 기반 캐시 추상화 |
| **SimpliXLabeledEnum** | enums | 라벨이 있는 Enum 인터페이스 |

---

## Module Dependencies

simplix-core는 다른 모든 SimpliX 모듈의 기반입니다:

```
                    +-----------------+
                    |   simplix-core  |
                    +--------+--------+
                             |
        +--------------------+--------------------+
        |                    |                    |
        v                    v                    v
+---------------+   +---------------+   +---------------+
| simplix-auth  |   | simplix-file  |   |simplix-event  |
+-------+-------+   +-------+-------+   +-------+-------+
        |                   |                   |
        v                   v                   v
+---------------+   +---------------+   +---------------+
|simplix-mybatis|   |simplix-excel  |   |simplix-       |
|               |   |               |   |hibernate      |
+---------------+   +---------------+   +---------------+
```

---

## External Dependencies

| 의존성 | 용도 |
|--------|------|
| `spring-boot-starter-data-jpa` | JPA/Hibernate 지원 |
| `spring-boot-starter-web` | Web 기능 |
| `spring-boot-starter-json` | JSON 처리 |
| `hibernate-validator` | Bean Validation |
| `owasp-java-html-sanitizer` | XSS 방지 |
| `modelmapper` | 객체 변환 |
| `uuid-creator` | UUID v7 생성 |
| `springdoc-openapi` | API 문서화 |

---

## 특징

### 라이브러리 모듈

simplix-core는 Auto-Configuration이 없는 순수 라이브러리 모듈입니다:

- Spring Boot starter가 아닌 일반 라이브러리
- `@AutoConfiguration` 없음
- 의존성 추가만으로 클래스 사용 가능
- 다른 모듈에서 공통 기능 제공

### 제네릭 타입 지원

모든 베이스 클래스가 제네릭을 사용하여 타입 안정성 제공:

```java
// 엔티티
public abstract class SimpliXBaseEntity<K> {
    public abstract K getId();
    public abstract void setId(K id);
}

// 리포지토리
public interface SimpliXBaseRepository<E, ID>
    extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {}

// 트리 서비스
public interface SimpliXTreeService<T extends TreeEntity<T, ID>, ID> { }
```

### SPI 패턴

CacheManager는 SPI(Service Provider Interface) 패턴으로 구현:

```java
// 프로바이더 인터페이스
public interface CacheProvider {
    void put(String cacheName, String key, Object value);
    <T> T get(String cacheName, String key, Class<T> type);
    void evict(String cacheName, String key);
}

// 자동 프로바이더 발견
ServiceLoader.load(CacheProvider.class)
```

---

## Related Documents

- [Entity & Repository Guide (엔티티/리포지토리)](ko/core/entity-repository.md) - 베이스 엔티티, 복합 키
- [Tree Structure Guide (트리 구조)](ko/core/tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](ko/core/type-converters.md) - Boolean, Enum, DateTime 변환
- [Security Guide (보안)](ko/core/security.md) - XSS 방지, 해싱, 마스킹
- [Exception & API Guide (예외/API)](ko/core/exception-api.md) - 에러 코드, API 응답
- [Cache Guide (캐시)](ko/core/cache.md) - CacheManager, CacheProvider
