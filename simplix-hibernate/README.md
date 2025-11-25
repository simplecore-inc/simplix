# Hibernate Cache Module

Automatic cache management for Hibernate second-level cache.

## Features

- ✔ Zero configuration required
- ✔ Automatic cache eviction on entity changes
- ✔ Local cache support with EhCache
- ✔ Query cache management
- ✔ JPA entity listener integration

## Installation

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'dev.simplecore.simplix:simplix-hibernate:${simplixVersion}'
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