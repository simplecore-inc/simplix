# SimpliX Email Advanced Guide

## Bulk Email

### Overview

`BulkEmailRequest`를 사용하여 대량의 이메일을 효율적으로 발송할 수 있습니다. 각 수신자별로 개별 변수를 지정할 수 있으며, Provider가 지원하는 경우 native bulk send를 활용합니다.

### BulkEmailRequest

```java
BulkEmailRequest request = BulkEmailRequest.builder()
    .templateCode("newsletter")
    .from(EmailAddress.of("Newsletter", "newsletter@example.com"))
    .recipients(List.of(
        BulkRecipient.builder()
            .address(EmailAddress.of("John", "john@example.com"))
            .variables(Map.of("firstName", "John", "unsubscribeToken", "abc123"))
            .build(),
        BulkRecipient.builder()
            .address(EmailAddress.of("Jane", "jane@example.com"))
            .variables(Map.of("firstName", "Jane", "unsubscribeToken", "def456"))
            .build()
    ))
    .commonVariables(Map.of(
        "companyName", "My Company",
        "currentYear", 2024
    ))
    .locale(Locale.KOREAN)
    .priority(MailPriority.LOW)
    .continueOnError(true)
    .batchId("batch-2024-01-15")
    .tags(List.of("newsletter", "january"))
    .build();

BulkEmailResult result = emailService.sendBulk(request);
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `templateCode` | `String` | ✔ | 템플릿 코드 |
| `from` | `EmailAddress` | - | 발신자 |
| `recipients` | `List<BulkRecipient>` | ✔ | 수신자 목록 |
| `subject` | `String` | - | 제목 override |
| `commonVariables` | `Map<String, Object>` | - | 공통 변수 |
| `locale` | `Locale` | - | 로케일 |
| `priority` | `MailPriority` | - | 우선순위 (기본: LOW) |
| `tenantId` | `String` | - | 테넌트 ID |
| `batchId` | `String` | - | 배치 ID (추적용) |
| `tags` | `List<String>` | - | 태그 |
| `continueOnError` | `boolean` | - | 오류 시 계속 여부 (기본: true) |

### BulkRecipient

```java
// Simple recipient
BulkRecipient recipient1 = BulkRecipient.of("user@example.com");

// With variables
BulkRecipient recipient2 = BulkRecipient.of(
    "user@example.com",
    Map.of("firstName", "John", "code", "ABC123")
);

// Builder
BulkRecipient recipient3 = BulkRecipient.builder()
    .address(EmailAddress.of("John Doe", "john@example.com"))
    .variables(Map.of(
        "firstName", "John",
        "personalOffer", "20% discount"
    ))
    .build();
```

### Variable Merging

공통 변수(`commonVariables`)와 개별 변수(`recipient.variables`)가 병합됩니다. 동일한 키의 경우 개별 변수가 우선합니다.

```java
BulkEmailRequest request = BulkEmailRequest.builder()
    .templateCode("promo")
    .commonVariables(Map.of(
        "companyName", "MyCompany",
        "discountRate", "10%"     // 기본 할인율
    ))
    .recipients(List.of(
        BulkRecipient.builder()
            .address(EmailAddress.of("vip@example.com"))
            .variables(Map.of("discountRate", "30%"))  // VIP는 30%
            .build(),
        BulkRecipient.builder()
            .address(EmailAddress.of("regular@example.com"))
            // discountRate는 공통 변수 10% 사용
            .build()
    ))
    .build();
```

### BulkEmailResult

```java
BulkEmailResult result = emailService.sendBulk(request);

// Summary
int total = result.getTotalCount();      // 전체 수신자 수
int success = result.getSuccessCount();  // 성공 수
int failure = result.getFailureCount();  // 실패 수
double rate = result.getSuccessRate();   // 성공률 (0.0 ~ 1.0)

// Check status
if (result.isAllSuccess()) {
    log.info("All emails sent successfully");
} else if (result.hasAnySuccess()) {
    log.warn("Partial success: {} / {}", success, total);
}

// Individual results
for (RecipientResult r : result.getResults()) {
    if (r.isSuccess()) {
        log.info("✔ {}: {}", r.getEmail(), r.getMessageId());
    } else {
        log.error("✖ {}: {}", r.getEmail(), r.getErrorMessage());
    }
}
```

### Async Bulk Send

```java
emailService.sendBulkAsync(request)
    .thenAccept(result -> {
        log.info("Bulk send completed: {} / {} success",
            result.getSuccessCount(), result.getTotalCount());

        // Log failures
        result.getResults().stream()
            .filter(r -> !r.isSuccess())
            .forEach(r -> log.error("Failed: {} - {}",
                r.getEmail(), r.getErrorMessage()));
    })
    .exceptionally(ex -> {
        log.error("Bulk send failed", ex);
        return null;
    });
