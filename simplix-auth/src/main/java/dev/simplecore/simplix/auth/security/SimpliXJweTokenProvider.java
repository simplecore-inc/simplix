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
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import dev.simplecore.simplix.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
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

@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXJweTokenProvider {
    private final SimpliXAuthProperties properties;
    private final MessageSource messageSource;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService;
    private RSAEncrypter encrypter;
    private RSADecrypter decrypter;

    public SimpliXJweTokenProvider(
            SimpliXAuthProperties properties,
            MessageSource messageSource,
            UserDetailsService userDetailsService,
            @Autowired(required = false) TokenBlacklistService blacklistService) {
        this.properties = properties;
        this.messageSource = messageSource;
        this.userDetailsService = userDetailsService;
        this.blacklistService = blacklistService;
    }

    @Bean
    @ConditionalOnMissingBean
    public static SimpliXJweTokenProvider jweTokenProvider(
            SimpliXAuthProperties properties,
            MessageSource messageSource,
            UserDetailsService userDetailsService,
            @Autowired(required = false) TokenBlacklistService blacklistService) {
        return new SimpliXJweTokenProvider(properties, messageSource, userDetailsService, blacklistService);
    }

    @PostConstruct
    public void init() throws JOSEException, ParseException {
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
        if (key == null) {
            throw new IllegalStateException(
                messageSource.getMessage("jwe.key.not.configured", null,
                    "JWE encryption key is not configured",
                    LocaleContextHolder.getLocale()));
        }
        RSAKey rsaKey = RSAKey.parse(key);
        this.encrypter = new RSAEncrypter((RSAPublicKey) rsaKey.toPublicKey());
        this.decrypter = new RSADecrypter((RSAPrivateKey) rsaKey.toPrivateKey());
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
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(subject)
            .jwtID(UUID.randomUUID().toString())  // Add JTI for blacklist support
            .issueTime(new Date())
            .expirationTime(expirationTime)
            .claim("clientIp", clientIp)
            .claim("userAgent", userAgent)
            .build();

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.parse(properties.getJwe().getAlgorithm()),
                EncryptionMethod.parse(properties.getJwe().getEncryptionMethod()))
            .build();

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
        jwt.decrypt(decrypter);
        return jwt.getJWTClaimsSet();
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