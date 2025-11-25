package dev.simplecore.simplix.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom access denied handler that returns JSON error responses
 * for authorization failures (403 Forbidden)
 */
@Component
@RequiredArgsConstructor
public class SimpliXAccessDeniedHandler implements AccessDeniedHandler {
    
    private static final Logger log = LoggerFactory.getLogger(SimpliXAccessDeniedHandler.class);
    
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    
    @Override
    public void handle(HttpServletRequest request, 
                      HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException, ServletException {
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        String message = messageSource.getMessage(
            "error.insufficientPermissions", 
            null, 
            "Access denied", 
            LocaleContextHolder.getLocale()
        );
        
        String detail = messageSource.getMessage(
            "error.insufficientPermissions.detail", 
            null, 
            "You do not have permission to access the requested resource", 
            LocaleContextHolder.getLocale()
        );
        
        SimpliXApiResponse<Object> errorResponse = SimpliXApiResponse.error(
            message,
            ErrorCode.AUTHZ_INSUFFICIENT_PERMISSIONS.getCode(),
            detail
        );
        
        // Add trace ID to response header and MDC for logging
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isEmpty()) {
            response.setHeader("X-Trace-Id", traceId);
            log.warn("Access denied - TraceId: {}, Path: {}, Message: {}", 
                traceId, request.getRequestURI(), accessDeniedException.getMessage());
        }
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
} 