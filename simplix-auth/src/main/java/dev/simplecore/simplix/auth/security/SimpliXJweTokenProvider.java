package dev.simplecore.simplix.auth.security;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import dev.simplecore.simplix.auth.exception.TokenValidationException;
import dev.simplecore.simplix.auth.jwe.provider.JweKeyProvider;
import dev.simplecore.simplix.auth.jwe.provider.StaticJweKeyProvider;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import dev.simplecore.simplix.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXJweTokenProvider {
    private final SimpliXAuthProperties properties;
    private final MessageSource messageSource;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService;
    private final JweKeyProvider jweKeyProvider;
    private RSAEncrypter encrypter;
    private RSADecrypter decrypter;

    public SimpliXJweTokenProvider(
            SimpliXAuthProperties properties,
            MessageSource messageSource,
            UserDetailsService userDetailsService,
            @Autowired(required = false) TokenBlacklistService blacklistService,
            @Autowired(required = false) JweKeyProvider jweKeyProvider) {
        this.properties = properties;
        this.messageSource = messageSource;
        this.userDetailsService = userDetailsService;
        this.blacklistService = blacklistService;
        this.jweKeyProvider = jweKeyProvider;
    }

    @Bean
    @ConditionalOnMissingBean
    public static SimpliXJweTokenProvider jweTokenProvider(
            SimpliXAuthProperties properties,
            MessageSource messageSource,
            UserDetailsService userDetailsService,
            @Autowired(required = false) TokenBlacklistService blacklistService,
            @Autowired(required = false) JweKeyProvider jweKeyProvider) {
        return new SimpliXJweTokenProvider(properties, messageSource, userDetailsService, blacklistService, jweKeyProvider);
    }

    @PostConstruct
    public void init() throws JOSEException, ParseException {
        // Check if JweKeyProvider is available and configured (key-rolling mode)
        if (jweKeyProvider != null && jweKeyProvider.isConfigured()) {
            KeyPair keyPair = jweKeyProvider.getCurrentKeyPair();
            this.encrypter = new RSAEncrypter((RSAPublicKey) keyPair.getPublic());
            this.decrypter = new RSADecrypter((RSAPrivateKey) keyPair.getPrivate());
            log.info("JweTokenProvider initialized with {} (version: {})",
                jweKeyProvider.getName(), jweKeyProvider.getCurrentVersion());
            return;
        }

        // Legacy mode: load key from properties
        String key = properties.getJwe().getEncryptionKey();
        if (key == null && properties.getJwe().getEncryptionKeyLocation() != null) {
            try {
                Resource resource = new DefaultResourceLoader().getResource(properties.getJwe().getEncryptionKeyLocation());
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                key = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(
                    messageSource.getMessage("jwe.key.load.failed",
                        new Object[]{properties.getJwe().getEncryptionKeyLocation()},
                        "Failed to load JWE key from {0}",
                        LocaleContextHolder.getLocale()),
                    e);
            }
        }

        // If key-rolling is enabled but provider not configured, wait for initialization
        if (properties.getJwe().getKeyRolling().isEnabled()) {
            if (key == null) {
                log.info("JWE key-rolling enabled, waiting for key initialization via JweKeyRotationService");
                return;
            }
        }

        if (key == null) {
            throw new IllegalStateException(
                messageSource.getMessage("jwe.key.not.configured", null,
                    "JWE encryption key is not configured",
                    LocaleContextHolder.getLocale()));
        }

        RSAKey rsaKey = RSAKey.parse(key);
        this.encrypter = new RSAEncrypter((RSAPublicKey) rsaKey.toPublicKey());
        this.decrypter = new RSADecrypter((RSAPrivateKey) rsaKey.toPrivateKey());

        // Initialize StaticJweKeyProvider if available (for kid header support)
        if (jweKeyProvider instanceof StaticJweKeyProvider staticProvider && !staticProvider.isConfigured()) {
            staticProvider.initialize(key);
        }

        log.info("JweTokenProvider initialized with static key from properties");
    }

    public TokenResponse createTokenPair(String username, String clientIp, String userAgent) throws JOSEException {
        Date now = new Date();

        // Read token lifetime from properties (in seconds)
        int accessLifetime = properties.getToken().getAccessTokenLifetime();
        int refreshLifetime = properties.getToken().getRefreshTokenLifetime();

        Date accessTokenExpiry = new Date(now.getTime() + accessLifetime * 1000L);
        Date refreshTokenExpiry = new Date(now.getTime() + refreshLifetime * 1000L);

        String accessToken = createToken(username, accessTokenExpiry, clientIp, userAgent);
        String refreshToken = createToken(username, refreshTokenExpiry, clientIp, userAgent);

        return new TokenResponse(accessToken, refreshToken, accessTokenExpiry, refreshTokenExpiry);
    }

    private String createToken(String subject, Date expirationTime, String clientIp, String userAgent) throws JOSEException {
        // Ensure encrypter is available (may be initialized lazily in key-rolling mode)
        if (encrypter == null && jweKeyProvider != null && jweKeyProvider.isConfigured()) {
            KeyPair keyPair = jweKeyProvider.getCurrentKeyPair();
            this.encrypter = new RSAEncrypter((RSAPublicKey) keyPair.getPublic());
            this.decrypter = new RSADecrypter((RSAPrivateKey) keyPair.getPrivate());
        }

        if (encrypter == null) {
            throw new IllegalStateException("JWE encrypter not initialized. Check key configuration.");
        }

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(subject)
            .jwtID(UUID.randomUUID().toString())  // Add JTI for blacklist support
            .issueTime(new Date())
            .expirationTime(expirationTime)
            .claim("clientIp", clientIp)
            .claim("userAgent", userAgent)
            .build();

        // Build header with kid (Key ID) for multi-key support
        String kid = jweKeyProvider != null ? jweKeyProvider.getCurrentVersion() : null;

        JWEHeader.Builder headerBuilder = new JWEHeader.Builder(
            JWEAlgorithm.parse(properties.getJwe().getAlgorithm()),
            EncryptionMethod.parse(properties.getJwe().getEncryptionMethod()));

        if (kid != null) {
            headerBuilder.keyID(kid);
        }

        JWEHeader header = headerBuilder.build();

        EncryptedJWT jwt = new EncryptedJWT(header, claims);
        jwt.encrypt(encrypter);

        return jwt.serialize();
    }

    public TokenResponse refreshTokens(String refreshToken, String remoteAddr, String userAgent) {
        try {
            // Clear existing authentication
            SecurityContextHolder.clearContext();

            // Validate refresh token and extract claims
            JWTClaimsSet claims = parseToken(refreshToken);
            String username = claims.getSubject();
            String oldJti = claims.getJWTID();

            // Token validation
            if (!validateToken(refreshToken, remoteAddr, userAgent)) {
                throw new TokenValidationException(
                    messageSource.getMessage("token.refresh.invalid", null,
                        "Invalid refresh token", LocaleContextHolder.getLocale()),
                    messageSource.getMessage("token.refresh.invalid.detail", null,
                        "The refresh token is not valid or has been tampered with",
                        LocaleContextHolder.getLocale())
                );
            }

            // Generate new token pair
            TokenResponse tokens = createTokenPair(
                username,
                remoteAddr,
                userAgent
            );

            // Blacklist old refresh token (if enabled and rotation is enabled)
            if (properties.getToken().isEnableBlacklist() &&
                properties.getToken().isEnableTokenRotation() &&
                blacklistService != null) {

                // Calculate remaining TTL for the old refresh token
                Date expiryDate = claims.getExpirationTime();
                Duration ttl = Duration.between(Instant.now(), expiryDate.toInstant());

                // Add old refresh token's JTI to blacklist (only if not already expired)
                if (ttl.toSeconds() > 0) {
                    blacklistService.blacklist(oldJti, ttl);
                }
            }

            // Set new authentication
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            return tokens;
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            throw new TokenValidationException(
                messageSource.getMessage("token.refresh.failed", null,
                    "Failed to refresh tokens", LocaleContextHolder.getLocale()),
                ex.getMessage()
            );
        }
    }

    public JWTClaimsSet parseToken(String token) throws ParseException, JOSEException {
        EncryptedJWT jwt = EncryptedJWT.parse(token);

        // Get kid (Key ID) from header to select correct decryption key
        String kid = jwt.getHeader().getKeyID();
        RSADecrypter tokenDecrypter;

        if (kid != null && jweKeyProvider != null) {
            // Use key from provider based on kid
            KeyPair keyPair = jweKeyProvider.getKeyPair(kid);
            tokenDecrypter = new RSADecrypter((RSAPrivateKey) keyPair.getPrivate());
        } else {
            // Fall back to default decrypter (legacy tokens without kid)
            if (decrypter == null) {
                throw new IllegalStateException("JWE decrypter not initialized. Check key configuration.");
            }
            tokenDecrypter = this.decrypter;
        }

        jwt.decrypt(tokenDecrypter);
        return jwt.getJWTClaimsSet();
    }

    /**
     * Revoke a token by adding its JTI to the blacklist.
     *
     * @param token the token to revoke
     * @return true if the token was added to blacklist, false if blacklist is disabled or token already expired
     */
    public boolean revokeToken(String token) {
        if (!properties.getToken().isEnableBlacklist() || blacklistService == null) {
            log.warn("Token blacklist is disabled. Tokens will remain valid until expiry.");
            return false;
        }

        try {
            JWTClaimsSet claims = parseToken(token);
            String jti = claims.getJWTID();
            Date expiryDate = claims.getExpirationTime();

            if (jti == null || expiryDate == null) {
                log.warn("Token does not contain JTI or expiration time, cannot revoke");
                return false;
            }

            Duration ttl = Duration.between(Instant.now(), expiryDate.toInstant());
            if (ttl.toSeconds() > 0) {
                blacklistService.blacklist(jti, ttl);
                log.debug("Token revoked successfully, JTI: {}", jti);
                return true;
            } else {
                log.debug("Token already expired, no need to blacklist");
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to revoke token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateToken(String token, String remoteAddr, String userAgent) {
        try {
            JWTClaimsSet claims = parseToken(token);
            String jti = claims.getJWTID();

            // Check blacklist (if enabled)
            if (properties.getToken().isEnableBlacklist() && blacklistService != null) {
                if (blacklistService.isBlacklisted(jti)) {
                    throw new TokenValidationException(
                        messageSource.getMessage("token.revoked", null,
                            "Token has been revoked",
                            LocaleContextHolder.getLocale()),
                        messageSource.getMessage("token.revoked.detail", null,
                            "This token has been invalidated",
                            LocaleContextHolder.getLocale())
                    );
                }
            }

            // Check token expiration (always required)
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                throw new TokenValidationException(
                    ErrorCode.AUTH_TOKEN_EXPIRED,
                    messageSource.getMessage("token.expired", null,
                        "Token is expired",
                        LocaleContextHolder.getLocale()),
                    messageSource.getMessage("token.expired.detail", null,
                        "The access token has expired",
                        LocaleContextHolder.getLocale())
                );
            }

            // Check IP address (optional)
            if (properties.getToken().isEnableIpValidation()) {
                String tokenIp = claims.getStringClaim("clientIp");
                if (!Objects.equals(tokenIp, remoteAddr)) {
                    throw new TokenValidationException(
                        messageSource.getMessage("token.ip.mismatch", null,
                            "IP address mismatch",
                            LocaleContextHolder.getLocale()),
                        messageSource.getMessage("token.ip.mismatch.detail",
                            new Object[]{tokenIp, remoteAddr},
                            "Expected IP: {0}, but got: {1}",
                            LocaleContextHolder.getLocale())
                    );
                }
            }

            // Check User Agent (optional)
            if (properties.getToken().isEnableUserAgentValidation()) {
                String tokenUserAgent = claims.getStringClaim("userAgent");
                if (!Objects.equals(tokenUserAgent, userAgent)) {
                    throw new TokenValidationException(
                        messageSource.getMessage("token.useragent.mismatch", null,
                            "User Agent mismatch",
                            LocaleContextHolder.getLocale()),
                        messageSource.getMessage("token.useragent.mismatch.detail",
                            new Object[]{tokenUserAgent, userAgent},
                            "Expected User Agent: {0}, but got: {1}",
                            LocaleContextHolder.getLocale())
                    );
                }
            }

            return true;
        } catch (TokenValidationException e) {
            throw e;  // Pass through custom exceptions
        } catch (Exception e) {
            throw new TokenValidationException(
                messageSource.getMessage("token.validation.failed", null,
                    "Token validation failed",
                    LocaleContextHolder.getLocale()),
                e.getMessage()
            );
        }
    }

    @Getter
    @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        private ZonedDateTime accessTokenExpiry;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        private ZonedDateTime refreshTokenExpiry;

        public TokenResponse(String accessToken, String refreshToken, Date accessTokenExpiry, Date refreshTokenExpiry) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessTokenExpiry = ZonedDateTime.ofInstant(accessTokenExpiry.toInstant(), ZoneId.systemDefault());
            this.refreshTokenExpiry = ZonedDateTime.ofInstant(refreshTokenExpiry.toInstant(), ZoneId.systemDefault());
        }
    }
} 