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
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("SimpliXAccessDeniedHandler")
@ExtendWith(MockitoExtension.class)
class SimpliXAccessDeniedHandlerTest {

    @Mock
    private MessageSource messageSource;

    private ObjectMapper objectMapper;
    private SimpliXAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        handler = new SimpliXAccessDeniedHandler(objectMapper, messageSource);
    }

    @Test
    @DisplayName("should return 403 status code")
    void shouldReturn403() throws Exception {
        when(messageSource.getMessage(eq("error.insufficientPermissions"), any(), anyString(), any(Locale.class)))
                .thenReturn("Access denied");
        when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Insufficient permissions");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should return JSON content type")
    void shouldReturnJsonContentType() throws Exception {
        when(messageSource.getMessage(eq("error.insufficientPermissions"), any(), anyString(), any(Locale.class)))
                .thenReturn("Access denied");
        when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Insufficient permissions");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    }

    @Test
    @DisplayName("should include error response in body")
    void shouldIncludeErrorResponseInBody() throws Exception {
        when(messageSource.getMessage(eq("error.insufficientPermissions"), any(), anyString(), any(Locale.class)))
                .thenReturn("Access denied");
        when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Insufficient permissions");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        String body = response.getContentAsString();
        assertThat(body).contains("Access denied");
        assertThat(body).contains("AUTHZ_INSUFFICIENT_PERMISSIONS");
    }

    @Test
    @DisplayName("should add trace ID header when MDC has traceId")
    void shouldAddTraceIdHeader() throws Exception {
        when(messageSource.getMessage(eq("error.insufficientPermissions"), any(), anyString(), any(Locale.class)))
                .thenReturn("Access denied");
        when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Insufficient permissions");

        org.slf4j.MDC.put("traceId", "test-trace-123");
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.handle(request, response, new AccessDeniedException("Access denied"));

            assertThat(response.getHeader("X-Trace-Id")).isEqualTo("test-trace-123");
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    @Test
    @DisplayName("should not add trace ID header when MDC is empty")
    void shouldNotAddTraceIdWhenEmpty() throws Exception {
        when(messageSource.getMessage(eq("error.insufficientPermissions"), any(), anyString(), any(Locale.class)))
                .thenReturn("Access denied");
        when(messageSource.getMessage(eq("error.insufficientPermissions.detail"), any(), anyString(), any(Locale.class)))
                .thenReturn("Insufficient permissions");

        org.slf4j.MDC.clear();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }
}
