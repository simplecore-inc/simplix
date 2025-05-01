package dev.simplecore.simplix.springboot.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@ConditionalOnClass({EnableJpaAuditing.class, SecurityContextHolder.class})
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class SimpliXJpaAuditingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXJpaAuditingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> auditorProvider() {
        log.info("Initializing SimpliX JPA Auditing...");
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }
            
            return Optional.of(authentication.getName());
        };
    }
} 