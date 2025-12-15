# SimpliX Email Provider Guide

## Provider Overview

SimpliX Email은 5가지 이메일 Provider를 지원합니다.

| Provider | Priority | Bulk 지원 | 용도 |
|----------|----------|-----------|------|
| AWS SES | 100 | ✔ | Production (AWS 인프라) |
| Resend | 55 | ✔ | Production (개발자 친화적) |
| SendGrid | 50 | ✔ | Production (Twilio) |
| SMTP | 10 | - | Self-hosted |
| Console | -100 | - | Development |

### Provider Selection Guide

```
Development
    +-- Console Provider (default)

Production
    +-- Using AWS --> AWS SES (cost effective)
    +-- Simple setup --> Resend / SendGrid
    +-- Self-hosted mail server --> SMTP
```

---

## Console Provider

### Overview

개발 및 테스트 환경용 Provider입니다. 실제 이메일을 보내지 않고 콘솔에 내용을 출력합니다.

### Configuration

```yaml
simplix:
  email:
    enabled: true
    provider: CONSOLE
```

### Output Example

```
╔══════════════════════════════════════════════════════════════════╗
║                        EMAIL MESSAGE                             ║
╠══════════════════════════════════════════════════════════════════╣
║ From:    Support <support@example.com>                           ║
║ To:      John Doe <john@example.com>                             ║
║ Subject: Welcome to Our Service!                                 ║
╠══════════════════════════════════════════════════════════════════╣
║ Priority: HIGH                                                   ║
║ Tags:    [welcome, onboarding]                                   ║
╠══════════════════════════════════════════════════════════════════╣
║                        TEXT BODY                                 ║
╠══════════════════════════════════════════════════════════════════╣
║ Hi John,                                                         ║
║ Welcome to our service!                                          ║
║                                                                  ║
║ Activate: https://example.com/activate?token=abc123              ║
╠══════════════════════════════════════════════════════════════════╣
║                        HTML BODY                                 ║
╠══════════════════════════════════════════════════════════════════╣
║ <h1>Welcome!</h1>                                                ║
║ <p>Hi John,</p>                                                  ║
║ ...                                                              ║
╚══════════════════════════════════════════════════════════════════╝
```

### Use Cases

- 개발 환경에서 이메일 로직 테스트
- 템플릿 렌더링 확인
- 통합 테스트

---

## SMTP Provider

### Overview

표준 SMTP 프로토콜을 사용하는 Provider입니다. 자체 메일 서버나 Gmail, Office 365 등의 SMTP 서비스를 사용할 수 있습니다.

### Dependencies

```gradle
// spring-boot-starter-mail is included in simplix-email
// No additional dependency required
```

### Configuration

```yaml
simplix:
  email:
    provider: SMTP
    from:
      address: noreply@example.com
      name: My Application

    smtp:
      enabled: true
      host: smtp.example.com
      port: 587
      username: smtp-user@example.com
      password: ${SMTP_PASSWORD}
      starttls: true          # STARTTLS 사용 (587 포트)
      ssl: false              # SSL/TLS 직접 연결 (465 포트)
      connection-timeout: 10000
      timeout: 10000
```

### Gmail SMTP

```yaml
simplix:
  email:
    smtp:
      enabled: true
      host: smtp.gmail.com
      port: 587
      username: your-email@gmail.com
      password: ${GMAIL_APP_PASSWORD}  # App Password required
      starttls: true
```

