# Hibernate Cache Module

Automatic distributed cache management for Hibernate second-level cache.

## Features

- ✔ Zero configuration required
- ✔ Automatic cache eviction on entity changes
- ✔ Auto-detects best cache provider (Redis > Hazelcast > Local)
- ✔ Distributed cache support
- ✔ Graceful fallback to local cache

## Installation

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-hibernate:${simplixVersion}'
}
```

## Usage

Just add the dependency. The module works automatically!

### Disable (if needed)

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

## Documentation

See [docs/](docs/) for detailed documentation in Korean.

## Requirements

- Spring Boot 3.x
- Hibernate 6.x
- Java 17+