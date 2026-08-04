package dev.simplecore.simplix.hibernate.transaction;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How a transaction refused the write lock is started over.
 *
 * @see WriteLockRetryAspect
 */
@Getter
@Setter
@ConfigurationProperties(prefix = WriteLockRetryProperties.PREFIX)
public class WriteLockRetryProperties {

    /** Where these settings live. */
    public static final String PREFIX = "simplix.transaction.write-lock-retry";

    /**
     * Whether a transaction the engine refused the write lock to is started over.
     *
     * <p>On by default. Against an engine that admits concurrent writers the refusal never
     * arrives, so this costs one method call per transaction.
     */
    private boolean enabled = true;

    /** How many times the transaction is started over before the caller hears the failure. */
    private int attempts = 6;

    /**
     * How long to hold off before the first retry. Doubles on each subsequent one, and each wait
     * is spread over a random half of its window so that contenders refused together do not come
     * back together.
     */
    private Duration initialBackoff = Duration.ofMillis(25);
}
