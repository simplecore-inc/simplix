[English](../../README.md) | 한국어

# SimpliX Framework

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-SCL--1.0-blue.svg)](../../LICENSE)

빠른 엔터프라이즈 애플리케이션 개발을 위한 모듈형 Spring Boot 스타터 라이브러리입니다.

> [!TIP]
> `spring-boot-starter-simplix` 하나로 모든 모듈을 사용할 수 있습니다!

## 주요 기능

SimpliX는 엔터프라이즈 애플리케이션 구축을 위한 포괄적인 모듈 세트를 제공합니다:

| 모듈 | 설명 |
|------|------|
| **simplix-core** | 핵심 유틸리티, 베이스 엔티티/리포지토리, 트리 구조, 보안 유틸리티, 표준화된 예외 및 API 응답, 유니크 검증(@Unique), I18n 번역(@I18nTrans) |
| **simplix-auth** | Spring Security 통합 JWT/JWE 토큰 인증 |
| **simplix-cache** | Caffeine(로컬) 및 Redis(분산) 지원 SPI 기반 캐싱 |
| **simplix-encryption** | 다중 키 프로바이더(Simple, Managed, Vault) 및 키 로테이션 지원 데이터 암호화 |
| **simplix-event** | NATS 메시징 지원 이벤트 기반 아키텍처 |
| **simplix-excel** | Apache POI 통합 Excel 및 CSV 가져오기/내보내기 |
| **simplix-file** | 로컬 파일시스템, AWS S3, Google Cloud Storage 지원 파일 스토리지 추상화 |
| **simplix-email** | 템플릿 지원 멀티 프로바이더 이메일 서비스 (SMTP, AWS SES, SendGrid, Resend) |
| **simplix-hibernate** | Ehcache, Redis, Hazelcast 지원 Hibernate L2 캐시 관리 |
| **simplix-mybatis** | 커스텀 타입 핸들러 포함 MyBatis 통합 |
| **spring-boot-starter-simplix** | 모든 모듈을 포함하는 자동 구성 통합 스타터 |

## 빠른 시작

### 1. 의존성 추가

모든 SimpliX 기능 사용 (통합 스타터):

```gradle
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix:${version}'
}
```

또는 필요한 개별 모듈만 추가:

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-core:${version}'
    implementation 'dev.simplecore:simplix-auth:${version}'
    // ... 기타 모듈
}
```

### 2. GitHub Packages 저장소 설정

SimpliX는 GitHub Packages에 배포됩니다. `settings.gradle`에 다음을 추가하세요:

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

`gradle.properties`에 GitHub 자격 증명을 생성하세요:

```properties
gpr.user=your_github_username
gpr.token=your_github_personal_access_token
```

> [!IMPORTANT]
> GitHub 토큰에 `read:packages` 권한이 필요합니다. [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)에서 생성하세요.

### 3. 기본 설정

```yaml
simplix:
  core:
    enabled: true
  date-time:
    default-timezone: Asia/Seoul
    use-utc-for-database: true
  i18n:
    default-locale: en
    supported-locales: [en, ko, ja]
```

### 4. SimpliX 컴포넌트 사용

```java
// 엔티티
@Entity
public class User extends SimpliXBaseEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
}

// 리포지토리
public interface UserRepository extends SimpliXBaseRepository<User, Long> {
    Optional<User> findByUsername(String username);
}

// 서비스
@Service
public class UserService extends SimpliXBaseService<User, Long> {
    public UserService(UserRepository repository, EntityManager em) {
        super(repository, em);
    }
}

// 컨트롤러
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

## 모듈 아키텍처

```
SimpliX Framework
|
+-- simplix-core ----------------- Base Library (no auto-config)
|   |
|   +-- spring-boot-starter-simplix - Umbrella Starter
|       |
|       +-- simplix-auth ------------ Authentication
|       +-- simplix-cache ----------- Caching
|       +-- simplix-encryption ------ Encryption
|       +-- simplix-event ----------- Event
|       +-- simplix-excel ----------- Excel/CSV
|       +-- simplix-file ------------ File Storage
|       +-- simplix-email ----------- Email
|       +-- simplix-hibernate ------- L2 Cache
|       +-- simplix-mybatis --------- MyBatis
```

