package dev.simplecore.simplix.auth.resourceserver;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXAccessDeniedHandler;
import dev.simplecore.simplix.auth.security.SimpliXAuthenticationEntryPoint;
import dev.simplecore.simplix.auth.security.SimpliXCorsConfigurations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Resource-server mode: secures the API with bearer JWTs from the configured trusted issuers.
 * <p>
 * The chain is stateless and holds no session. Requests matching
 * {@code simplix.auth.security.permit-all-patterns} pass without a token; every other request on
 * {@code simplix.auth.resource-server.path-patterns} needs a token from a trusted issuer. Errors
 * answer through the same entry point and access-denied handler the token mode uses.
 */
@AutoConfiguration
@EnableWebSecurity
@ConditionalOnClass(BearerTokenAuthenticationFilter.class)
@ConditionalOnProperty(prefix = "simplix.auth.resource-server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SimpliXAuthProperties.class)
public class SimpliXResourceServerAutoConfiguration {

    private final SimpliXAuthProperties properties;

    public SimpliXResourceServerAutoConfiguration(SimpliXAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * Grants the token's {@code scope} values as {@code SCOPE_} authorities. An application
     * replaces it to map the token to a local account.
     */
    @Bean
    @ConditionalOnMissingBean
    public SimpliXJwtAuthenticationConverter simplixJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        return (jwt, issuer) -> delegate.convert(jwt);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtIssuerAuthenticationManagerResolver simplixJwtIssuerAuthenticationManagerResolver(
            SimpliXJwtAuthenticationConverter converter) {
        return TrustedIssuerAuthenticationManagers.resolver(
                properties.getResourceServer().getIssuers(), converter);
    }

    @Bean
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        return SimpliXCorsConfigurations.source(properties.getCors());
    }

    @Bean
    @Order(100)
    public SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            JwtIssuerAuthenticationManagerResolver resolver,
            CorsConfigurationSource corsConfigurationSource,
            SimpliXAuthenticationEntryPoint authenticationEntryPoint,
            SimpliXAccessDeniedHandler accessDeniedHandler) throws Exception {
        String[] permitAll = properties.getSecurity().getPermitAllPatterns();
        http
                .securityMatcher(properties.getResourceServer().getPathPatterns())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (permitAll != null && permitAll.length > 0) {
                        auth.requestMatchers(permitAll).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolver)
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
