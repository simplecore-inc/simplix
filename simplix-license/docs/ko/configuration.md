# License 설정 레퍼런스

SimpliX License 모듈의 설정 옵션 전체 목록입니다.

> ⚠ 설정 접두사는 `application.license`입니다. 다른 SimpliX 모듈의 `simplix.*`와 다릅니다.

## 빠른 설정

```yaml
application:
  license:
    activation:
      server-url: https://license.example.com
```

나머지 값은 기본값으로 동작합니다. 모듈을 끄는 설정은 없으며, 의존성을 추가한 시점부터 강제가 적용됩니다.

---

## 설정 섹션

### 저장 경로

```yaml
application:
  license:
    token-path: ./license.key
    state-path: ./license-state.json
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.license.token-path` | String | `./license.key` | 수동 제공 토큰을 읽는 경로. 저장소에 등록 정보가 없거나 거부 상태일 때 사용 |
| `application.license.state-path` | String | `./license-state.json` | 파일 저장소가 상태를 기록하는 경로. JPA 저장소를 쓰면 사용되지 않음 |

### 판정 주기와 만료 동작

```yaml
application:
  license:
    re-verification-interval-minutes: 30
    grace-period-mode: READ_ONLY
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.license.re-verification-interval-minutes` | int | `30` | 재검증 주기 (분). 최소 1 |
| `application.license.grace-period-mode` | enum | `READ_ONLY` | 유예 기간 동작 |

**`grace-period-mode` 값:**

| 값 | 동작 |
|----|------|
| `READ_ONLY` | GET, HEAD, OPTIONS만 허용 |
| `RESTRICTED` | 모든 요청 허용, 기능 제한만 적용 |

### 제품 키

```yaml
application:
  license:
    product-key-prefix: ACPS
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.license.product-key-prefix` | String | `ACPS` | 제품 키 접두사. 체크섬 계산에 포함되므로 다른 제품군 키는 검증에 실패 |

### 온라인 활성화

```yaml
application:
  license:
    activation:
      server-url: https://license.example.com
      heartbeat-enabled: true
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.license.activation.server-url` | String | 없음 | 라이선스 서버 주소. 비우면 파일·오프라인 등록만 가능 |
| `application.license.activation.heartbeat-enabled` | boolean | `true` | 하트비트 전송 여부. 라이선스가 하트비트를 요구할 때만 실제로 전송 |

애플리케이션이 `ActivationServerDirectory` 빈을 등록하면 그 값이 설정보다 우선합니다. 설치 단계에서 확인한 주소를 그대로 쓰는 경로입니다.

### 강제 지점

