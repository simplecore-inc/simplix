# Service & Controller Guide

## Overview

SimpliX는 CRUD 작업을 위한 기본 서비스와 컨트롤러 추상 클래스를 제공합니다. 이를 통해 반복적인 코드를 줄이고 일관된 API를 구축할 수 있습니다.

## SimpliXBaseService

`SimpliXBaseService`는 기본 CRUD 작업과 검색 기능을 제공하는 추상 서비스 클래스입니다.

### Basic Usage

```java
@Service
public class UserService extends SimpliXBaseService<User, Long> {

    public UserService(UserRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }

    // 추가 비즈니스 로직
    public Optional<User> findByEmail(String email) {
        return ((UserRepository) repository).findByEmail(email);
    }
}
```

### Provided Methods

#### Read Operations

```java
// ID로 엔티티 조회
Optional<User> user = userService.findById(1L);

// ID로 조회 후 DTO로 매핑
Optional<UserDto> userDto = userService.findById(1L, UserDto.class);

// 여러 ID로 조회
List<User> users = userService.findAllById(List.of(1L, 2L, 3L));

// 여러 ID로 조회 후 DTO로 매핑
List<UserDto> userDtos = userService.findAllById(List.of(1L, 2L, 3L), UserDto.class);

// 전체 조회 (페이징)
Page<User> page = userService.findAll(PageRequest.of(0, 10));

// 전체 조회 (페이징) + DTO 매핑
Page<UserDto> dtoPage = userService.findAll(PageRequest.of(0, 10), UserDto.class);

// 전체 조회 (리스트) + DTO 매핑
List<UserDto> allDtos = userService.findAll(UserDto.class);

// 존재 여부 확인
Boolean exists = userService.existsById(1L);
```

#### Write Operations

```java
// 저장 (상속된 메서드)
User saved = userService.saveAndFlush(user);

// 여러 엔티티 저장
List<User> savedUsers = userService.saveAll(users);

// 삭제
userService.deleteById(1L);

// 엔티티로 삭제
userService.delete(user);

// 여러 ID로 삭제
userService.deleteAllByIds(List.of(1L, 2L, 3L));

// 여러 엔티티 삭제
userService.deleteAll(users);
```

#### Search Operations (Searchable Integration)

```java
// 동적 검색 조건으로 조회
SearchCondition<User> condition = SearchCondition.builder(User.class)
    .filter("name", "like", "%John%")
    .filter("status", "eq", "ACTIVE")
    .sort("createdAt", "desc")
    .page(0, 10)
    .build();

Page<User> result = userService.findAllWithSearch(condition);

// 검색 결과 DTO 매핑
Page<UserDto> dtoResult = userService.findAllWithSearch(condition, UserDto.class);
```

### ModelMapper Integration

`SimpliXBaseService`는 자동으로 `ModelMapper`를 주입받습니다:

```java
@Service
public class UserService extends SimpliXBaseService<User, Long> {

    public UserDto toDto(User user) {
        return modelMapper.map(user, UserDto.class);
    }

    public User toEntity(CreateUserRequest request) {
        return modelMapper.map(request, User.class);
    }
}
```

### Transaction Management

`SimpliXBaseService`는 트랜잭션을 자동으로 관리합니다:

- 읽기 전용 메서드: `@Transactional(readOnly = true)`
- 쓰기 메서드: `@Transactional`

```java
@Service
public class UserService extends SimpliXBaseService<User, Long> {

    // 기본 findById는 readOnly=true
    // 커스텀 쓰기 메서드는 @Transactional 추가
    @Transactional
    public User createUser(CreateUserRequest request) {
        User user = modelMapper.map(request, User.class);
        return repository.save(user);
    }
}
```

## SimpliXBaseController

`SimpliXBaseController`는 REST API 컨트롤러의 기본 클래스입니다.

### Basic Usage

```java
@RestController
@RequestMapping("/api/users")
public class UserController extends SimpliXBaseController<User, Long> {

    public UserController(UserService service) {
        super(service);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<UserDto>> getUser(@PathVariable Long id) {
        return service.findById(id, UserDto.class)
            .map(user -> ResponseEntity.ok(SimpliXApiResponse.success(user)))
            .orElseThrow(() -> new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "User not found"
            ));
    }

    @GetMapping
    public ResponseEntity<SimpliXApiResponse<Page<UserDto>>> getUsers(Pageable pageable) {
        Page<UserDto> users = service.findAll(pageable, UserDto.class);
        return ResponseEntity.ok(SimpliXApiResponse.success(users));
    }

    @PostMapping
    public ResponseEntity<SimpliXApiResponse<UserDto>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        User user = ((UserService) service).createUser(request);
        UserDto dto = ((UserService) service).toDto(user);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SimpliXApiResponse.success(dto));
    }
}
```

