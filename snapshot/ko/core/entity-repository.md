# Entity & Repository Guide

## Base Entity

### SimpliXBaseEntity<K>

모든 엔티티의 추상 베이스 클래스입니다.

```java
@Getter
@Setter
public abstract class SimpliXBaseEntity<K> {
    public abstract K getId();
    public abstract void setId(K id);
}
```

### 사용 예제

```java
@Entity
@Table(name = "users")
public class User extends SimpliXBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
```

### UUID 기반 엔티티

```java
@Entity
@Table(name = "articles")
public class Article extends SimpliXBaseEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)  // UUID v7
    private UUID id;

    private String title;
    private String content;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }
}
```

---

## Base Repository

### SimpliXBaseRepository<E, ID>

JpaRepository와 JpaSpecificationExecutor를 확장하는 베이스 리포지토리입니다.

```java
@NoRepositoryBean
public interface SimpliXBaseRepository<E, ID>
    extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {
}
```

### 사용 예제

```java
public interface UserRepository extends SimpliXBaseRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByEmailContaining(String email);
}
```

### JpaSpecificationExecutor 활용

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public List<User> searchUsers(String keyword, Boolean active) {
        Specification<User> spec = Specification.where(null);

        if (keyword != null) {
            spec = spec.and((root, query, cb) ->
                cb.or(
                    cb.like(root.get("username"), "%" + keyword + "%"),
                    cb.like(root.get("email"), "%" + keyword + "%")
                )
            );
        }

        if (active != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("active"), active)
            );
        }

        return userRepository.findAll(spec);
    }
}
```

---

## Composite Key

### SimpliXCompositeKey

복합 키를 사용하는 엔티티를 위한 인터페이스입니다.

```java
public interface SimpliXCompositeKey extends Serializable {
    SimpliXCompositeKey fromPathVariables(String... pathVariables);
    SimpliXCompositeKey fromCompositeId(String compositeId);
    void validate();
    default String toCompositeKeyString() { ... }
}
```

### 복합 키 정의

```java
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemId implements SimpliXCompositeKey {

    private Long orderId;
    private Long productId;

    public static OrderItemId fromUrlPath(String... pathVariables) {
        if (pathVariables.length != 2) {
            throw new IllegalArgumentException("Expected 2 path variables");
        }
        return new OrderItemId(
            Long.parseLong(pathVariables[0]),
            Long.parseLong(pathVariables[1])
        );
    }

    public static OrderItemId fromString(String compositeId) {
        String[] parts = compositeId.split("__");
        return new OrderItemId(
            Long.parseLong(parts[0]),
            Long.parseLong(parts[1])
        );
    }

    @Override
    public SimpliXCompositeKey fromPathVariables(String... pathVariables) {
        return fromUrlPath(pathVariables);
    }

    @Override
    public SimpliXCompositeKey fromCompositeId(String compositeId) {
        return fromString(compositeId);
    }

    @Override
    public void validate() {
        if (orderId == null || productId == null) {
            throw new IllegalArgumentException("Both orderId and productId are required");
        }
    }

    @Override
    public String toString() {
        return toCompositeKeyString();  // "123__456"
    }
}
```

### 복합 키 엔티티

```java
@Entity
@Table(name = "order_items")
public class OrderItem extends SimpliXBaseEntity<OrderItemId> {

    @EmbeddedId
    private OrderItemId id;

    private Integer quantity;
    private BigDecimal unitPrice;

    @Override
    public OrderItemId getId() {
        return id;
    }

    @Override
    public void setId(OrderItemId id) {
        this.id = id;
    }
}
```

---

## JPA Converters

### JsonListConverter

`List<String>`을 JSON 문자열로 자동 변환하는 JPA AttributeConverter입니다.

```java
@Entity
public class Article {
    @Id
    private Long id;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags;
}
```

**저장 예시:**
```java
Article article = new Article();
article.setTags(List.of("spring", "java", "simplix"));
// DB 저장값: ["spring","java","simplix"]
```

**특징:**
- 변환 오류 시 빈 리스트 `[]` 반환 (예외 발생 안 함)
- NULL 값 처리 지원
- JSON 형식으로 직렬화되어 가독성 우수

### JsonMapConverter

`Map<String, Object>`을 JSON 문자열로 변환합니다.

```java
@Entity
public class UserSettings {
    @Id
    private Long id;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> preferences;
}
```

---

## Utility Classes

### DtoUtils

Entity와 DTO 간 변환을 위한 유틸리티 클래스입니다. 내부적으로 ModelMapper를 사용합니다.

```java
// 단일 엔티티 → DTO 변환
UserDto dto = DtoUtils.toDto(user, UserDto.class);

// 리스트 변환
List<UserDto> dtos = DtoUtils.toDtoList(users, UserDto.class);

// Page 변환 (페이징 정보 유지)
Page<UserDto> dtoPage = DtoUtils.toDtoPage(userPage, UserDto.class);
```

**제공 메서드:**

| 메서드 | 설명 |
|--------|------|
| `toDto(E entity, Class<D> dtoClass)` | 단일 엔티티를 DTO로 변환 |
| `toDtoList(List<E> entities, Class<D> dtoClass)` | 리스트 변환 |
| `toDtoPage(Page<E> entityPage, Class<D> dtoClass)` | Page 변환 (페이징 정보 유지) |

**사용 예제:**

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Page<UserDto> getUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return DtoUtils.toDtoPage(users, UserDto.class);
    }

    public List<UserDto> getAllActiveUsers() {
        List<User> users = userRepository.findByActiveTrue();
        return DtoUtils.toDtoList(users, UserDto.class);
    }
}
```

### EntityUtils

엔티티 관련 리플렉션 기반 유틸리티입니다.

```java
// 제네릭 타입에서 엔티티 클래스 추출
Class<User> entityClass = EntityUtils.getEntityClass(UserService.class);

// DTO/다른 객체를 엔티티로 변환
User entity = EntityUtils.convertToEntity(userDto, User.class);

// 엔티티에서 @Id 필드 값 추출
Long userId = EntityUtils.getEntityId(user);
```

**제공 메서드:**

| 메서드 | 설명 |
|--------|------|
| `getEntityClass(Class<?> clazz)` | 제네릭 슈퍼클래스에서 엔티티 타입 추출 |
| `convertToEntity(Object source, Class<E> entityClass)` | 객체를 엔티티로 변환 (STRICT 매칭) |
| `getEntityId(E entity)` | `@Id` 어노테이션 필드 값 추출 |

**convertToEntity 특징:**
- `STRICT` 매칭 전략 사용
- NULL 값은 변환에서 제외
- Private 필드 접근 가능
- 같은 클래스인 경우 그대로 반환

---

## Related Documents

- [Overview (아키텍처 개요)](ko/core/overview.md) - 모듈 구조
- [Tree Structure Guide (트리 구조)](ko/core/tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](ko/core/type-converters.md) - Boolean, Enum, DateTime 변환
- [Security Guide (보안)](ko/core/security.md) - XSS 방지, 해싱, 마스킹
- [Exception & API Guide (예외/API)](ko/core/exception-api.md) - 에러 코드, API 응답
- [Cache Guide (캐시)](ko/core/cache.md) - CacheManager, CacheProvider
