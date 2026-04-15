package dev.simplecore.simplix.web.config;

import dev.simplecore.simplix.core.util.UuidUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Web configuration for SimpliX framework
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class SimpliXWebConfig {

    /**
     * Trace ID filter that generates a unique trace ID for each request
     * and sets it in MDC for logging purposes.
     *
     * <p>The trace ID is a 13-character hex string derived from UUID v7,
     * encoding a millisecond-precision timestamp for natural ordering.
     */
    @Bean
    public OncePerRequestFilter traceIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
                try {
                    String traceId = UuidUtils.generateTraceId();
                    MDC.put("traceId", traceId);
                    response.setHeader("X-Trace-Id", traceId);
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("traceId");
                }
            }
        };
    }
} 