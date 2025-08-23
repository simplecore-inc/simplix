package dev.simplecore.simplix.demo.web.sample;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/private")
@Tag(name = "Private API", description = "Protected APIs that require authentication")
@SecurityRequirement(name = "bearerAuth")
public class PrivateApiController {

    @GetMapping("/profile")
    @Operation(summary = "User Profile", description = "Get current user profile information")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getUserProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> result = new HashMap<>();
        result.put("username", auth.getName());
        result.put("authorities", auth.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList()));
        result.put("authenticated", auth.isAuthenticated());
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/secure-data")
    @Operation(summary = "Secure Data", description = "Get secure data that requires authentication")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getSecureData() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "This is secure data only available to authenticated users");
        result.put("accessedBy", auth.getName());
        result.put("securityLevel", "PROTECTED");
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @PostMapping("/secure-action")
    @Operation(summary = "Secure Action", description = "Perform a secure action that requires authentication")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> performSecureAction(@RequestBody Map<String, Object> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> result = new HashMap<>();
        result.put("action", "secure-action-completed");
        result.put("performedBy", auth.getName());
        result.put("payload", payload);
        result.put("timestamp", LocalDateTime.now());
        result.put("status", "SUCCESS");
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/admin-only")
    @Operation(summary = "Admin Only", description = "Admin only endpoint (requires ADMIN role)")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> adminOnlyEndpoint() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Note: In a real application, you would check for ADMIN role here
        // For demo purposes, we'll just return the user info
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "This is an admin-only endpoint");
        result.put("adminUser", auth.getName());
        result.put("authorities", auth.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList()));
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/user-data/{userId}")
    @Operation(summary = "User Data", description = "Get user-specific data")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getUserData(@PathVariable String userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> result = new HashMap<>();
        result.put("requestedUserId", userId);
        result.put("requestedBy", auth.getName());
        result.put("userData", Map.of(
                "id", userId,
                "name", "Sample User " + userId,
                "email", "user" + userId + "@example.com",
                "lastLogin", LocalDateTime.now().minusDays(1)
        ));
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }
}
