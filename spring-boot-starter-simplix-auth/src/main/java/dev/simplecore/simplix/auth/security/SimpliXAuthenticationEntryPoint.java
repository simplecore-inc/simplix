package dev.simplecore.simplix.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Custom authentication entry point that returns JSON error responses
 * for authentication failures (401 Unauthorized)
 */
@Component
@RequiredArgsConstructor
public class SimpliXAuthenticationEntryPoint implements AuthenticationEntryPoint {
    
    private static final Logger log = LoggerFactory.getLogger(SimpliXAuthenticationEntryPoint.class);
    
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    
    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        // Determine the specific error message
        String message = messageSource.getMessage(
            "error.authenticationFailed", 
            null, 
            "Authentication required", 
            LocaleContextHolder.getLocale()
        );
        
        String detail = messageSource.getMessage(
            "error.authenticationFailed.detail", 
            null, 
            "Login is required or token is invalid", 
            LocaleContextHolder.getLocale()
        );
        
        SimpliXApiResponse<Object> errorResponse = SimpliXApiResponse.error(
            message,
            ErrorCode.AUTH_AUTHENTICATION_REQUIRED.getCode(),
            detail
        );
        
        // Add trace ID to response header and MDC for logging
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            response.setHeader("X-Trace-Id", traceId);
            log.warn("Authentication failed - TraceId: {}, Path: {}, Message: {}", 
                traceId, request.getRequestURI(), authException.getMessage());
        }
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
} 