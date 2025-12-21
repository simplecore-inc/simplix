# Swagger & OpenAPI Guide

## Overview

SimpliX는 SpringDoc OpenAPI를 기반으로 API 문서화를 자동으로 설정합니다. Swagger UI와 Scalar UI 모두 지원합니다.

## Auto-Configuration

`SimpliXSwaggerAutoConfiguration`은 다음 조건에서 활성화됩니다:

- SpringDoc OpenAPI 의존성이 존재할 때
- `springdoc.api-docs.enabled=true` (기본값)

## Endpoints

SimpliX가 설정하는 기본 엔드포인트:

| Endpoint | Description |
|----------|-------------|
| `/v3/api-docs` | OpenAPI JSON specification |
| `/swagger-ui.html` | Swagger UI |
| `/scalar` | Scalar UI (현대적 대안) |

## Configuration

### Basic Configuration

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs

  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    operations-sorter: method  # alpha, method
    tags-sorter: alpha

  show-actuator: false
  packages-to-scan: com.example.controller
  paths-to-match: /api/**
```

### API Info Configuration

```yaml
springdoc:
  info:
    title: My API
    description: API Documentation
    version: 1.0.0
    terms-of-service: https://example.com/terms
    contact:
      name: API Support
      email: support@example.com
      url: https://example.com/support
    license:
      name: Apache 2.0
      url: https://www.apache.org/licenses/LICENSE-2.0
```

### Scalar UI Configuration

Scalar는 현대적인 API 문서 UI입니다:

```yaml
scalar:
  enabled: true
  url: /v3/api-docs    # OpenAPI spec URL
  path: /scalar        # Scalar UI 경로
```

## SimpliX Schema Enhancers

### EnumSchemaExtractor

Enum 타입의 스키마를 자동으로 추출하고 문서화합니다:

```java
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
```

자동으로 생성되는 스키마:
```json
{
  "OrderStatus": {
    "type": "string",
    "enum": ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"]
  }
}
```

비활성화:
```yaml
simplix:
  swagger:
    customizers:
      enum-extractor:
        enabled: false
```

### NestedObjectSchemaExtractor

중첩 객체의 스키마를 자동으로 추출합니다:

```java
public class OrderDto {
    private Long id;
    private CustomerDto customer;  // 중첩 객체
    private List<OrderItemDto> items;  // 중첩 리스트
}
```

비활성화:
```yaml
simplix:
  swagger:
    customizers:
      nested-object-extractor:
        enabled: false
```

### SwaggerSchemaEnhancer

i18n 지원을 위한 스키마 향상:

```yaml
simplix:
  swagger:
    i18n-enabled: true
```

## API Documentation Best Practices

### Controller Documentation

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management APIs")
public class UserController {

    @Operation(
        summary = "Get user by ID",
        description = "Returns a single user by their ID"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(schema = @Schema(implementation = UserDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found"
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<UserDto>> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {
        // ...
    }
}
```

### DTO Documentation

```java
@Schema(description = "User information")
public class UserDto {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john_doe", required = true)
    private String username;

    @Schema(description = "Email address", example = "john@example.com", required = true)
    private String email;

    @Schema(description = "User status", example = "ACTIVE")
    private UserStatus status;
}
```

### Request Body Documentation

```java
@Schema(description = "Create user request")
public class CreateUserRequest {

    @Schema(description = "Username", example = "john_doe", required = true)
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @Schema(description = "Email", example = "john@example.com", required = true)
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Password", example = "password123", required = true)
    @NotBlank
    @Size(min = 8)
    private String password;
}
```

## Security Documentation

### JWT Authentication

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("My API")
                .version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

### API Key Authentication

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .addSecurityItem(new SecurityRequirement().addList("apiKey"))
        .components(new Components()
            .addSecuritySchemes("apiKey",
                new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")));
}
```

## Grouping APIs

### By Package

```yaml
springdoc:
  group-configs:
    - group: public-api
      paths-to-match: /api/public/**
      packages-to-scan: com.example.controller.public

    - group: admin-api
      paths-to-match: /api/admin/**
      packages-to-scan: com.example.controller.admin
```

### By Tag

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management")
public class UserController { }

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management")
public class OrderController { }
```

## Customizing Swagger UI

### Theme and Layout

```yaml
springdoc:
  swagger-ui:
    # 레이아웃
    display-request-duration: true
    filter: true
    show-extensions: true
    show-common-extensions: true

    # Try it out 기본 활성화
    try-it-out-enabled: true

    # 응답 포맷
    default-model-rendering: model  # model, example
    default-models-expand-depth: 3

    # OAuth2 설정
    oauth:
      client-id: my-client-id
      client-secret: my-client-secret
```

## Hiding Endpoints

### Hide Specific Endpoints

```java
@Hidden
@GetMapping("/internal")
public void internalEndpoint() { }
```

### Hide by Package

```yaml
springdoc:
  packages-to-exclude: com.example.internal
  paths-to-exclude: /internal/**
```

## Production Considerations

### Disable in Production

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

scalar:
  enabled: false
```

### Restrict Access

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/scalar/**")
                .hasRole("ADMIN")
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```