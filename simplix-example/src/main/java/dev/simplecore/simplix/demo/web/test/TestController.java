package dev.simplecore.simplix.demo.web.test;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountDTOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Test API for validation messages")
public class TestController {

    @Autowired
    private MessageSource messageSource;
    
    @Autowired
    private Validator validator;

    @GetMapping("/message")
    @Operation(summary = "Test Message", description = "Test message source")
    public ResponseEntity<SimpliXApiResponse<Map<String, String>>> testMessage(
            @RequestParam(defaultValue = "validation.roles.required") String key,
            @RequestParam(defaultValue = "ko") String lang) {
        
        Locale locale = "ko".equals(lang) ? Locale.KOREAN : Locale.ENGLISH;
        String message = messageSource.getMessage(key, null, key, locale);
        
        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("locale", locale.toString());
        result.put("message", message);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @PostMapping("/validate")
    @Operation(summary = "Test Validation", description = "Test validation messages")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> testValidation(
            @RequestBody @Validated UserAccountDTOs.UserAccountCreateDTO dto,
            @RequestParam(defaultValue = "ko") String lang) {
        
        Locale locale = "ko".equals(lang) ? Locale.KOREAN : Locale.ENGLISH;
        LocaleContextHolder.setLocale(locale);
        
        Set<ConstraintViolation<UserAccountDTOs.UserAccountCreateDTO>> violations = validator.validate(dto);
        
        Map<String, Object> result = new HashMap<>();
        result.put("locale", locale.toString());
        result.put("violationCount", violations.size());
        
        Map<String, String> violationMessages = new HashMap<>();
        for (ConstraintViolation<UserAccountDTOs.UserAccountCreateDTO> violation : violations) {
            violationMessages.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        result.put("violations", violationMessages);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/ping")
    @Operation(summary = "Ping Test", description = "Simple ping endpoint for connectivity test")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> ping() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "pong");
        result.put("timestamp", java.time.LocalDateTime.now());
        result.put("message", "Test API is working");
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Health check endpoint")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("checks", Map.of(
                "database", "UP",
                "messageSource", "UP",
                "validator", "UP"
        ));
        result.put("timestamp", java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @PostMapping("/data")
    @Operation(summary = "Test Data Processing", description = "Test endpoint for data processing")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> processData(@RequestBody Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("received", data);
        result.put("processed", true);
        result.put("dataSize", data.size());
        result.put("timestamp", java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }
} 