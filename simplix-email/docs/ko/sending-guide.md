# SimpliX Email Sending Guide

## EmailService API Reference

### Overview

`EmailService`는 이메일 발송의 주 진입점입니다. 동기/비동기 발송, 템플릿 발송, 대량 발송을 지원합니다.

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    public void sendWelcomeEmail(User user) {
        EmailResult result = emailService.send(
            EmailRequest.simple(user.getEmail(), "Welcome!", "<h1>Welcome!</h1>")
        );

        if (result.isSuccess()) {
            log.info("Email sent: {}", result.getMessageId());
        }
    }
}
```

### API Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `send(EmailRequest)` | `EmailResult` | 단일 이메일 동기 발송 |
| `sendAsync(EmailRequest)` | `CompletableFuture<EmailResult>` | 단일 이메일 비동기 발송 |
| `sendTemplate(TemplateEmailRequest)` | `EmailResult` | 템플릿 이메일 동기 발송 |
| `sendTemplate(String, String, Map)` | `EmailResult` | 간편 템플릿 발송 |
| `sendTemplateAsync(TemplateEmailRequest)` | `CompletableFuture<EmailResult>` | 템플릿 이메일 비동기 발송 |
| `sendBulk(BulkEmailRequest)` | `BulkEmailResult` | 대량 발송 동기 |
| `sendBulkAsync(BulkEmailRequest)` | `CompletableFuture<BulkEmailResult>` | 대량 발송 비동기 |
| `isAvailable()` | `boolean` | Provider 사용 가능 여부 |

---

## EmailRequest

### Builder Pattern

```java
EmailRequest request = EmailRequest.builder()
    .from(EmailAddress.of("Support Team", "support@example.com"))
    .to(List.of(EmailAddress.of("user@example.com")))
    .cc(List.of(EmailAddress.of("manager@example.com")))
    .bcc(List.of(EmailAddress.of("archive@example.com")))
    .replyTo(EmailAddress.of("reply@example.com"))
    .subject("Important Update")
    .htmlBody("<h1>Hello</h1><p>This is an important update.</p>")
    .textBody("Hello\n\nThis is an important update.")
    .priority(MailPriority.HIGH)
    .headers(Map.of("X-Custom-Header", "value"))
    .tags(List.of("notification", "important"))
    .tenantId("tenant-123")
    .correlationId("corr-456")
    .build();
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | `EmailAddress` | - | 발신자 (설정된 기본값 사용 가능) |
| `to` | `List<EmailAddress>` | ✔ | 수신자 목록 |
| `cc` | `List<EmailAddress>` | - | 참조 목록 |
| `bcc` | `List<EmailAddress>` | - | 숨은 참조 목록 |
| `replyTo` | `EmailAddress` | - | 답장 주소 |
| `subject` | `String` | ✔ | 제목 |
| `htmlBody` | `String` | * | HTML 본문 |
| `textBody` | `String` | * | 텍스트 본문 |
| `attachments` | `List<EmailAttachment>` | - | 첨부 파일 |
| `priority` | `MailPriority` | - | 우선순위 (기본: NORMAL) |
| `headers` | `Map<String, String>` | - | 커스텀 헤더 |
| `tags` | `List<String>` | - | 태그 (Provider 지원 시) |
| `tenantId` | `String` | - | 테넌트 ID |
| `correlationId` | `String` | - | 상관 ID (추적용) |

> `*` htmlBody 또는 textBody 중 하나는 필수

### Factory Methods

```java
// 간단한 HTML 이메일
EmailRequest request1 = EmailRequest.simple(
    "user@example.com",
    "Welcome!",
    "<h1>Welcome to our service!</h1>"
);

// 텍스트 전용 이메일
EmailRequest request2 = EmailRequest.plainText(
    "user@example.com",
    "Password Reset",
    "Your password reset code is: 123456"
);
```

### Helper Methods

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Test")
    .htmlBody("<p>Test</p>")
    .textBody("Test")
    .build();

// Helper methods
boolean hasHtml = request.hasHtmlBody();        // true
boolean hasText = request.hasTextBody();        // true
boolean hasAttachments = request.hasAttachments();  // false
int recipientCount = request.getTotalRecipientCount();  // 1 (to + cc + bcc)
```

---

## EmailAddress

### Creation Methods

```java
// 이메일만
EmailAddress addr1 = EmailAddress.of("user@example.com");

// 이름 + 이메일
EmailAddress addr2 = EmailAddress.of("John Doe", "john@example.com");

