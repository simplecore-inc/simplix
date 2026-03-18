package dev.simplecore.simplix.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2AuthenticationService default methods")
class OAuth2AuthenticationServiceTest {

    /**
     * Minimal implementation to test default methods.
     */
    private static class TestOAuth2AuthenticationService implements OAuth2AuthenticationService {

        @Override
        public UserDetails authenticateOAuth2User(OAuth2UserInfo userInfo) {
            return User.withUsername("testuser")
                    .password("pass")
                    .authorities(Collections.emptyList())
                    .build();
        }

        @Override
        public void linkSocialAccount(String userId, OAuth2UserInfo userInfo) {
            // no-op
        }

        @Override
        public void unlinkSocialAccount(String userId, OAuth2ProviderType provider) {
            // no-op
        }

        @Override
        public Set<OAuth2ProviderType> getLinkedProviders(String userId) {
            return Collections.emptySet();
        }
    }

    private final TestOAuth2AuthenticationService service = new TestOAuth2AuthenticationService();

    @Test
    @DisplayName("authenticateOAuth2User with intent should delegate to default method")
    void shouldDelegateToDefaultMethod() {
        OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GOOGLE)
                .providerId("123")
                .build();

        UserDetails result = service.authenticateOAuth2User(userInfo, OAuth2Intent.LOGIN);
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("findUserIdByProviderConnection should return null by default")
    void shouldReturnNullForFindByProvider() {
        String result = service.findUserIdByProviderConnection(OAuth2ProviderType.GOOGLE, "123");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findUserIdByEmail should return null by default")
    void shouldReturnNullForFindByEmail() {
        String result = service.findUserIdByEmail("test@example.com");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("loadUserDetailsByUserId should return null by default")
    void shouldReturnNullForLoadByUserId() {
        UserDetails result = service.loadUserDetailsByUserId("user-1");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("onAuthenticationSuccess should be no-op by default")
    void shouldBeNoOpForOnSuccess() {
        OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.GOOGLE)
                .providerId("123")
                .build();
        UserDetails user = User.withUsername("testuser")
                .password("pass")
                .authorities(Collections.emptyList())
                .build();

        // Should not throw
        service.onAuthenticationSuccess(user, userInfo, "127.0.0.1", "Agent");
    }
}
