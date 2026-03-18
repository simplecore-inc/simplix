package dev.simplecore.simplix.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXWebConfig - trace ID filter for request tracking")
class SimpliXWebConfigTest {

    private SimpliXWebConfig config;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        config = new SimpliXWebConfig();
    }

    @Test
    @DisplayName("Should create traceIdFilter bean")
    void createTraceIdFilter() {
        OncePerRequestFilter filter = config.traceIdFilter();

        assertThat(filter).isNotNull();
    }

    @Test
    @DisplayName("Should set trace ID in MDC during request processing")
    void setTraceIdInMdc() throws Exception {
        OncePerRequestFilter filter = config.traceIdFilter();

        filter.doFilter(request, response, filterChain);

        // After the filter completes, MDC should be cleaned up
        assertThat(MDC.get("traceId")).isNull();

        // Verify that the response header was set
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Trace-Id"), headerCaptor.capture());

        String traceId = headerCaptor.getValue();
        assertThat(traceId).isNotNull();
        assertThat(traceId).isNotEmpty();
        // Format: YYYYMMDD-HHMMSS-UUID(8chars)
        assertThat(traceId).matches("\\d{8}-\\d{6}-.+");
    }

    @Test
    @DisplayName("Should clean up MDC after request processing even on exception")
    void cleanupMdcOnException() throws Exception {
        OncePerRequestFilter filter = config.traceIdFilter();

        org.mockito.Mockito.doThrow(new RuntimeException("test error"))
                .when(filterChain).doFilter(request, response);

        try {
            filter.doFilter(request, response, filterChain);
        } catch (Exception ignored) {
        }

        // MDC should be cleaned up
        assertThat(MDC.get("traceId")).isNull();
    }
}
