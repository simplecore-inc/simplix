package dev.simplecore.simplix.auth.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthProperties")
class SimpliXAuthPropertiesTest {

    private SimpliXAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
    }

    @Nested
    @DisplayName("default values")
    class DefaultValues {

        @Test
        @DisplayName("should have enabled=true by default")
        void shouldBeEnabledByDefault() {
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have non-null sub-properties")
        void shouldHaveNonNullSubProperties() {
            assertThat(properties.getJwe()).isNotNull();
            assertThat(properties.getToken()).isNotNull();
            assertThat(properties.getSecurity()).isNotNull();
            assertThat(properties.getCors()).isNotNull();
            assertThat(properties.getOauth2()).isNotNull();
        }
    }

    @Nested
    @DisplayName("JweProperties defaults")
    class JwePropertiesDefaults {

        @Test
        @DisplayName("should have correct JWE algorithm defaults")
        void shouldHaveCorrectJweDefaults() {
            assertThat(properties.getJwe().getAlgorithm()).isEqualTo("RSA-OAEP-256");
            assertThat(properties.getJwe().getEncryptionMethod()).isEqualTo("A256GCM");
            assertThat(properties.getJwe().getEncryptionKey()).isNull();
            assertThat(properties.getJwe().getEncryptionKeyLocation()).isNull();
        }

        @Test
        @DisplayName("should have key rolling disabled by default")
        void shouldHaveKeyRollingDisabled() {
            assertThat(properties.getJwe().getKeyRolling().isEnabled()).isFalse();
            assertThat(properties.getJwe().getKeyRolling().getKeySize()).isEqualTo(2048);
            assertThat(properties.getJwe().getKeyRolling().isAutoInitialize()).isTrue();
        }

        @Test
        @DisplayName("should have correct key retention defaults")
        void shouldHaveKeyRetentionDefaults() {
            var retention = properties.getJwe().getKeyRolling().getRetention();
            assertThat(retention.getBufferSeconds()).isEqualTo(86400);
            assertThat(retention.isAutoCleanup()).isFalse();
        }
    }

    @Nested
    @DisplayName("TokenProperties defaults")
    class TokenPropertiesDefaults {

        @Test
        @DisplayName("should have correct token lifetime defaults")
        void shouldHaveCorrectTokenDefaults() {
            assertThat(properties.getToken().getAccessTokenLifetime()).isEqualTo(1800);
            assertThat(properties.getToken().getRefreshTokenLifetime()).isEqualTo(604800);
        }

        @Test
        @DisplayName("should have IP and User-Agent validation disabled by default")
        void shouldHaveValidationDisabled() {
            assertThat(properties.getToken().isEnableIpValidation()).isFalse();
            assertThat(properties.getToken().isEnableUserAgentValidation()).isFalse();
        }

        @Test
        @DisplayName("should have token rotation enabled and blacklist disabled by default")
        void shouldHaveCorrectManagementDefaults() {
            assertThat(properties.getToken().isEnableTokenRotation()).isTrue();
            assertThat(properties.getToken().isEnableBlacklist()).isFalse();
            assertThat(properties.getToken().isCreateSessionOnTokenIssue()).isTrue();
        }
    }

    @Nested
    @DisplayName("Security defaults")
    class SecurityDefaults {

        @Test
        @DisplayName("should have correct security defaults")
        void shouldHaveCorrectSecurityDefaults() {
            var security = properties.getSecurity();
            assertThat(security.isEnableTokenEndpoints()).isTrue();
            assertThat(security.isEnableWebSecurity()).isTrue();
            assertThat(security.isEnableCors()).isTrue();
            assertThat(security.isEnableCsrf()).isTrue();
            assertThat(security.isEnableHttpBasic()).isFalse();
            assertThat(security.isRequireHttps()).isFalse();
            assertThat(security.isPreferTokenOverSession()).isTrue();
            assertThat(security.getLoginPageTemplate()).isEqualTo("login");
            assertThat(security.getLoginProcessingUrl()).isEqualTo("/login");
            assertThat(security.getLogoutUrl()).isEqualTo("/logout");
        }

        @Test
        @DisplayName("should have CSRF ignore patterns")
        void shouldHaveCsrfIgnorePatterns() {
            assertThat(properties.getSecurity().getCsrfIgnorePatterns())
                    .containsExactly("/api/token/**", "/h2-console/**");
        }
    }

    @Nested
    @DisplayName("CorsProperties defaults")
    class CorsPropertiesDefaults {

        @Test
        @DisplayName("should have null CORS properties by default")
        void shouldHaveNullCorsDefaults() {
            var cors = properties.getCors();
            assertThat(cors.getAllowedOrigins()).isNull();
            assertThat(cors.getAllowedOriginPatterns()).isNull();
            assertThat(cors.getAllowedMethods()).isNull();
            assertThat(cors.getAllowedHeaders()).isNull();
            assertThat(cors.getExposedHeaders()).isNull();
            assertThat(cors.getAllowCredentials()).isNull();
            assertThat(cors.getMaxAge()).isNull();
        }
    }
}
