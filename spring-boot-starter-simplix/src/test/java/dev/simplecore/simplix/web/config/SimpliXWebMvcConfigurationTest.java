package dev.simplecore.simplix.web.config;

import jakarta.servlet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXWebMvcConfiguration - MDC cleanup filter registration")
class SimpliXWebMvcConfigurationTest {

    private SimpliXWebMvcConfiguration configuration;

    @Mock
    private ServletRequest request;

    @Mock
    private ServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        configuration = new SimpliXWebMvcConfiguration();
    }

    @Test
    @DisplayName("Should register MDC cleanup filter with /* URL pattern")
    void registerMdcCleanupFilter() {
        FilterRegistrationBean<Filter> registration = configuration.mdcCleanupFilter();

        assertThat(registration).isNotNull();
        assertThat(registration.getFilter()).isNotNull();
        assertThat(registration.getUrlPatterns()).contains("/*");
        assertThat(registration.getOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("MDC cleanup filter should clear MDC after request processing")
    void mdcCleanupFilterClearsMdc() throws IOException, ServletException {
        FilterRegistrationBean<Filter> registration = configuration.mdcCleanupFilter();
        Filter filter = registration.getFilter();

        MDC.put("traceId", "test-trace-id");
        MDC.put("userId", "test-user");

        filter.doFilter(request, response, filterChain);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("MDC cleanup filter should clear MDC even when filter chain throws exception")
    void mdcCleanupOnException() throws IOException, ServletException {
        FilterRegistrationBean<Filter> registration = configuration.mdcCleanupFilter();
        Filter filter = registration.getFilter();

        MDC.put("traceId", "test-trace-id");
        doThrow(new ServletException("test error")).when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("traceId")).isNull();
    }
}
