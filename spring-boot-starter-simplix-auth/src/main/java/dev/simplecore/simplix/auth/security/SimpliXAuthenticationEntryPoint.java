package dev.simplecore.simplix.auth.security;

import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
        
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
} 