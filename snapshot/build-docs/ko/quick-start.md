# Quick Start Guide

SimpliX 프레임워크를 사용하여 Spring Boot 프로젝트를 빠르게 시작하는 가이드입니다.

## 1. 프로젝트 생성

### Spring Initializr 사용

[Spring Initializr](https://start.spring.io/)에서 새 프로젝트를 생성합니다:

- **Project**: Gradle - Groovy
- **Language**: Java
- **Spring Boot**: 3.5.x
- **Java**: 17 이상
- **Dependencies**: Spring Web, Spring Data JPA, Validation

### 프로젝트 구조

```
my-project/
├── src/main/java/com/example/myapp/
│   ├── MyApplication.java           # 메인 애플리케이션
│   ├── config/                       # 설정 클래스
│   ├── domain/
│   │   ├── entity/                   # JPA 엔티티
│   │   └── repository/               # 리포지토리 인터페이스
│   ├── service/                      # 비즈니스 로직
│   ├── web/
│   │   ├── controller/               # REST 컨트롤러
│   │   └── dto/                      # 데이터 전송 객체
│   └── security/                     # 보안 설정
├── src/main/resources/
│   ├── application.yml
│   └── messages/                     # i18n 메시지
└── build.gradle
```

## 2. 의존성 설정

### build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

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

dependencies {
    // SimpliX - 모든 기능 포함
    implementation 'dev.simplecore:spring-boot-starter-simplix:${version}'

    // 또는 개별 모듈만 사용
    // implementation 'dev.simplecore:simplix-core:${version}'
    // implementation 'dev.simplecore:simplix-auth:${version}'

    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Database
    runtimeOnly 'com.h2database:h2'           // 개발용
    // runtimeOnly 'org.postgresql:postgresql' // 프로덕션용

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### settings.gradle

```groovy
rootProject.name = 'my-project'

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/simplecore-inc/simplix")
            credentials {
                username = settings.ext.find('gpr.user') ?: System.getenv("GITHUB_USERNAME")
                password = settings.ext.find('gpr.token') ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### gradle.properties

```properties
# GitHub Package Registry 인증
gpr.user=your_github_username
gpr.token=your_github_personal_access_token
```

> **주의**: `gradle.properties`를 `.gitignore`에 추가하세요.

## 3. 애플리케이션 설정

### MyApplication.java

```java
package com.example.myapp;

import dev.simplecore.simplix.core.tree.factory.SimpliXRepositoryFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.myapp"
})
@EntityScan(basePackages = {
    "com.example.myapp.domain.entity"
})
@EnableJpaRepositories(
    repositoryFactoryBeanClass = SimpliXRepositoryFactoryBean.class,
    basePackages = {
        "com.example.myapp.domain.repository"
    }
)
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### application.yml

```yaml
spring:
  application:
    name: my-application

  # Database
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop    # 개발: create-drop, 프로덕션: validate
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: true

  # Jackson
  jackson:
    time-zone: Asia/Seoul
    serialization:
      write-dates-as-timestamps: false

  # Messages (i18n)
  messages:
    basename: messages/messages,messages/errors
    encoding: UTF-8

# SimpliX Configuration
simplix:
  core:
    enabled: true
  date-time:
    default-timezone: Asia/Seoul
    use-utc-for-database: true
  message-source:
    enabled: true
  exception-handler:
    enabled: true

# Server
server:
  port: 8080

# Swagger/OpenAPI
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

## 4. 기본 엔티티 생성

### BaseEntity.java

SimpliX의 `SimpliXBaseEntity`를 확장한 프로젝트 공통 베이스 엔티티:

```java
package com.example.myapp.domain.entity;

import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity<K> extends SimpliXBaseEntity<K> {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;
}
```

### JPA Auditing 활성화

```java
package com.example.myapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }
            return Optional.of(auth.getName());
        };
    }
}
```

## 5. 애플리케이션 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

실행 후 접속:
- 애플리케이션: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs

## 6. 다음 단계

- [CRUD Tutorial](ko/crud-tutorial.md) - 엔티티, 리포지토리, 서비스, 컨트롤러 구현
- [Application Setup Guide](ko/starter/application-setup.md) - 상세 설정 가이드
- [Configuration Guide](ko/starter/configuration.md) - YAML 설정 속성

## 트러블슈팅

### GitHub Package 인증 실패

```
Could not resolve dev.simplecore:spring-boot-starter-simplix
```

**해결**: `gradle.properties`의 GitHub 토큰 확인. 토큰에 `read:packages` 권한 필요.

### Bean 생성 실패

```
No qualifying bean of type 'EntityManager'
```

**해결**: `spring-boot-starter-data-jpa` 의존성 추가 확인.

### Swagger UI 접속 불가

```
404 Not Found: /swagger-ui.html
```

**해결**: `springdoc-openapi-starter-webmvc-ui` 의존성 확인 (SimpliX starter에 포함됨).