// Builder
EmailAddress addr3 = EmailAddress.builder()
    .name("Support Team")
    .address("support@example.com")
    .build();
```

### Formatting

```java
EmailAddress addr = EmailAddress.of("John Doe", "john@example.com");

// Formatted string (RFC 2822)
String formatted = addr.toFormattedString();
// Result: "John Doe" <john@example.com>

// Masked string (for logging)
String masked = addr.toMaskedString();
// Result: j***@example.com
```

### 개인정보 보호 (Privacy Masking)

이메일 주소는 개인정보이므로 로그에 출력할 때 마스킹 처리를 권장합니다:

```java
// Single address masking
EmailAddress addr = EmailAddress.of("user@example.com");
log.info("Sending email to: {}", addr.toMaskedString());
// Output: Sending email to: u***@example.com

// Name included
EmailAddress namedAddr = EmailAddress.of("John Doe", "john.doe@example.com");
log.info("Recipient: {}", namedAddr.toMaskedString());
// Output: Recipient: j***@example.com
```

**마스킹 규칙:**
- 이메일 로컬 파트의 첫 글자만 표시
- 나머지는 `***`로 대체
- 도메인은 그대로 유지

> Provider 내부에서는 `AbstractEmailProvider.maskRecipients()` 메서드가 자동으로 수신자 목록을 마스킹하여 로그에 출력합니다.

---

## EmailResult

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | 성공 여부 |
| `status` | `EmailStatus` | 상태 |
| `messageId` | `String` | 메시지 ID (Provider 발급) |
| `providerType` | `MailProviderType` | 사용된 Provider |
| `errorMessage` | `String` | 에러 메시지 |
| `errorCode` | `String` | 에러 코드 |
| `timestamp` | `Instant` | 처리 시간 |
| `retryCount` | `int` | 재시도 횟수 |
| `retryable` | `boolean` | 재시도 가능 여부 |
| `recipients` | `List<String>` | 수신자 목록 |

### Result Handling

```java
EmailResult result = emailService.send(request);

if (result.isSuccess()) {
    log.info("✔ Email sent successfully");
    log.info("  Message ID: {}", result.getMessageId());
    log.info("  Provider: {}", result.getProviderType());
} else {
    log.error("✖ Email sending failed");
    log.error("  Error: {}", result.getErrorMessage());
    log.error("  Code: {}", result.getErrorCode());

    if (result.isRetryable()) {
        log.warn("  This error is retryable");
    }
}
```

### Factory Methods

```java
// Success result
EmailResult success = EmailResult.success("msg-123", MailProviderType.AWS_SES);

// Failure result
EmailResult failure = EmailResult.failure("Connection timeout", MailProviderType.SMTP);

// Retryable failure
EmailResult retryable = EmailResult.retryableFailure(
    "Rate limit exceeded",
    "429",
    MailProviderType.SENDGRID
);
```

---

## EmailAttachment

### Regular Attachment

```java
// From byte array
byte[] pdfContent = Files.readAllBytes(Path.of("report.pdf"));

EmailAttachment attachment = EmailAttachment.of(
    "monthly-report.pdf",
    "application/pdf",
    pdfContent
);

// Builder
EmailAttachment attachment2 = EmailAttachment.builder()
    .filename("document.docx")
    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    .content(documentBytes)
    .build();
```

### Inline Image

```java
// For embedding images in HTML body
byte[] logoContent = Files.readAllBytes(Path.of("logo.png"));

EmailAttachment inlineImage = EmailAttachment.inline(
    "logo",                    // Content ID
    "logo.png",                // Filename
    "image/png",               // Content Type
    logoContent                // Content
);

// Use in HTML: <img src="cid:logo" alt="Logo">
```

### With Email Request

```java
EmailRequest request = EmailRequest.builder()
    .to(List.of(EmailAddress.of("user@example.com")))
    .subject("Report with Attachment")
    .htmlBody("""
        <h1>Monthly Report</h1>
        <p>Please find the attached report.</p>
        <img src="cid:chart" alt="Chart">
        """)
    .attachments(List.of(
        EmailAttachment.of("report.pdf", "application/pdf", pdfBytes),
        EmailAttachment.inline("chart", "chart.png", "image/png", chartBytes)
    ))
    .build();
```

### Helper Methods

```java
EmailAttachment attachment = EmailAttachment.of("file.pdf", "application/pdf", content);