> Gmail은 2022년 5월부터 "보안 수준이 낮은 앱"을 차단합니다. [앱 비밀번호](https://support.google.com/accounts/answer/185833)를 생성하여 사용하세요.

### AWS SES SMTP Interface

```yaml
simplix:
  email:
    smtp:
      enabled: true
      host: email-smtp.us-east-1.amazonaws.com
      port: 587
      username: ${AWS_SES_SMTP_USERNAME}
      password: ${AWS_SES_SMTP_PASSWORD}
      starttls: true
```

### Office 365 / Microsoft 365

```yaml
simplix:
  email:
    smtp:
      enabled: true
      host: smtp.office365.com
      port: 587
      username: your-email@yourdomain.com
      password: ${OFFICE365_PASSWORD}
      starttls: true
```

### Naver Works (네이버 웍스)

```yaml
simplix:
  email:
    smtp:
      enabled: true
      host: smtp.worksmobile.com
      port: 587
      username: your-email@company.com
      password: ${NAVERWORKS_PASSWORD}
      starttls: true
```

### Features

- **Priority**: 10
- **Bulk Support**: No (개별 발송)
- **Attachments**: ✔
- **Inline Images**: ✔
- **Custom Headers**: ✔

### Troubleshooting

**Connection Refused**
```
javax.mail.MessagingException: Could not connect to SMTP host
```
- 호스트/포트 확인
- 방화벽 설정 확인
- TLS/SSL 설정 확인

**Authentication Failed**
```
javax.mail.AuthenticationFailedException: 535 Authentication failed
```
- 사용자명/비밀번호 확인
- Gmail: 앱 비밀번호 사용 필요
- 2FA 활성화 여부 확인

---

## AWS SES Provider

### Overview

Amazon Simple Email Service (SES)를 사용하는 Provider입니다. 대량 이메일 발송에 최적화되어 있으며 비용 효율적입니다.

### Dependencies

```gradle
dependencies {
    implementation 'software.amazon.awssdk:ses:2.x.x'
}
```

### Configuration

```yaml
simplix:
  email:
    provider: AWS_SES
    from:
      address: noreply@example.com
      name: My Application

    aws-ses:
      enabled: true
      region: us-east-1
      configuration-set: my-email-config  # Optional
      # IAM Role 사용 시 생략 (권장)
      # access-key: ${AWS_ACCESS_KEY_ID}
      # secret-key: ${AWS_SECRET_ACCESS_KEY}
```

### IAM Policy

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ses:SendEmail",
                "ses:SendRawEmail",
                "ses:SendBulkEmail"
            ],
            "Resource": "*"
        }
    ]
}
```

### Sandbox Mode

AWS SES는 기본적으로 샌드박스 모드입니다:

| 제한 | 샌드박스 | 프로덕션 |
|------|----------|----------|
| 수신자 | 검증된 주소만 | 모든 주소 |
| 일일 발송량 | 200통 | 요청 시 증가 |
| 초당 발송량 | 1통/초 | 요청 시 증가 |

**프로덕션 모드 전환:**
1. AWS Console > SES > Account dashboard
2. "Request production access" 클릭
3. 사용 목적 및 발송량 작성

### Configuration Set

Configuration Set을 사용하면 이벤트 추적이 가능합니다:

```yaml
simplix:
  email:
    aws-ses:
      configuration-set: email-tracking
```

AWS Console에서 Configuration Set 생성 후:
- SNS 토픽으로 이벤트 전달
- CloudWatch 메트릭
- Kinesis Data Firehose

### Domain Verification

SES에서 도메인을 검증해야 합니다:

1. AWS Console > SES > Verified identities
2. "Create identity" > Domain
3. DNS 레코드 추가 (DKIM, SPF)

### Features

- **Priority**: 100 (highest)
- **Bulk Support**: ✔ (native)
- **Attachments**: ✔
- **Inline Images**: ✔
- **Custom Headers**: ✔
- **Tags**: ✔

### Pricing (2024 기준)

- 월 62,000통 무료 (EC2에서 발송 시)
- 이후 $0.10 / 1,000통
- 첨부 파일: $0.12 / GB

---

## SendGrid Provider

### Overview

Twilio SendGrid를 사용하는 Provider입니다. 웹 API를 통해 이메일을 발송합니다.

### Dependencies

```gradle
dependencies {
    implementation 'com.sendgrid:sendgrid-java:4.x.x'
}
```

### Configuration

```yaml
simplix:
  email:
    provider: SENDGRID
    from:
      address: noreply@example.com
      name: My Application

    sendgrid:
      enabled: true
      api-key: ${SENDGRID_API_KEY}
```

### API Key Setup

1. SendGrid Console > Settings > API Keys
2. "Create API Key" 클릭
3. "Mail Send" 권한 부여
4. API Key 복사

### Domain Authentication

SendGrid에서 도메인 인증:

1. Settings > Sender Authentication
2. "Authenticate Your Domain" 클릭
3. DNS 레코드 추가

### Features

- **Priority**: 50
- **Bulk Support**: ✔
- **Attachments**: ✔ (Base64 encoded)
- **Inline Images**: ✔
- **Custom Headers**: ✔
- **Categories/Tags**: ✔

### Category & Tags

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Newsletter")
    .htmlBody("<h1>Weekly Update</h1>")
    .tags(List.of("newsletter", "weekly"))  // SendGrid categories
    .build();
```

### Rate Limiting

SendGrid는 Rate Limit 초과 시 HTTP 429를 반환합니다. SimpliX Email은 이를 retryable 에러로 처리합니다.

### Pricing (2024 기준)

| Plan | Price | Emails/day |
|------|-------|------------|
| Free | $0 | 100 |
| Essentials | $19.95/mo | 50,000/mo |
| Pro | $89.95/mo | 100,000/mo |

---

## Resend Provider

### Overview

개발자 친화적인 현대적 이메일 API입니다. 간단한 설정과 뛰어난 개발자 경험을 제공합니다.

