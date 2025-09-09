package dev.simplecore.simplix.web.config;

import jakarta.servlet.*;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@AutoConfiguration
public class SimpliXWebMvcConfiguration implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<Filter> mdcCleanupFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new MDCCleanupFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Integer.MAX_VALUE); // Execute last
        return registrationBean;
    }

    /**
     * Filter to clean up MDC after request processing
     */
    private static class MDCCleanupFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try {
                chain.doFilter(request, response);
            } finally {
                // Clean up MDC after request processing
                MDC.clear();
            }
        }
    }
} 