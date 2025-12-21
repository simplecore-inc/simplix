package dev.simplecore.simplix.scheduler.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a custom scheduler name for logging and registry.
 * <p>
 * When used on a @Scheduled method, this name will be used instead of
 * the auto-generated "ClassName_methodName" format.
 * <p>
 * Example usage:
 * <pre>{@code
 * @Scheduled(cron = "0 0 3 * * ?")
 * @SchedulerName("audit-purge")
 * public void purgeOldAuditData() {
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchedulerName {

    /**
     * The custom scheduler name.
     * <p>
     * Should be unique across the application.
     * Recommended format: kebab-case (e.g., "audit-purge", "sync-users")
     *
     * @return the scheduler name
     */
    String value();
}