```

### Best Practices for Bulk Email

1. **배치 크기 제한**: 한 번에 1,000명 이하로 발송
2. **비동기 사용**: 대량 발송은 `sendBulkAsync()` 사용
3. **낮은 우선순위**: `MailPriority.LOW` 설정
4. **오류 처리**: `continueOnError: true`로 설정
5. **배치 ID 사용**: 추적 및 재발송을 위해 `batchId` 설정

---

## Async Configuration

### Default Configuration

```yaml
simplix:
  email:
    async:
      core-pool-size: 2      # 기본 스레드 수
      max-pool-size: 10      # 최대 스레드 수
      queue-capacity: 100    # 큐 용량
      thread-name-prefix: email-async-
```

### Pool Size Tuning

| Scenario | Core | Max | Queue |
|----------|------|-----|-------|
| Low volume (< 100/day) | 2 | 5 | 50 |
| Medium volume (< 1,000/day) | 4 | 10 | 100 |
| High volume (> 10,000/day) | 8 | 20 | 500 |

### Custom Configuration

```java
@Configuration
public class EmailAsyncConfig {

    @Bean
    public TaskExecutor emailAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

---

## Multi-Tenancy

### Overview

SimpliX Email은 멀티테넌시를 지원합니다. 각 이메일 요청에 `tenantId`를 설정하여 테넌트별로 이메일을 구분할 수 있습니다.

### Basic Usage

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Notification")
    .htmlBody("<p>Hello</p>")
    .tenantId("tenant-123")  // Tenant ID
    .build();
```

### With Template

```java
TemplateEmailRequest request = TemplateEmailRequest.builder()
    .templateCode("welcome")
    .to(List.of(EmailAddress.of("user@example.com")))
    .variables(Map.of("userName", "John"))
    .tenantId("tenant-123")
    .build();
```

### Tenant-Aware Template Resolver

```java
@Configuration
public class TenantEmailConfig {

    @Bean
    public DatabaseEmailTemplateResolver databaseEmailTemplateResolver(
            EmailTemplateRepository repository,
            TenantContextHolder tenantContext) {

        return new DatabaseEmailTemplateResolver(
            (code, locale) -> {
                String tenantId = tenantContext.getCurrentTenantId();
                return repository
                    .findByCodeAndLocaleAndTenantId(code, locale.getLanguage(), tenantId)
                    .or(() -> repository.findByCodeAndLocaleAndTenantIdIsNull(code, locale.getLanguage()))
                    .map(this::toTemplateData);
            },
            tenantContext::getCurrentTenantId
        );
    }
}
```

### Tenant-Specific From Address

```java
@Service
@RequiredArgsConstructor
public class TenantEmailService {

    private final EmailService emailService;
    private final TenantConfigRepository tenantConfig;

    public EmailResult sendTenantEmail(String tenantId, EmailRequest request) {
        TenantConfig config = tenantConfig.findByTenantId(tenantId);

        EmailRequest tenantRequest = EmailRequest.builder()
            .from(EmailAddress.of(config.getFromName(), config.getFromAddress()))
            .to(request.getTo())
            .subject(request.getSubject())
            .htmlBody(request.getHtmlBody())
            .tenantId(tenantId)
            .build();

        return emailService.send(tenantRequest);
    }
}
```

---

## Priority Handling

### Priority Levels

| Priority | Value | Use Case |
|----------|-------|----------|
| `CRITICAL` | 10 | 보안 알림, OTP |
| `HIGH` | 5 | 비밀번호 재설정, 주문 확인 |
| `NORMAL` | 3 | 일반 알림 |
| `LOW` | 1 | 뉴스레터, 마케팅 |

### Usage

```java
// Security alert - CRITICAL
EmailRequest securityAlert = EmailRequest.builder()
    .to(List.of(EmailAddress.of(user.getEmail())))
    .subject("Security Alert: New Login Detected")
    .htmlBody(alertHtml)
    .priority(MailPriority.CRITICAL)
    .build();

// Order confirmation - HIGH
EmailRequest orderConfirm = EmailRequest.builder()
    .to(List.of(EmailAddress.of(order.getCustomerEmail())))
    .subject("Order Confirmed")
    .htmlBody(orderHtml)
    .priority(MailPriority.HIGH)
    .build();

// Newsletter - LOW
BulkEmailRequest newsletter = BulkEmailRequest.builder()
    .templateCode("newsletter")
    .recipients(subscribers)
    .priority(MailPriority.LOW)
    .build();
```

### Priority-Based Processing

높은 우선순위의 이메일은 동기적으로, 낮은 우선순위는 비동기적으로 처리하는 패턴:

```java
@Service
@RequiredArgsConstructor
public class SmartEmailService {