## 튜토리얼

SimpliX를 시작하기 위한 단계별 가이드:

| 가이드 | 설명 |
|--------|------|
| [빠른 시작](./quick-start.md) | 프로젝트 설정 및 기본 구성 |
| [CRUD 튜토리얼](./crud-tutorial.md) | Entity, Repository, Service, Controller 구현 |
| [애플리케이션 설정](../../spring-boot-starter-simplix/docs/ko/application-setup.md) | 메인 클래스 및 어노테이션 설정 |
| [보안 통합](./security-integration.md) | Spring Security 및 JWE 토큰 인증 |
| [문서 기여 가이드](./documentation-guide.md) | 문서 빌드, 미리보기, 기여 방법 |

## 문서

각 모듈은 상세한 문서가 포함된 README를 제공합니다:

- [simplix-core](../../simplix-core/README.md) - 핵심 유틸리티 및 베이스 클래스
- [simplix-auth](../../simplix-auth/README.md) - 인증 및 권한 부여
- [simplix-cache](../../simplix-cache/README.md) - 캐싱 추상화
- [simplix-encryption](../../simplix-encryption/README.md) - 데이터 암호화
- [simplix-event](../../simplix-event/README.md) - 이벤트 시스템
- [simplix-excel](../../simplix-excel/README.md) - Excel/CSV 처리
- [simplix-file](../../simplix-file/README.md) - 파일 스토리지
- [simplix-email](../../simplix-email/README.md) - 이메일 서비스
- [simplix-hibernate](../../simplix-hibernate/README.md) - Hibernate L2 캐시
- [simplix-mybatis](../../simplix-mybatis/README.md) - MyBatis 통합
- [spring-boot-starter-simplix](../../spring-boot-starter-simplix/README.md) - 통합 스타터

## 보안

SimpliX는 엔터프라이즈 보안을 고려하여 설계되었습니다:

| 기능 | 설명 |
|------|------|
| **OWASP Top 10** | 일반적인 웹 취약점 방어 |
| **XSS 방지** | `HtmlSanitizer`를 통한 내장 HTML 살균 |
| **SQL Injection 방지** | 파라미터화된 쿼리 및 `SqlInjectionValidator` |
| **데이터 암호화** | 키 로테이션 지원 AES-256 암호화 |
| **데이터 마스킹** | 로그에서 PII 자동 마스킹 (`DataMaskingUtils`, `IpAddressMaskingUtils`) |
| **JWT/JWE 토큰** | 암호화를 통한 안전한 토큰 기반 인증 |
| **BCrypt 비밀번호** | Spring Security를 통한 안전한 비밀번호 해싱 |

> [!WARNING]
> GitHub 토큰이 포함된 `gradle.properties`를 버전 관리에 커밋하지 마세요. `.gitignore`에 추가하세요.

## 요구 사항

- Java 17+
- Spring Boot 3.5.x
- Gradle 8.5+

## 소스에서 빌드

```bash
# 저장소 클론
git clone https://github.com/simplecore-inc/simplix.git
cd simplix

# 빌드 (테스트 제외)
./gradlew clean build -x test

# 테스트 포함 빌드
./gradlew build

# 로컬 Maven 저장소에 배포
./gradlew publishToMavenLocal
```

## 라이선스

이 프로젝트는 [SimpleCORE License 1.0 (SCL-1.0)](./license.md) 라이선스를 따릅니다.

## 개발팀

SimpliX는 [SimpleCORE Inc.](https://simplecore.kr) CoreLabs에서 만들고 있습니다.

- **Website**: [simplecore.kr](https://simplecore.kr)
- **GitHub**: [github.com/simplecore-inc](https://github.com/simplecore-inc)
- **Contact**: license@simplecore.kr
