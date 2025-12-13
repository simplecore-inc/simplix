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

## Related Documents

- [Overview (아키텍처 개요)](./overview.md) - 모듈 구조
- [Tree Structure Guide (트리 구조)](./tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](./type-converters.md) - Boolean, Enum, DateTime 변환
- [Security Guide (보안)](./security.md) - XSS 방지, 해싱, 마스킹
- [Exception & API Guide (예외/API)](./exception-api.md) - 에러 코드, API 응답
- [Cache Guide (캐시)](./cache.md) - CacheManager, CacheProvider
