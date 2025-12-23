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

Scalar는 Swagger UI의 현대적인 대안으로, 더 깔끔하고 사용자 친화적인 API 문서 UI를 제공합니다.

#### 의존성

SimpliX starter에 이미 포함되어 있습니다:
```gradle
// spring-boot-starter-simplix에 포함
api 'com.scalar.maven:scalar'
```

#### 기본 설정

```yaml
scalar:
  enabled: true           # Scalar UI 활성화 (기본값: true)
  url: /api-docs          # OpenAPI spec URL (프로젝트에 맞게 설정)
  path: /scalar           # Scalar UI 접근 경로
```

#### 상세 설정 옵션

```yaml
scalar:
  enabled: true
  url: /api-docs
  path: /scalar

  # 테마 설정
  theme: default          # default, alternate, moon, purple, solarized,
                          # bluePlanet, deepSpace, saturn, kepler, elysiajs,
                          # fastify, mars, none

  # 레이아웃 설정
  layout: modern          # modern, classic

  # 다운로드 옵션
  document-download-type: both  # both, spec, markdown

  # 기타 옵션
  show-sidebar: true
  hide-download-button: false
  hide-models: false
  hide-dark-mode-toggle: false
  dark-mode: false
  force-dark-mode-state: ""     # dark, light (빈 값이면 시스템 설정 따름)

  # 메타데이터
  metadata-title: "API Reference"

  # 인증 설정
  authentication:
    preferred-security-scheme: ""  # 기본 인증 스킴 선택
    api-key:
      token: ""                    # 기본 API 키 값
```

#### 엔드포인트 비교

| Feature | Swagger UI | Scalar |
|---------|-----------|--------|
| 경로 | `/swagger-ui.html` | `/scalar` |
| 디자인 | 클래식 | 모던/미니멀 |
| 다크 모드 | 미지원 | 지원 |
| 코드 예제 | 기본 | 다양한 언어 |
| 검색 | 기본 | 향상된 검색 |
| 모바일 | 제한적 | 반응형 |

#### 보안 설정

Scalar 엔드포인트는 SwaggerSecurityConfig에서 관리됩니다:

```java
@Configuration
public class SwaggerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/swagger-ui/**", "/swagger-ui.html",
                           "/api-docs/**", "/scalar/**")
            .authorizeHttpRequests(auth -> auth
                // localhost에서만 API 문서 접근 허용
                .requestMatchers("/api-docs/**").access((auth2, ctx) ->
                    new AuthorizationDecision(isLocalhostRequest(ctx.getRequest()))
                )
                .anyRequest().permitAll()
            )
            .build();
    }
}
```

#### 문제 해결

**"Failed to fetch document" 오류**

Scalar가 OpenAPI 스펙을 가져오지 못하는 경우:

1. `scalar.url` 설정 확인:
   ```yaml
   scalar:
     url: /api-docs  # springdoc.api-docs.path와 일치해야 함
   ```

2. 보안 설정 확인:
   - `/api-docs/**` 경로가 접근 가능한지 확인
   - localhost 제한이 있는 경우 브라우저에서 직접 접근 테스트

3. CORS 설정 확인:
   ```yaml
   simplix:
     auth:
       cors:
         allowed-origins:
           - http://localhost:8080
   ```

**기본 데모 API가 표시되는 경우**

`scalar.url`이 설정되지 않으면 Scalar 데모 API가 표시됩니다.
반드시 프로젝트의 OpenAPI 스펙 경로를 설정하세요:

```yaml
scalar:
  url: /api-docs  # 또는 /v3/api-docs
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