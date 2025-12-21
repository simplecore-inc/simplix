# SimpliX MyBatis Module

Spring Boot 애플리케이션을 위한 MyBatis 자동 구성 모듈입니다.

## Features

- ✔ **자동 구성** - SqlSessionFactory, SqlSessionTemplate 자동 생성
- ✔ **기본 설정 적용** - snake_case → camelCase 자동 변환
- ✔ **Mapper 자동 스캔** - @MapperScan 자동 적용
- ✔ **트랜잭션 관리** - @EnableTransactionManagement 포함

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-mybatis:${version}'
}
```

### 2. Configuration

```yaml
mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.example.domain
```

### 3. Mapper Interface

```java
@Mapper
public interface UserMapper {
    List<User> findAll();
    User findById(@Param("id") Long id);
    void insert(User user);
}
```

### 4. Mapper XML

**위치:** `src/main/resources/mapper/UserMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.mapper.UserMapper">
    <select id="findAll" resultType="User">
        SELECT id, user_name, email FROM users
    </select>
</mapper>
```

## 기본 설정

SimpliX MyBatis는 다음 설정을 자동으로 적용합니다:

| 설정 | 값 | 설명 |
|------|-----|------|
| `mapUnderscoreToCamelCase` | `true` | snake_case → camelCase 자동 변환 |
| `callSettersOnNulls` | `true` | NULL 값도 setter 호출 |

## Configuration

```yaml
mybatis:
  enabled: true                              # 모듈 활성화 (기본: true)
  mapper-locations: classpath*:mapper/**/*.xml  # Mapper XML 위치
  type-aliases-package: com.example.domain   # Type Aliases 패키지
  config-location: classpath:mybatis-config.xml  # 설정 파일 (선택)
```

## 비활성화

```yaml
mybatis:
  enabled: false
```

## Documentation

- [Overview (상세 가이드)](ko/mybatis/overview.md)

## Requirements

- Spring Boot 3.x
- Java 17+

## License

SimpleCORE License 1.0 (SCL-1.0)
