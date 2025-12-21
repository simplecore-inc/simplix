package dev.simplecore.simplix.scheduler.aspect;

import dev.simplecore.simplix.scheduler.annotation.SchedulerName;
import dev.simplecore.simplix.scheduler.config.SchedulerProperties;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingService;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Aspect for automatically logging scheduler executions.
 * <p>
 * Intercepts all @Scheduled methods and records their execution.
 * <p>
 * Features:
 * <ul>
 *   <li>Automatic registry entry creation on first execution</li>
 *   <li>Execution time tracking and status logging</li>
 *   <li>Service name distinction (api-server vs scheduler-server)</li>
 *   <li>Graceful error handling - scheduler execution continues even if logging fails</li>
 * </ul>
 */
@Aspect
@RequiredArgsConstructor
@Slf4j
public class SchedulerExecutionAspect {

    private final SchedulerLoggingService loggingService;
    private final SchedulerProperties properties;
    private final String serviceName;
    private final Environment environment;

    /**
     * Around advice for @Scheduled methods.
     * Records execution start, end, and status.
     *
     * @param joinPoint The join point representing the scheduled method
     * @return The result of the scheduled method
     * @throws Throwable If the scheduled method throws an exception
     */
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object logSchedulerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!loggingService.isEnabled()) {
            log.trace("Scheduler logging is disabled, skipping");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();

        String className = targetClass.getName();
        String methodName = method.getName();
        String schedulerName = buildSchedulerName(method, className, methodName);

        // Skip if excluded
        if (loggingService.isExcluded(schedulerName)) {
            log.debug("Scheduler [{}] is excluded from logging", schedulerName);
            return joinPoint.proceed();
        }

        log.debug("Logging execution for scheduler [{}]", schedulerName);

        // Extract scheduler metadata
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        SchedulerLock shedlock = method.getAnnotation(SchedulerLock.class);

        SchedulerMetadata metadata = SchedulerMetadata.builder()
            .schedulerName(schedulerName)
            .className(className)
            .methodName(methodName)
            .cronExpression(extractCronExpression(scheduled))
            .shedlockName(shedlock != null ? shedlock.name() : null)
            .schedulerType(shedlock != null
                ? SchedulerMetadata.SchedulerType.DISTRIBUTED
                : SchedulerMetadata.SchedulerType.LOCAL)
            .build();

        SchedulerRegistryEntry registry;
        SchedulerExecutionContext context;
        Instant startTime = Instant.now();

        try {
            // Ensure registry entry exists
            registry = loggingService.ensureRegistryEntry(metadata);

            // Create execution context
            context = loggingService.createExecutionContext(registry, serviceName);
        } catch (Exception e) {
            log.warn("Failed to initialize execution log for [{}]: {}", schedulerName, e.getMessage());
            // Continue with scheduler execution even if logging initialization fails
            return joinPoint.proceed();
        }

        try {
            Object result = joinPoint.proceed();
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();

            loggingService.saveExecutionResult(context, SchedulerExecutionResult.success(durationMs));

            log.debug("Scheduler [{}] completed successfully in {}ms", schedulerName, durationMs);
            return result;
        } catch (Exception e) {
            long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();

            loggingService.saveExecutionResult(context,
                SchedulerExecutionResult.failure(durationMs, e.getMessage()));

            log.error("Scheduler [{}] failed after {}ms: {}", schedulerName, durationMs, e.getMessage());
            throw e;
        }
    }

    /**
     * Build scheduler name from method.
     * <p>
     * Priority:
     * <ol>
     *   <li>@SchedulerName annotation value (if present)</li>
     *   <li>Auto-generated "ClassName_methodName" format</li>
     * </ol>
     *
     * @param method     The scheduled method
     * @param className  Fully qualified class name
     * @param methodName Method name
     * @return Scheduler name
     */
    private String buildSchedulerName(Method method, String className, String methodName) {
        // Check for custom @SchedulerName annotation
        SchedulerName schedulerName = method.getAnnotation(SchedulerName.class);
        if (schedulerName != null && !schedulerName.value().isBlank()) {
            return schedulerName.value();
        }

        // Fallback to auto-generated name
        String simpleClassName = className.contains(".")
            ? className.substring(className.lastIndexOf('.') + 1)
            : className;
        if (simpleClassName.contains("$$")) {
            simpleClassName = simpleClassName.substring(0, simpleClassName.indexOf("$$"));
        }
        return simpleClassName + "_" + methodName;
    }

    /**
     * Extract cron expression or fixed delay/rate info from @Scheduled annotation.
     * Resolves Spring placeholders (e.g., ${...}) to actual values.
     */
    private String extractCronExpression(Scheduled scheduled) {
        if (scheduled == null) {
            return null;
        }

        if (!scheduled.cron().isEmpty()) {
            return "cron: " + resolvePlaceholder(scheduled.cron());
        }
        if (scheduled.fixedDelay() > 0) {
            return "fixedDelay: " + scheduled.fixedDelay() + "ms";
        }
        if (scheduled.fixedRate() > 0) {
            return "fixedRate: " + scheduled.fixedRate() + "ms";
        }
        if (!scheduled.fixedDelayString().isEmpty()) {
            return "fixedDelay: " + resolvePlaceholder(scheduled.fixedDelayString());
        }
        if (!scheduled.fixedRateString().isEmpty()) {
            return "fixedRate: " + resolvePlaceholder(scheduled.fixedRateString());
        }

        return null;
    }

    /**
     * Resolve Spring placeholders in the given value.
     *
     * @param value The value that may contain placeholders
     * @return The resolved value
     */
    private String resolvePlaceholder(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return environment.resolvePlaceholders(value);
    }
}