### SimpliXStandardApi Annotation

`SimpliXBaseController`는 `@SimpliXStandardApi` 어노테이션을 포함합니다:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@RestController
public @interface SimpliXStandardApi {
}
```

### Response Patterns

#### Success Response

```java
@GetMapping("/{id}")
public ResponseEntity<SimpliXApiResponse<UserDto>> getUser(@PathVariable Long id) {
    UserDto user = service.findById(id, UserDto.class)
        .orElseThrow(() -> new SimpliXGeneralException(ErrorCode.GEN_NOT_FOUND));
    return ResponseEntity.ok(SimpliXApiResponse.success(user));
}
```

Response:
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

#### List Response

```java
@GetMapping
public ResponseEntity<SimpliXApiResponse<List<UserDto>>> getUsers() {
    List<UserDto> users = service.findAll(UserDto.class);
    return ResponseEntity.ok(SimpliXApiResponse.success(users));
}
```

#### Page Response

```java
@GetMapping
public ResponseEntity<SimpliXApiResponse<Page<UserDto>>> getUsers(
        @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    Page<UserDto> page = service.findAll(pageable, UserDto.class);
    return ResponseEntity.ok(SimpliXApiResponse.success(page));
}
```

Response:
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [...],
    "pageable": {...},
    "totalElements": 100,
    "totalPages": 5,
    "number": 0,
    "size": 20
  }
}
```

## SimpliXService Interface

`SimpliXBaseService`가 구현하는 인터페이스입니다:

```java
public interface SimpliXService<E, ID> {
    Optional<E> findById(ID id);
    <P> Optional<P> findById(ID id, Class<P> projection);
    List<E> findAllById(Iterable<ID> ids);
    <P> List<P> findAllById(Iterable<ID> ids, Class<P> projection);
    Page<E> findAll(Pageable pageable);
    <P> Page<P> findAll(Pageable pageable, Class<P> projection);
    <P> List<P> findAll(Class<P> projection);
    Boolean existsById(ID id);
    void deleteById(ID id);
    void deleteAllByIds(Iterable<ID> ids);
    void delete(E entity);
    void deleteAll(Iterable<? extends E> entities);
    List<? extends E> saveAll(Iterable<? extends E> entities);
    E saveAndFlush(E entity);
}
```

## Tree Service Support

계층 구조 엔티티를 위한 `SimpliXTreeService`도 제공됩니다:

```java
@Service
public class CategoryService extends SimpliXSimpliXTreeService<Category, Long> {

    public CategoryService(CategoryRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### Tree Methods

```java
// 루트 노드 조회
List<Category> roots = categoryService.findRoots();

// 자식 노드 조회
List<Category> children = categoryService.findChildren(parentId);

// 전체 트리 조회
List<Category> tree = categoryService.findTree();

// 노드 이동
categoryService.moveNode(nodeId, newParentId);
```

## Best Practices

### 1. Use DTOs for APIs

API 응답에는 항상 DTO를 사용하세요:

```java
// 좋은 예
public ResponseEntity<SimpliXApiResponse<UserDto>> getUser(@PathVariable Long id) {
    return service.findById(id, UserDto.class)...
}

// 피해야 할 예 (엔티티 직접 노출)
public ResponseEntity<SimpliXApiResponse<User>> getUser(@PathVariable Long id) {
    return service.findById(id)...
}
```

### 2. Validate Input

입력 값은 DTO 레벨에서 검증하세요:

```java
public class CreateUserRequest {
    @NotBlank
    @Size(min = 2, max = 50)
    private String name;

    @NotBlank
    @Email
    private String email;
}

@PostMapping
public ResponseEntity<SimpliXApiResponse<UserDto>> createUser(
        @Valid @RequestBody CreateUserRequest request) {
    // ...
}
```

### 3. Custom Service Methods

비즈니스 로직은 서비스에 구현하세요:

```java
@Service
public class OrderService extends SimpliXBaseService<Order, Long> {

    @Transactional
    public Order placeOrder(CreateOrderRequest request) {
        // 비즈니스 로직
        validateStock(request.getItems());
        Order order = createOrder(request);
        deductStock(request.getItems());
        sendNotification(order);
        return order;
    }
}
```

### 4. Exception Handling

예외는 `SimpliXGeneralException`을 사용하세요:

```java
public UserDto getUser(Long id) {
    return service.findById(id, UserDto.class)
        .orElseThrow(() -> new SimpliXGeneralException(
            ErrorCode.GEN_NOT_FOUND,
            "User not found",
            Map.of("userId", id)
        ));
}
```