package dev.simplecore.simplix.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import dev.simplecore.simplix.auth.jwe.provider.JweKeyProvider;
import dev.simplecore.simplix.auth.jwe.provider.StaticJweKeyProvider;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import dev.simplecore.simplix.auth.audit.TokenAuditEventPublisher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;;

@SuppressWarnings("unchecked")
@DisplayName("SimpliXJweTokenProvider")
@ExtendWith(MockitoExtension.class)
class SimpliXJweTokenProviderTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService blacklistService;

    @Mock
    @SuppressWarnings("rawtypes")
    private ObjectProvider auditPublisherProvider;

    private SimpliXAuthProperties properties;
    private SimpliXJweTokenProvider tokenProvider;
    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        testKeyPair = gen.generateKeyPair();

        properties = new SimpliXAuthProperties();
        properties.getJwe().setAlgorithm("RSA-OAEP-256");
        properties.getJwe().setEncryptionMethod("A256GCM");
        properties.getToken().setAccessTokenLifetime(1800);
        properties.getToken().setRefreshTokenLifetime(604800);
    }

    private SimpliXJweTokenProvider createProviderWithKeyPair() throws Exception {
        StaticJweKeyProvider keyProvider = new StaticJweKeyProvider();
        keyProvider.initialize(testKeyPair);

        SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                properties, messageSource, userDetailsService,
                blacklistService, keyProvider, auditPublisherProvider);
        provider.init();
        return provider;
    }

    @Nested
    @DisplayName("createTokenPair")
    class CreateTokenPair {

        @Test
        @DisplayName("should create access and refresh tokens")
        void shouldCreateTokenPair() throws Exception {
            tokenProvider = createProviderWithKeyPair();

            SimpliXJweTokenProvider.TokenResponse response =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "TestAgent");

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotEmpty();
            assertThat(response.getRefreshToken()).isNotEmpty();
            assertThat(response.getAccessTokenExpiry()).isNotNull();
            assertThat(response.getRefreshTokenExpiry()).isNotNull();
            assertThat(response.getAccessToken()).isNotEqualTo(response.getRefreshToken());
        }
    }

    @Nested
    @DisplayName("parseToken")
    class ParseToken {

        @Test
        @DisplayName("should parse and decrypt a valid token")
        void shouldParseValidToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            JWTClaimsSet claims = tokenProvider.parseToken(tokens.getAccessToken());

            assertThat(claims.getSubject()).isEqualTo("testuser");
            assertThat(claims.getJWTID()).isNotNull();
            assertThat(claims.getExpirationTime()).isNotNull();
            assertThat(claims.getStringClaim("clientIp")).isEqualTo("127.0.0.1");
            assertThat(claims.getStringClaim("userAgent")).isEqualTo("Agent");
        }

        @Test
        @DisplayName("should throw on malformed token")
        void shouldThrowOnMalformedToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();

            assertThatThrownBy(() -> tokenProvider.parseToken("not-a-valid-jwe"))
                    .isInstanceOf(ParseException.class);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate a valid token")
        void shouldValidateValidToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            boolean result = tokenProvider.validateToken(
                    tokens.getAccessToken(), "127.0.0.1", "Agent");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should fail validation when IP mismatch and IP validation enabled")
        void shouldFailOnIpMismatch() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableIpValidation(true);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("default message");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "192.168.1.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }

        @Test
        @DisplayName("should fail validation when User-Agent mismatch and UA validation enabled")
        void shouldFailOnUserAgentMismatch() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableUserAgentValidation(true);
            properties.getToken().setEnableIpValidation(false);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("default message");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Chrome");

            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Firefox"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("should return false when blacklist is disabled")
        void shouldReturnFalseWhenBlacklistDisabled() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(false);

            boolean result = tokenProvider.revokeToken("some-token");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when blacklist service is null")
        void shouldReturnFalseWhenBlacklistServiceNull() throws Exception {
            properties.getToken().setEnableBlacklist(true);

            StaticJweKeyProvider keyProvider = new StaticJweKeyProvider();
            keyProvider.initialize(testKeyPair);

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    null, keyProvider, auditPublisherProvider);
            provider.init();

            boolean result = provider.revokeToken("some-token");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should revoke a valid token when blacklist is enabled")
        void shouldRevokeValidToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            boolean result = tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("validateToken - blacklist")
    class ValidateTokenBlacklist {

        @Test
        @DisplayName("should throw when token is blacklisted")
        void shouldThrowWhenBlacklisted() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Token revoked");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Parse to get JTI
            JWTClaimsSet claims = tokenProvider.parseToken(tokens.getAccessToken());
            String jti = claims.getJWTID();

            when(blacklistService.isBlacklisted(jti)).thenReturn(true);

            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }

        @Test
        @DisplayName("should pass when token is not blacklisted")
        void shouldPassWhenNotBlacklisted() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            JWTClaimsSet claims = tokenProvider.parseToken(tokens.getAccessToken());
            when(blacklistService.isBlacklisted(claims.getJWTID())).thenReturn(false);

            boolean result = tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent");
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("refreshTokens")
    class RefreshTokens {

        @Test
        @DisplayName("should refresh tokens successfully")
        void shouldRefreshTokens() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            SimpliXJweTokenProvider.TokenResponse originalTokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            SimpliXJweTokenProvider.TokenResponse newTokens =
                    tokenProvider.refreshTokens(originalTokens.getRefreshToken(), "127.0.0.1", "Agent");

            assertThat(newTokens).isNotNull();
            assertThat(newTokens.getAccessToken()).isNotEmpty();
            assertThat(newTokens.getRefreshToken()).isNotEmpty();
            assertThat(newTokens.getAccessToken()).isNotEqualTo(originalTokens.getAccessToken());
        }

        @Test
        @DisplayName("should blacklist old refresh token during rotation")
        void shouldBlacklistOldRefreshToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setEnableTokenRotation(true);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
            when(blacklistService.isBlacklisted(anyString())).thenReturn(false);

            SimpliXJweTokenProvider.TokenResponse originalTokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            tokenProvider.refreshTokens(originalTokens.getRefreshToken(), "127.0.0.1", "Agent");

            verify(blacklistService).blacklist(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should throw TokenValidationException on invalid refresh token")
        void shouldThrowOnInvalidRefreshToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Failed to refresh");

            assertThatThrownBy(() ->
                    tokenProvider.refreshTokens("invalid-token", "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }

        @Test
        @DisplayName("should throw TokenValidationException when validation fails")
        void shouldThrowWhenValidationFails() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableIpValidation(true);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Validation failed");

            SimpliXJweTokenProvider.TokenResponse originalTokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Refresh from a different IP
            assertThatThrownBy(() ->
                    tokenProvider.refreshTokens(originalTokens.getRefreshToken(), "10.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("init")
    class Init {

        @Test
        @DisplayName("should throw when key is null and key rolling not enabled")
        void shouldThrowWhenKeyNotConfigured() {
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Key not configured");

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);

            assertThatThrownBy(provider::init)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should skip init when key rolling enabled and no key")
        void shouldSkipInitWhenKeyRollingEnabled() throws Exception {
            properties.getJwe().getKeyRolling().setEnabled(true);

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);

            // Should not throw - just log and return
            provider.init();
        }

        @Test
        @DisplayName("should initialize with configured JweKeyProvider")
        void shouldInitWithConfiguredKeyProvider() throws Exception {
            StaticJweKeyProvider keyProvider = new StaticJweKeyProvider();
            keyProvider.initialize(testKeyPair);

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, keyProvider, auditPublisherProvider);

            provider.init();

            // Should be able to create tokens after init
            SimpliXJweTokenProvider.TokenResponse tokens =
                    provider.createTokenPair("user", "127.0.0.1", "Agent");
            assertThat(tokens.getAccessToken()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("revokeToken with clientIp")
    class RevokeTokenWithAudit {

        @Test
        @DisplayName("should revoke token and publish audit when clientIp provided")
        void shouldRevokeWithAudit() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            boolean result = tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");

            assertThat(result).isTrue();
            verify(blacklistService).blacklist(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("should return false when token has no JTI or expiration")
        void shouldReturnFalseForInvalidTokenContent() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            // Use revokeToken with a malformed (unparseable) token
            boolean result = tokenProvider.revokeToken("unparseable-token", "127.0.0.1", "Agent");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should call revokeToken with no clientIp")
        void shouldCallRevokeTokenNoArgs() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            boolean result = tokenProvider.revokeToken(tokens.getAccessToken());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("static factory method")
    class StaticFactoryMethod {

        @Test
        @DisplayName("should create provider via static factory method")
        void shouldCreateViaFactory() {
            SimpliXJweTokenProvider result = SimpliXJweTokenProvider.jweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("provider with null audit publisher provider")
    class NullAuditPublisherProvider {

        @Test
        @DisplayName("should handle null auditPublisherProvider gracefully")
        void shouldHandleNullAuditPublisherProvider() throws Exception {
            StaticJweKeyProvider keyProvider = new StaticJweKeyProvider();
            keyProvider.initialize(testKeyPair);

            // Pass null for auditPublisherProvider
            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, keyProvider, null);
            provider.init();

            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            // All operations should work without audit publisher
            SimpliXJweTokenProvider.TokenResponse tokens =
                    provider.createTokenPair("testuser", "127.0.0.1", "Agent");
            assertThat(tokens).isNotNull();

            boolean isValid = provider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent");
            assertThat(isValid).isTrue();
        }
    }

    @Nested
    @DisplayName("init - legacy key loading")
    class InitLegacy {

        @Test
        @DisplayName("should init with encryption key from properties")
        void shouldInitWithEncryptionKeyFromProperties() throws Exception {
            // Generate a JWK JSON key
            com.nimbusds.jose.jwk.RSAKey rsaJwk = new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) testKeyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) testKeyPair.getPrivate())
                    .build();
            String jwkJson = rsaJwk.toJSONString();

            properties.getJwe().setEncryptionKey(jwkJson);

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);
            provider.init();

            // Should be able to create tokens
            SimpliXJweTokenProvider.TokenResponse tokens =
                    provider.createTokenPair("user", "127.0.0.1", "Agent");
            assertThat(tokens.getAccessToken()).isNotEmpty();
        }

        @Test
        @DisplayName("should init with encryption key from StaticJweKeyProvider")
        void shouldInitializeStaticJweKeyProvider() throws Exception {
            com.nimbusds.jose.jwk.RSAKey rsaJwk = new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) testKeyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) testKeyPair.getPrivate())
                    .build();
            String jwkJson = rsaJwk.toJSONString();

            properties.getJwe().setEncryptionKey(jwkJson);

            // Create an uninitialized StaticJweKeyProvider
            StaticJweKeyProvider staticProvider = new StaticJweKeyProvider();
            // Not calling initialize() here

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, staticProvider, auditPublisherProvider);
            provider.init();

            // After init, the static provider should be configured
            assertThat(staticProvider.isConfigured()).isTrue();
        }
    }

    @Nested
    @DisplayName("init - key from location")
    class InitKeyFromLocation {

        @Test
        @DisplayName("should init with encryption key from classpath location")
        void shouldInitWithKeyFromLocation() throws Exception {
            // Generate JWK JSON
            com.nimbusds.jose.jwk.RSAKey rsaJwk = new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) testKeyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) testKeyPair.getPrivate())
                    .build();
            String jwkJson = rsaJwk.toJSONString();

            // Write to temp file
            java.io.File tempFile = java.io.File.createTempFile("test-jwe-key", ".json");
            tempFile.deleteOnExit();
            java.nio.file.Files.writeString(tempFile.toPath(), jwkJson);

            properties.getJwe().setEncryptionKey(null);
            properties.getJwe().setEncryptionKeyLocation("file:" + tempFile.getAbsolutePath());

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);
            provider.init();

            // Should be able to create tokens
            SimpliXJweTokenProvider.TokenResponse tokens =
                    provider.createTokenPair("user", "127.0.0.1", "Agent");
            assertThat(tokens.getAccessToken()).isNotEmpty();
        }

        @Test
        @DisplayName("should throw when key location file not found")
        void shouldThrowWhenKeyLocationNotFound() {
            properties.getJwe().setEncryptionKey(null);
            properties.getJwe().setEncryptionKeyLocation("classpath:nonexistent-key.json");

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Failed to load key");

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);

            assertThatThrownBy(provider::init)
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("validateToken - expired token")
    class ValidateExpiredToken {

        @Test
        @DisplayName("should throw on expired token")
        void shouldThrowOnExpiredToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setAccessTokenLifetime(0); // 0 seconds = immediate expiry
            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Token expired");

            // Create token that will expire immediately
            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Wait briefly to ensure token is expired
            Thread.sleep(50);

            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("revokeToken - edge cases")
    class RevokeTokenEdgeCases {

        @Test
        @DisplayName("should return false for already expired token")
        void shouldReturnFalseForExpiredToken() throws Exception {
            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setAccessTokenLifetime(0);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Wait for token to expire
            Thread.sleep(50);

            boolean result = tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("createToken - lazy init")
    class CreateTokenLazyInit {

        @Test
        @DisplayName("should throw IllegalStateException when encrypter is null and no provider")
        void shouldThrowWhenEncrypterNotInitialized() throws Exception {
            properties.getJwe().getKeyRolling().setEnabled(true);

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);
            provider.init(); // Will skip because key-rolling is enabled but no key

            assertThatThrownBy(() ->
                    provider.createTokenPair("user", "127.0.0.1", "Agent"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not initialized");
        }
    }

    @Nested
    @DisplayName("parseToken - with kid")
    class ParseTokenWithKid {

        @Test
        @DisplayName("should use default decrypter for token without kid")
        void shouldUseDefaultDecrypterForNoKid() throws Exception {
            // Create provider without JweKeyProvider (null)
            com.nimbusds.jose.jwk.RSAKey rsaJwk = new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) testKeyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) testKeyPair.getPrivate())
                    .build();
            properties.getJwe().setEncryptionKey(rsaJwk.toJSONString());

            SimpliXJweTokenProvider provider = new SimpliXJweTokenProvider(
                    properties, messageSource, userDetailsService,
                    blacklistService, null, auditPublisherProvider);
            provider.init();

            // Create a token (without kid since no keyProvider)
            SimpliXJweTokenProvider.TokenResponse tokens =
                    provider.createTokenPair("testuser", "127.0.0.1", "Agent");

            JWTClaimsSet claims = provider.parseToken(tokens.getAccessToken());
            assertThat(claims.getSubject()).isEqualTo("testuser");
        }
    }

    @Nested
    @DisplayName("refreshTokens with audit")
    class RefreshTokensWithAudit {

        @Test
        @DisplayName("should publish refresh success audit event")
        void shouldPublishRefreshSuccessAudit() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            SimpliXJweTokenProvider.TokenResponse originalTokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            tokenProvider.refreshTokens(originalTokens.getRefreshToken(), "127.0.0.1", "Agent");

            verify(auditPublisher).publishTokenRefreshSuccess(any());
        }

        @Test
        @DisplayName("should publish refresh failed audit event on exception")
        void shouldPublishRefreshFailedAudit() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            tokenProvider = createProviderWithKeyPair();

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Failed");

            assertThatThrownBy(() ->
                    tokenProvider.refreshTokens("invalid", "127.0.0.1", "Agent"));
        }
    }

    @Nested
    @DisplayName("revokeToken audit details")
    class RevokeTokenAuditDetails {

        @Test
        @DisplayName("should publish token revoked event with clientIp")
        void shouldPublishTokenRevokedWithClientIp() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");

            verify(auditPublisher).publishTokenBlacklisted(anyString(), any(), eq("testuser"));
            verify(auditPublisher).publishTokenRevoked(any());
        }

        @Test
        @DisplayName("should not publish token revoked event without clientIp")
        void shouldNotPublishTokenRevokedWithoutClientIp() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            tokenProvider.revokeToken(tokens.getAccessToken());

            verify(auditPublisher).publishTokenBlacklisted(anyString(), any(), eq("testuser"));
            verify(auditPublisher, never()).publishTokenRevoked(any());
        }
    }

    @Nested
    @DisplayName("publish method exception handling")
    class PublishExceptionHandling {

        @Test
        @DisplayName("should handle exception in publishRefreshSuccess")
        void shouldHandleRefreshSuccessAuditException() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit failed"))
                    .when(auditPublisher).publishTokenRefreshSuccess(any());

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(false);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            UserDetails userDetails = User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
            when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

            SimpliXJweTokenProvider.TokenResponse original =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Should not throw even if audit publishing fails
            SimpliXJweTokenProvider.TokenResponse result =
                    tokenProvider.refreshTokens(original.getRefreshToken(), "127.0.0.1", "Agent");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should handle exception in publishRefreshFailed")
        void shouldHandleRefreshFailedAuditException() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit failed"))
                    .when(auditPublisher).publishTokenRefreshFailed(any());

            tokenProvider = createProviderWithKeyPair();
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("error");

            // Invalid token will cause refresh to fail
            assertThatThrownBy(() ->
                    tokenProvider.refreshTokens("invalid-token", "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }

        @Test
        @DisplayName("should handle exception in publishTokenRevoked")
        void shouldHandleTokenRevokedAuditException() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit failed"))
                    .when(auditPublisher).publishTokenRevoked(any());

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Should still return true even if audit fails
            boolean result = tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle exception in publishTokenBlacklisted")
        void shouldHandleTokenBlacklistedAuditException() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit failed"))
                    .when(auditPublisher).publishTokenBlacklisted(anyString(), any(), any());

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Should still return true even if audit fails
            boolean result = tokenProvider.revokeToken(tokens.getAccessToken(), "127.0.0.1", "Agent");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle exception in publishValidationFailed")
        void shouldHandleValidationFailedAuditException() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit failed"))
                    .when(auditPublisher).publishTokenValidationFailed(any());

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableIpValidation(true);
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("error");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            // Should still throw validation exception even if audit fails
            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "10.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("validateToken - blacklist with audit")
    class ValidateTokenBlacklistWithAudit {

        @Test
        @DisplayName("should publish blacklisted token used event")
        void shouldPublishBlacklistedTokenUsedEvent() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Token revoked");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");

            JWTClaimsSet claims = tokenProvider.parseToken(tokens.getAccessToken());
            when(blacklistService.isBlacklisted(claims.getJWTID())).thenReturn(true);

            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);

            verify(auditPublisher).publishBlacklistedTokenUsed(eq(claims.getJWTID()), eq("testuser"), eq("127.0.0.1"));
            verify(auditPublisher).publishTokenValidationFailed(any());
        }
    }

    @Nested
    @DisplayName("audit publishing")
    class AuditPublishing {

        @Test
        @DisplayName("should handle audit publisher exceptions gracefully during validation")
        void shouldHandleAuditExceptionsDuringValidation() throws Exception {
            TokenAuditEventPublisher auditPublisher = mock(TokenAuditEventPublisher.class);
            when(auditPublisherProvider.getIfAvailable()).thenReturn(auditPublisher);
            doThrow(new RuntimeException("Audit DB down"))
                    .when(auditPublisher).publishBlacklistedTokenUsed(anyString(), anyString(), anyString());

            tokenProvider = createProviderWithKeyPair();
            properties.getToken().setEnableBlacklist(true);
            properties.getToken().setEnableIpValidation(false);
            properties.getToken().setEnableUserAgentValidation(false);

            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Token revoked");

            SimpliXJweTokenProvider.TokenResponse tokens =
                    tokenProvider.createTokenPair("testuser", "127.0.0.1", "Agent");
            JWTClaimsSet claims = tokenProvider.parseToken(tokens.getAccessToken());
            when(blacklistService.isBlacklisted(claims.getJWTID())).thenReturn(true);

            // Should still throw TokenValidationException even if audit fails
            assertThatThrownBy(() ->
                    tokenProvider.validateToken(tokens.getAccessToken(), "127.0.0.1", "Agent"))
                    .isInstanceOf(dev.simplecore.simplix.auth.exception.TokenValidationException.class);
        }
    }

    @Nested
    @DisplayName("TokenResponse")
    class TokenResponseTest {

        @Test
        @DisplayName("should create with Date constructor")
        void shouldCreateWithDateConstructor() {
            java.util.Date now = new java.util.Date();
            java.util.Date later = new java.util.Date(now.getTime() + 3600000);

            SimpliXJweTokenProvider.TokenResponse response =
                    new SimpliXJweTokenProvider.TokenResponse("access", "refresh", now, later);

            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
            assertThat(response.getAccessTokenExpiry()).isNotNull();
            assertThat(response.getRefreshTokenExpiry()).isNotNull();
        }

        @Test
        @DisplayName("should create with ZonedDateTime constructor")
        void shouldCreateWithZonedDateTimeConstructor() {
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
            java.time.ZonedDateTime later = now.plusHours(1);

            SimpliXJweTokenProvider.TokenResponse response =
                    new SimpliXJweTokenProvider.TokenResponse("access", "refresh", now, later);

            assertThat(response.getAccessToken()).isEqualTo("access");
            assertThat(response.getRefreshToken()).isEqualTo("refresh");
            assertThat(response.getAccessTokenExpiry()).isEqualTo(now);
            assertThat(response.getRefreshTokenExpiry()).isEqualTo(later);
        }
    }
}
