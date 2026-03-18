package dev.simplecore.simplix.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2Intent")
class OAuth2IntentTest {

    @Test
    @DisplayName("should have all expected intent values")
    void shouldHaveAllExpectedValues() {
        assertThat(OAuth2Intent.values()).containsExactlyInAnyOrder(
                OAuth2Intent.LOGIN,
                OAuth2Intent.REGISTER,
                OAuth2Intent.AUTO
        );
    }

    @Test
    @DisplayName("should resolve from name string")
    void shouldResolveFromName() {
        assertThat(OAuth2Intent.valueOf("LOGIN")).isEqualTo(OAuth2Intent.LOGIN);
        assertThat(OAuth2Intent.valueOf("REGISTER")).isEqualTo(OAuth2Intent.REGISTER);
        assertThat(OAuth2Intent.valueOf("AUTO")).isEqualTo(OAuth2Intent.AUTO);
    }
}
