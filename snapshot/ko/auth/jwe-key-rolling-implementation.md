# JWE 키 롤링 - 애플리케이션 구현 가이드

이 문서는 JWE 키 롤링 기능을 애플리케이션에서 구현하는 방법을 설명합니다.

## 목차

- [사전 요구사항](#사전-요구사항)
- [1단계: 의존성 설정](#1단계-의존성-설정)
- [2단계: 데이터베이스 설정](#2단계-데이터베이스-설정)
- [3단계: JweKeyStore 구현](#3단계-jwekeystore-구현)
- [4단계: 스케줄러 구현](#4단계-스케줄러-구현)
- [5단계: 설정 파일](#5단계-설정-파일)
- [전체 예제](#전체-예제)

---

## 사전 요구사항

- Spring Boot 3.x
- simplix-auth 모듈
- simplix-encryption 모듈
- 데이터베이스 (JPA 사용 시 Hibernate 지원 DB)
- (선택) ShedLock - 분산 환경용

---

## 1단계: 의존성 설정

### Gradle

```groovy
dependencies {
    // SimpliX 모듈
    implementation 'dev.simplecore:spring-boot-starter-simplix-auth'
    implementation 'dev.simplecore:simplix-encryption'

    // JPA (JweKeyStore 구현용)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // 분산 스케줄러 (멀티 노드 환경)
    implementation 'net.javacrumbs.shedlock:shedlock-spring'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template'
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>dev.simplecore</groupId>
        <artifactId>spring-boot-starter-simplix-auth</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.simplecore</groupId>
        <artifactId>simplix-encryption</artifactId>
    </dependency>
    <dependency>
        <groupId>net.javacrumbs.shedlock</groupId>
        <artifactId>shedlock-spring</artifactId>
    </dependency>
    <dependency>
        <groupId>net.javacrumbs.shedlock</groupId>
        <artifactId>shedlock-provider-jdbc-template</artifactId>
    </dependency>
</dependencies>
```

---

## 2단계: 데이터베이스 설정

### JWE 키 테이블

> **Note**: 버전 ID 형식이 `jwe-v{timestamp}-{uuid8}` (예: `jwe-v1702345678901-a1b2c3d4`)로 변경되었습니다. `key_version` 컬럼 길이가 충분한지 확인하세요.

```sql
-- PostgreSQL
CREATE TABLE jwe_keys (
    key_version VARCHAR(50) PRIMARY KEY,
    encrypted_public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    initialization_marker VARCHAR(20),
    CONSTRAINT uk_jwe_keys_init_marker UNIQUE (initialization_marker)
);

CREATE INDEX idx_jwe_keys_active ON jwe_keys(active);
CREATE INDEX idx_jwe_keys_expires_at ON jwe_keys(expires_at);

COMMENT ON COLUMN jwe_keys.initialization_marker IS 'Race condition protection marker (INITIAL or AFTER-{version})';
```

```sql
-- MySQL
CREATE TABLE jwe_keys (
    key_version VARCHAR(50) PRIMARY KEY,
    encrypted_public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6),
    initialization_marker VARCHAR(20),
    CONSTRAINT uk_jwe_keys_init_marker UNIQUE (initialization_marker)
);

CREATE INDEX idx_jwe_keys_active ON jwe_keys(active);
CREATE INDEX idx_jwe_keys_expires_at ON jwe_keys(expires_at);
```

> **Note**: `initialization_marker`의 unique constraint는 분산 환경에서 여러 서버가 동시에 키를 생성하려 할 때 레이스 컨디션을 방지합니다.

### ShedLock 테이블 (분산 환경)

```sql
-- PostgreSQL / MySQL
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL
);
```

---

## 3단계: JweKeyStore 구현

### Entity 클래스

```java
package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "jwe_keys",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_jwe_keys_init_marker", columnNames = "initialization_marker")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JweKeyEntity {

    @Id
    @Column(name = "key_version", length = 50)
    private String keyVersion;

    @Column(name = "encrypted_public_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPublicKey;

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Race condition protection marker.
     * - "INITIAL" for first key creation
     * - "AFTER-{version}" for rotation (e.g., "AFTER-jwe-v1702345678901")
     */
    @Column(name = "initialization_marker", length = 20)
    private String initializationMarker;
}
```

### Repository 인터페이스

```java
package com.example.auth.repository;

import com.example.auth.entity.JweKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JweKeyRepository extends JpaRepository<JweKeyEntity, String> {

    /**
     * Find active key.
     */
    Optional<JweKeyEntity> findByActiveTrue();

    /**
     * Find expired keys.
     */
    List<JweKeyEntity> findByExpiresAtBefore(Instant now);

    /**
     * Deactivate all keys except the specified version.
     */
    @Modifying
    @Query("UPDATE JweKeyEntity k SET k.active = false WHERE k.keyVersion <> :keyVersion")
    int deactivateAllExcept(@Param("keyVersion") String keyVersion);

    /**
     * Delete expired keys.
     */
    @Modifying
    @Query("DELETE FROM JweKeyEntity k WHERE k.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
```

### JweKeyStore 구현체

```java
package com.example.auth.store;

import com.example.auth.entity.JweKeyEntity;
import com.example.auth.repository.JweKeyRepository;
import dev.simplecore.simplix.auth.jwe.store.JweKeyData;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JpaJweKeyStore implements JweKeyStore {

    private final JweKeyRepository repository;

    @Override
    @Transactional
    public JweKeyData save(JweKeyData keyData) {
        JweKeyEntity entity = toEntity(keyData);
        JweKeyEntity saved = repository.save(entity);
        log.info("Saved JWE key: version={}, active={}", saved.getKeyVersion(), saved.isActive());
        return toData(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JweKeyData> findByVersion(String version) {
        return repository.findById(version).map(this::toData);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JweKeyData> findCurrent() {
        return repository.findByActiveTrue().map(this::toData);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JweKeyData> findAll() {
        return repository.findAll().stream()
            .map(this::toData)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deactivateAllExcept(String exceptVersion) {
        int updated = repository.deactivateAllExcept(exceptVersion);
        log.info("Deactivated {} JWE key(s), except version: {}", updated, exceptVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JweKeyData> findExpired() {
        return repository.findByExpiresAtBefore(Instant.now()).stream()
            .map(this::toData)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteByVersion(String version) {
        if (repository.existsById(version)) {
            repository.deleteById(version);
            log.info("Deleted JWE key: version={}", version);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public int deleteExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired JWE key(s)", deleted);
        }
        return deleted;
    }

    // Entity <-> DTO mapping

    private JweKeyEntity toEntity(JweKeyData data) {
        return JweKeyEntity.builder()
            .keyVersion(data.getVersion())
            .encryptedPublicKey(data.getEncryptedPublicKey())
            .encryptedPrivateKey(data.getEncryptedPrivateKey())
            .active(data.isActive())
            .createdAt(data.getCreatedAt())
            .expiresAt(data.getExpiresAt())
            .initializationMarker(data.getInitializationMarker())
            .build();
    }

    private JweKeyData toData(JweKeyEntity entity) {
        return JweKeyData.builder()
            .version(entity.getKeyVersion())
            .encryptedPublicKey(entity.getEncryptedPublicKey())
            .encryptedPrivateKey(entity.getEncryptedPrivateKey())
            .active(entity.isActive())
            .createdAt(entity.getCreatedAt())
            .expiresAt(entity.getExpiresAt())
            .initializationMarker(entity.getInitializationMarker())
            .build();
    }
}
```

---

## 4단계: 스케줄러 구현

### ShedLock 설정

```java
package com.example.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
```

### JWE 키 스케줄러

```java
package com.example.scheduler;

import dev.simplecore.simplix.auth.jwe.provider.DatabaseJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.service.JweKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JweKeyRotationService.class)
@RequiredArgsConstructor
@Slf4j
public class JweKeyScheduler {

    private final JweKeyRotationService rotationService;
    private final DatabaseJweKeyProvider keyProvider;

    /**
     * Key rotation (distributed schedule).
     *
     * Schedule: Every Sunday at 02:00
     *
     * Guide:
     * - Set shorter than refresh-token-lifetime
     * - e.g., 7-day token -> weekly or biweekly rotation
     *
     * Safety: Even if ShedLock fails, the DB unique constraint on
     * initialization_marker prevents duplicate key creation.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    @SchedulerLock(
        name = "jweKeyRotation",
        lockAtMostFor = "10m",
        lockAtLeastFor = "5m"
    )
    public void rotateKey() {
        log.info("Starting scheduled JWE key rotation...");
        String newVersion = rotationService.rotateKey();
        if (newVersion != null) {
            log.info("JWE key rotation completed: {}", newVersion);
        } else {
            log.info("JWE key rotation skipped (another server already completed)");
        }
    }

    /**
     * 캐시 갱신 (로컬 스케줄)
     *
     * 실행 주기: 5분마다
     *
     * 목적: 다른 노드에서 로테이션된 키를 로드
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void refreshCache() {
        log.debug("Refreshing JWE key cache...");
        try {
            keyProvider.refresh();
        } catch (Exception e) {
            log.warn("JWE key cache refresh failed: {}", e.getMessage());
        }
    }

    /**
     * 만료 키 정리 (분산 스케줄) - 선택적
     *
     * auto-cleanup: false인 경우 이 스케줄러로 정리
     * 실행 주기: 매일 03:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(
        name = "jweKeyCleanup",
        lockAtMostFor = "5m"
    )
    public void cleanupExpiredKeys() {
        log.info("Starting scheduled JWE key cleanup...");
        try {
            int deleted = rotationService.cleanupExpiredKeys();
            if (deleted > 0) {
                log.info("Cleaned up {} expired JWE key(s)", deleted);
            }
        } catch (Exception e) {
            log.warn("JWE key cleanup failed: {}", e.getMessage());
        }
    }
}
```

### 로테이션 주기 권장 값

| Refresh Token 유효기간 | 권장 로테이션 주기 | Cron 표현식 |
|----------------------|-----------------|------------|
| 1일 | 12시간 | `0 0 */12 * * *` |
| 7일 | 1주일 | `0 0 2 * * SUN` |
| 14일 | 1주일 | `0 0 2 * * SUN` |
| 30일 | 2주일 | `0 0 2 1,15 * *` |

---

## 5단계: 설정 파일

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

simplix:
  # 암호화 설정 (RSA 키 암호화에 사용)
  encryption:
    enabled: true
    provider: configurable
    configurable:
      current-version: v1
      keys:
        v1:
          # 32바이트 AES 키를 Base64로 인코딩
          # 생성: openssl rand -base64 32
          key: ${ENCRYPTION_KEY}
          deprecated: false

  auth:
    enabled: true

    token:
      access-token-lifetime: 1800      # 30분
      refresh-token-lifetime: 604800   # 7일
      enable-token-rotation: true
      enable-blacklist: false

    jwe:
      algorithm: RSA-OAEP-256
      encryption-method: A256GCM

      key-rolling:
        enabled: true
        key-size: 2048
        auto-initialize: true

        retention:
          buffer-seconds: 86400        # 1일
          auto-cleanup: true

logging:
  level:
    dev.simplecore.simplix.auth.jwe: DEBUG
```

### 환경별 설정

```yaml
# application-prod.yml
simplix:
  auth:
    jwe:
      key-rolling:
        key-size: 4096                 # 프로덕션에서는 더 긴 키 사용
        retention:
          buffer-seconds: 172800       # 2일 버퍼
          auto-cleanup: true
```

---

## 전체 예제

### 프로젝트 구조

```
src/main/java/com/example/
├── Application.java
├── config/
│   └── SchedulerConfig.java
├── auth/
│   ├── entity/
│   │   └── JweKeyEntity.java
│   ├── repository/
│   │   └── JweKeyRepository.java
│   └── store/
│       └── JpaJweKeyStore.java
└── scheduler/
    └── JweKeyScheduler.java
```

### Application.java

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 동작 확인

1. **애플리케이션 시작 로그 확인**
   ```
   DatabaseJweKeyProvider initialized with 1 key(s), current version: jwe-v1702345678901
   JweKeyRotationService configured - keySize: 2048 bits, retention: 604800 + 86400 seconds, autoCleanup: true
   ```

2. **토큰 발급 시 kid 헤더 확인**
   ```bash
   # 토큰 디코딩 (헤더 부분)
   echo "eyJraWQiOiJqd2UtdjE3MDIzNDU2Nzg5MDEi..." | cut -d'.' -f1 | base64 -d
   # 출력: {"kid":"jwe-v1702345678901","alg":"RSA-OAEP-256","enc":"A256GCM"}
   ```

3. **DB 확인**
   ```sql
   SELECT version, active, created_at, expires_at
   FROM jwe_keys
   ORDER BY created_at DESC;
   ```

---

## 다음 단계

- [JWE 키 롤링 개요](ko/auth/jwe-key-rolling.md)로 돌아가기
- [simplix-encryption 문서](ko/encryption/) 참조
