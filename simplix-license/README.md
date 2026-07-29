# SimpliX License Module

서명된 라이선스 토큰으로 배포본을 판정하고, 기능·수량 제한을 강제하는 모듈입니다.

## Features

- ✔ **서명 검증** - 빌드에 내장된 공개키로만 토큰을 검증하며, 검증 키는 설정으로 바꿀 수 없음
- ✔ **온라인 활성화** - 제품 키로 라이선스 서버에 좌석을 확보하고 머신에 바인딩된 토큰 발급
- ✔ **오프라인 활성화** - 폐쇄망 설치를 위한 활성화 요청 파일 생성과 서명된 응답 등록
- ✔ **하트비트 갱신** - 주기적으로 새 토큰을 받아 폐기·만료·계약 변경을 배포본에 반영
- ✔ **주기적 재검증** - 설정된 주기마다 저장된 등록 정보를 다시 판정
- ✔ **유예 기간** - 만료 후 읽기 전용(READ_ONLY) 또는 기능 제한(RESTRICTED) 운영
- ✔ **기능 게이팅** - `@RequiresFeature`로 라이선스가 부여하지 않은 기능 차단
- ✔ **수량 제한** - 애플리케이션이 등록한 카운터로 신규 등록만 상한에서 차단
- ✔ **요청 단위 강제** - 라이선스 상태에 따라 API 요청을 필터에서 거부
- ✔ **최초 설치 게이트** - 설치가 끝나기 전에는 설치 마법사 경로만 열어 두는 필터
- ✔ **저장소 자동 선택** - JPA가 있으면 DB, 없으면 파일에 등록 정보 보관
- ✔ **모니터링** - Actuator Health Indicator, Micrometer 게이지, 감사 기록

## Quick Start

### 1. Dependency

이 모듈은 umbrella starter(`spring-boot-starter-simplix`)에 포함되지 않습니다. 라이선스가 필요한 애플리케이션만 직접 추가합니다.

```gradle
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix'
    implementation 'dev.simplecore:simplix-license'

    // Optional: DB 기반 등록 정보 저장
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Optional: @RequiresFeature 기능 게이팅
    implementation 'org.springframework.boot:spring-boot-starter-aop'

    // Optional: Health Indicator, 메트릭
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

라이선스 SDK(`dev.accesscore:license-sdk`)는 전용 저장소에서 내려받습니다. `gradle.properties` 또는 환경 변수에 자격 증명을 넣습니다.

```properties
accesscore.gpr.user=github_username
accesscore.gpr.token=github_personal_access_token
```

### 2. 필수 구현

애플리케이션은 자신이 어떤 제품인지 두 개의 빈으로 알려야 합니다. 기본값은 없으며, 없으면 컨텍스트가 기동하지 않습니다.

```java
import dev.accesscore.license.sdk.spi.LicenseSpi.ProductIdentity;
import dev.simplecore.simplix.license.config.VerificationKeyIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LicenseIdentityConfig {

    @Value("${application.version}")
    private String applicationVersion;

    @Bean
    public ProductIdentity productIdentity() {
        return new ProductIdentity() {

            @Override
            public String productCode() {
                return "ACPS-PORTAL";
            }

            @Override
            public String release() {
                return applicationVersion;
            }
        };
    }

    @Bean
    public VerificationKeyIdentity verificationKeyIdentity() {
        return () -> "acps-portal-2026";
    }
}
```

`release()`는 활성화 시 라이선스 서버에 보고되며, 계약된 릴리스 상한과 비교됩니다.

검증 공개키는 `src/main/resources/license-public-key.pem`에 둡니다. 위치가 고정되어 있어 배포본이 스스로 만든 키로 토큰을 검증할 수 없습니다.

### 3. Configuration

설정 접두사는 `application.license`입니다. 다른 SimpliX 모듈의 `simplix.*`와 다릅니다.

```yaml
application:
  license:
    token-path: ./license.key
    state-path: ./license-state.json
    re-verification-interval-minutes: 30
    grace-period-mode: READ_ONLY
    product-key-prefix: ACPS
    activation:
      server-url: https://license.example.com
      heartbeat-enabled: true
```

재검증과 하트비트는 Spring 스케줄링으로 동작하므로 애플리케이션에 `@EnableScheduling`이 필요합니다.

### 4. 라이선스 등록

운영자가 화면에서 제품 키를 입력하면 온라인 활성화가 실행됩니다. 폐쇄망 설치는 활성화 요청 파일을 내려받아 라이선스 서버에 제출하고, 받은 토큰을 등록합니다.

```bash
curl -X POST "http://localhost:8080/api/v1/deployment-license/activate" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"productKey": "ACPS-XXXX-XXXX-XXXX-XXXX"}'
```

개발 환경이나 폐쇄망에서는 발급받은 토큰 파일을 `token-path` 위치에 두고 재기동해도 등록됩니다.

### 5. 기능 게이팅

```java
import dev.accesscore.license.sdk.adapter.RequiresFeature;
import org.springframework.stereotype.Service;