    private final EmailService emailService;

    public void send(EmailRequest request) {
        if (request.getPriority().getValue() >= MailPriority.HIGH.getValue()) {
            // Critical/High: Synchronous send
            EmailResult result = emailService.send(request);
            if (!result.isSuccess()) {
                throw new EmailSendException(result.getErrorMessage());
            }
        } else {
            // Normal/Low: Asynchronous send
            emailService.sendAsync(request);
        }
    }
}
```

---

## Error Handling

### EmailResult Status Codes

| Status | Description |
|--------|-------------|
| `PENDING` | 큐에 대기 중 |
| `SENDING` | 발송 중 |
| `SENT` | Provider에 전달됨 |
| `DELIVERED` | 수신자에게 도착 |
| `FAILED` | 발송 실패 |
| `BOUNCED` | 반송됨 |
| `COMPLAINED` | 스팸 신고됨 |
| `SUPPRESSED` | 발송 제외 목록 |

### Error Handling Pattern

```java
public void sendWithErrorHandling(EmailRequest request) {
    EmailResult result = emailService.send(request);

    switch (result.getStatus()) {
        case SENT:
        case DELIVERED:
            log.info("Email sent successfully: {}", result.getMessageId());
            break;

        case FAILED:
            if (result.isRetryable()) {
                log.warn("Retryable error: {}", result.getErrorMessage());
                scheduleRetry(request);
            } else {
                log.error("Non-retryable error: {}", result.getErrorMessage());
                notifyAdmin(request, result);
            }
            break;

        case BOUNCED:
            log.warn("Email bounced: {}", result.getErrorMessage());
            markEmailInvalid(request.getTo().get(0).getAddress());
            break;

        case SUPPRESSED:
            log.info("Email suppressed (unsubscribed)");
            break;

        default:
            log.warn("Unexpected status: {}", result.getStatus());
    }
}
```

### Retry Strategy

```java
@Service
@RequiredArgsConstructor
public class EmailRetryService {

    private final EmailService emailService;
    private final EmailRetryRepository retryRepository;

    @Scheduled(fixedDelay = 60000)  // Every minute
    public void processRetryQueue() {
        List<EmailRetry> pending = retryRepository.findPendingRetries();

        for (EmailRetry retry : pending) {
            if (retry.getRetryCount() >= 3) {
                retry.setStatus(RetryStatus.FAILED);
                retryRepository.save(retry);
                continue;
            }

            EmailResult result = emailService.send(retry.getRequest());

            if (result.isSuccess()) {
                retry.setStatus(RetryStatus.SUCCESS);
            } else if (result.isRetryable()) {
                retry.setRetryCount(retry.getRetryCount() + 1);
                retry.setNextRetryAt(calculateNextRetry(retry.getRetryCount()));
            } else {
                retry.setStatus(RetryStatus.FAILED);
            }

            retryRepository.save(retry);
        }
    }

    private Instant calculateNextRetry(int retryCount) {
        // Exponential backoff: 1min, 5min, 30min
        int[] delays = {1, 5, 30};
        int minutes = delays[Math.min(retryCount, delays.length - 1)];
        return Instant.now().plusSeconds(minutes * 60);
    }
}
```

---

## Monitoring & Logging

### Correlation ID

상관 ID를 사용하여 이메일을 추적합니다:

```java
String correlationId = UUID.randomUUID().toString();

EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Order Confirmation")
    .htmlBody(html)
    .correlationId(correlationId)
    .build();

log.info("Sending email: correlationId={}", correlationId);

EmailResult result = emailService.send(request);

log.info("Email result: correlationId={}, messageId={}, status={}",
    correlationId, result.getMessageId(), result.getStatus());
```

### Tags

Provider가 지원하는 경우 태그를 활용하여 분류합니다:

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Welcome")
    .htmlBody(html)
    .tags(List.of(
        "category:onboarding",
        "type:welcome",
        "source:signup"
    ))
    .build();
```

### Logging Configuration

```yaml
logging:
  level:
    dev.simplecore.simplix.email: DEBUG       # 이메일 모듈 전체
    dev.simplecore.simplix.email.service: INFO
    dev.simplecore.simplix.email.provider: DEBUG
    dev.simplecore.simplix.email.template: DEBUG
```

| Level | Content |
|-------|---------|
| TRACE | 모든 요청/응답 상세 |
| DEBUG | 발송 시도, 결과, 템플릿 처리 |
| INFO | 성공적인 발송, 초기화 |
| WARN | 재시도, fallback 발생 |
| ERROR | 발송 실패, 예외 |

---

## Best Practices

### 1. Default From Address

환경별로 기본 발신자를 설정합니다:

```yaml
# application.yml
simplix:
  email:
    from:
      address: noreply@example.com
      name: My Application

# application-prod.yml
simplix:
  email:
    from:
      address: noreply@mycompany.com
      name: My Company
```

### 2. Template Organization

```
templates/email/
├── auth/
│   ├── welcome/
│   ├── password-reset/
│   ├── email-verify/
│   └── account-locked/
├── order/
│   ├── confirmation/
│   ├── shipped/
│   ├── delivered/
│   └── cancelled/
├── notification/
│   ├── reminder/
│   └── alert/
└── marketing/
    ├── newsletter/
    └── promotion/
```

### 3. Error Handling Pattern

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final EmailService emailService;

