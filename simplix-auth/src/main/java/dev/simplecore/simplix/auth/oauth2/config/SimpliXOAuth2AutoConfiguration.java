package dev.simplecore.simplix.auth.oauth2.config;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.extractor.OAuth2UserInfoExtractor;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginFailureHandler;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOAuth2UserService;
import dev.simplecore.simplix.auth.oauth2.service.SimpliXOidcUserService;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

/**
 * Auto-configuration for SimpliX OAuth2 social login support.
 *
 * <p>This configuration is activated when:</p>
 * <ul>
 *   <li>{@code simplix.auth.oauth2.enabled=true} (default)</li>
 *   <li>Spring Security OAuth2 Client is on the classpath</li>
 *   <li>An {@link OAuth2AuthenticationService} bean is provided by the application</li>
 * </ul>
 *
 * <p>Applications must implement {@link OAuth2AuthenticationService} to enable
 * OAuth2 authentication. This interface handles user lookup, creation, and
 * social account management.</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
@AutoConfigureAfter(JpaRepositoriesAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "simplix.auth.oauth2",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnClass(OAuth2LoginConfigurer.class)
@ConditionalOnBean(OAuth2AuthenticationService.class)
public class SimpliXOAuth2AutoConfiguration {

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
            SimpliXJweTokenProvider tokenProvider,
            SimpliXAuthProperties properties) {
        return new OAuth2LoginSuccessHandler(
                authService,
                extractor,
                tokenProvider,
                properties.getOauth2()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2LoginFailureHandler oauth2LoginFailureHandler(
            SimpliXAuthProperties properties) {
        return new OAuth2LoginFailureHandler(properties.getOauth2());
    }
}