int size = attachment.getSize();           // Content size in bytes
boolean isInline = attachment.isInline();  // false
String contentId = attachment.getContentId();  // null for regular attachments
```

---

## Asynchronous Sending

### Basic Async

```java
CompletableFuture<EmailResult> future = emailService.sendAsync(request);

// Non-blocking callback
future.thenAccept(result -> {
    if (result.isSuccess()) {
        log.info("Email sent: {}", result.getMessageId());
    } else {
        log.error("Failed to send: {}", result.getErrorMessage());
    }
});
```

### With Exception Handling

```java
emailService.sendAsync(request)
    .thenAccept(result -> {
        if (result.isSuccess()) {
            log.info("✔ Sent: {}", result.getMessageId());
        } else {
            log.warn("⚠ Failed: {}", result.getErrorMessage());
        }
    })
    .exceptionally(ex -> {
        log.error("✖ Exception occurred", ex);
        return null;
    });
```

### Blocking Wait

```java
try {
    EmailResult result = emailService.sendAsync(request)
        .get(30, TimeUnit.SECONDS);

    log.info("Result: {}", result.isSuccess());
} catch (TimeoutException e) {
    log.error("Timeout waiting for email result");
} catch (ExecutionException e) {
    log.error("Email sending failed", e.getCause());
}
```

### Multiple Emails in Parallel

```java
List<EmailRequest> requests = createEmailRequests();

List<CompletableFuture<EmailResult>> futures = requests.stream()
    .map(emailService::sendAsync)
    .toList();

// Wait for all to complete
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> {
        long successCount = futures.stream()
            .map(CompletableFuture::join)
            .filter(EmailResult::isSuccess)
            .count();

        log.info("Sent {} / {} emails", successCount, requests.size());
    });
