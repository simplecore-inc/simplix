package dev.simplecore.simplix.auth.autoconfigure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level security configuration
 * Available security annotations:
 * - @PreAuthorize: Check authorization before method execution
 * - @PostAuthorize: Check authorization after method execution
 * - @Secured: Simple role-based security
 * - @RolesAllowed: JSR-250 standard security annotation
 * <p>
 * Usage examples:
 *  - @PreAuthorize("hasRole('ADMIN')")
 *  - @PostAuthorize("returnObject.username == authentication.name")
 *  - @Secured("ROLE_MANAGER")
 *  - @RolesAllowed("ROLE_USER")
 */
@AutoConfiguration
@EnableMethodSecurity(
    prePostEnabled = true,      // Enable @PreAuthorize, @PostAuthorize
    securedEnabled = true,      // Enable @Secured
    jsr250Enabled = true        // Enable @RolesAllowed
)
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "simplix.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthMethodSecurityConfiguration {

    @Autowired(required = false)
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    @ConditionalOnMissingBean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = 
            new DefaultMethodSecurityExpressionHandler();
        if (permissionEvaluator != null) {
            expressionHandler.setPermissionEvaluator(permissionEvaluator);
        }
        expressionHandler.setApplicationContext(applicationContext);
        return expressionHandler;
    }
} 