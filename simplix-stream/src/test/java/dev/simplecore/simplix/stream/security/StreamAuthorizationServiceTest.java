package dev.simplecore.simplix.stream.security;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.exception.AuthorizationDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamAuthorizationService.
 */
@DisplayName("StreamAuthorizationService")
class StreamAuthorizationServiceTest {

    private StreamAuthorizationService service;

    private ResourceAuthorizer createAuthorizer(String resource, boolean authorize) {
        return new ResourceAuthorizer() {
            @Override
            public String getResource() {
                return resource;
            }

            @Override
            public boolean authorize(String userId, Map<String, Object> params) {
                return authorize;
            }
        };
    }

    private ResourceAuthorizer createAuthorizerWithPermission(String resource, String permission) {
        return new ResourceAuthorizer() {
            @Override
            public String getResource() {
                return resource;
            }

            @Override
            public String getRequiredPermission() {
                return permission;
            }

            @Override
            public boolean authorize(String userId, Map<String, Object> params) {
                return true;
            }
        };
    }

    @Nested
    @DisplayName("with enforcement disabled")
    class WithEnforcementDisabled {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should allow access when no authorizer found")
        void shouldAllowAccessWhenNoAuthorizerFound() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("should allow access when authorizer grants permission")
        void shouldAllowAccessWhenAuthorizerGrantsPermission() {
            service.registerAuthorizer(createAuthorizer("stock", true));
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("should deny access when authorizer denies permission")
        void shouldDenyAccessWhenAuthorizerDeniesPermission() {
            service.registerAuthorizer(createAuthorizer("stock", false));
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isDenied());
            assertNotNull(result.getReason());
        }
    }

    @Nested
    @DisplayName("with enforcement enabled")
    class WithEnforcementEnabled {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(true);
        }

        @Test
        @DisplayName("should deny access when no authorizer found")
        void shouldDenyAccessWhenNoAuthorizerFound() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isDenied());
            assertTrue(result.getReason().contains("No authorizer configured"));
        }

        @Test
        @DisplayName("should allow access when authorizer grants permission")
        void shouldAllowAccessWhenAuthorizerGrantsPermission() {
            service.registerAuthorizer(createAuthorizer("stock", true));
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("registerAuthorizer()")
    class RegisterAuthorizerMethod {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should register authorizer")
        void shouldRegisterAuthorizer() {
            ResourceAuthorizer authorizer = createAuthorizer("stock", true);

            service.registerAuthorizer(authorizer);

            assertTrue(service.hasAuthorizer("stock"));
        }

        @Test
        @DisplayName("should replace existing authorizer")
        void shouldReplaceExistingAuthorizer() {
            service.registerAuthorizer(createAuthorizer("stock", false));
            service.registerAuthorizer(createAuthorizer("stock", true));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("setAuthorizers()")
    class SetAuthorizersMethod {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should register all authorizers from list")
        void shouldRegisterAllAuthorizersFromList() {
            List<ResourceAuthorizer> authorizers = List.of(
                    createAuthorizer("stock", true),
                    createAuthorizer("forex", true)
            );

            service.setAuthorizers(authorizers);

            assertTrue(service.hasAuthorizer("stock"));
            assertTrue(service.hasAuthorizer("forex"));
        }

        @Test
        @DisplayName("should handle null list")
        void shouldHandleNullList() {
            assertDoesNotThrow(() -> service.setAuthorizers(null));
        }
    }

    @Nested
    @DisplayName("authorize()")
    class AuthorizeMethod {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(true);
        }

        @Test
        @DisplayName("should not throw when authorized")
        void shouldNotThrowWhenAuthorized() {
            service.registerAuthorizer(createAuthorizer("stock", true));
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            assertDoesNotThrow(() -> service.authorize("user123", key));
        }

        @Test
        @DisplayName("should throw AuthorizationDeniedException when denied")
        void shouldThrowAuthorizationDeniedExceptionWhenDenied() {
            service.registerAuthorizer(createAuthorizer("stock", false));
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            assertThrows(AuthorizationDeniedException.class,
                    () -> service.authorize("user123", key));
        }
    }

    @Nested
    @DisplayName("hasAuthorizer()")
    class HasAuthorizerMethod {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should return true when authorizer exists")
        void shouldReturnTrueWhenAuthorizerExists() {
            service.registerAuthorizer(createAuthorizer("stock", true));

            assertTrue(service.hasAuthorizer("stock"));
        }

        @Test
        @DisplayName("should return false when authorizer does not exist")
        void shouldReturnFalseWhenAuthorizerDoesNotExist() {
            assertFalse(service.hasAuthorizer("stock"));
        }
    }

    @Nested
    @DisplayName("getRequiredPermission()")
    class GetRequiredPermissionMethod {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should return permission when authorizer exists")
        void shouldReturnPermissionWhenAuthorizerExists() {
            service.registerAuthorizer(createAuthorizerWithPermission("stock", "STOCK_READ"));

            assertEquals("STOCK_READ", service.getRequiredPermission("stock"));
        }

        @Test
        @DisplayName("should return null when authorizer does not exist")
        void shouldReturnNullWhenAuthorizerDoesNotExist() {
            assertNull(service.getRequiredPermission("stock"));
        }

        @Test
        @DisplayName("should return null when authorizer has no permission")
        void shouldReturnNullWhenAuthorizerHasNoPermission() {
            service.registerAuthorizer(createAuthorizer("stock", true));

            assertNull(service.getRequiredPermission("stock"));
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @BeforeEach
        void setUp() {
            service = new StreamAuthorizationService(false);
        }

        @Test
        @DisplayName("should handle authorizer exception gracefully")
        void shouldHandleAuthorizerExceptionGracefully() {
            ResourceAuthorizer failingAuthorizer = new ResourceAuthorizer() {
                @Override
                public String getResource() {
                    return "stock";
                }

                @Override
                public boolean authorize(String userId, Map<String, Object> params) {
                    throw new RuntimeException("Authorization failed");
                }
            };

            service.registerAuthorizer(failingAuthorizer);
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamAuthorizationService.AuthorizationResult result = service.checkAuthorization("user123", key);

            assertTrue(result.isDenied());
            assertTrue(result.getReason().contains("Authorization check failed"));
        }
    }

    @Nested
    @DisplayName("AuthorizationResult")
    class AuthorizationResultTest {

        @Test
        @DisplayName("allow() should create allowed result")
        void allowShouldCreateAllowedResult() {
            StreamAuthorizationService.AuthorizationResult result = StreamAuthorizationService.AuthorizationResult.allow();

            assertTrue(result.isAllowed());
            assertFalse(result.isDenied());
            assertNull(result.getReason());
        }

        @Test
        @DisplayName("deny() should create denied result with reason")
        void denyShouldCreateDeniedResultWithReason() {
            StreamAuthorizationService.AuthorizationResult result = StreamAuthorizationService.AuthorizationResult.deny("Test reason");

            assertFalse(result.isAllowed());
            assertTrue(result.isDenied());
            assertEquals("Test reason", result.getReason());
        }
    }
}