@Service
public class VisitorAnalyticsService {

    @RequiresFeature("analytics")
    public AnalyticsReport buildReport(String siteId) {
        // Runs only when the license grants the analytics feature
        return report;
    }
}
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `application.license.token-path` | `./license.key` | 수동 제공 토큰 파일 경로 |
| `application.license.state-path` | `./license-state.json` | 파일 저장소가 상태를 기록하는 경로 |
| `application.license.re-verification-interval-minutes` | `30` | 재검증 주기 (분, 최소 1) |
| `application.license.grace-period-mode` | `READ_ONLY` | 유예 기간 동작 (READ_ONLY/RESTRICTED) |
| `application.license.product-key-prefix` | `ACPS` | 제품 키 접두사 (체크섬 계산에 포함) |
| `application.license.activation.server-url` | 없음 | 라이선스 서버 주소, 비우면 파일 등록만 가능 |
| `application.license.activation.heartbeat-enabled` | `true` | 하트비트 전송 여부 |
| `application.license.enforcement.http-filter-enabled` | `true` | 요청 단위 강제 필터 등록 여부 |
| `api.version.prefix` | `/api/v1` | 필터가 예외 경로를 계산할 때 쓰는 API 접두사 |
| `simplix.license.setup.token` | 없음 | 원격 설치를 허용하는 부트스트랩 토큰, 비우면 로컬 접속만 허용 |

> ℹ 강제 실행을 끄는 설정은 없습니다. `http-filter-enabled`는 판정을 없애는 것이 아니라 판정을 묻는 위치를 요청 필터에서 애플리케이션 코드로 옮깁니다.

## Architecture

```
simplix-license/
+-- config/
|   +-- LicenseAutoConfiguration        # Bean assembly over the license SDK
|   +-- LicenseActuatorAutoConfiguration# Health indicator and metrics
|   +-- LicenseProperties               # Configuration properties
|   +-- VerificationKeyIdentity         # Key name the product files its key under
|   +-- ContactIdentity                 # Address the deployment answers at
|   +-- LicenseRuntimeHints             # Native image hints
+-- core/
|   +-- LicenseManager                  # Verification orchestration
|   +-- FileLicenseStore                # File-backed registration store
|   +-- LicenseAuditTrail               # Status and lifecycle recording
+-- store/
|   +-- JpaLicenseStore                 # Database-backed registration store
|   +-- LicenseRegistration             # Registration entity
+-- activation/
|   +-- LicenseActivationService        # Online and offline registration
|   +-- LicenseActivationClient         # Activation server transport
|   +-- ActivationServerProbe           # Server identification
|   +-- ActivationFailures              # Failure to exception mapping
+-- enforcement/
|   +-- LicenseEnforcementFilter        # Request-level enforcement
|   +-- FeatureGateAspect               # @RequiresFeature enforcement
|   +-- FeatureActivationAspect         # Administrator activation check
|   +-- FeatureAccess                   # Single feature availability judgement
|   +-- LicenseQuotaGuard               # Quota ceiling guard
+-- setup/
|   +-- InitializationGateFilter        # First-run setup gate
|   +-- LicenseSetupController          # Installer license step
|   +-- EnvSetupController              # Installer env profile step
|   +-- SetupState                      # Setup state SPI
+-- scheduler/
|   +-- LicenseReVerificationScheduler  # Periodic re-verification
|   +-- LicenseHeartbeatScheduler       # Periodic token refresh
+-- controller/
|   +-- DeploymentLicenseRestController # License registration and status API
+-- health/
|   +-- LicenseHealthIndicator          # Actuator health
|   +-- LicenseMetrics                  # Micrometer gauges
+-- integrity/
|   +-- RuntimeIntegrityChecker         # Binary checksum verification
+-- model/
    +-- LicenseState                    # Shared judgement every gate reads
```

## API Endpoints

경로는 애플리케이션의 API 접두사(`api.version.prefix`, 기본 `/api/v1`) 아래에 붙습니다.

### Deployment License

| Method | Endpoint | Permission | Description |
|--------|----------|------------|-------------|
| GET | `/deployment-license/status` | `LICENSE:view` | 현재 판정과 계약 내용, 머신 지문, 검증 키 지문 조회 |
| GET | `/deployment-license/features` | 인증 | 라이선스가 부여하고 활성화된 기능 키 목록 |
| POST | `/deployment-license/activate` | `LICENSE:manage` | 제품 키로 온라인 활성화 |
| GET | `/deployment-license/activation-request` | `LICENSE:manage` | 오프라인 활성화 요청 파일 생성 |
| POST | `/deployment-license/activation-response` | `LICENSE:manage` | 서명된 오프라인 응답 등록 |
| POST | `/deployment-license/heartbeat` | `LICENSE:manage` | 즉시 토큰 갱신 |
| POST | `/deployment-license/deactivate` | `LICENSE:manage` | 좌석 반납 후 로컬 등록 정보 삭제 |

