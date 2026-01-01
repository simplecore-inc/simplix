package dev.simplecore.simplix.web.advice;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

public class SimpliXResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    
    private static final Logger log = LoggerFactory.getLogger(SimpliXResponseBodyAdvice.class);

    @Override
    public boolean supports(@NonNull MethodParameter returnType, 
                          @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> containingClass = returnType.getContainingClass();
        
        // Exclude API documentation related packages
        if (containingClass.getName().startsWith("org.springdoc") || 
            containingClass.getName().startsWith("io.swagger") ||
            containingClass.getName().startsWith("springfox")) {
            log.trace("Skipping documentation class: {}", containingClass.getName());
            return false;
        }
        
        // Exclude if already wrapped with SimpliXApiResponse
        if (SimpliXApiResponse.class.isAssignableFrom(returnType.getParameterType())) {
            log.trace("Skipping already wrapped response: {}", returnType.getParameterType().getName());
            return false;
        }
        
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body, 
                                @Nullable MethodParameter returnType, 
                                @Nullable MediaType selectedContentType,
                                @Nullable Class<? extends HttpMessageConverter<?>> selectedConverterType, 
                                @Nullable ServerHttpRequest request,
                                @Nullable ServerHttpResponse response) {
        log.trace("Processing response body: {}", body != null ? body.getClass().getName() : "null");

        // Handle null case
        if (body == null) {
            return SimpliXApiResponse.success(null);
        }

        // Handle ResponseEntity case
        if (body instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) body;
            if (response != null) {
                response.setStatusCode(responseEntity.getStatusCode());
                response.getHeaders().putAll(responseEntity.getHeaders());
            }
            return SimpliXApiResponse.success(responseEntity.getBody());
        }

        // Already an SimpliXApiResponse case
        if (body instanceof SimpliXApiResponse) {
            return body;
        }

        // Wrap regular object with SimpliXApiResponse
        return SimpliXApiResponse.success(body);
    }
}