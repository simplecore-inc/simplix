package dev.simplecore.simplix.auth.properties;

import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "simplix.auth")
public class SimpliXAuthProperties {
    private boolean enabled = true;
    private JweProperties jwe = new JweProperties();
    private TokenProperties token = new TokenProperties();
    private Security security = new Security();
    private CorsProperties cors = new CorsProperties();
    private SimpliXOAuth2Properties oauth2 = new SimpliXOAuth2Properties();

    @Getter
    @Setter
    public static class Security {
        private boolean enableTokenEndpoints = true;
        private boolean enableWebSecurity = true;
        private boolean enableCors = true;
        private boolean enableCsrf = true;
        private boolean enableHttpBasic = false;
        private boolean requireHttps = false;
        private boolean preferTokenOverSession = true;
        private String[] csrfIgnorePatterns = new String[]{"/api/token/**", "/h2-console/**"};
        private String loginPageTemplate = "login";
        private String loginProcessingUrl = "/login";
        private String logoutUrl = "/logout";
        private String[] permitAllPatterns;
    }
    
    @Getter
    @Setter
    public static class CorsProperties {
        private String[] allowedOrigins;
        private String[] allowedMethods;
        private String[] allowedHeaders;
        private String[] exposedHeaders;
        private Boolean allowCredentials;
        private Long maxAge;
    }

    @Getter
    @Setter
    public static class JweProperties {
        private String encryptionKey;
        private String encryptionKeyLocation;
        private String algorithm = "RSA-OAEP-256";
        private String encryptionMethod = "A256GCM";

        /**
         * Key rolling configuration for database-backed key management.
         */
        private KeyRolling keyRolling = new KeyRolling();

        @Getter
        @Setter
        public static class KeyRolling {
            /**
             * Enable database-backed key rolling.
             * When enabled, keys are stored in DB and rotation is supported.
             * Requires JweKeyStore bean to be provided by the application.
             */
            private boolean enabled = false;

            /**
             * RSA key size in bits for generated keys.
             * Recommended: 2048 (minimum) or 4096 (higher security).
             */
            private int keySize = 2048;

            /**
             * Automatically initialize with a key if none exists.
             * When true, generates an initial key on application startup
             * if the key store is empty.
             */
            private boolean autoInitialize = true;

            /**
             * Key retention configuration.
             */
            private KeyRetention retention = new KeyRetention();

            @Getter
            @Setter
            public static class KeyRetention {
                /**
                 * Buffer period in seconds added to token lifetime for key expiration.
                 * Key expiration = creation time + max token lifetime + buffer.
                 * Default: 86400 (1 day)
                 */
                private int bufferSeconds = 86400;

                /**
                 * Whether to automatically clean up expired keys.
                 * When true, expired keys are deleted during rotation.
                 * When false, expired keys are only marked but not deleted.
                 */
                private boolean autoCleanup = false;
            }
        }
    }

    @Getter
    @Setter
    public static class TokenProperties {
        // Token lifetime (in seconds)
        private int accessTokenLifetime = 1800;  // 30 minutes
        private int refreshTokenLifetime = 604800;  // 7 days

        // Security validation
        private boolean enableIpValidation = false;
        private boolean enableUserAgentValidation = false;

        // Token management
        private boolean enableTokenRotation = true;

        // Blacklist (optional feature)
        private boolean enableBlacklist = false;

        // Session management
        private boolean createSessionOnTokenIssue = true;
    }
} 