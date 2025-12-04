package dev.simplecore.simplix.auth.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpliXAuthProperties {
    private boolean enabled = true;
    private JweProperties jwe = new JweProperties();
    private TokenProperties token = new TokenProperties();
    private Security security = new Security();
    private CorsProperties cors = new CorsProperties();

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