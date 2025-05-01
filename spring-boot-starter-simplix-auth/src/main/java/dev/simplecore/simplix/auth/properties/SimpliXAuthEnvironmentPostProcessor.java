package dev.simplecore.simplix.auth.properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class SimpliXAuthEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        
        // Default settings
        defaults.put("simplix.auth.enabled", true);
        defaults.put("simplix.auth.access-token-validity-minutes", 60);
        defaults.put("simplix.auth.refresh-token-validity-days", 30);
        defaults.put("simplix.auth.login-page-template", "login");
        defaults.put("simplix.auth.login-processing-url", "/login");
        defaults.put("simplix.auth.logout-url", "/logout");
        
        // Security default settings
        defaults.put("simplix.auth.security.require-https", false);
        defaults.put("simplix.auth.security.enable-csrf", true);
        defaults.put("simplix.auth.security.enable-xss-protection", true);
        defaults.put("simplix.auth.security.enable-frame-options", true);
        defaults.put("simplix.auth.security.enable-hsts", true);
        defaults.put("simplix.auth.security.hsts-max-age-seconds", 31536000);
        defaults.put("simplix.auth.security.enable-cors", true);
        
        // CORS default settings
        String serverPort = environment.getProperty("server.port", "8080");
        defaults.put("simplix.auth.cors.allowed-origins", "http://localhost:" + serverPort);
        defaults.put("simplix.auth.cors.allowed-methods", "*");
        defaults.put("simplix.auth.cors.allowed-headers", "*");
        defaults.put("simplix.auth.cors.exposed-headers", "Authorization");
        defaults.put("simplix.auth.cors.allow-credentials", true);
        defaults.put("simplix.auth.cors.max-age", 3600L);
        
        environment.getPropertySources()
            .addLast(new MapPropertySource("simplixAuthDefaults", defaults));
    }
} 