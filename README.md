[한국어](docs/ko/README.md) | English

# SimpliX Framework

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-SCL--1.0-blue.svg)](LICENSE)

A modular Spring Boot starter library providing common enterprise features for rapid application development.

> [!TIP]
> Use `spring-boot-starter-simplix` to get all modules in one dependency!

## Features

SimpliX provides a comprehensive set of modules for building enterprise applications:

| Module | Description |
|--------|-------------|
| **simplix-core** | Core utilities, base entities/repositories, tree structures, security utilities, standardized exceptions & API responses |
| **simplix-auth** | JWT/JWE token authentication with Spring Security integration |
| **simplix-cache** | SPI-based caching with Caffeine (local) and Redis (distributed) support |
| **simplix-encryption** | Data encryption with multiple key providers (Simple, Managed, Vault) and key rotation |
| **simplix-event** | Event-driven architecture with NATS messaging support |
| **simplix-excel** | Excel and CSV import/export with Apache POI integration |
| **simplix-file** | File storage abstraction supporting local filesystem, AWS S3, and Google Cloud Storage |
| **simplix-email** | Multi-provider email service (SMTP, AWS SES, SendGrid, Resend) with template support |
| **simplix-hibernate** | Hibernate L2 cache management with Ehcache, Redis, Hazelcast support |
| **simplix-mybatis** | MyBatis integration with custom type handlers |
| **simplix-scheduler** | @Scheduled method execution logging and monitoring with ShedLock integration |
| **spring-boot-starter-simplix** | Umbrella starter that includes all modules with auto-configuration |

## Quick Start

### 1. Add Dependency

For all SimpliX features (umbrella starter):

```gradle
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix:${version}'
}
```

Or add individual modules as needed:

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-core:${version}'
    implementation 'dev.simplecore:simplix-auth:${version}'
    // ... other modules
}
```

### 2. Configure GitHub Packages Repository

SimpliX is published to GitHub Packages. Add the following to your `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/simplecore-inc/simplix")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Create `gradle.properties` with your GitHub credentials:

```properties
gpr.user=your_github_username
gpr.token=your_github_personal_access_token
```

> [!IMPORTANT]
> Your GitHub token needs `read:packages` permission. Generate one at [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens).

### 3. Basic Configuration

```yaml
simplix:
  core:
    enabled: true
  date-time:
    default-timezone: Asia/Seoul
    use-utc-for-database: true
```

### 4. Use SimpliX Components

```java
// Entity
@Entity
public class User extends SimpliXBaseEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
}

// Repository
public interface UserRepository extends SimpliXBaseRepository<User, Long> {
    Optional<User> findByUsername(String username);
}

// Service
@Service
public class UserService extends SimpliXBaseService<User, Long> {
    public UserService(UserRepository repository, EntityManager em) {
        super(repository, em);
    }
}

// Controller
@RestController
@RequestMapping("/api/users")
public class UserController extends SimpliXBaseController<User, Long> {
    public UserController(UserService service) {
        super(service);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<User>> getUser(@PathVariable Long id) {
        return service.findById(id)
            .map(user -> ResponseEntity.ok(SimpliXApiResponse.success(user)))
            .orElseThrow(() -> new SimpliXGeneralException(ErrorCode.GEN_NOT_FOUND));
    }
}
```

## Module Architecture

```
SimpliX Framework
│
├── simplix-core ─────────────────── Base library (no auto-config)
│   │
│   └── spring-boot-starter-simplix ─ Umbrella starter
│       │
│       ├── simplix-auth ──────────── Authentication
│       ├── simplix-cache ─────────── Caching
│       ├── simplix-encryption ────── Encryption
│       ├── simplix-event ─────────── Events
│       ├── simplix-excel ─────────── Excel/CSV
│       ├── simplix-file ──────────── File Storage
│       ├── simplix-email ─────────── Email
│       ├── simplix-hibernate ─────── L2 Cache
│       ├── simplix-mybatis ───────── MyBatis
│       └── simplix-scheduler ─────── Scheduler Logging
```

## Tutorials

Step-by-step guides to get started with SimpliX:

| Guide | Description |
|-------|-------------|
| [Quick Start](docs/ko/quick-start.md) | Project setup and basic configuration |
| [CRUD Tutorial](docs/ko/crud-tutorial.md) | Entity, Repository, Service, Controller implementation |
| [Application Setup](spring-boot-starter-simplix/docs/ko/application-setup.md) | Main class and annotation configuration |
| [Security Integration](docs/ko/security-integration.md) | Spring Security and JWE token authentication |

## Documentation

Each module has its own README with detailed documentation:

- [simplix-core](simplix-core/README.md) - Core utilities and base classes
- [simplix-auth](simplix-auth/README.md) - Authentication and authorization
- [simplix-cache](simplix-cache/README.md) - Caching abstraction
- [simplix-encryption](simplix-encryption/README.md) - Data encryption
- [simplix-event](simplix-event/README.md) - Event system
- [simplix-excel](simplix-excel/README.md) - Excel/CSV processing
- [simplix-file](simplix-file/README.md) - File storage
- [simplix-email](simplix-email/README.md) - Email service
- [simplix-hibernate](simplix-hibernate/README.md) - Hibernate L2 cache
- [simplix-mybatis](simplix-mybatis/README.md) - MyBatis integration
- [simplix-scheduler](simplix-scheduler/README.md) - Scheduler execution logging
- [spring-boot-starter-simplix](spring-boot-starter-simplix/README.md) - Umbrella starter

## Security

SimpliX is built with enterprise security in mind:

| Feature | Description |
|---------|-------------|
| **OWASP Top 10** | Protection against common web vulnerabilities |
| **XSS Prevention** | Built-in HTML sanitization with `HtmlSanitizer` |
| **SQL Injection** | Parameterized queries and `SqlInjectionValidator` |
| **Data Encryption** | AES-256 encryption with key rotation support |
| **Data Masking** | Automatic PII masking in logs (`DataMaskingUtils`, `IpAddressMaskingUtils`) |
| **JWT/JWE Tokens** | Secure token-based authentication with encryption |
| **BCrypt Passwords** | Secure password hashing via Spring Security |

> [!WARNING]
> Never commit `gradle.properties` with your GitHub token to version control. Add it to `.gitignore`.

## Requirements

- Java 17+
- Spring Boot 3.5.x
- Gradle 8.5+

## Building from Source

```bash
# Clone the repository
git clone https://github.com/simplecore-inc/simplix.git
cd simplix

# Build (skip tests)
./gradlew clean build -x test

# Build with tests
./gradlew build

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

## License

This project is licensed under the [SimpleCORE License 1.0 (SCL-1.0)](LICENSE).