### Dependencies

```gradle
dependencies {
    implementation 'com.resend:resend-java:3.x.x'
}
```

### Configuration

```yaml
simplix:
  email:
    provider: RESEND
    from:
      address: noreply@example.com
      name: My Application

    resend:
      enabled: true
      api-key: ${RESEND_API_KEY}
```

### API Key Setup

1. [Resend Dashboard](https://resend.com/api-keys) 접속
2. "Create API Key" 클릭
3. API Key 복사

### Domain Setup

1. Resend Dashboard > Domains
2. "Add Domain" 클릭
3. DNS 레코드 추가 (MX, TXT, DKIM)

### Features

- **Priority**: 55
- **Bulk Support**: ✔
- **Attachments**: ✔ (Base64 encoded)
- **Inline Images**: ✔
- **Tags**: ✔ (name/value pairs)

### Tags

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Welcome")
    .htmlBody("<h1>Welcome!</h1>")
    .tags(List.of("welcome", "onboarding"))
    .build();
```

### Pricing (2024 기준)

| Plan | Price | Emails/month |
|------|-------|--------------|
| Free | $0 | 3,000 |
| Pro | $20/mo | 50,000 |
| Scale | Custom | Unlimited |

---

## Provider Failover

### How It Works

SimpliX Email은 여러 Provider를 동시에 활성화할 수 있으며, 발송 실패 시 자동으로 다음 Provider로 전환합니다.

```
EmailService.send()
    |
    v
+-----------------------------------------+
| Sort providers by priority (descending) |
| [AWS_SES(100), Resend(55), SMTP(10)]    |
+-----------------------------------------+
    |
    v
+-----------------------------------------+
| Try AWS SES (priority: 100)             |
|   +-- Success --> Return result         |
|   +-- Failure (retryable) --> Continue  |
+-----------------------------------------+
    |
    v
+-----------------------------------------+
| Try Resend (priority: 55)               |
|   +-- Success --> Return result         |
|   +-- Failure (retryable) --> Continue  |
+-----------------------------------------+
    |
    v
+-----------------------------------------+
| Try SMTP (priority: 10)                 |
|   +-- Success --> Return result         |
|   +-- Failure --> Return last error     |
+-----------------------------------------+
```

### Multi-Provider Configuration

```yaml
simplix:
  email:
    # Primary provider (참고용, 실제로는 priority 순으로 시도)
    provider: AWS_SES

    from:
      address: noreply@example.com
      name: My Application

    # Enable multiple providers for failover
    aws-ses:
      enabled: true
      region: us-east-1

    sendgrid:
      enabled: true
      api-key: ${SENDGRID_API_KEY}

    smtp:
      enabled: true
      host: smtp.example.com
      port: 587
      username: user
      password: ${SMTP_PASSWORD}
```

### Retryable vs Non-Retryable Errors

| Provider | Retryable | Non-Retryable |
|----------|-----------|---------------|
| SMTP | Connection timeout, Network error | Auth failure |
| AWS SES | Throttling, ServiceUnavailable | Invalid params, Auth failure |
| SendGrid | HTTP 429, 5xx errors | 4xx client errors |
| Resend | Rate limit, 500/503 | Invalid params |

---

## Provider Comparison

### Feature Comparison

| Feature | SMTP | AWS SES | SendGrid | Resend |
|---------|------|---------|----------|--------|
| Bulk Send | - | ✔ | ✔ | ✔ |
| Attachments | ✔ | ✔ | ✔ | ✔ |
| Inline Images | ✔ | ✔ | ✔ | ✔ |
| Event Tracking | - | ✔ | ✔ | ✔ |
| Templates | - | ✔ | ✔ | - |
| API Access | - | ✔ | ✔ | ✔ |

### When to Use

| Scenario | Recommended |
|----------|-------------|
| AWS 인프라 사용 | AWS SES |
| 빠른 설정 필요 | Resend |
| 엔터프라이즈 기능 | SendGrid |
| 자체 서버 보유 | SMTP |
| 개발/테스트 | Console |
| 비용 최소화 | AWS SES |

### Cost Comparison (Monthly, 100,000 emails)

| Provider | Approximate Cost |
|----------|------------------|
| AWS SES | ~$10 |
| SendGrid | ~$90 |
| Resend | ~$40 |
| SMTP | 서버 비용 |

---

## Related Documents

- [Sending Guide (이메일 발송)](ko/email/sending-guide.md) - 기본 이메일 발송
- [Template Guide (템플릿 사용)](ko/email/template-guide.md) - 템플릿 기반 이메일
- [Advanced Guide (고급 기능)](ko/email/advanced-guide.md) - 대량 발송, 멀티테넌시