    public void sendNotification(User user, String templateCode, Map<String, Object> variables) {
        try {
            TemplateEmailRequest request = TemplateEmailRequest.builder()
                .templateCode(templateCode)
                .to(List.of(EmailAddress.of(user.getName(), user.getEmail())))
                .variables(variables)
                .locale(user.getLocale())
                .correlationId(UUID.randomUUID().toString())
                .build();

            EmailResult result = emailService.sendTemplate(request);

            if (!result.isSuccess()) {
                handleFailure(user, templateCode, result);
            }
        } catch (Exception e) {
            log.error("Failed to send email: user={}, template={}", user.getId(), templateCode, e);
            // Don't throw - email failure shouldn't break business flow
        }
    }

    private void handleFailure(User user, String templateCode, EmailResult result) {
        log.warn("Email failed: user={}, template={}, error={}",
            user.getId(), templateCode, result.getErrorMessage());

        if (result.isRetryable()) {
            // Schedule retry
        } else {
            // Notify admin
        }
    }
}
```

### 4. Async for Non-Critical Emails

```java
// Critical: Use sync
emailService.send(passwordResetRequest);

// Non-critical: Use async
emailService.sendAsync(welcomeEmailRequest);
emailService.sendBulkAsync(newsletterRequest);
```

### 5. Rate Limiting

대량 발송 시 Rate Limiting을 고려합니다:

```java
@Service
@RequiredArgsConstructor
public class ThrottledEmailService {

    private final EmailService emailService;
    private final RateLimiter rateLimiter = RateLimiter.create(10.0);  // 10 emails/sec

    public void sendBulkWithThrottle(List<EmailRequest> requests) {
        for (EmailRequest request : requests) {
            rateLimiter.acquire();  // Wait for permit
            emailService.sendAsync(request);
        }
    }
}
```

---

## Troubleshooting

### Provider Connection Issues

**Symptom**: `Connection refused` or `Connection timeout`

**Solutions**:
1. 호스트/포트 확인
2. 방화벽 설정 확인
3. VPN/프록시 설정 확인
4. Provider 상태 페이지 확인

```bash
# Test connectivity
telnet smtp.example.com 587
curl -v https://api.sendgrid.com/v3/mail/send
```

### Authentication Errors

**Symptom**: `Authentication failed` or `Invalid credentials`

**Solutions**:
1. API Key/비밀번호 확인
2. 환경 변수 설정 확인
3. API Key 권한 확인
4. Gmail: 앱 비밀번호 사용

```bash
# Check environment variable
echo $SENDGRID_API_KEY
```

### Template Not Found

**Symptom**: `TemplateNotFoundException: Template not found: welcome`

**Solutions**:
1. 템플릿 경로 확인: `templates/email/welcome/en/`
2. 파일명 확인: `subject.txt`, `body.html`
3. locale 폴더 확인
4. classpath 리소스 포함 확인

```bash
# Check resources
jar tf app.jar | grep templates/email
```

### Rate Limiting

**Symptom**: `Rate limit exceeded` or HTTP 429

**Solutions**:
1. 발송 간격 조절
2. Bulk API 사용
3. Provider 한도 증가 요청
4. 여러 Provider 분산

### Encoding Issues

**Symptom**: 한글이 깨짐 (???로 표시)

**Solutions**:
1. 템플릿 파일 UTF-8 저장
2. HTML에 `<meta charset="UTF-8">` 추가
3. Content-Type 헤더 확인

---

## Related Documents

- [Overview (개요)](./overview.md) - 아키텍처 및 설정
- [Sending Guide (이메일 발송)](./sending-guide.md) - 기본 이메일 발송
- [Template Guide (템플릿 사용)](./template-guide.md) - 템플릿 시스템
- [Provider Guide (Provider 설정)](./provider-guide.md) - Provider 설정
