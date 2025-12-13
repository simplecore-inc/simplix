# Application Setup Guide

Spring Boot 애플리케이션에서 SimpliX를 사용하기 위한 메인 클래스 설정 가이드입니다.

## Basic Setup

### Minimal Configuration

가장 기본적인 설정:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

SimpliX의 Auto-Configuration이 대부분의 설정을 자동으로 처리합니다.

### Full Configuration

모든 SimpliX 기능을 사용하는 전체 설정:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.myapp"
})
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity"
})
@EnableJpaRepositories(
    repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
    basePackages = {
        "com.example.myapp.domain.repository"
    }
)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## Annotation Guide

### @ComponentScan

Spring 컴포넌트 스캔 범위를 지정합니다:

```java
@ComponentScan(basePackages = {
    "com.example.myapp"           // 전체 애플리케이션 패키지
})
```

**참고**: SimpliX 패키지는 Auto-Configuration으로 자동 등록되므로 별도 추가 불필요합니다.

### @EntityScan

JPA 엔티티 스캔 범위를 지정합니다:

```java
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity"
})
```

다중 패키지 지정:

```java
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity",
    "com.example.myapp.audit.entity"
})
```

### @EnableJpaRepositories

JPA 리포지토리 설정입니다. **SimpliX Tree 기능을 사용하려면 필수**입니다.

```java
@EnableJpaRepositories(
    repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
    basePackages = {
        "com.example.myapp.domain.repository"
    }
)
```

| 속성 | 설명 |
|------|------|
| `repositoryFactoryBeanClass` | SimpliX Tree Repository 지원을 위해 `SimpliXRepositoryFactoryBean.class` 지정 |
| `basePackages` | 리포지토리 인터페이스가 위치한 패키지 |

## SimpliXRepositoryFactoryBean

`SimpliXRepositoryFactoryBean`은 SimpliX의 Tree Repository 기능을 활성화하는 핵심 컴포넌트입니다.

### 왜 필요한가?

- `SimpliXTreeRepository` 인터페이스 자동 구현
- 트리 구조 엔티티의 계층 쿼리 지원
- `@TreeEntityAttributes` 어노테이션 처리

### 사용 시점

다음 기능을 사용할 때 필수:

- `SimpliXTreeRepository<T, ID>` 인터페이스
- `TreeEntity<T, ID>` 구현 엔티티
- `SimpliXTreeService<T, ID>` 서비스

### 예시

```java
// 엔티티
@Entity
@TreeEntityAttributes(
    parentIdField = "parentId",
    sortKeyField = "sortKey"
)
public class Category implements TreeEntity<Category, Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;
    private Integer sortKey;
    private String name;

    // TreeEntity 구현...
}

// 리포지토리
public interface CategoryRepository
    extends SimpliXTreeRepository<Category, Long> {
}

// 서비스
@Service
public class CategoryService
    extends SimpliXTreeBaseService<Category, Long> {

    public CategoryService(CategoryRepository repository) {
        super(repository);
    }
}
```

## Optional Annotations

### @EnableScheduling

스케줄링 기능 활성화:

```java
@SpringBootApplication
@EnableScheduling
public class MyApplication { }
```

SimpliX 모듈 중 스케줄링이 필요한 경우:
- JWE Key Rolling 자동 로테이션
- 캐시 자동 정리
- 토큰 블랙리스트 정리

### @EnableIntegration

Spring Integration 기능 활성화:

```java
@SpringBootApplication
@EnableIntegration
public class MyApplication { }
```

SimpliX Event 모듈의 일부 기능에서 사용될 수 있습니다.

### @EnableAsync

비동기 처리 활성화:

```java
@SpringBootApplication
@EnableAsync
public class MyApplication { }
```

## Package Structure Recommendation

권장 패키지 구조:

```
com.example.myapp/
├── MyApplication.java              # @SpringBootApplication
├── config/                         # 설정 클래스
│   ├── SecurityConfig.java
│   └── WebConfig.java
├── domain/
│   ├── entity/                     # @EntityScan 대상
│   │   ├── User.java
│   │   └── Category.java
│   └── repository/                 # @EnableJpaRepositories 대상
│       ├── UserRepository.java
│       └── CategoryRepository.java
├── service/
│   ├── UserService.java
│   └── CategoryService.java
├── web/
│   └── controller/
│       ├── UserController.java
│       └── CategoryController.java
└── dto/
    ├── UserDto.java
    └── CategoryDto.java
```

## Multi-Module Project Setup

멀티 모듈 프로젝트에서의 설정:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.myapp.api",        // API 모듈
    "com.example.myapp.service",    // 서비스 모듈
    "com.example.myapp.domain"      // 도메인 모듈
})
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity"
})
@EnableJpaRepositories(
    repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
    basePackages = {
        "com.example.myapp.domain.repository"
    }
)
public class ApiServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiServerApplication.class, args);
    }
}
```

## Troubleshooting

### Tree Repository가 작동하지 않음

**증상**: `SimpliXTreeRepository` 메서드 호출 시 `UnsupportedOperationException`

**해결**: `@EnableJpaRepositories`에 `repositoryFactoryBeanClass` 설정 확인

```java
@EnableJpaRepositories(
    repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,  // 이 설정 필수
    basePackages = {...}
)
```

### Entity not found

**증상**: `Not a managed type: class com.example.MyEntity`

**해결**: `@EntityScan`에 엔티티 패키지 추가

```java
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity"  // 엔티티 패키지 확인
})
```

### Bean not found

**증상**: `NoSuchBeanDefinitionException`

**해결**: `@ComponentScan`에 컴포넌트 패키지 추가

```java
@ComponentScan(basePackages = {
    "com.example.myapp"  // 서비스, 컨트롤러 등 포함
})
```

## Related Documents

- [Configuration Guide](./configuration.md) - YAML 설정 속성
- [Tree Structure Guide](../../simplix-core/docs/ko/tree-structure.md) - 트리 구조 상세 가이드
- [Service & Controller Guide](./service-controller.md) - 서비스/컨트롤러 가이드