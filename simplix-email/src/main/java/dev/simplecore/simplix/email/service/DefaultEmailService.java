package dev.simplecore.simplix.email.service;

import dev.simplecore.simplix.email.model.BulkEmailRequest;
import dev.simplecore.simplix.email.model.BulkEmailResult;
import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.TemplateEmailRequest;
import dev.simplecore.simplix.email.provider.EmailProvider;
import dev.simplecore.simplix.email.template.EmailTemplateService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Default implementation of EmailService.
 * <p>
 * Coordinates email sending through available providers with automatic
 * failover support. Integrates with template service for template-based
 * emails.
 */
@Slf4j
public class DefaultEmailService implements EmailService {

    private final List<EmailProvider> providers;
    private final EmailTemplateService templateService;
    private final Executor asyncExecutor;
    private final EmailAddress defaultFrom;

    public DefaultEmailService(List<EmailProvider> providers,
                               EmailTemplateService templateService,
                               Executor asyncExecutor,
                               EmailAddress defaultFrom) {
        // Sort providers by priority (highest first)
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(EmailProvider::getPriority).reversed())
                .toList();
        this.templateService = templateService;
        this.asyncExecutor = asyncExecutor;
        this.defaultFrom = defaultFrom;

        log.info("EmailService initialized with {} providers: {}",
                providers.size(),
                providers.stream().map(p -> p.getType().name()).toList());
    }

    @Override
    public EmailResult send(EmailRequest request) {
        if (request.getFrom() == null && defaultFrom != null) {
            request.setFrom(defaultFrom);
        }

        for (EmailProvider provider : providers) {
            if (!provider.isAvailable()) {
                continue;
            }

            EmailResult result = provider.send(request);

            if (result.isSuccess()) {
                return result;
            }

            // If not retryable, return immediately
            if (!result.isRetryable()) {
                log.warn("Email send failed via {}, not retryable: {}",
                        provider.getType(), result.getErrorMessage());
                return result;
            }

            // Try next provider
            log.warn("Email send failed via {}, trying next provider: {}",
                    provider.getType(), result.getErrorMessage());
        }

        return EmailResult.failure("All email providers failed or unavailable", null);
    }

    @Override
    public EmailResult sendTemplate(TemplateEmailRequest request) {
        try {
            EmailRequest emailRequest = templateService.processTemplate(request);
            if (emailRequest.getFrom() == null && defaultFrom != null) {
                emailRequest.setFrom(defaultFrom);
            }
            return send(emailRequest);
        } catch (EmailTemplateService.TemplateNotFoundException e) {
            log.error("Template not found: {}", request.getTemplateCode());
            return EmailResult.failure("Template not found: " + request.getTemplateCode(), null);
        } catch (Exception e) {
            log.error("Failed to process template {}: {}", request.getTemplateCode(), e.getMessage(), e);
            return EmailResult.failure("Template processing failed: " + e.getMessage(), null);
        }
    }

    @Override
    public EmailResult sendTemplate(String templateCode, String to, Map<String, Object> variables) {
        return sendTemplate(TemplateEmailRequest.of(templateCode, to, variables));
    }

    @Override
    public BulkEmailResult sendBulk(BulkEmailRequest request) {
        String batchId = request.getBatchId() != null ?
                request.getBatchId() : UUID.randomUUID().toString();

        Instant startTime = Instant.now();
        List<BulkEmailResult.RecipientResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (BulkEmailRequest.BulkRecipient recipient : request.getRecipients()) {
            // Merge common variables with recipient-specific variables
            Map<String, Object> mergedVariables = new HashMap<>(request.getCommonVariables());
            if (recipient.getVariables() != null) {
                mergedVariables.putAll(recipient.getVariables());
            }

            try {
                EmailRequest emailRequest = templateService.processTemplate(
                        request.getTemplateCode(),
                        recipient.getAddress(),
                        mergedVariables,
                        request.getLocale() != null ? request.getLocale() : Locale.getDefault()
                );

                if (request.getFrom() != null) {
                    emailRequest.setFrom(request.getFrom());
                } else if (defaultFrom != null) {
                    emailRequest.setFrom(defaultFrom);
                }

                if (request.getSubject() != null) {
                    emailRequest.setSubject(request.getSubject());
                }

                emailRequest.setPriority(request.getPriority());
                emailRequest.setTenantId(request.getTenantId());
                emailRequest.setTags(request.getTags());

                EmailResult result = send(emailRequest);

                if (result.isSuccess()) {
                    results.add(BulkEmailResult.RecipientResult.success(
                            recipient.getAddress().getAddress(),
                            result.getMessageId()
                    ));
                    successCount++;
                } else {
                    results.add(BulkEmailResult.RecipientResult.failure(
                            recipient.getAddress().getAddress(),
                            result.getErrorMessage()
                    ));
                    failureCount++;

                    if (!request.isContinueOnError()) {
                        break;
                    }
                }
            } catch (Exception e) {
                results.add(BulkEmailResult.RecipientResult.failure(
                        recipient.getAddress().getAddress(),
                        e.getMessage()
                ));
                failureCount++;

                if (!request.isContinueOnError()) {
                    break;
                }
            }
        }

        return BulkEmailResult.builder()
                .batchId(batchId)
                .totalCount(request.getRecipientCount())
                .successCount(successCount)
                .failureCount(failureCount)
                .pendingCount(request.getRecipientCount() - successCount - failureCount)
                .results(results)
                .startTime(startTime)
                .endTime(Instant.now())
                .build();
    }

    @Override
    public CompletableFuture<EmailResult> sendAsync(EmailRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request), asyncExecutor);
    }

    @Override
    public CompletableFuture<EmailResult> sendTemplateAsync(TemplateEmailRequest request) {
        return CompletableFuture.supplyAsync(() -> sendTemplate(request), asyncExecutor);
    }

    @Override
    public CompletableFuture<BulkEmailResult> sendBulkAsync(BulkEmailRequest request) {
        return CompletableFuture.supplyAsync(() -> sendBulk(request), asyncExecutor);
    }

    @Override
    public boolean isAvailable() {
        return providers.stream().anyMatch(EmailProvider::isAvailable);
    }
}
