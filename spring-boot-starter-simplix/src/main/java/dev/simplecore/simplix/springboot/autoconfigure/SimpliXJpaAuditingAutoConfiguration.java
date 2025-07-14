package dev.simplecore.simplix.springboot.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@ConditionalOnClass({EnableJpaAuditing.class, SecurityContextHolder.class})
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
public class SimpliXJpaAuditingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXJpaAuditingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> auditorProvider() {
        log.info("Initializing SimpliX JPA Auditing AuditorAware...");
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("No authentication found, returning SYSTEM");
                return Optional.of("SYSTEM");
            }
            
            try {
                Object principal = authentication.getPrincipal();
                String name = authentication.getName();
                
                log.debug("Authentication details - Principal type: {}, Principal: {}, Name: {}, Name type: {}", 
                    principal.getClass().getName(), 
                    principal.toString(),
                    name, 
                    name != null ? name.getClass().getName() : "null");
                
                if (name == null) {
                    log.warn("Authentication.getName() returned null, using SYSTEM");
                    return Optional.of("SYSTEM");
                }
                
                if (!(name instanceof String)) {
                    log.error("Authentication.getName() returned non-String type: {}, value: {}, using SYSTEM", 
                        name.getClass().getName(), name);
                    return Optional.of("SYSTEM");
                }
                
                return Optional.of(name);
            } catch (Exception e) {
                log.error("Error getting auditor from authentication", e);
                return Optional.of("SYSTEM");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public DateTimeProvider dateTimeProvider() {
        log.info("Initializing SimpliX JPA Auditing DateTimeProvider...");
        return () -> {
            OffsetDateTime now = OffsetDateTime.now();
            log.debug("DateTimeProvider returning: {}", now);
            return Optional.of(now);
        };
    }
} 