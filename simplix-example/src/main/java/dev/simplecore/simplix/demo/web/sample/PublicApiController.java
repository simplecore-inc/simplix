package dev.simplecore.simplix.demo.web.sample;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public API", description = "Public APIs that don't require authentication")
public class PublicApiController {

    @GetMapping("/hello")
    @Operation(summary = "Hello World", description = "Simple hello world endpoint")
    public ResponseEntity<SimpliXApiResponse<String>> hello() {
        return ResponseEntity.ok(SimpliXApiResponse.success("Hello, World!"));
    }

    @GetMapping("/time")
    @Operation(summary = "Current Time", description = "Get current server time")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getCurrentTime() {
        Map<String, Object> result = new HashMap<>();
        result.put("serverTime", LocalDateTime.now());
        result.put("timezone", "Asia/Seoul");
        result.put("message", "Current server time");
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/status")
    @Operation(summary = "Service Status", description = "Get service status information")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "SimpliX Demo");
        result.put("version", "1.0.0");
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @PostMapping("/echo")
    @Operation(summary = "Echo Message", description = "Echo back the received message")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> echo(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        result.put("received", payload);
        result.put("timestamp", LocalDateTime.now());
        result.put("message", "Echo successful");
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    @GetMapping("/info/{id}")
    @Operation(summary = "Get Info", description = "Get information by ID")
    public ResponseEntity<SimpliXApiResponse<Map<String, Object>>> getInfo(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("type", "public-info");
        result.put("description", "This is public information for ID: " + id);
        result.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }
}