### Setup Wizard

`SetupState` 빈을 등록한 애플리케이션에서만 활성화되며, 설치가 끝나기 전까지만 열립니다.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/system/setup/license` | 설치 단계의 라이선스 상태 조회 |
| POST | `/system/setup/license/server` | 라이선스 서버 주소 확인 및 저장 |
| POST | `/system/setup/license/activate` | 설치 단계의 온라인 활성화 |
| GET | `/system/setup/license/activation-request` | 설치 단계의 오프라인 요청 파일 생성 |
| POST | `/system/setup/license/activation-response` | 설치 단계의 오프라인 응답 등록 |
| GET | `/system/setup/env/profiles` | env 프로필 목록 조회 |
| GET | `/system/setup/env/profiles/{name}` | env 프로필 내용 조회 |
| POST | `/system/setup/env/profiles/{name}` | env 프로필 저장 |
| POST | `/system/setup/env/encryption-key` | 암호화 키 생성 |

## License Status

| Status | 요청 처리 | Description |
|--------|----------|-------------|
| `VALID` | 전체 허용 | 모든 검사 통과 |
| `GRACE_PERIOD` | 모드에 따름 | 만료됐으나 유예 기간 내 |
| `RELEASE_NOT_ENTITLED` | 전체 허용 | 계약된 릴리스 상한을 넘겨 실행 중, 유료 기능은 모두 차단 |
| `EXPIRED` | 차단 | 유예 기간까지 지남 |
| `NOT_ACTIVATED` | 차단 | 등록된 라이선스 없음 |
| `NOT_LOADED` | 차단 | 아직 판정 전 |
| `INVALID_SIGNATURE` | 차단 | 이 배포본의 공개키로 검증되지 않는 토큰 |
| `MACHINE_MISMATCH` | 차단 | 다른 머신에 바인딩된 토큰 |
| `FINGERPRINT_UNAVAILABLE` | 차단 | 머신 지문을 읽을 수 없음 |
| `PRODUCT_MISMATCH` | 차단 | 다른 제품에 발급된 토큰 |
| `SCHEMA_UNSUPPORTED` | 차단 | 이 릴리스가 읽지 못하는 토큰 형식 |
| `REVOKED` | 차단 | 라이선스 서버가 활성화를 폐기함 |
| `CLOCK_TAMPERED` | 차단 | 호스트 시계가 되돌려짐 |
| `INTEGRITY_FAILED` | 차단 | 바이너리 체크섬 불일치 |
| `HEARTBEAT_OVERDUE` | 차단 | 하트비트가 요구 주기를 넘김 |

차단 상태에서도 다음 경로는 열려 있어 운영자가 셸 없이 라이선스를 등록할 수 있습니다.

| 경로 | 이유 |
|------|------|
| `{prefix}/auth/token/issue`, `/refresh`, `/revoke` | 로그인 없이는 라이선스 화면에 접근할 수 없음 |
| `{prefix}/deployment-license/` | 라이선스 등록 화면 |
| `{prefix}/system/setup`, `{prefix}/install` | 최초 설치 마법사 |
| `{prefix}/public/` | 공개 엔드포인트 |
| `/actuator/health`, `/backend/login`, `/backend/logout` | 상태 확인과 로그인 화면 |

## Required Implementations

### ProductIdentity (필수)

라이선스 SDK가 정의하는 인터페이스입니다. 이 배포본이 어떤 제품인지 알려주며, 토큰이 이 제품에 발급된 것인지 판정하는 기준이 됩니다.

```java
public interface ProductIdentity {

    /** 발급자 카탈로그의 제품 코드 */
    String productCode();

