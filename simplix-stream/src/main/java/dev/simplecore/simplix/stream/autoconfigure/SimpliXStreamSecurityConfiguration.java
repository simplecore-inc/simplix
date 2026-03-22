package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.security.SessionValidationResult;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for stream security.
 * <p>
 * Configures authorization service for stream subscriptions.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXStreamSecurityConfiguration {

    /**
     * Stream authorization service bean.
     * <p>
     * Provides authorization checking for stream subscriptions.
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamAuthorizationService streamAuthorizationService(StreamProperties properties) {
        boolean enforceAuth = properties.getSecurity().isEnforceAuthorization();
        log.info("Creating stream authorization service (enforceAuthorization={})", enforceAuth);
        return new StreamAuthorizationService(enforceAuth);
    }

    /**
     * Default no-op session validator.
     * <p>
     * Always returns valid. Applications should provide their own
     * implementation to enforce session validation (e.g., token expiration).
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionValidator sessionValidator() {
        return session -> SessionValidationResult.ok();
    }
}
