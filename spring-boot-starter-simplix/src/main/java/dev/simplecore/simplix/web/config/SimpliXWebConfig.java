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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Web configuration for SimpliX framework
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class SimpliXWebConfig {

    /**
     * Trace ID filter that generates a unique trace ID for each request
     * and sets it in MDC for logging purposes
     */
    @Bean
    public OncePerRequestFilter traceIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, 
                                         HttpServletResponse response, 
                                         FilterChain filterChain) throws ServletException, IOException {
                try {
                    // Generate trace ID for the request
                    String traceId = generateTraceId();
                    
                    // Set trace ID in MDC for logging
                    MDC.put("traceId", traceId);
                    
                    // Add trace ID to response header
                    response.setHeader("X-Trace-Id", traceId);
                    
                    // Continue with the filter chain
                    filterChain.doFilter(request, response);
                } finally {
                    // Clean up MDC after request processing
                    MDC.remove("traceId");
                }
            }
            
            /**
             * Generate unique trace ID for request tracking
             * Format: YYYYMMDD-HHMMSS-UUID(8chars)
             * Uses UTC time for consistent logging across timezones
             */
            private String generateTraceId() {
                String timestamp = Instant.now().atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String uuid = UuidUtils.generateShortUuid();
                return String.format("%s-%s", timestamp, uuid);
            }
        };
    }
} 