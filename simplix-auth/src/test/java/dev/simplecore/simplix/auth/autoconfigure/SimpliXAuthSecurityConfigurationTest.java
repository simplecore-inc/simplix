package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthSecurityConfiguration")
class SimpliXAuthSecurityConfigurationTest {

    private SimpliXAuthProperties properties;
    private SimpliXAuthSecurityConfiguration configuration;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
        configuration = new SimpliXAuthSecurityConfiguration(properties);
    }

    @Nested
    @DisplayName("passwordEncoder")
    class PasswordEncoderTest {

        @Test
        @DisplayName("should create delegating password encoder")
        void shouldCreatePasswordEncoder() {
            PasswordEncoder encoder = configuration.passwordEncoder();
            assertThat(encoder).isNotNull();

            // Verify it can encode and match
            String encoded = encoder.encode("testPassword");
            assertThat(encoder.matches("testPassword", encoded)).isTrue();
            assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
        }
    }

    @Nested
    @DisplayName("corsConfigurationSource")
    class CorsConfigurationSourceTest {

        @Test
        @DisplayName("should create CORS configuration with default settings")
        void shouldCreateCorsWithDefaults() {
            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
            assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        }

        @Test
        @DisplayName("should configure allowed origin patterns when set")
        void shouldConfigureAllowedOriginPatterns() {
            properties.getCors().setAllowedOriginPatterns(new String[]{"https://*.example.com", "http://localhost:*"});

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure allowed origins when patterns not set")
        void shouldConfigureAllowedOrigins() {
            properties.getCors().setAllowedOrigins(new String[]{"https://example.com"});

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure allowed methods")
        void shouldConfigureAllowedMethods() {
            properties.getCors().setAllowedMethods(new String[]{"GET", "POST", "PUT", "DELETE"});

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure allowed headers")
        void shouldConfigureAllowedHeaders() {
            properties.getCors().setAllowedHeaders(new String[]{"Authorization", "Content-Type"});

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure exposed headers")
        void shouldConfigureExposedHeaders() {
            properties.getCors().setExposedHeaders(new String[]{"X-Custom-Header"});

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure allow credentials")
        void shouldConfigureAllowCredentials() {
            properties.getCors().setAllowCredentials(true);

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should configure max age")
        void shouldConfigureMaxAge() {
            properties.getCors().setMaxAge(3600L);

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }

        @Test
        @DisplayName("should handle null CORS settings gracefully")
        void shouldHandleNullCorsSettings() {
            // All CORS settings are null by default
            properties.getCors().setAllowedOriginPatterns(null);
            properties.getCors().setAllowedOrigins(null);
            properties.getCors().setAllowedMethods(null);
            properties.getCors().setAllowedHeaders(null);
            properties.getCors().setExposedHeaders(null);
            properties.getCors().setAllowCredentials(null);
            properties.getCors().setMaxAge(null);

            CorsConfigurationSource source = configuration.corsConfigurationSource();
            assertThat(source).isNotNull();
        }
    }

    @Nested
    @DisplayName("helper methods")
    class HelperMethods {

        @Test
        @DisplayName("should combine arrays correctly")
        void shouldCombineArrays() throws Exception {
            Method method = SimpliXAuthSecurityConfiguration.class.getDeclaredMethod(
                    "combineArrays", String[].class, String[].class);
            method.setAccessible(true);

            String[] arr1 = {"a", "b"};
            String[] arr2 = {"c", "d", "e"};

            String[] result = (String[]) method.invoke(configuration, arr1, arr2);
            assertThat(result).containsExactly("a", "b", "c", "d", "e");
        }

        @Test
        @DisplayName("should return empty array when no API permit patterns")
        void shouldReturnEmptyWhenNoPatterns() throws Exception {
            Method method = SimpliXAuthSecurityConfiguration.class.getDeclaredMethod("getApiPermitPatterns");
            method.setAccessible(true);

            properties.getSecurity().setPermitAllPatterns(null);
            String[] result = (String[]) method.invoke(configuration);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should filter API-related patterns")
        void shouldFilterApiRelatedPatterns() throws Exception {
            Method method = SimpliXAuthSecurityConfiguration.class.getDeclaredMethod("getApiPermitPatterns");
            method.setAccessible(true);

            properties.getSecurity().setPermitAllPatterns(new String[]{
                    "/api/public/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**",
                    "/actuator/health",
                    "/login",  // should be filtered out
                    "/css/**"  // should be filtered out
            });

            String[] result = (String[]) method.invoke(configuration);
            assertThat(result).contains("/api/public/**", "/swagger-ui/**", "/v3/api-docs/**",
                    "/webjars/**", "/actuator/health");
            assertThat(result).doesNotContain("/login", "/css/**");
        }

        @Test
        @DisplayName("should return empty for empty permit patterns")
        void shouldReturnEmptyForEmptyPatterns() throws Exception {
            Method method = SimpliXAuthSecurityConfiguration.class.getDeclaredMethod("getApiPermitPatterns");
            method.setAccessible(true);

            properties.getSecurity().setPermitAllPatterns(new String[]{});
            String[] result = (String[]) method.invoke(configuration);
            assertThat(result).isEmpty();
        }
    }
}
