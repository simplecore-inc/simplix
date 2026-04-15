package dev.simplecore.simplix.auth.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenFailureReason")
class TokenFailureReasonTest {

    @Test
    @DisplayName("should contain all expected failure reasons")
    void shouldContainAllExpectedReasons() {
        TokenFailureReason[] values = TokenFailureReason.values();

        assertThat(values).containsExactlyInAnyOrder(
                TokenFailureReason.NONE,
                TokenFailureReason.TOKEN_EXPIRED,
                TokenFailureReason.TOKEN_REVOKED,
                TokenFailureReason.INVALID_SIGNATURE,
                TokenFailureReason.MALFORMED_TOKEN,
                TokenFailureReason.IP_MISMATCH,
                TokenFailureReason.USER_AGENT_MISMATCH,
                TokenFailureReason.MISSING_TOKEN,
                TokenFailureReason.MISSING_REFRESH_TOKEN,
                TokenFailureReason.INVALID_CREDENTIALS,
                TokenFailureReason.USER_NOT_FOUND,
                TokenFailureReason.ACCOUNT_LOCKED,
                TokenFailureReason.ACCOUNT_DISABLED,
                TokenFailureReason.TOKEN_TYPE_MISMATCH,
                TokenFailureReason.BLACKLIST_SERVICE_ERROR,
                TokenFailureReason.UNKNOWN
        );
    }

    @Test
    @DisplayName("should resolve from name string")
    void shouldResolveFromName() {
        assertThat(TokenFailureReason.valueOf("TOKEN_EXPIRED"))
                .isEqualTo(TokenFailureReason.TOKEN_EXPIRED);
        assertThat(TokenFailureReason.valueOf("NONE"))
                .isEqualTo(TokenFailureReason.NONE);
    }
}
