package dev.simplecore.simplix.auth.oauth2.session;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PendingSocialRegistration")
class PendingSocialRegistrationTest {

    @Nested
    @DisplayName("from factory method")
    class FromFactory {

        @Test
        @DisplayName("should create from OAuth2UserInfo with TTL")
        void shouldCreateFromUserInfo() {
            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.GOOGLE)
                    .providerId("google-123")
                    .email("user@gmail.com")
                    .emailVerified(true)
                    .name("John Doe")
                    .firstName("John")
                    .lastName("Doe")
                    .profileImageUrl("https://example.com/photo.jpg")
                    .locale("en")
                    .build();

            PendingSocialRegistration registration = PendingSocialRegistration.from(userInfo, 300);

            assertThat(registration.getProvider()).isEqualTo(OAuth2ProviderType.GOOGLE);
            assertThat(registration.getProviderId()).isEqualTo("google-123");
            assertThat(registration.getEmail()).isEqualTo("user@gmail.com");
            assertThat(registration.isEmailVerified()).isTrue();
            assertThat(registration.getName()).isEqualTo("John Doe");
            assertThat(registration.getFirstName()).isEqualTo("John");
            assertThat(registration.getLastName()).isEqualTo("Doe");
            assertThat(registration.getProfileImageUrl()).isEqualTo("https://example.com/photo.jpg");
            assertThat(registration.getLocale()).isEqualTo("en");
            assertThat(registration.getCreatedAt()).isNotNull();
            assertThat(registration.getTtlSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("should use default TTL when provided TTL is zero or negative")
        void shouldUseDefaultTtlWhenZeroOrNegative() {
            OAuth2UserInfo userInfo = OAuth2UserInfo.builder()
                    .provider(OAuth2ProviderType.KAKAO)
                    .providerId("kakao-456")
                    .build();

            PendingSocialRegistration registration = PendingSocialRegistration.from(userInfo, 0);

            assertThat(registration.getTtlSeconds()).isEqualTo(PendingSocialRegistration.DEFAULT_TTL_SECONDS);
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("should return false for non-expired registration")
        void shouldReturnFalseForNonExpired() {
            PendingSocialRegistration registration = PendingSocialRegistration.builder()
                    .createdAt(Instant.now())
                    .ttlSeconds(600)
                    .build();

            assertThat(registration.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should return true for expired registration")
        void shouldReturnTrueForExpired() {
            PendingSocialRegistration registration = PendingSocialRegistration.builder()
                    .createdAt(Instant.now().minusSeconds(700))
                    .ttlSeconds(600)
                    .build();

            assertThat(registration.isExpired()).isTrue();
        }

        @Test
        @DisplayName("should return true when createdAt is null")
        void shouldReturnTrueWhenCreatedAtNull() {
            PendingSocialRegistration registration = PendingSocialRegistration.builder()
                    .build();

            assertThat(registration.isExpired()).isTrue();
        }

        @Test
        @DisplayName("should use default TTL when ttlSeconds is zero")
        void shouldUseDefaultTtlWhenZero() {
            PendingSocialRegistration registration = PendingSocialRegistration.builder()
                    .createdAt(Instant.now())
                    .ttlSeconds(0)
                    .build();

            assertThat(registration.isExpired()).isFalse();
        }
    }

    @Test
    @DisplayName("should return provider name as string")
    void shouldReturnProviderName() {
        PendingSocialRegistration registration = PendingSocialRegistration.builder()
                .provider(OAuth2ProviderType.NAVER)
                .build();

        assertThat(registration.getProviderName()).isEqualTo("NAVER");
    }

    @Test
    @DisplayName("should return null provider name when provider is null")
    void shouldReturnNullProviderName() {
        PendingSocialRegistration registration = PendingSocialRegistration.builder()
                .build();

        assertThat(registration.getProviderName()).isNull();
    }

    @Test
    @DisplayName("SESSION_ATTR constant should be oauth2.pending")
    void sessionAttrConstant() {
        assertThat(PendingSocialRegistration.SESSION_ATTR).isEqualTo("oauth2.pending");
    }

    @Test
    @DisplayName("DEFAULT_TTL_SECONDS constant should be 600")
    void defaultTtlConstant() {
        assertThat(PendingSocialRegistration.DEFAULT_TTL_SECONDS).isEqualTo(600L);
    }
}
