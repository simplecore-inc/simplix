package dev.simplecore.simplix.auth.oauth2.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables SimpliX OAuth2 social login support.
 *
 * <p>Add this annotation to a {@code @Configuration} class in your application
 * to enable OAuth2 social login functionality. Your application must provide
 * an implementation of {@link dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService}
 * as a Spring bean.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Configuration
 * @EnableSimplixOAuth2
 * public class OAuth2Config {
 *     // OAuth2AuthenticationService implementation should be a @Service
 * }
 * }</pre>
 *
 * <p>This annotation imports the necessary configuration for:</p>
 * <ul>
 *   <li>OAuth2 security filter chain (order 90)</li>
 *   <li>OAuth2 user info extraction</li>
 *   <li>OAuth2/OIDC user services</li>
 *   <li>Login success/failure handlers</li>
 * </ul>
 *
 * <p>Configuration properties are read from {@code simplix.auth.oauth2.*}</p>
 *
 * @see dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService
 * @see SimpliXOAuth2Configuration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SimpliXOAuth2Configuration.class)
public @interface EnableSimplixOAuth2 {
}
