package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-web-security", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthLoginController {
    
    private final SimpliXAuthProperties properties;
    
    @GetMapping("/login")
    public String login() {
        return properties.getSecurity().getLoginPageTemplate();
    }
} 