```

---

## Code Examples

### Simple Text Email

```java
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final EmailService emailService;

    public void sendPasswordResetCode(String email, String code) {
        EmailResult result = emailService.send(
            EmailRequest.plainText(
                email,
                "Password Reset Code",
                "Your password reset code is: " + code + "\n\nThis code expires in 10 minutes."
            )
        );

        if (!result.isSuccess()) {
            throw new EmailSendException("Failed to send reset code: " + result.getErrorMessage());
        }
    }
}
```

### HTML Email

```java
public void sendWelcomeEmail(User user) {
    String htmlBody = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                .container { max-width: 600px; margin: 0 auto; }
                .header { background: #4A90D9; color: white; padding: 20px; }
                .content { padding: 20px; }
                .button { background: #4A90D9; color: white; padding: 10px 20px;
                         text-decoration: none; border-radius: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Welcome!</h1>
                </div>
                <div class="content">
                    <p>Hi %s,</p>
                    <p>Thank you for joining us!</p>
                    <a href="https://example.com/activate?token=%s" class="button">
                        Activate Account
                    </a>
                </div>
            </div>
        </body>
        </html>
        """.formatted(user.getName(), user.getActivationToken());

    EmailRequest request = EmailRequest.builder()
        .to(List.of(EmailAddress.of(user.getName(), user.getEmail())))
        .subject("Welcome to Our Service!")
        .htmlBody(htmlBody)
        .textBody("Welcome, " + user.getName() + "!\n\nActivate: https://example.com/activate?token=" + user.getActivationToken())
        .priority(MailPriority.HIGH)
        .tags(List.of("welcome", "onboarding"))
        .build();

    emailService.send(request);
}
```

### Email with Attachment

```java
public void sendInvoice(Order order, byte[] invoicePdf) {
    EmailRequest request = EmailRequest.builder()
        .from(EmailAddress.of("Billing", "billing@example.com"))
        .to(List.of(EmailAddress.of(order.getCustomerName(), order.getCustomerEmail())))
        .subject("Invoice #" + order.getInvoiceNumber())
        .htmlBody("""
            <h2>Invoice #%s</h2>
            <p>Dear %s,</p>
            <p>Please find your invoice attached.</p>
            <table>
                <tr><td>Order ID:</td><td>%s</td></tr>
                <tr><td>Amount:</td><td>$%.2f</td></tr>
                <tr><td>Due Date:</td><td>%s</td></tr>
            </table>
            <p>Thank you for your business!</p>
            """.formatted(
                order.getInvoiceNumber(),
                order.getCustomerName(),
                order.getId(),
                order.getTotalAmount(),
                order.getDueDate()
            ))
        .attachments(List.of(
            EmailAttachment.of(
                "invoice-" + order.getInvoiceNumber() + ".pdf",
                "application/pdf",
                invoicePdf
            )
        ))
        .priority(MailPriority.HIGH)
        .correlationId(order.getId())
        .build();

    emailService.send(request);
}
```

### CC/BCC Example

```java
public void sendTeamNotification(String subject, String content, Team team) {
    EmailRequest request = EmailRequest.builder()
        .from(EmailAddress.of("System", "system@example.com"))
        .to(List.of(EmailAddress.of(team.getLeader().getEmail())))
        .cc(team.getMembers().stream()
            .map(m -> EmailAddress.of(m.getName(), m.getEmail()))
            .toList())
        .bcc(List.of(EmailAddress.of("audit@example.com")))
        .subject(subject)
        .htmlBody(content)
        .priority(MailPriority.NORMAL)
        .tags(List.of("team-notification", team.getId()))
        .build();

    emailService.send(request);
}
```

### Async with Callback

```java
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final EmailService emailService;
    private final NotificationLogRepository logRepository;

    public void sendOrderConfirmationAsync(Order order) {
        EmailRequest request = createOrderConfirmationRequest(order);

        emailService.sendAsync(request)
            .thenAccept(result -> {
                NotificationLog log = NotificationLog.builder()
                    .orderId(order.getId())
                    .type("ORDER_CONFIRMATION")
                    .success(result.isSuccess())
                    .messageId(result.getMessageId())
                    .provider(result.getProviderType().name())
                    .errorMessage(result.getErrorMessage())
                    .timestamp(result.getTimestamp())
                    .build();

                logRepository.save(log);
            })
            .exceptionally(ex -> {
                log.error("Failed to send order confirmation for order: {}", order.getId(), ex);
                return null;
            });
    }
}
```

---

## Error Handling

### Common Errors

| Error | Retryable | Description |
|-------|-----------|-------------|
| Connection Timeout | ✔ | 네트워크 연결 실패 |
| Rate Limit Exceeded | ✔ | Provider 제한 초과 |
| Service Unavailable | ✔ | Provider 일시 장애 |
| Authentication Failed | - | 인증 정보 오류 |
| Invalid Address | - | 잘못된 이메일 주소 |
| Quota Exceeded | - | 발송 한도 초과 |

### Exception Handling Pattern

```java
public void sendEmailWithRetry(EmailRequest request, int maxRetries) {
    int attempts = 0;
    EmailResult result;

    do {
        result = emailService.send(request);

        if (result.isSuccess()) {
            log.info("Email sent on attempt {}", attempts + 1);
            return;
        }

        if (!result.isRetryable()) {
            throw new EmailSendException("Non-retryable error: " + result.getErrorMessage());
        }

        attempts++;
        if (attempts < maxRetries) {
            try {
                Thread.sleep(1000 * attempts);  // Exponential backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EmailSendException("Interrupted during retry");
            }
        }
    } while (attempts < maxRetries);

    throw new EmailSendException("Max retries exceeded: " + result.getErrorMessage());
}
```

---

## Best Practices

### 1. Always Check Result

```java
// Bad
emailService.send(request);

// Good
EmailResult result = emailService.send(request);
if (!result.isSuccess()) {
    // Handle failure
}
```

### 2. Use Async for Non-Critical Emails

```java
// Marketing emails, notifications
emailService.sendAsync(request);

// Critical emails (password reset, OTP)
EmailResult result = emailService.send(request);
```

### 3. Set Appropriate Priority

```java
// Password reset - CRITICAL
EmailRequest.builder()
    .priority(MailPriority.CRITICAL)
    ...

// Order confirmation - HIGH
EmailRequest.builder()
    .priority(MailPriority.HIGH)
    ...

// Newsletter - LOW
EmailRequest.builder()
    .priority(MailPriority.LOW)
    ...
```

### 4. Use Correlation ID for Tracking

```java
String correlationId = UUID.randomUUID().toString();

EmailRequest request = EmailRequest.builder()
    .correlationId(correlationId)
    ...
    .build();

log.info("Sending email with correlationId: {}", correlationId);
```

### 5. Provide Both HTML and Text

```java
// Good - supports all email clients
EmailRequest.builder()
    .htmlBody("<h1>Hello</h1>")
    .textBody("Hello")
    .build();
```

---

## Related Documents

- [Template Guide (템플릿 사용)](./template-guide.md) - 템플릿 기반 이메일 발송
- [Provider Guide (Provider 설정)](./provider-guide.md) - Provider 상세 설정
- [Advanced Guide (고급 기능)](./advanced-guide.md) - 대량 발송, 멀티테넌시
