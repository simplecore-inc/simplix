# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

SimpliX is a modular Spring Boot starter library that provides common enterprise features. It's built as a multi-module Gradle project with Spring Boot 3.5.7 and Java 17.

## License

This project is licensed under **SimpleCORE License 1.0 (SCL-1.0)**. Always use this license information when creating README files or documentation.

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

### MessageSource Configuration
Combines application and library message sources with proper locale fallback handling. Library messages are prefixed with `simplix_`.

### Dependency Management
Uses Spring Boot BOM for managed dependencies. Additional versions are defined in root `build.gradle`:
- Spring Boot: 3.5.7
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

# Generate dependency report
./gradlew dependencies
```

## CRITICAL PROJECT DEVELOPMENT RULES
**These rules are absolutely mandatory and must be referenced for all development work!**

### CRITICAL SUMMARY (Zero Tolerance)
- Absolutely NO emojis in code, documentation, or comments
- Use only log-symbols type symbols (✔ ✖ ⚠ ℹ) for status indicators
- English-only for all artifacts (code, comments, documentation, commit messages)
- Do not generate debug logs unless explicitly requested
- Do not add defensive logic for uncertain situations
- Do not write retry logic for uncertain states
- Do not provide estimated time or duration when explaining implementation plans

### General Development Rules

#### Language Requirements
- **ALL code, comments, test names, and documentation must be written in English**
- **ALL explanations and responses to the user must be written in Korean**
- This includes:
  - Variable names, method names, class names (English only)
  - Comments and JavaDoc (English only)
  - Test method names and @DisplayName annotations (English only)
  - Log messages and debug output (English only)
  - Exception messages (English only)
  - Explanations, analysis, and responses to user (Korean only)
- User-facing text should use i18n (messages.properties) for localization

#### Symbol Usage Guidelines
- Use ONLY the following symbols for status indicators in code, logs, and documentation:
  - ✔ (success/completed/ok)
  - ✖ (error/failed/no)
  - ⚠ (warning/caution)
  - ℹ (info/note)
- NEVER use emojis or other Unicode symbols outside the approved list

#### Code Style Guidelines
- Do not add comments to indicate changes from previous code versions
- Find and solve the actual root cause; do not insert defensive logic
- Do not change the specified implementation approach unless explicitly instructed
- Do not write fallback or backup functionality except when errors are unavoidable

#### Naming and Comments
- Use clear, descriptive English variable and function names
- Avoid abbreviations unless they are widely accepted standards
- Write concise comments for complex logic; use proper grammar and punctuation

#### Git Commit Messages
- ALL commit messages MUST be written in English
- Use the conventional commit format: feat:, fix:, docs:, refactor:, test:, etc.
- Be clear and descriptive about what changed and why
- NEVER add AI-related signatures like "Generated with Claude Code" or "Co-Authored-By: Claude"
- Keep commit messages clean and professional without any AI attributions
- Do not include Claude-related content in commit messages
- Do NOT add "Generated with Claude Code" or "Co-Authored-By: Claude" to commit messages

#### Development Workflow
- Do not perform git commit/push operations unless explicitly requested
- When the same problem occurs more than twice, consult official documentation
- File Size Management: If a file exceeds 500 lines, prioritize refactoring and propose solutions
- Refactoring Safety: Ensure no duplicate functionality remains; fully remove obsolete code

### Java/Spring Specific Guidelines

#### Core Principles
- Follow Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Follow Spring Boot best practices and conventions
- Implement proper exception handling with meaningful messages
- Use Spring Data JPA repositories for database operations
- Follow RESTful API design principles
- Use Bean Validation annotations for input validation
- Use Spring Security for authentication and authorization
- Follow Spring Boot configuration management best practices
- For logging and console output, use only approved symbols (✔ ✖ ⚠ ℹ) instead of emojis

### Testing Guidelines

#### General
- Write unit tests for business logic
- Write integration tests for Spring components
- Use JUnit 5 and Mockito
- Co-locate tests with the code they test
- NEVER modify test conditions or add hardcoding to make tests pass; seek feedback if tests are wrong
- Solve one problem at a time systematically

### Performance Considerations
- Use Spring Boot's performance monitoring
- Optimize database queries and indexes
- Apply caching strategies when appropriate
- Monitor and review application performance metrics

### Documentation Guidelines
- **All documentation files MUST be created in the `docs/` folder**
- Never create documentation files in the project root directory
- Do not create new documentation files unless explicitly requested
- Maintain existing documentation only when specifically asked
- Prefer concise inline comments for complex logic over separate docs

#### Documentation Update Requirements
- **ALWAYS review related documentation** when code is added or modified
- **ALWAYS update documentation** to reflect the latest code changes
- This includes:
  - Module README.md files
  - API documentation in docs/ folders
  - Configuration examples and property descriptions
  - Usage examples and code snippets
- If documentation is outdated or missing, update it as part of the code change
- **NEVER include specific version numbers** in documentation (e.g., `1.0.6`, `3.5.7`)
  - Versions change frequently and cause documentation to become outdated
  - Use `${version}` placeholder or omit version entirely in examples
  - Exception: CLAUDE.md may contain versions for internal reference only

#### Documentation Folder Structure
- **Korean documentation**: Place in `docs/ko/` folder (e.g., `docs/ko/README.md`, `docs/ko/usage-guide.md`)
- **English documentation**: Place in `docs/` folder directly (e.g., `docs/README.md`)
- Do NOT use language suffixes in filenames (e.g., `README_ko.md` is incorrect)
- Use folder-based localization instead of filename-based localization

## Security Guidelines (Enterprise Best Practices)

### Data Protection

#### Encryption Requirements
- **Personal data MUST be encrypted at rest**: Use appropriate encryption converters for PII
- **Create searchable hashes for encrypted unique fields**: For fields that need lookup capability
- **Passwords MUST use BCrypt**: Via `PasswordEncoder`, NEVER store plain text
- **Audit logs MUST mask sensitive data**: IP addresses, User-Agent, and JSON details

### Secure Development

#### Input Validation
- **ALWAYS validate at DTO level** using Bean Validation annotations
- **ALWAYS sanitize HTML input** to prevent XSS
- **NEVER trust client-side validation**

#### SQL Injection Prevention
- **ALWAYS use parameterized queries** (JPA/MyBatis)
- **NEVER concatenate user input** into SQL
- **ALWAYS use @Param** in custom queries

#### XSS Prevention
- **ALWAYS escape output** in templates
- **NEVER render user input as raw HTML**

### Password Security

#### Requirements
- Minimum 8 characters with uppercase, lowercase, digit, special character
- BCrypt hashing in service layer only
- Consider password history to prevent reuse
- Consider account lockout after failed attempts

### Encryption Key Management

#### Production Requirements
- **NEVER use static keys** in production
- **MUST use Vault** or secure key management system for production
- Plan for key rotation

### Prohibited Practices (Automatic Rejection)

1. Plain text passwords or PII storage
2. Logging sensitive data without masking
3. Bypassing authentication/authorization
4. Exposing internal errors to users
5. SQL string concatenation
6. Trusting client input without validation
7. Storing encryption keys in code

### Security Review Triggers

Mandatory review required for:
- New entity storing personal data
- Authentication/authorization changes
- Encryption/hashing implementation
- Access control logic changes
- External API integration
- Audit logging modifications