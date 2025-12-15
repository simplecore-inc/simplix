# SimpliX Email Module

Spring Boot 애플리케이션을 위한 유연한 다중 Provider 이메일 발송 모듈입니다.

## Features

- ✔ **다중 Provider 지원** - SMTP, AWS SES, SendGrid, Resend
- ✔ **자동 Failover** - Provider 장애 시 자동 전환
- ✔ **템플릿 엔진** - Thymeleaf 기반 이메일 템플릿
- ✔ **비동기 발송** - CompletableFuture 기반 비동기 처리
- ✔ **대량 발송** - 수신자별 개별 변수 지원
- ✔ **다국어 지원** - 로케일 기반 템플릿 선택
- ✔ **첨부 파일** - 일반 첨부 및 인라인 이미지

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-email:${version}'

    // Optional: Provider별 의존성
    implementation 'software.amazon.awssdk:ses:2.x.x'      // AWS SES
    implementation 'com.sendgrid:sendgrid-java:4.x.x'      // SendGrid
    implementation 'com.resend:resend-java:3.x.x'          // Resend
}
```

### 2. Configuration

```yaml
simplix:
  email:
    enabled: true
    provider: SMTP
    from:
      address: noreply@example.com
      name: My Application
    smtp:
      enabled: true
      host: smtp.example.com
      port: 587
      username: user@example.com
      password: ${SMTP_PASSWORD}
      starttls: true
```

### 3. Usage

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    // 간단한 이메일 발송
    public void sendSimpleEmail(String to, String subject, String content) {
        EmailResult result = emailService.send(
            EmailRequest.simple(to, subject, content)
        );

        if (result.isSuccess()) {
            log.info("Email sent: {}", result.getMessageId());
        }
    }

    // 템플릿 이메일 발송
    public void sendWelcomeEmail(User user) {
        emailService.sendTemplate(
            TemplateEmailRequest.builder()
                .templateCode("welcome")
                .to(List.of(EmailAddress.of(user.getName(), user.getEmail())))
                .variables(Map.of("userName", user.getName()))
                .locale(Locale.KOREAN)
                .build()
        );
    }

    // 비동기 발송
    public void sendAsyncEmail(EmailRequest request) {
        emailService.sendAsync(request)
            .thenAccept(result -> log.info("Sent: {}", result.getMessageId()))
            .exceptionally(ex -> {
                log.error("Failed", ex);
                return null;
            });
    }
}
```

## Providers

| Provider | Priority | 용도 | 설정 |
|----------|----------|------|------|
| AWS SES | 100 | Production (AWS) | `aws-ses.enabled: true` |
| Resend | 55 | Production | `resend.enabled: true` |
| SendGrid | 50 | Production | `sendgrid.enabled: true` |
| SMTP | 10 | Self-hosted | `smtp.enabled: true` |
| Console | -100 | Development | `provider: CONSOLE` |

## Template

```
templates/email/
└── welcome/
    ├── en/
    │   ├── subject.txt
    │   └── body.html
    └── ko/
        ├── subject.txt
        └── body.html
```

**subject.txt:**
```
[(${userName})]님, 환영합니다!
```

**body.html:**
```html
<h1>Welcome!</h1>
<p th:text="'Hello, ' + ${userName}">Hello, User</p>
```

## Configuration

```yaml
simplix:
  email:
    enabled: true
    provider: SMTP                    # CONSOLE, SMTP, AWS_SES, SENDGRID, RESEND
    from:
      address: noreply@example.com
      name: My Application
    smtp:
      enabled: true
      host: smtp.example.com
      port: 587
      username: user
      password: secret
    aws-ses:
      enabled: false
      region: us-east-1
    sendgrid:
      enabled: false
      api-key: SG.xxxx
    resend:
      enabled: false
      api-key: re_xxxx
    template:
      base-path: templates/email
    async:
      core-pool-size: 2
      max-pool-size: 10
```

## Documentation

- [Overview (아키텍처 상세)](ko/email/overview.md)
- [Sending Guide (이메일 발송)](ko/email/sending-guide.md)
- [Template Guide (템플릿 사용)](ko/email/template-guide.md)
- [Provider Guide (Provider 설정)](ko/email/provider-guide.md)
- [Advanced Guide (고급 기능)](ko/email/advanced-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
