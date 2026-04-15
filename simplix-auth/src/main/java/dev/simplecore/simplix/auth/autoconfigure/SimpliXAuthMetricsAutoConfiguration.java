package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.metrics.AuthenticationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for authentication metrics.
 * <p>
 * Activates only when Micrometer is on the classpath. Registers:
 * <ul>
 *   <li>{@link AuthenticationMetrics} — counters and timers for auth events</li>
 * </ul>
 * <p>
 * Blacklist size gauge ({@code simplix.auth.blacklist.size}) requires
 * {@code TokenBlacklistService.getBlacklistSize()} to be added by Domain 1.
 * Once that method is available, a gauge registrar can be added here.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class SimpliXAuthMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public AuthenticationMetrics authenticationMetrics(MeterRegistry registry) {
        return new AuthenticationMetrics(registry);
    }
}