    /** 실행 중인 릴리스, 활성화 시 보고되어 상한과 비교됨 */
    String release();
}
```

### VerificationKeyIdentity (필수)

토큰이 어떤 이름의 키로 서명됐는지, 내장 공개키를 어떤 이름으로 등록할지 결정합니다.

```java
public interface VerificationKeyIdentity {
    String verificationKeyId();
}
```

### 선택 구현

| 인터페이스 | 주요 메서드 | 등록 시 동작 |
|-----------|------------|-------------|
| `FeatureCatalogue` | `gatedFeatures()` | 이 배포본이 게이팅하는 기능 키 목록을 라이선스 서버에 보고 |
| `QuotaCounter` | `countedQuotaCodes()`, `count(String)` | 수량 제한 검사에 쓰이는 현재 개수 제공, 없으면 상한이 강제되지 않음 |
| `FeatureActivationChecker` | `isEffectivelyActive(String)` | 라이선스가 부여했더라도 관리자가 끈 기능을 차단 |
| `AuditRecorder` | `recordStatusChange`, `recordLifecycle`, `recordLimitReached` | 상태 전이, 등록 이력, 상한 도달을 감사 기록으로 남김 |
| `ActivationServerDirectory` | `serverUrl()` | 라이선스 서버 주소를 설정 대신 애플리케이션이 보관 |
| `ContactIdentity` | `contactEmail()` | 활성화 시 담당자 주소를 함께 보고해 다른 회사 설치본에 키가 잘못 쓰이는 것을 막음 |
| `SetupState` | `isInitialized()`, `licenseServerUrl()`, `saveLicenseServerUrl(String)` | 최초 설치 마법사와 설치 게이트 필터 활성화 |
| `LicenseStore` | `load()`, `save(RegistrationRecord)`, `clear()` | 등록 정보를 애플리케이션이 직접 보관, 없으면 JPA 또는 파일 저장소 |

### QuotaCounter 구현 예시

```java
import dev.accesscore.license.sdk.spi.LicenseSpi.QuotaCounter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceQuotaCounter implements QuotaCounter {

    private final DeviceRepository deviceRepository;

    public DeviceQuotaCounter(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public List<String> countedQuotaCodes() {
        return List.of("devices");
    }

    @Override
    public long count(String quotaCode) {
        return deviceRepository.countByDeletedFalse();
    }
}
```

등록 지점에서 `LicenseQuotaGuard`로 상한을 확인합니다.

```java
@Service
public class DeviceRegistrationService {

    private final LicenseQuotaGuard quotaGuard;

    public DeviceRegistrationService(LicenseQuotaGuard quotaGuard) {
        this.quotaGuard = quotaGuard;
    }

    public Device register(DeviceCreateDTO dto) {
        quotaGuard.require("devices");
        return deviceRepository.save(dto.toEntity());
    }
}
```

계약이 이미 설치된 수량보다 줄어들어도 기존 설비는 계속 동작합니다. 신규 등록만 거부되며, 초과분은 라이선스 화면과 감사 기록에 남습니다.

## Storage

| 저장소 | 조건 | 특징 |
|--------|------|------|
| `JpaLicenseStore` | JPA가 클래스패스에 있음 | 컨테이너를 교체해도 볼륨 없이 라이선스 유지 |
| `FileLicenseStore` | JPA 없음 | `state-path`에 상태 기록, 볼륨 마운트 필요 |
| 직접 구현 | `LicenseStore` 빈 등록 | 애플리케이션이 보관 위치 결정 |

DB 저장소는 `license_registration` 테이블 한 개를 사용하며 항상 한 행만 유지합니다.

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health/license
```

**Response:**
```json
{
  "status": "UP",
  "details": {
    "status": "VALID",
    "lastChecked": "2026-07-29T02:10:00Z",
    "licenseId": "LIC-0001",
    "productCode": "ACPS-PORTAL",
    "tierLabel": "Enterprise",
    "customer": "Example Corp",
    "expiresAt": "2027-07-29T00:00:00Z",
    "features": ["analytics", "reporting"]
  }
}
```

유예 기간에는 `GRACE_PERIOD` 상태를 반환하고, 그 밖의 차단 상태에서는 `DOWN`입니다.

### Metrics

| Metric | Description |
|--------|-------------|
| `license.valid` | 배포본이 계속 동작 가능한 상태면 1, 아니면 0 |
| `license.days.remaining` | 만료까지 남은 일수 |
| `license.grace.period` | 유예 기간이면 1, 아니면 0 |

## Documentation

| Document | Description |
|----------|-------------|
| [Overview](docs/ko/overview.md) | 모듈 아키텍처와 판정 흐름 |
| [Activation Guide](docs/ko/activation-guide.md) | 온라인·오프라인 라이선스 등록 절차 |
| [Enforcement Guide](docs/ko/enforcement-guide.md) | 기능 게이팅과 수량 제한 적용 |
| [Configuration Reference](docs/ko/configuration.md) | 설정 옵션 전체 목록 |

## Requirements

- Java 17+
- Spring Boot 3.5+
- spring-boot-starter-simplix
- 라이선스 SDK 저장소 접근 자격 증명
- (Optional) Spring Data JPA - DB 저장소 사용 시
- (Optional) Spring AOP - `@RequiresFeature` 사용 시
- (Optional) Spring Boot Actuator, Micrometer - 모니터링 사용 시

## License

SimpleCORE License 1.0 (SCL-1.0)
