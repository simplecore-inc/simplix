package dev.simplecore.simplix.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.RetryAfterHint;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The base handler resolves a {@code {key}} message and writes a refusal's {@code Retry-After}. */
class SimpliXExceptionHandlerRetryAfterTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final StaticMessageSource messages = new StaticMessageSource();
    private final SimpliXExceptionHandler<Object> handler = new SimpliXExceptionHandler<>(messages, new ObjectMapper());

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        LocaleContextHolder.setLocale(Locale.KOREAN);
        messages.addMessage("error.credit.insufficient", Locale.KOREAN, "크레딧이 부족합니다");
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void a_refusal_with_a_hint_sets_retry_after() {
        handler.handleSimpliXGeneralException(new Refusal(3), request);

        assertThat(response.getHeader("Retry-After")).isEqualTo("3");
    }

    @Test
    void an_exception_without_a_hint_sets_no_retry_after() {
        handler.handleSimpliXGeneralException(
                new SimpliXGeneralException(ErrorCode.BIZ_INVALID_STATE, "{error.unknown}", null), request);

        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void a_hint_of_zero_sets_no_retry_after() {
        handler.handleSimpliXGeneralException(new Refusal(0), request);

        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void insufficient_payment_answers_402_with_the_key_resolved_in_the_request_locale() {
        Object body = handler.handleSimpliXGeneralException(
                new SimpliXGeneralException(ErrorCode.BIZ_PAYMENT_REQUIRED, "{error.credit.insufficient}", null), request);

        assertThat(response.getStatus()).isEqualTo(402);
        assertThat(((SimpliXApiResponse<?>) body).getMessage()).isEqualTo("크레딧이 부족합니다");
    }

    @Test
    void a_plain_message_or_an_undeclared_key_passes_through() {
        Object plain = handler.handleSimpliXGeneralException(
                new SimpliXGeneralException(ErrorCode.BIZ_INVALID_STATE, "Plain", null), request);
        Object unknown = handler.handleSimpliXGeneralException(
                new SimpliXGeneralException(ErrorCode.BIZ_INVALID_STATE, "{error.unknown}", null), request);

        assertThat(((SimpliXApiResponse<?>) plain).getMessage()).isEqualTo("Plain");
        assertThat(((SimpliXApiResponse<?>) unknown).getMessage()).isEqualTo("{error.unknown}");
    }

    private static final class Refusal extends SimpliXGeneralException implements RetryAfterHint {

        private final int seconds;

        Refusal(int seconds) {
            super(ErrorCode.BIZ_QUOTA_EXCEEDED, "{error.credit.userLimit}", null);
            this.seconds = seconds;
        }

        @Override
        public int retryAfterSeconds() {
            return seconds;
        }
    }
}
