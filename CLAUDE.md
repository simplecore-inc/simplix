# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

SimpliX is a modular Spring Boot starter library that provides common enterprise features. It's built as a multi-module Gradle project with Spring Boot 3.5.5 and Java 17.

## Build Commands

```bash
# Build entire project (skip tests)
./gradlew clean build -x test

# Build with tests
./gradlew build

# Build specific module
./gradlew :spring-boot-starter-simplix:build

# Compile with deprecation warnings
./gradlew :module-name:compileJava -Xlint:deprecation

# Run tests for specific module
./gradlew :simplix-core:test

# Run single test
./gradlew :module-name:test --tests "TestClassName.testMethodName"

# Clean build artifacts
./gradlew clean

# Publish to local Maven repository
./gradlew publishToMavenLocal
```

## Architecture

### Module Structure

```
simplix/
├── simplix-core/                      # Core utilities and base classes
│   └── SimpliXBaseEntity, SimpliXBaseRepository, SimpliXBaseService
├── spring-boot-starter-simplix/       # Main auto-configuration module
│   └── autoconfigure/                 # Spring Boot auto-configurations
├── spring-boot-starter-simplix-auth/  # Authentication & authorization
│   └── JWT/JWE token support, security filters
├── spring-boot-starter-simplix-event/ # Event-driven architecture support
│   └── NATS integration, event publishers/listeners
├── spring-boot-starter-simplix-excel/ # Excel/CSV import/export
│   └── POI integration, template processing
└── spring-boot-starter-simplix-mybatis/ # MyBatis integration
    └── Mapper configurations, type handlers
```

### Key Auto-Configuration Classes

The library uses Spring Boot's `@AutoConfiguration` with specific ordering:

1. **SimpliXMessageSourceAutoConfiguration** - Runs before MessageSourceAutoConfiguration and WebMvcAutoConfiguration
2. **SimpliXValidatorAutoConfiguration** - Runs after MessageSourceAutoConfiguration
3. **SimpliXJpaAutoConfiguration** - Runs after HibernateJpaAutoConfiguration
4. **SimpliXTreeRepositoryAutoConfiguration** - Runs after JpaRepositoriesAutoConfiguration
5. **SimpliXThymeleafAutoConfiguration** - Runs before ErrorMvcAutoConfiguration

### Core Components Integration

- **Type Conversion System**: Centralized in `simplix-core` with converters for Boolean, DateTime, Enum types
- **Exception Hierarchy**: Base `SimpliXException` with specialized exceptions per module
- **Repository Pattern**: `SimpliXBaseRepository` extends JpaRepository with tree structure support
- **Service Pattern**: `SimpliXBaseService` provides CRUD operations with ModelMapper integration

### Configuration Properties Pattern

All modules follow the pattern:
```properties
simplix.module-name.feature.property=value
```

Example:
```properties
simplix.message-source.enabled=true
simplix.excel.export.streaming-threshold=100000
simplix.auth.jwt.secret-key=your-secret
```

## Important Configuration Notes

### Locale Resolution
The library configures `CookieLocaleResolver` by default. It overrides Spring Boot's default `AcceptHeaderLocaleResolver` to support locale changes via URL parameters (`?lang=ko`).

### MessageSource Configuration
Combines application and library message sources with proper locale fallback handling. Library messages are prefixed with `simplix_`.

### Dependency Management
Uses Spring Boot BOM for managed dependencies. Additional versions are defined in root `build.gradle`:
- Spring Boot: 3.5.5
- SpringDoc OpenAPI: 2.7.0
- Apache POI: 5.2.4
- ModelMapper: 3.2.0

### GitHub Package Repository
Requires GitHub credentials in `gradle.properties`:
```properties
gpr.user=your_github_username
gpr.token=your_github_personal_access_token
```

## Module Dependencies

```
simplix-core (base)
    ├── spring-boot-starter-simplix (depends on core)
    ├── spring-boot-starter-simplix-auth (depends on starter)
    ├── spring-boot-starter-simplix-event (depends on starter)
    ├── spring-boot-starter-simplix-excel (depends on starter)
    └── spring-boot-starter-simplix-mybatis (depends on starter)
```

## Common Development Tasks

### Adding a New Auto-Configuration
1. Create class in `autoconfigure` package
2. Add `@AutoConfiguration` with ordering if needed
3. Use `@ConditionalOnProperty` for feature toggles
4. Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Working with Type Converters
Core converters are in `simplix-core/convert`. To add custom converter:
1. Extend `TypeConverter<F, T>`
2. Register in `TypeConverterRegistry`
3. Converters are automatically discovered by Excel and other modules

### Debugging Build Issues
```bash
# Check for deprecation warnings
./gradlew build --warning-mode all

# Run with stack trace
./gradlew build --stacktrace

# Get detailed test failure info
./gradlew test --info
```