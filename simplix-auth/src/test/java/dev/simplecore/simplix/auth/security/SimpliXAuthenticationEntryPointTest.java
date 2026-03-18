package dev.simplecore.simplix.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("SimpliXAuthenticationEntryPoint")
@ExtendWith(MockitoExtension.class)
class SimpliXAuthenticationEntryPointTest {

    @Mock
    private MessageSource messageSource;

    private ObjectMapper objectMapper;
    private SimpliXAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Register JavaTimeModule for OffsetDateTime serialization
        objectMapper.findAndRegisterModules();
        entryPoint = new SimpliXAuthenticationEntryPoint(objectMapper, messageSource);
    }

    @Test
    @DisplayName("should return 401 status code")
    void shouldReturn401() throws Exception {
        when(messageSource.getMessage(eq("error.authenticationFailed"), any(), anyString(), any(Locale.class)))
                .thenReturn("Authentication required");
        when(messageSource.getMessage(eq("error.authenticationFailed.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Login is required");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("should return JSON content type")
    void shouldReturnJsonContentType() throws Exception {
        when(messageSource.getMessage(eq("error.authenticationFailed"), any(), anyString(), any(Locale.class)))
                .thenReturn("Authentication required");
        when(messageSource.getMessage(eq("error.authenticationFailed.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Login is required");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    }

    @Test
    @DisplayName("should include error response in body")
    void shouldIncludeErrorResponseInBody() throws Exception {
        when(messageSource.getMessage(eq("error.authenticationFailed"), any(), anyString(), any(Locale.class)))
                .thenReturn("Authentication required");
        when(messageSource.getMessage(eq("error.authenticationFailed.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Login is required");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        String body = response.getContentAsString();
        assertThat(body).contains("Authentication required");
        assertThat(body).contains("AUTH_AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("should add trace ID header when MDC has traceId")
    void shouldAddTraceIdHeader() throws Exception {
        when(messageSource.getMessage(eq("error.authenticationFailed"), any(), anyString(), any(Locale.class)))
                .thenReturn("Auth required");
        when(messageSource.getMessage(eq("error.authenticationFailed.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Login required");

        org.slf4j.MDC.put("traceId", "test-trace-456");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

            assertThat(response.getHeader("X-Trace-Id")).isEqualTo("test-trace-456");
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @Test
    @DisplayName("should not add trace ID header when MDC is empty")
    void shouldNotAddTraceIdWhenEmpty() throws Exception {
        when(messageSource.getMessage(eq("error.authenticationFailed"), any(), anyString(), any(Locale.class)))
                .thenReturn("Auth required");
        when(messageSource.getMessage(eq("error.authenticationFailed.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Login required");

        org.slf4j.MDC.clear();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }
}
