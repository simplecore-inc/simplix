package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXTokenAuthenticationFailureHandler;
import dev.simplecore.simplix.auth.security.SimpliXTokenAuthenticationSuccessHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "dev.simplecore.simplix.auth")
@Import({
    SimpliXAuthSecurityConfiguration.class
})
public class SimpliXAuthAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "simplix.auth")
    public SimpliXAuthProperties simplixAuthProperties() {
        return new SimpliXAuthProperties();
    }

    /**
     * Form login success handler - redirects to saved request or default success URL.
     *
     * How to customize:
     * <pre>
     * {@literal @}Bean(name = "authenticationSuccessHandler")
     * public AuthenticationSuccessHandler authenticationSuccessHandler(
     *         UserRepository userRepository,
     *         ApplicationEventPublisher eventPublisher) {
     *     return (request, response, authentication) -> {
     *         String username = authentication.getName();
     *         log.info("Form login success: {}", username);
     *
     *         // Custom logic: update last login timestamp
     *         userRepository.findByUsername(username).ifPresent(user -> {
     *             user.setLastLoginAt(LocalDateTime.now());
     *             userRepository.save(user);
     *         });
     *
     *         // Publish event
     *         eventPublisher.publishEvent(new LoginSuccessEvent(username));
     *
     *         // Redirect to dashboard
     *         response.sendRedirect("/dashboard");
     *     };
     * }
     * </pre>
     */
    @Bean(name = "authenticationSuccessHandler")
    @ConditionalOnMissingBean(name = "authenticationSuccessHandler")
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new SavedRequestAwareAuthenticationSuccessHandler();
    }

    /**
     * Form login failure handler - redirects to login page with error parameter.
     *
     * How to customize:
     * <pre>
     * {@literal @}Bean(name = "authenticationFailureHandler")
     * public AuthenticationFailureHandler authenticationFailureHandler(
     *         LoginAttemptService loginAttemptService) {
     *     return (request, response, exception) -> {
     *         String username = request.getParameter("username");
     *         log.warn("Form login failed: {}", username);
     *
     *         // Custom logic: track failed attempts
     *         if (username != null) {
     *             loginAttemptService.recordFailedAttempt(username);
     *         }
     *
     *         // Redirect to login with error
     *         response.sendRedirect("/login?error=invalid_credentials");
     *     };
     * }
     * </pre>
     */
    @Bean(name = "authenticationFailureHandler")
    @ConditionalOnMissingBean(name = "authenticationFailureHandler")
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler("/login?error");
    }

    /**
     * Token authentication success handler - performs logging/auditing without writing response.
     * Response is handled by the REST controller.
     *
     * How to customize:
     * <pre>
     * {@literal @}Bean(name = "tokenAuthenticationSuccessHandler")
     * public AuthenticationSuccessHandler tokenAuthenticationSuccessHandler(
     *         UserRepository userRepository,
     *         ApplicationEventPublisher eventPublisher) {
     *     return (request, response, authentication) -> {
     *         String username = authentication.getName();
     *         log.info("Token issued: {}", username);
     *
     *         // Custom logic: update last login timestamp
     *         userRepository.findByUsername(username).ifPresent(user -> {
     *             user.setLastLoginAt(LocalDateTime.now());
     *             userRepository.save(user);
     *         });
     *
     *         // Publish event
     *         eventPublisher.publishEvent(new TokenIssuedEvent(username));
     *
     *         // DO NOT write to response - handled by controller
     *     };
     * }
     * </pre>
     */
    @Bean(name = "tokenAuthenticationSuccessHandler")
    @ConditionalOnMissingBean(name = "tokenAuthenticationSuccessHandler")
    public AuthenticationSuccessHandler tokenAuthenticationSuccessHandler() {
        return new SimpliXTokenAuthenticationSuccessHandler();
    }

    /**
     * Token authentication failure handler - performs logging/auditing without writing response.
     * Response is handled by the REST controller/exception handler.
     *
     * How to customize:
     * <pre>
     * {@literal @}Bean(name = "tokenAuthenticationFailureHandler")
     * public AuthenticationFailureHandler tokenAuthenticationFailureHandler(
     *         LoginAttemptService loginAttemptService,
     *         ApplicationEventPublisher eventPublisher) {
     *     return (request, response, exception) -> {
     *         String authHeader = request.getHeader("Authorization");
     *         String username = extractUsername(authHeader);
     *         log.warn("Token authentication failed: {}", username);
     *
     *         // Custom logic: track failed attempts
     *         if (username != null) {
     *             loginAttemptService.recordFailedAttempt(username);
     *
     *             // Lock account after 5 failed attempts
     *             if (loginAttemptService.getFailedAttempts(username) >= 5) {
     *                 eventPublisher.publishEvent(new AccountLockedEvent(username));
     *             }
     *         }
     *
     *         // DO NOT write to response - handled by controller/exception handler
     *     };
     * }
     * </pre>
     */
    @Bean(name = "tokenAuthenticationFailureHandler")
    @ConditionalOnMissingBean(name = "tokenAuthenticationFailureHandler")
    public AuthenticationFailureHandler tokenAuthenticationFailureHandler() {
        return new SimpliXTokenAuthenticationFailureHandler();
    }
} 