```yaml
application:
  license:
    enforcement:
      http-filter-enabled: true
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `application.license.enforcement.http-filter-enabled` | boolean | `true` | 요청 단위 강제 필터 등록 여부 |

`false`로 두면 필터가 등록되지 않고, 애플리케이션이 `LicenseState`나 `FeatureAccess`를 직접 호출해 판정을 적용합니다. 판정 자체는 끌 수 없습니다.

---

## 연관 설정

라이선스 모듈이 읽지만 다른 곳에서 관리하는 값입니다.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `api.version.prefix` | String | `/api/v1` | 필터가 예외 경로를 계산할 때 쓰는 API 접두사 |
| `simplix.license.setup.token` | String | 없음 | 설치 마법사에 원격 접근을 허용하는 부트스트랩 토큰. 비우면 로컬 접속만 허용 |
| `APP_BINARY_HASH` | String | 없음 | 배포된 바이너리의 기대 체크섬. 설정하면 무결성 검사에 사용 |

---

## 고정된 값 (설정 불가)

다음은 의도적으로 설정으로 노출하지 않습니다. 온프레미스 배포본이 스스로 제어할 수 있으면 검증이 무력화되기 때문입니다.

| 항목 | 값 | 이유 |
|------|-----|------|
| 검증 공개키 위치 | `classpath:license-public-key.pem` | 배포본이 자기가 만든 키로 토큰을 검증하지 못하게 함 |
| 강제 실행 여부 | 항상 켜짐 | 우회 플래그를 만들지 않기 위함 |
| 검증 키 이름 | `VerificationKeyIdentity` 빈 | 컴파일된 코드에 두어 설정 파일 수정으로 바꾸지 못하게 함 |
| 제품 코드 | `ProductIdentity` 빈 | 같은 이유 |

개발 환경도 같은 검증 경로를 거치며, 개발용 채널로 발급된 라이선스를 사용합니다.

---

## 환경 변수

Spring Boot의 완화된 바인딩 규칙이 적용됩니다.

| Property | Environment Variable |
|----------|---------------------|
| `application.license.token-path` | `APPLICATION_LICENSE_TOKEN_PATH` |
| `application.license.state-path` | `APPLICATION_LICENSE_STATE_PATH` |
| `application.license.re-verification-interval-minutes` | `APPLICATION_LICENSE_REVERIFICATIONINTERVALMINUTES` |
| `application.license.grace-period-mode` | `APPLICATION_LICENSE_GRACEPERIODMODE` |
| `application.license.product-key-prefix` | `APPLICATION_LICENSE_PRODUCTKEYPREFIX` |
| `application.license.activation.server-url` | `APPLICATION_LICENSE_ACTIVATION_SERVERURL` |
| `application.license.activation.heartbeat-enabled` | `APPLICATION_LICENSE_ACTIVATION_HEARTBEATENABLED` |
| `application.license.enforcement.http-filter-enabled` | `APPLICATION_LICENSE_ENFORCEMENT_HTTPFILTERENABLED` |

---

## 환경별 설정

### 개발

```yaml
application:
  license:
    token-path: ./license-dev.key
    state-path: ./build/license-state.json
    re-verification-interval-minutes: 60
    grace-period-mode: RESTRICTED
    activation:
      heartbeat-enabled: false
```

개발용 채널 라이선스 토큰을 파일로 제공하고, 하트비트는 끕니다. 검증 경로는 운영과 같습니다.

### 운영 (온라인)

```yaml
application:
  license:
    grace-period-mode: READ_ONLY
    activation:
      server-url: https://license.example.com
      heartbeat-enabled: true
```

JPA를 클래스패스에 두어 DB 저장소를 사용하면 컨테이너를 교체해도 라이선스가 유지됩니다.

### 운영 (폐쇄망)

```yaml
application:
  license:
    token-path: /opt/app/license.key
    state-path: /opt/app/data/license-state.json
    grace-period-mode: READ_ONLY
    activation:
      server-url: ""
      heartbeat-enabled: false
```

파일 저장소를 쓴다면 `state-path`를 볼륨에 두어야 재배포 후에도 상태가 유지됩니다.

---

## 필요한 애플리케이션 빈

| 빈 | 필수 | 없을 때 |
|----|------|---------|
| `ProductIdentity` | 예 | 컨텍스트 기동 실패 |
| `VerificationKeyIdentity` | 예 | 컨텍스트 기동 실패 |
| `FeatureCatalogue` | 아니오 | 기능 목록을 서버에 보고하지 않음 |
| `QuotaCounter` | 아니오 | 상한이 강제되지 않음 (기동 시 경고) |
| `FeatureActivationChecker` | 아니오 | 관리자 활성화 게이트 비활성 |
| `AuditRecorder` | 아니오 | 감사 기록 없음 |
| `ActivationServerDirectory` | 아니오 | 설정의 `server-url` 사용 |
| `ContactIdentity` | 아니오 | 담당자 주소를 보고하지 않음 |
| `SetupState` | 아니오 | 설치 마법사와 설치 게이트 비활성 |
| `LicenseStore` | 아니오 | JPA 또는 파일 저장소 자동 선택 |

검증 공개키 파일(`src/main/resources/license-public-key.pem`)도 필수입니다. 없으면 기동 시 키를 읽지 못해 실패합니다.

---

## 관련 문서

- [모듈 개요](./overview.md) - 아키텍처와 판정 흐름
- [활성화 가이드](./activation-guide.md) - 라이선스 등록 절차
- [강제 적용 가이드](./enforcement-guide.md) - 기능 게이팅과 수량 제한
