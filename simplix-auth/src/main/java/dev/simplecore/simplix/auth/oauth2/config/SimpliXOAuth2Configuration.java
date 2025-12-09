package dev.simplecore.simplix.auth.oauth2.config;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.filter.OAuth2IntentFilter;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginFailureHandler;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOAuth2UserService;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOidcUserService;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration class for SimpliX OAuth2 social login support.
 *
 * <p>This configuration is imported by {@link EnableSimplixOAuth2} annotation
 * and provides all necessary beans for OAuth2 social login functionality.</p>
 *
 * <p>Required beans from application:</p>
 * <ul>
 *   <li>{@link OAuth2AuthenticationService} - Handles user authentication and account management</li>
 *   <li>{@link SimpliXJweTokenProvider} - Provides JWT token generation</li>
 *   <li>{@link SimpliXAuthProperties} - Authentication configuration properties</li>
 * </ul>
 *
 * @see EnableSimplixOAuth2
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "simplix.auth.oauth2",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SimpliXOAuth2Configuration {

    private final SimpliXAuthProperties authProperties;

    @Bean
    @ConditionalOnMissingBean
    public OAuth2UserInfoExtractor oauth2UserInfoExtractor() {
        return new OAuth2UserInfoExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpliXOAuth2UserService simplixOAuth2UserService() {
        return new SimpliXOAuth2UserService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpliXOidcUserService simplixOidcUserService() {
        return new SimpliXOidcUserService();
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2LoginSuccessHandler oauth2LoginSuccessHandler(
            OAuth2AuthenticationService authService,
            OAuth2UserInfoExtractor extractor,
            SimpliXJweTokenProvider tokenProvider) {
        return new OAuth2LoginSuccessHandler(
                authService,
                extractor,
                tokenProvider,
                authProperties.getOauth2()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2LoginFailureHandler oauth2LoginFailureHandler() {
        return new OAuth2LoginFailureHandler(authProperties.getOauth2());
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2IntentFilter oauth2IntentFilter() {
        return new OAuth2IntentFilter(authProperties.getOauth2());
    }

    @Bean
    public FilterRegistrationBean<OAuth2IntentFilter> oauth2IntentFilterRegistration(
            OAuth2IntentFilter oauth2IntentFilter) {
        FilterRegistrationBean<OAuth2IntentFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(oauth2IntentFilter);
        registration.addUrlPatterns(
                authProperties.getOauth2().getLoginBaseUrl() + "/*",
                authProperties.getOauth2().getRegisterBaseUrl() + "/*"
        );
        // Must run AFTER Spring Session's SessionRepositoryFilter to use the correct session
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
        registration.setName("oauth2IntentFilter");
        return registration;
    }

    @Bean
    @Order(90)
    public SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity http,
            SimpliXOAuth2UserService oauth2UserService,
            SimpliXOidcUserService oidcUserService,
            OAuth2LoginSuccessHandler successHandler,
            OAuth2LoginFailureHandler failureHandler) throws Exception {

        SimpliXOAuth2Properties oauth2Props = authProperties.getOauth2();

        log.info("Configuring OAuth2 security filter chain with authorization base URL: {}",
                oauth2Props.getAuthorizationBaseUrl());

        // Extract success/failure paths for permitAll
        String linkSuccessPath = extractPath(oauth2Props.getLinkSuccessUrl());
        String linkFailurePath = extractPath(oauth2Props.getLinkFailureUrl());

        http
                .securityMatcher(
                        oauth2Props.getAuthorizationBaseUrl() + "/**",
                        oauth2Props.getCallbackBaseUrl() + "/**",
                        oauth2Props.getLinkBaseUrl() + "/**",
                        oauth2Props.getLoginBaseUrl() + "/**",
                        oauth2Props.getRegisterBaseUrl() + "/**",
                        "/login/oauth2/**"
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                oauth2Props.getAuthorizationBaseUrl() + "/**",
                                oauth2Props.getCallbackBaseUrl() + "/**",
                                oauth2Props.getLoginBaseUrl() + "/**",
                                oauth2Props.getRegisterBaseUrl() + "/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        // Link result pages should be accessible without authentication
                        .requestMatchers(linkSuccessPath, linkFailurePath).permitAll()
                        .requestMatchers(oauth2Props.getLinkBaseUrl() + "/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri(oauth2Props.getAuthorizationBaseUrl())
                        )
                        .redirectionEndpoint(endpoint -> endpoint
                                .baseUri(oauth2Props.getCallbackBaseUrl() + "/*")
                        )
                        .userInfoEndpoint(endpoint -> endpoint
                                .userService(oauth2UserService)
                                .oidcUserService(oidcUserService)
                        )
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * Extract path from URL (removes query parameters).
     */
    private String extractPath(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) : url;
    }
}
